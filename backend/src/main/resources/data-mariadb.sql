-- Ensure MariaDB enum columns include newly supported adapter types.
ALTER TABLE reconciliation_sources
  MODIFY adapter_type ENUM('CSV_FILE','EXCEL_FILE','FIXED_WIDTH_FILE','XML_FILE','JSON_FILE','DATABASE','REST_API','MESSAGE_QUEUE','LLM_DOCUMENT');

SELECT 1;
