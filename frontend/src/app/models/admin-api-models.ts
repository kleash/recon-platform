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

export type TransformationType =
  | 'GROOVY_SCRIPT'
  | 'EXCEL_FORMULA'
  | 'FUNCTION_PIPELINE'
  | 'LLM_PROMPT';

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
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminCanonicalFieldMapping {
  id?: number | null;
  sourceCode: string;
  sourceColumn: string;
  transformationExpression?: string | null;
  defaultValue?: string | null;
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

export interface TransformationSampleResponse {
  rows: TransformationSampleRow[];
}

export interface GroovyScriptGenerationRequest {
  prompt: string;
  fieldName: string;
  fieldDataType?: FieldDataType | null;
  sourceCode?: string | null;
  sourceColumn?: string | null;
  sampleValue?: unknown;
  rawRecord?: Record<string, unknown>;
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

export interface PreviewTransformationDto {
  type: TransformationType;
  expression?: string | null;
  configuration?: string | null;
  displayOrder?: number | null;
  active?: boolean;
}

export interface TransformationPreviewRequest {
  value: unknown;
  rawRecord: Record<string, unknown>;
  transformations: PreviewTransformationDto[];
}

export interface TransformationPreviewResponse {
  result: unknown;
}

export type TransformationSampleFileType = 'CSV' | 'EXCEL' | 'JSON' | 'XML' | 'DELIMITED';

export interface TransformationFilePreviewUploadRequest {
  fileType: TransformationSampleFileType;
  hasHeader: boolean;
  delimiter?: string | null;
  sheetName?: string | null;
  recordPath?: string | null;
  valueColumn?: string | null;
  encoding?: string | null;
  limit?: number | null;
  transformations: PreviewTransformationDto[];
}

export interface TransformationFilePreviewRow {
  rowNumber: number;
  rawRecord: Record<string, unknown>;
  valueBefore: unknown;
  transformedValue: unknown;
  error?: string | null;
}

export interface TransformationFilePreviewResponse {
  rows: TransformationFilePreviewRow[];
}
