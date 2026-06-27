ALTER TABLE transactions
  ADD COLUMN status ENUM('PENDING','POSTED','CANCELLED') NOT NULL DEFAULT 'POSTED'
  AFTER direction;
CREATE INDEX ix_tx_status ON transactions (user_id, status);
