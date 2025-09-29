CREATE TABLE cash_ledger (
    transaction_id VARCHAR(32) PRIMARY KEY,
    amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    trade_date DATE NOT NULL,
    product VARCHAR(64),
    sub_product VARCHAR(64),
    entity_name VARCHAR(64)
);
