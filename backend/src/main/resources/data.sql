INSERT INTO reconciliation_definitions (id, code, name, description, maker_checker_enabled)
VALUES (
    1,
    'CASH_VS_GL',
    'Cash vs General Ledger',
    'Phase 2 sample reconciliation with configurable matching and maker-checker workflow.',
    true);

INSERT INTO reconciliation_fields (
    id,
    definition_id,
    source_field,
    display_name,
    role,
    data_type,
    comparison_logic,
    threshold_percentage)
VALUES
  (1, 1, 'transactionId', 'Transaction ID', 'KEY', 'STRING', 'EXACT_MATCH', NULL),
  (2, 1, 'amount', 'Amount', 'COMPARE', 'DECIMAL', 'NUMERIC_THRESHOLD', 5.0),
  (3, 1, 'currency', 'Currency', 'COMPARE', 'STRING', 'CASE_INSENSITIVE', NULL),
  (4, 1, 'tradeDate', 'Trade Date', 'COMPARE', 'DATE', 'DATE_ONLY', NULL),
  (5, 1, 'product', 'Product', 'PRODUCT', 'STRING', 'EXACT_MATCH', NULL),
  (6, 1, 'subProduct', 'Sub Product', 'SUB_PRODUCT', 'STRING', 'EXACT_MATCH', NULL),
  (7, 1, 'entityName', 'Entity', 'ENTITY', 'STRING', 'EXACT_MATCH', NULL),
  (8, 1, 'transactionId', 'Transaction ID', 'DISPLAY', 'STRING', 'EXACT_MATCH', NULL),
  (9, 1, 'amount', 'Amount', 'DISPLAY', 'DECIMAL', 'NUMERIC_THRESHOLD', 5.0),
  (10, 1, 'currency', 'Currency', 'DISPLAY', 'STRING', 'CASE_INSENSITIVE', NULL),
  (11, 1, 'tradeDate', 'Trade Date', 'DISPLAY', 'DATE', 'DATE_ONLY', NULL);

INSERT INTO access_control_entries (
    id,
    ldap_group_dn,
    definition_id,
    product,
    sub_product,
    entity_name,
    role)
VALUES
  (1, 'recon-makers', 1, 'Payments', 'Wire', 'US', 'MAKER'),
  (2, 'recon-checkers', 1, 'Payments', 'Wire', 'US', 'CHECKER');

INSERT INTO source_a_records (
    id,
    transaction_id,
    amount,
    currency,
    trade_date,
    product,
    sub_product,
    entity_name)
VALUES
  (1, 'TXN-1001', 100.00, 'USD', '2024-04-01', 'Payments', 'Wire', 'US'),
  (2, 'TXN-1002', 250.50, 'USD', '2024-04-01', 'Payments', 'Wire', 'US'),
  (3, 'TXN-1003', 75.25, 'EUR', '2024-04-02', 'Payments', 'Wire', 'EU');

INSERT INTO source_b_records (
    id,
    transaction_id,
    amount,
    currency,
    trade_date,
    product,
    sub_product,
    entity_name)
VALUES
  (1, 'TXN-1001', 100.00, 'USD', '2024-04-01', 'Payments', 'Wire', 'US'),
  (2, 'TXN-1002', 200.50, 'USD', '2024-04-01', 'Payments', 'Wire', 'US'),
  (3, 'TXN-1004', 50.00, 'USD', '2024-04-02', 'Payments', 'Wire', 'US');

INSERT INTO report_templates (
    id,
    definition_id,
    name,
    description,
    include_matched,
    include_mismatched,
    include_missing,
    highlight_differences)
VALUES (
    1,
    1,
    'Phase 3 Default Template',
    'Configured export layout for the cash vs GL reconciliation.',
    true,
    true,
    true,
    true);

INSERT INTO report_columns (
    id,
    template_id,
    header,
    source,
    source_field,
    display_order,
    highlight_differences)
VALUES
  (1, 1, 'Transaction ID (A)', 'SOURCE_A', 'transactionId', 1, true),
  (2, 1, 'Transaction ID (B)', 'SOURCE_B', 'transactionId', 2, true),
  (3, 1, 'Amount (A)', 'SOURCE_A', 'amount', 3, true),
  (4, 1, 'Amount (B)', 'SOURCE_B', 'amount', 4, true),
  (5, 1, 'Currency (A)', 'SOURCE_A', 'currency', 5, true),
  (6, 1, 'Currency (B)', 'SOURCE_B', 'currency', 6, true),
  (7, 1, 'Trade Date (A)', 'SOURCE_A', 'tradeDate', 7, true),
  (8, 1, 'Trade Date (B)', 'SOURCE_B', 'tradeDate', 8, true),
  (9, 1, 'Workflow Status', 'BREAK_METADATA', 'status', 9, false);
