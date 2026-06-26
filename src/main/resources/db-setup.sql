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
