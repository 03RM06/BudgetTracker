# BudgetTracker — Architecture

Author: lr.rmmaray@mmdc.mcl.edu.ph
Stack: Java 25 · Maven · MySQL 8.0 · JavaFX · JUnit 5

## Conventions (binding)
- **Money:** `BigDecimal` in Java, `DECIMAL(19,4)` in MySQL. Never `double`/`float`/`FLOAT`/`DOUBLE`.
  Build BigDecimal from `String` (`new BigDecimal("0.00")`), never from a double literal.
- **Time:** `java.time` only. Persisted timestamps are **UTC** `DATETIME`. Calendar dates (when money
  "happened" from the user's view) are `DATE`. Recurrence carries an explicit IANA zone.
- **Layer direction:** `ui -> service -> persistence -> MySQL`. Arrows are one-way.

---

## 1. Module list

One Maven artifact, five packages under `com.budgettracker`.

```
com.budgettracker
├── domain        // pure model: entities, enums, value objects, domain exceptions, business rules
├── persistence   // repositories (interfaces + JDBC impls), Database, row mappers, SQL
├── service       // orchestration, validation, recurrence engine, budget math, tx boundaries
├── ui            // JavaFX: App entry, controllers, FXML, view models, formatters
└── util          // leaf helpers: money/date formatting, zone helpers, password hashing, config
```

| Module | Owns | Must NOT do |
|---|---|---|
| `domain` | Entity types (records/POJOs), enums (`AccountType`, `TxDirection`, `CategoryType`, `IncomeNature`, `Frequency`, `PeriodType`, `BudgetStatus`), value objects, domain exceptions, pure invariants. | No JDBC / no `java.sql` import. No JavaFX. No SQL. No I/O. |
| `persistence` | `Database` (connection singleton), repository interfaces and JDBC implementations, `ResultSet`→entity mappers, all SQL strings, `setBigDecimal`/`getBigDecimal`. | No business rules. No JavaFX. Returns `BigDecimal`/`java.time`, never `double`/`String` money. |
| `service` | Use-case orchestration, input validation, transaction boundaries, recurrence materialization engine, budget-envelope spend calculation and alert evaluation, balance recomputation. | No `java.sql` and no raw SQL. No JavaFX. Does not open connections directly. |
| `ui` | JavaFX `Application` entry point, FXML views, controllers, view models, screen navigation, calling services, formatting via `util`. | Never imports `persistence` or `java.sql`. Never calls a repository. Contains no money math or business rules. |
| `util` | Stateless helpers: `MoneyFormat`, `Dates`/`Zones`, `PasswordHasher` (BCrypt), `AppConfig`. | No business rules. No dependency on `domain`, `persistence`, `service`, or `ui` (leaf module). |

Dependency-direction rule:
`util` ← everyone; `domain` ← persistence, service, ui; `persistence` ← service; `service` ← ui.

---

## 2. Entity list

Enums (in `domain`):
- `AccountType { CASH, BANK, EWALLET }`
- `TxDirection { INCOME, EXPENSE, TRANSFER }`
- `CategoryType { INCOME, EXPENSE }`
- `IncomeNature { FIXED, VARIABLE, ONE_TIME }`
- `Frequency { DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY }`
- `PeriodType { WEEKLY, MONTHLY, YEARLY, CUSTOM }`

### 2.1 User
| Field | Java type | Notes |
|---|---|---|
| id | `long` | PK |
| email | `String` | unique, not null |
| displayName | `String` | not null |
| passwordHash | `String` | BCrypt hash |
| baseCurrency | `String` | ISO 4217, 3 chars, default `PHP` |
| defaultZoneId | `String` | IANA zone, e.g. `Asia/Manila` |
| createdAt | `Instant` | UTC |
| updatedAt | `Instant` | UTC |

### 2.2 Account
| Field | Java type | Notes |
|---|---|---|
| id | `long` | PK |
| userId | `long` | FK → users |
| name | `String` | not null, unique per user |
| type | `AccountType` | CASH/BANK/EWALLET |
| openingBalance | `BigDecimal` | DECIMAL(19,4) |
| currentBalance | `BigDecimal` | DECIMAL(19,4), cached |
| currency | `String` | ISO 4217 |
| archived | `boolean` | soft-hide |
| createdAt/updatedAt | `Instant` | UTC |

### 2.3 Category
| Field | Java type | Notes |
|---|---|---|
| id | `long` | PK |
| userId | `long` | FK → users |
| name | `String` | not null |
| type | `CategoryType` | INCOME or EXPENSE |
| parentId | `Long` | nullable self-FK for sub-categories |
| createdAt/updatedAt | `Instant` | UTC |

### 2.4 Transaction
| Field | Java type | Notes |
|---|---|---|
| id | `long` | PK |
| userId | `long` | FK → users |
| accountId | `long` | FK → accounts |
| categoryId | `Long` | nullable FK → categories |
| direction | `TxDirection` | INCOME/EXPENSE/TRANSFER |
| amount | `BigDecimal` | DECIMAL(19,4), always positive |
| currency | `String` | ISO 4217 |
| occurredOn | `LocalDate` | user-local day money moved |
| occurredAt | `Instant` | optional precise UTC instant |
| incomeNature | `IncomeNature` | nullable; set when direction = INCOME |
| description | `String` | nullable |
| recurringId | `Long` | nullable FK → recurring_transactions |
| transferAccountId | `Long` | nullable FK → accounts (TRANSFER only) |
| exchangeRate | `BigDecimal` | nullable; DECIMAL(19,8); set only for cross-currency TRANSFER |
| createdAt/updatedAt | `Instant` | UTC |

Rules: amount > 0; INCOME requires non-null incomeNature; TRANSFER requires transferAccountId ≠ accountId. A TRANSFER is stored as **one row**: `accountId = source`, `transferAccountId = destination`. Balance computation uses a second query (`WHERE transfer_account_id = ?`) to credit the destination account.

### 2.5 RecurringTransaction
| Field | Java type | Notes |
|---|---|---|
| id | `long` | PK |
| userId | `long` | FK → users |
| accountId | `long` | FK → accounts |
| categoryId | `Long` | nullable FK → categories |
| direction | `TxDirection` | INCOME or EXPENSE (no recurring transfers v1) |
| templateAmount | `BigDecimal` | DECIMAL(19,4); estimate when variableAmount = true |
| variableAmount | `boolean` | true → confirm actual amount at materialization |
| incomeNature | `IncomeNature` | FIXED or VARIABLE for income rules |
| currency | `String` | ISO 4217 |
| frequency | `Frequency` | DAILY…YEARLY |
| intervalCount | `int` | every N units |
| anchorDate | `LocalDate` | first occurrence, user-local |
| zoneId | `String` | IANA zone — timezone-safe recurrence |
| dayOfMonth | `Integer` | nullable; clamped to month length |
| nextRunDate | `LocalDate` | next date to materialize |
| endDate | `LocalDate` | nullable hard stop |
| maxOccurrences | `Integer` | nullable cap |
| active | `boolean` | pause without deleting |
| description | `String` | nullable |
| createdAt/updatedAt | `Instant` | UTC |

### 2.6 BudgetEnvelope
| Field | Java type | Notes |
|---|---|---|
| id | `long` | PK |
| userId | `long` | FK → users |
| categoryId | `long` | FK → categories (EXPENSE only) |
| name | `String` | not null |
| periodType | `PeriodType` | WEEKLY/MONTHLY/YEARLY/CUSTOM |
| periodStart | `LocalDate` | inclusive |
| periodEnd | `LocalDate` | inclusive |
| limitAmount | `BigDecimal` | DECIMAL(19,4), > 0 |
| rollover | `boolean` | carry unspent to next period |
| alertThresholdPct | `BigDecimal` | DECIMAL(5,2), default 80.00 |
| zoneId | `String` | IANA zone for period boundaries |
| active | `boolean` | |
| createdAt/updatedAt | `Instant` | UTC |

---

## 3. MySQL schema DDL

UTC convention: every `DATETIME` stores a UTC instant. JDBC URL pins `serverTimezone=UTC`.
`DATE` columns hold user-local calendar dates (no timezone shift). No `FLOAT`/`DOUBLE` on any monetary column.

```sql
CREATE DATABASE IF NOT EXISTS budgettracker_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE budgettracker_db;

CREATE TABLE users (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  email           VARCHAR(255) NOT NULL,
  display_name    VARCHAR(100) NOT NULL,
  password_hash   CHAR(60)     NOT NULL,
  base_currency   CHAR(3)      NOT NULL DEFAULT 'PHP',
  default_zone_id VARCHAR(64)  NOT NULL DEFAULT 'Asia/Manila',
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE accounts (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL,
  name            VARCHAR(100)  NOT NULL,
  account_type    ENUM('CASH','BANK','EWALLET') NOT NULL,
  opening_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
  current_balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
  currency        CHAR(3)       NOT NULL DEFAULT 'PHP',
  archived        TINYINT(1)    NOT NULL DEFAULT 0,
  created_at      DATETIME      NOT NULL,
  updated_at      DATETIME      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_accounts_user_name (user_id, name),
  KEY ix_accounts_user (user_id),
  CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE categories (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  name            VARCHAR(100) NOT NULL,
  category_type   ENUM('INCOME','EXPENSE') NOT NULL,
  parent_id       BIGINT       NULL,
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_categories_user_name_type (user_id, name, category_type),
  KEY ix_categories_user (user_id),
  CONSTRAINT fk_categories_user   FOREIGN KEY (user_id)   REFERENCES users(id),
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
) ENGINE=InnoDB;

CREATE TABLE recurring_transactions (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL,
  account_id      BIGINT        NOT NULL,
  category_id     BIGINT        NULL,
  direction       ENUM('INCOME','EXPENSE') NOT NULL,
  template_amount DECIMAL(19,4) NOT NULL,
  variable_amount TINYINT(1)    NOT NULL DEFAULT 0,
  income_nature   ENUM('FIXED','VARIABLE','ONE_TIME') NULL,
  currency        CHAR(3)       NOT NULL DEFAULT 'PHP',
  frequency       ENUM('DAILY','WEEKLY','BIWEEKLY','MONTHLY','QUARTERLY','YEARLY') NOT NULL,
  interval_count  INT           NOT NULL DEFAULT 1,
  anchor_date     DATE          NOT NULL,
  zone_id         VARCHAR(64)   NOT NULL,
  day_of_month    INT           NULL,
  next_run_date   DATE          NULL,
  end_date        DATE          NULL,
  max_occurrences INT           NULL,
  active          TINYINT(1)    NOT NULL DEFAULT 1,
  description     VARCHAR(255)  NULL,
  created_at      DATETIME      NOT NULL,
  updated_at      DATETIME      NOT NULL,
  PRIMARY KEY (id),
  KEY ix_recurring_user (user_id),
  KEY ix_recurring_next_run (active, next_run_date),
  CONSTRAINT fk_recurring_user     FOREIGN KEY (user_id)     REFERENCES users(id),
  CONSTRAINT fk_recurring_account  FOREIGN KEY (account_id)  REFERENCES accounts(id),
  CONSTRAINT fk_recurring_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT ck_recurring_amount   CHECK (template_amount > 0),
  CONSTRAINT ck_recurring_interval CHECK (interval_count >= 1)
) ENGINE=InnoDB;

CREATE TABLE transactions (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  user_id             BIGINT        NOT NULL,
  account_id          BIGINT        NOT NULL,
  category_id         BIGINT        NULL,
  direction           ENUM('INCOME','EXPENSE','TRANSFER') NOT NULL,
  amount              DECIMAL(19,4) NOT NULL,
  currency            CHAR(3)       NOT NULL DEFAULT 'PHP',
  occurred_on         DATE          NOT NULL,
  occurred_at         DATETIME      NULL,
  income_nature       ENUM('FIXED','VARIABLE','ONE_TIME') NULL,
  description         VARCHAR(255)  NULL,
  recurring_id        BIGINT        NULL,
  exchange_rate       DECIMAL(19,8) NULL,           -- only for cross-currency transfers
  transfer_account_id BIGINT        NULL,
  created_at          DATETIME      NOT NULL,
  updated_at          DATETIME      NOT NULL,
  PRIMARY KEY (id),
  KEY ix_tx_user_date (user_id, occurred_on),
  KEY ix_tx_account (account_id),
  KEY ix_tx_category_date (category_id, occurred_on),
  KEY ix_tx_recurring (recurring_id),
  CONSTRAINT fk_tx_user      FOREIGN KEY (user_id)             REFERENCES users(id),
  CONSTRAINT fk_tx_account   FOREIGN KEY (account_id)          REFERENCES accounts(id),
  CONSTRAINT fk_tx_category  FOREIGN KEY (category_id)         REFERENCES categories(id),
  CONSTRAINT fk_tx_recurring FOREIGN KEY (recurring_id)        REFERENCES recurring_transactions(id),
  CONSTRAINT fk_tx_transfer  FOREIGN KEY (transfer_account_id) REFERENCES accounts(id),
  CONSTRAINT ck_tx_amount    CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE TABLE budget_envelopes (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  user_id             BIGINT        NOT NULL,
  category_id         BIGINT        NOT NULL,
  name                VARCHAR(100)  NOT NULL,
  period_type         ENUM('WEEKLY','MONTHLY','YEARLY','CUSTOM') NOT NULL,
  period_start        DATE          NOT NULL,
  period_end          DATE          NOT NULL,
  limit_amount        DECIMAL(19,4) NOT NULL,
  rollover            TINYINT(1)    NOT NULL DEFAULT 0,
  alert_threshold_pct DECIMAL(5,2)  NOT NULL DEFAULT 80.00,
  zone_id             VARCHAR(64)   NOT NULL,
  active              TINYINT(1)    NOT NULL DEFAULT 1,
  created_at          DATETIME      NOT NULL,
  updated_at          DATETIME      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_envelope_user_cat_period (user_id, category_id, period_type, period_start),
  KEY ix_envelope_user (user_id),
  CONSTRAINT fk_envelope_user     FOREIGN KEY (user_id)     REFERENCES users(id),
  CONSTRAINT fk_envelope_category FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT ck_envelope_limit    CHECK (limit_amount > 0),
  CONSTRAINT ck_envelope_period   CHECK (period_end >= period_start)
) ENGINE=InnoDB;
```

---

## 4. pom.xml dependency block

```xml
<properties>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <maven.compiler.source>25</maven.compiler.source>
  <maven.compiler.target>25</maven.compiler.target>
  <javafx.version>25</javafx.version>
  <junit.version>5.11.3</junit.version>
</properties>

<dependencies>
  <!-- JavaFX UI controls (Stage, Scene, Button, TableView, charts) -->
  <dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>${javafx.version}</version>
  </dependency>

  <!-- JavaFX FXML: declarative views loaded by FXMLLoader -->
  <dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-fxml</artifactId>
    <version>${javafx.version}</version>
  </dependency>

  <!-- MySQL JDBC driver: only path from persistence layer to MySQL 8 -->
  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.1.0</version>
  </dependency>

  <!-- BCrypt password hashing for user login -->
  <dependency>
    <groupId>org.mindrot</groupId>
    <artifactId>jbcrypt</artifactId>
    <version>0.4</version>
  </dependency>

  <!-- JUnit 5 API: write unit tests -->
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <version>${junit.version}</version>
    <scope>test</scope>
  </dependency>

  <!-- JUnit 5 Engine: runtime to execute Jupiter tests -->
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-engine</artifactId>
    <version>${junit.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.13.0</version>
    </plugin>
    <plugin>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-maven-plugin</artifactId>
      <version>0.0.8</version>
      <configuration>
        <mainClass>com.budgettracker.ui.App</mainClass>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.5.2</version>
    </plugin>
  </plugins>
</build>
```

---

## 5. Data-flow description

```
JavaFX controller (ui)
  reads form → builds request with amount as BigDecimal, occurredOn as LocalDate
        |
        v
TransactionService (service)
  validates, opens JDBC transaction, calls repositories, recomputes currentBalance,
  evaluates BudgetEnvelopes, commits / rolls back
        |
        v
TransactionRepository + AccountRepository (persistence)
  PreparedStatement.setBigDecimal() for money, UTC DATETIME for timestamps, DATE for occurredOn
        |
        v
MySQL (budgettracker_db)
  DECIMAL(19,4) money columns, DATETIME (UTC), DATE (local)
```

Money is `BigDecimal` end to end. Dates/times are `java.time` end to end.
`ui` never holds a `Connection` and never imports `persistence`.

---

## 6. Irregular / variable income model

Three mechanisms:

1. **Classification on every income transaction** — `Transaction.incomeNature` ∈ `{FIXED, VARIABLE, ONE_TIME}`. Separates dependable income from unpredictable income in reports.

2. **One-time / irregular income = a plain Transaction.** No recurrence rule required. A user logs a single income entry with `direction = INCOME`, `incomeNature = ONE_TIME` (or `VARIABLE`), its own `amount` (BigDecimal) and `occurredOn` (LocalDate).

3. **Variable recurring income = estimate-then-confirm.** A `RecurringTransaction` with `direction = INCOME`, `incomeNature = VARIABLE`, and `variable_amount = 1` stores `template_amount` as an **estimate** for forecasting. When the recurrence engine reaches `next_run_date`, it creates a pending materialization that the user confirms with the actual received amount; the resulting `Transaction.amount` is the confirmed BigDecimal. Fixed income (`variable_amount = 0`) materializes straight to a confirmed transaction.

Materialization engine (service): on app start / on demand, for each active recurring rule where `next_run_date <= today-in-rule-zone`, generate occurrences, advance `next_run_date` in the rule's `ZoneId` (clamp `day_of_month` to month length), stop at `end_date`/`max_occurrences`. Idempotency: skip any (`recurring_id`, `occurred_on`) already materialized.

---

## 7. Budget envelope model

An envelope = **one EXPENSE category + a period + a limit** (`category_id`, `period_start`/`period_end`, `limit_amount`). Optional `rollover` and `alert_threshold_pct` (default 80%). Period boundaries computed in `zone_id`.

Spend (computed in `service`):
```
spent     = Σ transactions.amount WHERE direction='EXPENSE' AND category_id=? AND occurred_on BETWEEN period_start AND period_end
remaining = effectiveLimit - spent       // BigDecimal
usagePct  = spent / effectiveLimit * 100 // BigDecimal, HALF_EVEN
```
Transfers (direction=TRANSFER) are excluded from spend.

Rollover: `effectiveLimit = limit_amount + max(0, prior period remaining)` when rollover=1.

Alert states (evaluated by `BudgetService` after any expense add/edit/delete):
- **OK** — `spent < effectiveLimit * alertThresholdPct / 100`
- **WARN** — `spent >= effectiveLimit * alertThresholdPct / 100` and `spent < effectiveLimit`
- **OVER** — `spent > effectiveLimit`

Service returns status enum + BigDecimal figures; `ui` renders the banner/color.

---

## Open questions
1. Single-user vs multi-user: schema is multi-user-ready, but is login/multi-account in v1 scope?
2. Multi-currency now or later? Recommend PHP only for v1, defer FX conversion.
3. JavaFX GA version against Java 25: pin exact published OpenJFX version at build time.
4. Recurrence trigger: app-start materialization (recommended) vs scheduled job.
5. `current_balance` cached on account row vs always-computed.
