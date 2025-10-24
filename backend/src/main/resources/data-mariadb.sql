-- Ensure MariaDB enum columns include newly supported adapter types.
ALTER TABLE reconciliation_sources
  MODIFY adapter_type ENUM('CSV_FILE','EXCEL_FILE','FIXED_WIDTH_FILE','XML_FILE','JSON_FILE','DATABASE','REST_API','MESSAGE_QUEUE','LLM_DOCUMENT');

ALTER TABLE canonical_field_mappings
  DROP COLUMN IF EXISTS transformation_expression;

SELECT 1;
