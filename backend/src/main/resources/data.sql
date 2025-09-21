INSERT INTO reconciliation_definitions (id, code, name, description, maker_checker_enabled)
VALUES (1, 'CASH_VS_GL', 'Cash vs General Ledger', 'Phase 1 sample reconciliation using exact matching.', false);

INSERT INTO reconciliation_fields (id, definition_id, source_field, display_name, role)
VALUES
  (1, 1, 'transactionId', 'Transaction ID', 'KEY'),
  (2, 1, 'amount', 'Amount', 'COMPARE'),
  (3, 1, 'currency', 'Currency', 'COMPARE'),
  (4, 1, 'tradeDate', 'Trade Date', 'COMPARE');

INSERT INTO access_control_entries (id, ldap_group_dn, definition_id, permission_scope)
VALUES (1, 'recon-ops', 1, 'VIEW');

INSERT INTO source_a_records (id, transaction_id, amount, currency, trade_date)
VALUES
  (1, 'TXN-1001', 100.00, 'USD', '2024-04-01'),
  (2, 'TXN-1002', 250.50, 'USD', '2024-04-01'),
  (3, 'TXN-1003', 75.25, 'EUR', '2024-04-02');

INSERT INTO source_b_records (id, transaction_id, amount, currency, trade_date)
VALUES
  (1, 'TXN-1001', 100.00, 'USD', '2024-04-01'),
  (2, 'TXN-1002', 200.50, 'USD', '2024-04-01'),
  (3, 'TXN-1004', 50.00, 'USD', '2024-04-02');
