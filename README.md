# BudgetTracker

A personal finance desktop application built with Java 25 and JavaFX 25. School capstone project at MMDC / MCL.

---

## 1. Project Overview

BudgetTracker lets a user manage their personal finances from a single desktop window connected to a local MySQL database. Core features:

- **Multi-account** — track Cash, Bank, and e-wallet accounts, each with its own currency and running balance.
- **Multi-currency** — accounts carry their own ISO 4217 currency; cross-currency transfers require a user-supplied exchange rate.
- **Budget envelopes** — weekly, monthly, yearly, or custom spending limits per expense category, with configurable alert thresholds (OK / WARN / OVER) and optional rollover.
- **Recurring rules** — fixed or variable-amount rules at any frequency (daily through yearly); the recurrence engine materialises due entries automatically on app startup.
- **Variable-income pending confirmation** — recurring income rules marked as variable generate a pending transaction that the user confirms with the actual received amount before it is posted to the account balance.

**Tech stack:** Java 25, JavaFX 25 (OpenJFX 25.0.1), MySQL 8.0, Maven (bundled in NetBeans), HikariCP 6.2.1, Flyway 10.20.1, SLF4J 2.0 / Logback 1.5, jBCrypt 0.4, JUnit 5.11.

---

## 2. Prerequisites

| Requirement | Version |
|---|---|
| Java (Oracle or OpenJDK) | 25+ |
| MySQL | 8.0+ |
| Apache NetBeans | any recent release (bundles Maven — `mvn` is **not** a standalone install) |
| Git | any |

> `mvn` is **not on the system PATH**. Use NetBeans to build and run, or call the full path to the bundled `mvn.cmd` (see Section 5).

---

## 3. Database Setup (one-time)

Create the database in MySQL Workbench or a MySQL client before the first run:

```sql
CREATE DATABASE budgettracker_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

That is all. **Flyway handles every table automatically** — all schema migrations (`V1` through `V3`) run when the application starts for the first time. Do not run any SQL migration files by hand.

---

## 4. Configuration

`AppConfig` reads credentials from environment variables first, then falls back to a local properties file.

### Option A — Environment variables (recommended)

Set these three variables before launching the app or NetBeans:

| Variable | Example value |
|---|---|
| `BT_DB_URL` | `jdbc:mysql://localhost:3306/budgettracker_db` |
| `BT_DB_USER` | `root` |
| `BT_DB_PASSWORD` | `your_password` |

In PowerShell (session-only):

```powershell
$env:BT_DB_URL      = "jdbc:mysql://localhost:3306/budgettracker_db"
$env:BT_DB_USER     = "root"
$env:BT_DB_PASSWORD = "your_password"
```

### Option B — Local properties file

Copy the example file and fill in your credentials:

```powershell
Copy-Item src\main\resources\config.properties.example `
          src\main\resources\config.properties
```

Then edit `src/main/resources/config.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/budgettracker_db
db.user=your_mysql_username
db.password=your_mysql_password
```

`config.properties` is listed in `.gitignore` and will never be committed.

---

## 5. Build and Run

### Option A — NetBeans (easiest)

1. Open NetBeans and choose **File > Open Project**, then select the `BudgetTracker` folder.
2. Press **Run** (F6) or **Shift+F6** on `App.java`. NetBeans uses its bundled Maven automatically.

### Option B — Terminal with the bundled Maven

First locate the NetBeans install directory:

```powershell
Get-ChildItem "C:\Program Files\" -Filter "NetBeans*"
```

Then use the full path to `mvn.cmd`:

```powershell
# Compile and launch the JavaFX app
& "C:\Program Files\NetBeans-<VERSION>\java\maven\bin\mvn.cmd" -f pom.xml javafx:run

# Run unit tests only (no UI)
& "C:\Program Files\NetBeans-<VERSION>\java\maven\bin\mvn.cmd" -f pom.xml test
```

Replace `<VERSION>` with the actual directory name found in the step above.

---

## 6. Project Structure

```
src/
├── main/
│   ├── java/com/budgettracker/
│   │   ├── domain/          # Entities, enums, value objects, domain exceptions
│   │   ├── persistence/     # Repository interfaces, JDBC implementations, Database, TxRunner
│   │   ├── service/         # Business logic: validation, recurrence engine, budget math
│   │   ├── ui/              # JavaFX App entry point, AppContext, Session, controllers
│   │   └── util/            # AppConfig, PasswordHasher, MoneyFormat, Dates (leaf helpers)
│   └── resources/
│       ├── config.properties.example   # Copy and rename; gitignored when renamed
│       ├── db/migration/               # Flyway versioned SQL (V1–V3)
│       ├── fxml/                       # JavaFX FXML screen definitions
│       ├── css/                        # Application stylesheets
│       └── logback.xml                 # Logging configuration
└── test/
    └── java/com/budgettracker/service/ # JUnit 5 service-layer unit tests
```

---

## 7. Screen Guide

| Screen | Purpose |
|---|---|
| **Login / Register** | Sign in with email and password, or create a new account. |
| **Dashboard Home** | Overview cards showing total accounts, net balance, and pending transaction count, plus active budget alerts. |
| **Accounts** | View and manage bank, cash, and e-wallet accounts. Add new accounts or archive inactive ones. |
| **Transactions** | Add, edit, and delete income, expense, and transfer entries. Filter by date range, account, or direction. |
| **Budgets** | Manage monthly, weekly, or custom spending envelopes with live OK / WARN / OVER status badges. |
| **Recurring** | Create and view recurring rules (fixed or variable). Rules are materialized automatically on startup. |
| **Pending Confirm** | Review pending variable-income entries generated by recurring rules. Confirm with the actual amount received or cancel. |

---

## 8. Logs

Log output goes to both the console and a rolling file:

- Active log file: `logs/budgettracker.log` (created relative to the working directory)
- Daily rollover pattern: `logs/budgettracker.YYYY-MM-DD.log`
- Retention: 30 days (`maxHistory=30` in `logback.xml`)

---

## 9. Known Limitations

- **Requires a running MySQL instance.** There is no embedded or offline mode.
- **Recurring rule toggle not yet wired.** The enable/disable button in the Recurring screen does not persist the `active` flag (repository `findById`/`update` for rules pending).
- **Transaction list shows account ID, not account name.** The view-model lookup to resolve account names is not yet implemented.
- **No automated UI tests.** The service layer has 60 unit tests; UI correctness requires manual verification.
- **`mvn` is not on the system PATH.** Use NetBeans or the full path to the bundled `mvn.cmd` (see Section 5).

---

## 10. Development Notes

### Secrets

Never commit `config.properties`. Always use environment variables in shared or CI environments, or keep a local, untracked copy of the properties file.

### Schema migrations

Add new schema changes as `V{n}__description.sql` in `src/main/resources/db/migration/`. Flyway picks them up automatically on the next application startup. `baselineOnMigrate=true` is configured, so existing databases are baselined at V0 and only new migration files are applied.

### Dependency injection

All services are wired in `AppContext.java`. Controllers retrieve services via `AppContext.get()`. Never construct a service directly inside a controller and never import `persistence` or `java.sql` from the `ui` package.

### Architecture rules

The one-way layering contract is `ui → service → persistence → MySQL`. See `ARCHITECTURE.md` for the full module contract and the list of what each layer must not do.
