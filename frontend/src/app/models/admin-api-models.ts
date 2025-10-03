export type ReconciliationLifecycleStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED';

export type FieldRole =
  | 'KEY'
  | 'COMPARE'
  | 'DISPLAY'
  | 'PRODUCT'
  | 'SUB_PRODUCT'
  | 'ENTITY'
  | 'CLASSIFIER'
  | 'ATTRIBUTE';

export type FieldDataType = 'STRING' | 'DECIMAL' | 'INTEGER' | 'DATE';

export type ComparisonLogic = 'EXACT_MATCH' | 'CASE_INSENSITIVE' | 'NUMERIC_THRESHOLD' | 'DATE_ONLY';

export type IngestionAdapterType =
  | 'CSV_FILE'
  | 'FIXED_WIDTH_FILE'
  | 'XML_FILE'
  | 'JSON_FILE'
  | 'DATABASE'
  | 'REST_API'
  | 'MESSAGE_QUEUE'
  | 'LLM_DOCUMENT';

export type ReportColumnSource = 'SOURCE_A' | 'SOURCE_B' | 'BREAK_METADATA';

export type AccessRole = 'VIEWER' | 'MAKER' | 'CHECKER';

export type DataBatchStatus = 'PENDING' | 'LOADING' | 'COMPLETE' | 'FAILED' | 'ARCHIVED';

export type TransformationType = 'GROOVY_SCRIPT' | 'EXCEL_FORMULA' | 'FUNCTION_PIPELINE';

export interface AdminReconciliationSummary {
  id: number;
  code: string;
  name: string;
  status: ReconciliationLifecycleStatus;
  makerCheckerEnabled: boolean;
  updatedAt?: string | null;
  owner?: string | null;
  updatedBy?: string | null;
  lastIngestionAt?: string | null;
}

export interface AdminReconciliationSummaryPage {
  items: AdminReconciliationSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export type SourceRowOperationType = 'FILTER' | 'AGGREGATE' | 'SPLIT';
export type SourceRowFilterMode = 'RETAIN_MATCHING' | 'EXCLUDE_MATCHING';
export type SourceRowComparisonOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'GREATER_THAN'
  | 'GREATER_OR_EQUAL'
  | 'LESS_THAN'
  | 'LESS_OR_EQUAL'
  | 'IS_BLANK'
  | 'IS_NOT_BLANK';
export type SourceAggregationFunction = 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'COUNT' | 'FIRST' | 'LAST';
export type SourceColumnOperationType = 'COMBINE' | 'PIPELINE' | 'ROUND';
export type SourceRoundingMode =
  | 'UP'
  | 'DOWN'
  | 'CEILING'
  | 'FLOOR'
  | 'HALF_UP'
  | 'HALF_DOWN'
  | 'HALF_EVEN';

export interface SourceRowFilterOperation {
  column: string;
  mode: SourceRowFilterMode;
  operator: SourceRowComparisonOperator;
  value?: string | null;
  values?: string[];
  caseInsensitive?: boolean;
}

export interface SourceAggregationDefinition {
  sourceColumn?: string | null;
  resultColumn?: string | null;
  function: SourceAggregationFunction;
  scale?: number | null;
  roundingMode?: SourceRoundingMode;
}

export interface SourceRowAggregateOperation {
  groupBy: string[];
  aggregations: SourceAggregationDefinition[];
  retainColumns?: string[];
  sortByGroup?: boolean;
}

export interface SourceRowSplitOperation {
  sourceColumn: string;
  targetColumn?: string | null;
  delimiter?: string | null;
  trimValues?: boolean;
  dropEmptyValues?: boolean;
}

export interface SourceRowOperationConfig {
  type: SourceRowOperationType;
  filter?: SourceRowFilterOperation;
  aggregate?: SourceRowAggregateOperation;
  split?: SourceRowSplitOperation;
}

export interface SourceColumnCombineOperation {
  targetColumn: string;
  sources: string[];
  delimiter?: string | null;
  skipBlanks?: boolean;
  prefix?: string | null;
  suffix?: string | null;
}

export interface SourceColumnPipelineOperation {
  targetColumn: string;
  sourceColumn?: string | null;
  configuration: string;
}

export interface SourceColumnRoundOperation {
  targetColumn: string;
  sourceColumn?: string | null;
  scale?: number | null;
  roundingMode?: SourceRoundingMode;
}

export interface SourceColumnOperationConfig {
  type: SourceColumnOperationType;
  combine?: SourceColumnCombineOperation;
  pipeline?: SourceColumnPipelineOperation;
  round?: SourceColumnRoundOperation;
}

export interface SourceTransformationPlan {
  datasetGroovyScript?: string | null;
  rowOperations: SourceRowOperationConfig[];
  columnOperations: SourceColumnOperationConfig[];
}

export interface AdminSourceSchemaField {
  name: string;
  displayName?: string | null;
  dataType: FieldDataType;
  required: boolean;
  description?: string | null;
}

export interface AdminSource {
  id?: number | null;
  code: string;
  displayName: string;
  adapterType: IngestionAdapterType;
  anchor: boolean;
  description?: string | null;
  connectionConfig?: string | null;
  arrivalExpectation?: string | null;
  arrivalTimezone?: string | null;
  arrivalSlaMinutes?: number | null;
  adapterOptions?: string | null;
  schemaFields?: AdminSourceSchemaField[] | null;
  availableColumns?: string[] | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  transformationPlan?: SourceTransformationPlan | null;
}

export interface AdminCanonicalFieldMapping {
  id?: number | null;
  sourceCode: string;
  sourceColumn: string;
  transformationExpression?: string | null;
  defaultValue?: string | null;
  sourceDateFormat?: string | null;
  targetDateFormat?: string | null;
  ordinalPosition?: number | null;
  required: boolean;
  transformations: AdminCanonicalFieldTransformation[];
}

export interface AdminCanonicalFieldTransformation {
  id?: number | null;
  type: TransformationType;
  expression?: string | null;
  configuration?: string | null;
  displayOrder?: number | null;
  active: boolean;
}

export interface TransformationSampleRow {
  recordId: number;
  batchLabel?: string | null;
  ingestedAt?: string | null;
  canonicalKey: string;
  externalReference?: string | null;
  rawRecord: Record<string, unknown>;
  canonicalPayload: Record<string, unknown>;
}

export interface SourceSchemaInferenceRequest {
  fileType: TransformationSampleFileType;
  hasHeader: boolean;
  delimiter?: string;
  sheetName?: string;
  sheetNames?: string[];
  includeAllSheets?: boolean;
  recordPath?: string;
  encoding?: string;
  limit?: number;
  skipRows?: number;
}

export interface SourceSchemaInferenceResponse {
  fields: AdminSourceSchemaField[];
  sampleRows: Record<string, unknown>[];
}

export interface TransformationSampleResponse {
  rows: TransformationSampleRow[];
}

export interface GroovyScriptGenerationRequest {
  prompt: string;
  fieldName?: string;
  fieldDataType?: FieldDataType | null;
  sourceCode?: string | null;
  sourceColumn?: string | null;
  sampleValue?: unknown;
  rawRecord?: Record<string, unknown>;
  availableColumns?: string[] | null;
  scope?: 'FIELD' | 'DATASET';
}

export interface GroovyScriptGenerationResponse {
  script: string;
  summary?: string | null;
}

export interface GroovyScriptTestRequest {
  script: string;
  value?: unknown;
  rawRecord?: Record<string, unknown>;
}

export interface GroovyScriptTestResponse {
  result: unknown;
}

export interface AdminCanonicalField {
  id?: number | null;
  canonicalName: string;
  displayName: string;
  role: FieldRole;
  dataType: FieldDataType;
  comparisonLogic: ComparisonLogic;
  thresholdPercentage?: number | null;
  classifierTag?: string | null;
  formattingHint?: string | null;
  displayOrder?: number | null;
  required: boolean;
  mappings: AdminCanonicalFieldMapping[];
}

export interface AdminReportColumn {
  id?: number | null;
  header: string;
  source: ReportColumnSource;
  sourceField?: string | null;
  displayOrder: number;
  highlightDifferences: boolean;
}

export interface AdminReportTemplate {
  id?: number | null;
  name: string;
  description: string;
  includeMatched: boolean;
  includeMismatched: boolean;
  includeMissing: boolean;
  highlightDifferences: boolean;
  columns: AdminReportColumn[];
}

export interface AdminAccessControlEntry {
  id?: number | null;
  ldapGroupDn: string;
  role: AccessRole;
  product?: string | null;
  subProduct?: string | null;
  entityName?: string | null;
  notifyOnPublish: boolean;
  notifyOnIngestionFailure: boolean;
  notificationChannel?: string | null;
}

export interface AdminIngestionBatch {
  id: number;
  label?: string | null;
  status: DataBatchStatus;
  recordCount?: number | null;
  checksum?: string | null;
  ingestedAt?: string | null;
}

export interface AdminReconciliationDetail {
  id: number;
  code: string;
  name: string;
  description: string;
  owner?: string | null;
  notes?: string | null;
  makerCheckerEnabled: boolean;
  status: ReconciliationLifecycleStatus;
  createdAt?: string | null;
  updatedAt?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  publishedAt?: string | null;
  publishedBy?: string | null;
  retiredAt?: string | null;
  retiredBy?: string | null;
  version: number;
  autoTriggerEnabled: boolean;
  autoTriggerCron?: string | null;
  autoTriggerTimezone?: string | null;
  autoTriggerGraceMinutes?: number | null;
  sources: AdminSource[];
  canonicalFields: AdminCanonicalField[];
  reportTemplates: AdminReportTemplate[];
  accessControlEntries: AdminAccessControlEntry[];
  ingestionBatches?: AdminIngestionBatch[];
}

export interface AdminReconciliationRequest {
  code: string;
  name: string;
  description: string;
  owner?: string | null;
  makerCheckerEnabled: boolean;
  notes?: string | null;
  status: ReconciliationLifecycleStatus;
  autoTriggerEnabled: boolean;
  autoTriggerCron?: string | null;
  autoTriggerTimezone?: string | null;
  autoTriggerGraceMinutes?: number | null;
  version?: number | null;
  sources: AdminSource[];
  canonicalFields: AdminCanonicalField[];
  reportTemplates: AdminReportTemplate[];
  accessControlEntries: AdminAccessControlEntry[];
}

export interface AdminReconciliationSchema {
  definitionId: number;
  code: string;
  name: string;
  sources: {
    code: string;
    adapterType: IngestionAdapterType;
    anchor: boolean;
    connectionConfig?: string | null;
    arrivalExpectation?: string | null;
    arrivalTimezone?: string | null;
    arrivalSlaMinutes?: number | null;
    adapterOptions?: string | null;
    ingestionEndpoint: string;
  }[];
  fields: {
    displayName: string;
    canonicalName: string;
    role: FieldRole;
    dataType: FieldDataType;
    comparisonLogic: ComparisonLogic;
    thresholdPercentage?: number | null;
    formattingHint?: string | null;
    required: boolean;
    mappings: {
      sourceCode: string;
      sourceColumn: string;
      transformationExpression?: string | null;
      defaultValue?: string | null;
      ordinalPosition?: number | null;
      required: boolean;
      transformations: SchemaFieldTransformation[];
    }[];
  }[];
}

export interface AdminIngestionRequestMetadata {
  adapterType: IngestionAdapterType;
  options?: Record<string, unknown>;
  label?: string;
}

export interface SchemaFieldTransformation {
  id?: number | null;
  type: TransformationType;
  expression?: string | null;
  configuration?: string | null;
  displayOrder?: number | null;
  active: boolean;
}

export interface TransformationValidationRequest {
  type: TransformationType;
  expression?: string | null;
  configuration?: string | null;
}

export interface TransformationValidationResponse {
  valid: boolean;
  message: string;
}

export type TransformationSampleFileType = 'CSV' | 'EXCEL' | 'JSON' | 'XML' | 'DELIMITED';

export interface SourceTransformationPreviewUploadRequest {
  fileType: TransformationSampleFileType;
  hasHeader: boolean;
  delimiter?: string | null;
  sheetName?: string | null;
  sheetNames?: string[] | null;
  includeAllSheets?: boolean | null;
  includeSheetNameColumn?: boolean | null;
  sheetNameColumn?: string | null;
  recordPath?: string | null;
  encoding?: string | null;
  limit?: number | null;
  skipRows?: number | null;
  transformationPlan?: SourceTransformationPlan | null;
}

export interface SourceTransformationPreviewResponse {
  rawRows: Record<string, unknown>[];
  transformedRows: Record<string, unknown>[];
}

export interface SourceTransformationApplyRequest {
  transformationPlan?: SourceTransformationPlan | null;
  rows: Record<string, unknown>[];
}

export interface SourceTransformationApplyResponse {
  transformedRows: Record<string, unknown>[];
}
