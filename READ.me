# BudgetTracker — Project Log

## Project
- **Name:** BudgetTracker
- **Stack:** Java 25, Maven, MySQL 8.0, JUnit 5, JavaFX
- **Author:** lr.rmmaray@mmdc.mcl.edu.ph

## Conventions (apply to every cycle)
- Money in Java: `BigDecimal` only — never `double`/`float`. Construct from `String`, never `double`.
- Money in MySQL: `DECIMAL(19,4)` only — never `FLOAT`/`DOUBLE`.
- Dates/times in Java: `java.time` only (`Instant`, `LocalDate`, `LocalDateTime`, `ZonedDateTime`, `ZoneId`).
- DB timestamps stored in **UTC** as `DATETIME`. Calendar-only fields stored as `DATE` (user local).
- Layering: `ui -> service -> persistence -> MySQL`. `ui` never touches `java.sql` or repositories. `domain` has no JDBC and no JavaFX.
- Package root: `com.budgettracker`.

## Cycle 1 — Architecture
- **Task:** System design (modules, entities, DDL, pom, data flow, variable-income model, budget envelopes)
- **Owner:** Architect
- **Status:** Complete
- **Decisions made:**
  1. Single Maven artifact with enforced package boundaries (`domain`, `persistence`, `service`, `ui`, `util`) instead of multi-module reactor — multi-module is overkill for a single-user desktop app and slows the build; boundaries are enforced by package discipline + a dependency-direction rule.
  2. Account type modeled as a MySQL `ENUM('CASH','BANK','EWALLET')` mirrored by a Java enum `AccountType` — fixed, small, closed set; no lookup table needed.
  3. Transactions store a positive `amount` plus a `direction` enum (`INCOME`/`EXPENSE`/`TRANSFER`); sign is never stored, it is derived from direction. Avoids negative-amount ambiguity.
  4. Money never crosses a layer as anything but `BigDecimal`; JDBC uses `getBigDecimal`/`setBigDecimal` exclusively.
  5. Recurrence is timezone-safe: each recurring rule stores an `anchor_date` (LocalDate) plus an IANA `zone_id` (e.g. `Asia/Manila`); next-occurrence math is done in that zone, never in UTC, so DST/offset shifts never move a "1st of the month" entry.
  6. Variable/irregular income is a first-class concept: income is classified by `income_nature` (`FIXED`/`VARIABLE`/`ONE_TIME`), and variable recurring income uses an estimate-then-confirm materialization flow (see ARCHITECTURE.md §6).
  7. Budget envelopes = category + period + limit, with a configurable alert threshold and optional rollover.
  8. Passwords hashed with BCrypt (consistent with the sibling payroll project).
- **Open questions resolved before Cycle 2:**
  - [x] Q1: **Multi-user with login** — login/registration screen in v1 scope. Schema already supports this.
  - [x] Q2: **Multi-currency** — accounts carry their own currency; transactions inherit account currency; cross-currency transfers require user-entered exchange rate; budgets and reports show per-currency totals (no external FX API).
  - [x] Q3: **JavaFX 25** — pin `javafx.version=25` (OpenJFX GA matches JDK major version since v11; verify artifact exists on Maven Central at build time).
  - [x] Q4: **App-start materialization** — recurrence engine runs on startup and on-demand; no background scheduler needed for a desktop app.
  - [x] Q5: **Cached `current_balance`** — stored on the `accounts` row, recomputed and written by `AccountService` after any transaction add/edit/delete. Single writer rule enforced in service.

## Cycle 2 — Maven scaffold + domain layer
- **Task:** pom.xml, Maven directory structure, db-setup.sql, 7 enums, 6 entities, 4 domain exceptions, App.java stub; ARCHITECTURE.md updated for exchangeRate
- **Owner:** Developer
- **Status:** Complete
- **Verified by Orchestrator:** BigDecimal on all money fields, java.time on all date/time fields, DECIMAL(19,4) in DDL, no FLOAT/DOUBLE anywhere. Exchange_rate column DECIMAL(19,8) added. All 30 files written.
- **Minor delta logged:** BudgetStatus enum added to domain package but was missing from ARCHITECTURE.md §2 enum list — fixed in Cycle 2 patch.

## Cycle 3 — util package + Database + repository interfaces
- **Task:** AppConfig, PasswordHasher, MoneyFormat, Dates (util); Database.java (persistence); 6 repository interfaces
- **Owner:** Developer
- **Status:** Complete
- **Verified:** Dates.java is the sole file with java.sql types. All repo interfaces use domain types / java.time only in signatures.
- **Key convention:** JDBC impls must use Dates.* helpers; never import java.sql.Date or Timestamp directly outside Dates.java.

## Cycle 4 — JDBC repository implementations
- **Task:** 6 concrete JDBC implementations for all repository interfaces
- **Owner:** Developer
- **Status:** Complete
- **Verified:** JdbcTransactionRepository — setBigDecimal/getBigDecimal on all money columns, Dates.* for all date conversions, no direct java.sql.Date/Timestamp imports, rs.wasNull() for nullable longs. Pattern consistent across all 6 impls.
- **Key decisions by Developer (logged):** user_id immutable on recurring + envelope UPDATE; dayOfMonth/maxOccurrences use rs.wasNull() after getInt(); updateBalance/updateNextRunDate stamp updated_at = Instant.now() internally.

## Cycle 5 — Service layer
- **Task:** UserService, AccountService, CategoryService, TransactionService, RecurrenceService, BudgetService
- **Owner:** Developer
- **Status:** Complete (with bug fix pending — see Cycle 5b)
- **Verified:** Zero java.sql imports in service layer. BigDecimal math with HALF_EVEN. All SQLExceptions wrapped.
- **Bug flagged by Developer:** TRANSFER balance logic conflict — spec said "two rows" but recomputeBalance was written for single-row semantics.

## Cycle 5b — TRANSFER single-row fix
- **Orchestrator decision:** Use single-row transfer model. One row per transfer: accountId=source, transferAccountId=dest. Remove paired-row creation from TransactionService. Add findTransferCreditsForAccount(long) to TransactionRepository + JdbcTransactionRepository. Fix recomputeBalance to use both queries.
- **Owner:** Developer
- **Status:** Complete. ARCHITECTURE.md §2.4 updated. Dead helper methods removed.

## Cycle 6 — QA: unit tests for service layer
- **Task:** JUnit 5 tests covering money math, transfer balance, recurrence date advancement, budget envelope alerts, idempotency
- **Owner:** QA Engineer
- **Status:** Complete
- **Verified:** 31 tests, 0 failures, 0 errors. No Mockito, no database. Manual in-memory stubs only. RecurrenceService.advanceDate made package-private to enable direct testing.
- **Test classes:** AccountServiceTest (6), BudgetServiceTest (9), RecurrenceServiceTest (9), TransactionServiceTest (7)

## Cycle 7 — JavaFX UI layer
- **Task:** Login/registration screen, main Dashboard shell, and primary FXML views (Accounts, Transactions, Budget Envelopes, Experience timeline)
- **Owner:** Developer
- **Status:** Planned
- **Scope:**
  - `App.java` fully wired (loads Login.fxml on start)
  - `LoginController.java` + `Login.fxml` — email/password form, calls UserService.login/register
  - `DashboardController.java` + `Dashboard.fxml` — nav sidebar, center content area swaps views
  - `AccountsController.java` + `Accounts.fxml` — table of accounts, add/archive actions
  - `TransactionsController.java` + `Transactions.fxml` — filterable transaction list, add/delete
  - `BudgetController.java` + `Budget.fxml` — envelope list with OK/WARN/OVER color badges
  - `RecurringController.java` + `Recurring.fxml` — recurring rules list, enable/disable
  - View models in `ui` package for data binding; no repository imports in any controller
  - CSS in `src/main/resources/css/` for basic styling

## Cycle Log
- 2026-06-25 — Cycle 1 architecture completed. Q1–Q5 resolved.
- 2026-06-25 — Cycle 2 complete. Maven scaffold + full domain layer verified.
- 2026-06-25 — Cycle 3 started. Developer building util + Database + repo interfaces.
- 2026-06-26 — Cycle 6 complete. 31 JUnit 5 unit tests written and passing. No Mockito, no DB. advanceDate made package-private. Cycle 7 (JavaFX UI) is next.
