import { AsyncPipe, CommonModule, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, finalize, take, takeUntil } from 'rxjs';
import {
  AccessRole,
  AdminAccessControlEntry,
  AdminCanonicalField,
  AdminCanonicalFieldMapping,
  AdminCanonicalFieldTransformation,
  AdminReconciliationDetail,
  AdminReconciliationRequest,
  AdminReportColumn,
  AdminReportTemplate,
  AdminSource,
  ComparisonLogic,
  FieldDataType,
  FieldRole,
  IngestionAdapterType,
  ReportColumnSource,
  ReconciliationLifecycleStatus,
  SourceTransformationPlan,
  SourceRowOperationConfig,
  SourceRowOperationType,
  SourceRowFilterOperation,
  SourceRowAggregateOperation,
  SourceAggregationDefinition,
  SourceRowSplitOperation,
  SourceColumnOperationConfig,
  SourceColumnCombineOperation,
  SourceColumnPipelineOperation,
  SourceColumnRoundOperation,
  SourceRowFilterMode,
  SourceRowComparisonOperator,
  SourceAggregationFunction,
  SourceColumnOperationType,
  SourceRoundingMode,
  SourceTransformationPreviewUploadRequest,
  TransformationSampleFileType,
  GroovyScriptGenerationRequest,
  GroovyScriptTestRequest,
  TransformationType,
  TransformationValidationRequest,
  TransformationValidationResponse
} from '../../models/admin-api-models';
import { AdminReconciliationStateService } from '../../services/admin-reconciliation-state.service';

interface WizardStep {
  key: string;
  label: string;
}

type LlmAdapterOptionsFormValue = {
  model: string;
  promptTemplate: string;
  extractionSchema: string;
  recordPath: string;
  temperature: number | null;
  maxOutputTokens: number | null;
};

type GroovyAssistantState = {
  prompt: string;
  generating: boolean;
  summary?: string;
  error?: string;
};

type SourceTransformationPreviewUiState = {
  file?: File;
  fileName?: string;
  fileType: TransformationSampleFileType;
  hasHeader: boolean;
  delimiter?: string;
  sheetName?: string;
  recordPath?: string;
  encoding?: string;
  limit: number;
  uploading: boolean;
  error?: string;
  rawRows: Record<string, unknown>[];
  transformedRows: Record<string, unknown>[];
  lastRows?: Record<string, unknown>[];
};

type SampleUploadUiState = {
  fileType: TransformationSampleFileType;
  hasHeader: boolean;
  delimiter?: string;
  sheetName?: string;
  recordPath?: string;
  encoding?: string;
  limit: number;
  valueColumn?: string;
  file?: File;
  fileName?: string;
  uploading: boolean;
  rows: Array<{
    rowNumber: number;
    valueBefore: unknown;
    transformedValue: unknown;
    rawRecord: Record<string, unknown>;
    error?: string;
  }>;
  error?: string;
  confirmed: boolean;
  stale: boolean;
};

type MappingPreviewState = { value: string; raw: string; result?: string; error?: string };


@Component({
  selector: 'urp-admin-reconciliation-wizard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, NgIf, NgFor, AsyncPipe],
  templateUrl: './admin-reconciliation-wizard.component.html',
  styleUrls: ['./admin-reconciliation-wizard.component.css']
})
export class AdminReconciliationWizardComponent implements OnInit, OnDestroy {
  readonly steps: WizardStep[] = [
    { key: 'definition', label: 'Definition' },
    { key: 'sources', label: 'Sources' },
    { key: 'transformations', label: 'Transformations' },
    { key: 'schema', label: 'Schema' },
    { key: 'reports', label: 'Reports' },
    { key: 'access', label: 'Access' },
    { key: 'review', label: 'Review & Publish' }
  ];

  readonly statuses: ReconciliationLifecycleStatus[] = ['DRAFT', 'PUBLISHED', 'RETIRED'];
  readonly adapterTypes: IngestionAdapterType[] = [
    'CSV_FILE',
    'FIXED_WIDTH_FILE',
    'XML_FILE',
    'JSON_FILE',
    'DATABASE',
    'REST_API',
    'MESSAGE_QUEUE',
    'LLM_DOCUMENT'
  ];
  readonly fieldRoles: FieldRole[] = [
    'KEY',
    'COMPARE',
    'DISPLAY',
    'PRODUCT',
    'SUB_PRODUCT',
    'ENTITY',
    'CLASSIFIER',
    'ATTRIBUTE'
  ];
  readonly dataTypes: FieldDataType[] = ['STRING', 'DECIMAL', 'INTEGER', 'DATE'];
  readonly comparisonLogics: ComparisonLogic[] = ['EXACT_MATCH', 'CASE_INSENSITIVE', 'NUMERIC_THRESHOLD', 'DATE_ONLY'];
  readonly reportSources: ReportColumnSource[] = ['SOURCE_A', 'SOURCE_B', 'BREAK_METADATA'];
  readonly accessRoles: AccessRole[] = ['VIEWER', 'MAKER', 'CHECKER'];
  readonly transformationTypes: TransformationType[] = ['GROOVY_SCRIPT', 'EXCEL_FORMULA', 'FUNCTION_PIPELINE'];
  readonly pipelineFunctionOptions: { value: string; label: string }[] = [
    { value: 'TRIM', label: 'Trim whitespace' },
    { value: 'TO_UPPERCASE', label: 'Uppercase' },
    { value: 'TO_LOWERCASE', label: 'Lowercase' },
    { value: 'REPLACE', label: 'Replace text' },
    { value: 'SUBSTRING', label: 'Substring' },
    { value: 'DEFAULT_IF_BLANK', label: 'Default if blank' },
    { value: 'PREFIX', label: 'Add prefix' },
    { value: 'SUFFIX', label: 'Add suffix' },
    { value: 'FORMAT_DATE', label: 'Format date' }
  ];

  readonly form: FormGroup = this.fb.group({
    definition: this.fb.group({
      id: [null],
      code: ['', Validators.required],
      name: ['', Validators.required],
      description: ['', Validators.required],
      owner: [''],
      makerCheckerEnabled: [true],
      notes: [''],
      status: ['DRAFT' as ReconciliationLifecycleStatus, Validators.required],
      autoTriggerEnabled: [false],
      autoTriggerCron: [''],
      autoTriggerTimezone: [''],
      autoTriggerGraceMinutes: [null, [Validators.min(0)]],
      version: [null]
    }),
    sources: this.fb.array<FormGroup>([]),
    canonicalFields: this.fb.array<FormGroup>([]),
    reportTemplates: this.fb.array<FormGroup>([]),
    accessControlEntries: this.fb.array<FormGroup>([])
  });

  currentStep = 0;
  mode: 'create' | 'edit' = 'create';
  definitionId: number | null = null;
  optimisticLockError: string | null = null;
  isSaving = false;
  groovyTestState: Record<string, { running: boolean; result?: string; error?: string }> = {};
  groovyAssistantState: Record<string, GroovyAssistantState> = {};
  sourcePreviewState: SourceTransformationPreviewUiState[] = [];
  previewState: Record<string, MappingPreviewState> = {};
  sampleUploadState: Record<string, SampleUploadUiState> = {};
  previewConfirmations = new Set<string>();
  previewStaleKeys = new Set<string>();

  private readonly destroy$ = new Subject<void>();
  private patchedFromDetail = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly state: AdminReconciliationStateService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('id');
      const id = idParam ? Number(idParam) : NaN;
      if (!Number.isNaN(id)) {
        this.mode = 'edit';
        this.definitionId = id;
        this.state.loadDefinition(id);
      } else {
        this.mode = 'create';
        this.definitionId = null;
        this.state.clearSelection();
        if (this.sources.length === 0) {
          this.addSource();
        }
        if (this.canonicalFields.length === 0) {
          this.addCanonicalField();
        }
      }
    });

    const autoTriggerEnabledControl = this.definitionGroup.get('autoTriggerEnabled');
    const autoTriggerCronControl = this.definitionGroup.get('autoTriggerCron');
    const autoTriggerTimezoneControl = this.definitionGroup.get('autoTriggerTimezone');
    const applyAutoTriggerValidators = (enabled: boolean) => {
      if (enabled) {
        autoTriggerCronControl?.addValidators(Validators.required);
        autoTriggerTimezoneControl?.addValidators(Validators.required);
      } else {
        autoTriggerCronControl?.clearValidators();
        autoTriggerTimezoneControl?.clearValidators();
      }
      autoTriggerCronControl?.updateValueAndValidity({ emitEvent: false });
      autoTriggerTimezoneControl?.updateValueAndValidity({ emitEvent: false });
    };
    applyAutoTriggerValidators(!!autoTriggerEnabledControl?.value);
    autoTriggerEnabledControl
      ?.valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((enabled: boolean) => applyAutoTriggerValidators(!!enabled));

    this.state.selected$
      .pipe(takeUntil(this.destroy$))
      .subscribe((detail) => {
        if (detail && this.mode === 'edit' && !this.patchedFromDetail) {
          this.patchFromDetail(detail);
          this.patchedFromDetail = true;
        }
      });

    this.route.queryParamMap
      .pipe(takeUntil(this.destroy$))
      .subscribe((params) => {
        const duplicateParam = params.get('from');
        const duplicateId = duplicateParam ? Number(duplicateParam) : NaN;
        if (this.mode === 'create' && !Number.isNaN(duplicateId)) {
          this.state
            .fetchDefinition(duplicateId)
            .pipe(take(1))
            .subscribe((detail) => this.patchFromTemplate(detail));
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get definitionGroup(): FormGroup {
    return this.form.get('definition') as FormGroup;
  }

  get sources(): FormArray<FormGroup> {
    return this.form.get('sources') as FormArray<FormGroup>;
  }

  get canonicalFields(): FormArray<FormGroup> {
    return this.form.get('canonicalFields') as FormArray<FormGroup>;
  }

  get reportTemplates(): FormArray<FormGroup> {
    return this.form.get('reportTemplates') as FormArray<FormGroup>;
  }

  get accessControlEntries(): FormArray<FormGroup> {
    return this.form.get('accessControlEntries') as FormArray<FormGroup>;
  }

  sourceMappings(index: number): FormArray<FormGroup> {
    return this.canonicalFields.at(index).get('mappings') as FormArray<FormGroup>;
  }

  mappingTransformations(fieldIndex: number, mappingIndex: number): FormArray<FormGroup> {
    return this.sourceMappings(fieldIndex)
      .at(mappingIndex)
      .get('transformations') as FormArray<FormGroup>;
  }

  pipelineSteps(fieldIndex: number, mappingIndex: number, transformationIndex: number): FormArray<FormGroup> {
    return this.mappingTransformations(fieldIndex, mappingIndex)
      .at(transformationIndex)
      .get('pipelineSteps') as FormArray<FormGroup>;
  }

  pipelineStepArgs(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number,
    stepIndex: number
  ): FormArray<FormControl<string | null>> {
    return this.pipelineSteps(fieldIndex, mappingIndex, transformationIndex)
      .at(stepIndex)
      .get('args') as FormArray<FormControl<string | null>>;
  }

  reportColumns(index: number): FormArray<FormGroup> {
    return this.reportTemplates.at(index).get('columns') as FormArray<FormGroup>;
  }

  addSource(source?: AdminSource): void {
    this.sources.push(this.createSourceGroup(source));
    this.ensureSourcePreviewState(this.sources.length - 1);
  }

  removeSource(index: number): void {
    this.sources.removeAt(index);
    this.sourcePreviewState.splice(index, 1);
  }

  addCanonicalField(field?: AdminCanonicalField): void {
    this.canonicalFields.push(this.createFieldGroup(field));
    const index = this.canonicalFields.length - 1;
    if (!field || !field.mappings || field.mappings.length === 0) {
      this.addMapping(index);
    } else {
      this.sourceMappings(index).controls.forEach((_, mappingIndex) => this.ensurePreviewState(index, mappingIndex));
    }
  }

  removeCanonicalField(index: number): void {
    this.canonicalFields.removeAt(index);
  }

  addMapping(fieldIndex: number, mapping?: AdminCanonicalFieldMapping): void {
    const mappings = this.sourceMappings(fieldIndex);
    mappings.push(this.createMappingGroup(mapping));
    this.ensurePreviewState(fieldIndex, mappings.length - 1);
    this.sampleUploadStateFor(fieldIndex, mappings.length - 1);
  }

  removeMapping(fieldIndex: number, mappingIndex: number): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    this.previewConfirmations.delete(key);
    this.previewStaleKeys.delete(key);
    delete this.sampleUploadState[key];
    this.sourceMappings(fieldIndex).removeAt(mappingIndex);
  }

  addTransformation(
    fieldIndex: number,
    mappingIndex: number,
    transformation?: AdminCanonicalFieldTransformation
  ): void {
    this.mappingTransformations(fieldIndex, mappingIndex).push(this.createTransformationGroup(transformation));
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  removeTransformation(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    this.mappingTransformations(fieldIndex, mappingIndex).removeAt(transformationIndex);
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  addPipelineStep(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    this.pipelineSteps(fieldIndex, mappingIndex, transformationIndex).push(this.createPipelineStepGroup());
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  removePipelineStep(fieldIndex: number, mappingIndex: number, transformationIndex: number, stepIndex: number): void {
    this.pipelineSteps(fieldIndex, mappingIndex, transformationIndex).removeAt(stepIndex);
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  addPipelineArg(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number,
    stepIndex: number
  ): void {
    this.pipelineStepArgs(fieldIndex, mappingIndex, transformationIndex, stepIndex).push(
      this.fb.control('')
    );
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  removePipelineArg(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number,
    stepIndex: number,
    argIndex: number
  ): void {
    this.pipelineStepArgs(fieldIndex, mappingIndex, transformationIndex, stepIndex).removeAt(argIndex);
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  addReportTemplate(report?: AdminReportTemplate): void {
    this.reportTemplates.push(this.createReportGroup(report));
    const index = this.reportTemplates.length - 1;
    if (!report || !report.columns || report.columns.length === 0) {
      this.addReportColumn(index);
    }
  }

  removeReportTemplate(index: number): void {
    this.reportTemplates.removeAt(index);
  }

  addReportColumn(reportIndex: number, column?: AdminReportColumn): void {
    this.reportColumns(reportIndex).push(this.createReportColumnGroup(column));
  }

  removeReportColumn(reportIndex: number, columnIndex: number): void {
    this.reportColumns(reportIndex).removeAt(columnIndex);
  }

  onTransformationTypeChange(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    const group = this.mappingTransformations(fieldIndex, mappingIndex).at(transformationIndex) as FormGroup;
    const type = group.get('type')?.value as TransformationType;
    if (type === 'FUNCTION_PIPELINE') {
      const steps = this.pipelineSteps(fieldIndex, mappingIndex, transformationIndex);
      if (steps.length === 0) {
        steps.push(this.createPipelineStepGroup());
      }
    } else {
      this.pipelineSteps(fieldIndex, mappingIndex, transformationIndex).clear();
      if (type === 'GROOVY_SCRIPT') {
        group.get('expression')?.setValue(group.get('expression')?.value ?? 'return value');
      }
    }
    group.get('validationMessage')?.setValue('');
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  addAccessEntry(entry?: AdminAccessControlEntry): void {
    this.accessControlEntries.push(this.createAccessGroup(entry));
  }

  removeAccessEntry(index: number): void {
    this.accessControlEntries.removeAt(index);
  }

  goToStep(index: number): void {
    if (index < 0 || index >= this.steps.length) {
      return;
    }
    if (index > this.currentStep && !this.validateStep(this.currentStep)) {
      return;
    }
    this.currentStep = index;
  }

  nextStep(): void {
    this.goToStep(this.currentStep + 1);
  }

  previousStep(): void {
    this.goToStep(this.currentStep - 1);
  }

  validateTransformation(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    const group = this.mappingTransformations(fieldIndex, mappingIndex).at(transformationIndex) as FormGroup;
    const payload = this.buildTransformationValidationPayload(group);
    this.state
      .validateTransformation(payload)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          group.get('validationMessage')?.setValue(response.valid ? 'Transformation is valid.' : response.message);
        },
        error: (error) => {
          const message = error?.error || 'Validation failed.';
          group.get('validationMessage')?.setValue(message);
        }
      });
  }

  runGroovyTest(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    const transformationGroup = this.mappingTransformations(fieldIndex, mappingIndex).at(
      transformationIndex
    ) as FormGroup;
    const script = this.normalize(transformationGroup.get('expression')?.value);
    if (!script) {
      transformationGroup.get('validationMessage')?.setValue('Script is required before running a test.');
      return;
    }

    const mappingGroup = this.sourceMappings(fieldIndex).at(mappingIndex) as FormGroup;
    const sourceCode = this.normalize(mappingGroup.get('sourceCode')?.value);
    const sourceColumn = this.normalize(mappingGroup.get('sourceColumn')?.value);
    const preview = this.lookupSourcePreview(sourceCode);
    const rawRecord = preview?.rawRows?.[0];
    if (!rawRecord) {
      this.setGroovyTestState(fieldIndex, mappingIndex, transformationIndex, {
        running: false,
        error: 'Upload or load rows in the Transformations step before running a test.'
      });
      return;
    }

    const value = sourceColumn ? this.extractColumnValue(rawRecord, sourceColumn) : undefined;

    const request: GroovyScriptTestRequest = {
      script,
      value: value ?? null,
      rawRecord
    };

    this.setGroovyTestState(fieldIndex, mappingIndex, transformationIndex, { running: true });

    this.state
      .testGroovyScript(request)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.setGroovyTestState(fieldIndex, mappingIndex, transformationIndex, {
            running: false,
            result: this.stringifyResult(response.result)
          });
        },
        error: (error) => {
          const message = error?.error?.details ?? error?.error ?? 'Execution failed.';
          this.setGroovyTestState(fieldIndex, mappingIndex, transformationIndex, {
            running: false,
            error: message
          });
        }
      });
  }

  updatePreviewValue(
    fieldIndex: number,
    mappingIndex: number,
    property: 'value' | 'raw',
    newValue: string
  ): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    this.ensurePreviewState(fieldIndex, mappingIndex);
    this.previewState[key] = {
      ...this.previewState[key],
      [property]: newValue
    };
    this.invalidateMappingPreview(fieldIndex, mappingIndex);
  }

  getGroovyTestState(fieldIndex: number, mappingIndex: number, transformationIndex: number): {
    running: boolean;
    result?: string;
    error?: string;
  } {
    return (
      this.groovyTestState[this.transformationKey(fieldIndex, mappingIndex, transformationIndex)] ?? {
        running: false
      }
    );
  }

  handlePreviewInput(
    fieldIndex: number,
    mappingIndex: number,
    property: 'value' | 'raw',
    event: Event
  ): void {
    const target = event.target as HTMLInputElement | HTMLTextAreaElement | null;
    this.updatePreviewValue(fieldIndex, mappingIndex, property, target?.value ?? '');
  }

  previewKey(fieldIndex: number, mappingIndex: number): string {
    return `${fieldIndex}:${mappingIndex}`;
  }

  private ensurePreviewState(fieldIndex: number, mappingIndex: number): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    if (!this.previewState[key]) {
      this.previewState[key] = { value: '', raw: '{}' };
    }
  }

  private setGroovyTestState(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number,
    state: { running: boolean; result?: string; error?: string }
  ): void {
    this.groovyTestState[this.transformationKey(fieldIndex, mappingIndex, transformationIndex)] = state;
  }

  getGroovyAssistantState(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number
  ): GroovyAssistantState {
    const key = this.transformationKey(fieldIndex, mappingIndex, transformationIndex);
    return this.getOrCreateGroovyAssistantState(key);
  }

  onGroovyPromptChange(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number,
    event: Event
  ): void {
    const prompt = (event.target as HTMLTextAreaElement | null)?.value ?? '';
    const key = this.transformationKey(fieldIndex, mappingIndex, transformationIndex);
    this.setGroovyAssistantState(key, { prompt, summary: undefined, error: undefined });
  }

  generateGroovyScript(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    const key = this.transformationKey(fieldIndex, mappingIndex, transformationIndex);
    const assistantState = this.getOrCreateGroovyAssistantState(key);
    const prompt = assistantState.prompt?.trim();
    if (!prompt) {
      this.setGroovyAssistantState(key, {
        generating: false,
        summary: undefined,
        error: 'Describe the transformation before generating a script.'
      });
      return;
    }

    const transformationGroup = this.mappingTransformations(fieldIndex, mappingIndex).at(
      transformationIndex
    ) as FormGroup;
    const fieldGroup = this.canonicalFields.at(fieldIndex) as FormGroup;
    const mappingGroup = this.sourceMappings(fieldIndex).at(mappingIndex) as FormGroup;

    const sourceCode = this.normalize(mappingGroup.get('sourceCode')?.value);
    const sourceColumn = this.normalize(mappingGroup.get('sourceColumn')?.value);
    const preview = this.lookupSourcePreview(sourceCode);
    const sampleRawRecord = preview?.rawRows?.[0];
    const dataType = (fieldGroup.get('dataType')?.value as FieldDataType | null) ?? null;
    const rawValue = sampleRawRecord && sourceColumn ? this.extractColumnValue(sampleRawRecord, sourceColumn) : undefined;
    const sampleValue = this.coerceSampleValue(rawValue, dataType);
    const fieldName =
      this.normalize(fieldGroup.get('displayName')?.value) ??
      this.normalize(fieldGroup.get('canonicalName')?.value) ??
      'Field';

    const payload: GroovyScriptGenerationRequest = {
      prompt,
      fieldName,
      fieldDataType: dataType,
      sourceCode: sourceCode ?? null,
      sourceColumn: sourceColumn ?? null,
      sampleValue,
      rawRecord: sampleRawRecord ?? undefined
    };

    this.setGroovyAssistantState(key, { generating: true, summary: undefined, error: undefined });

    this.state
      .generateGroovyScript(payload)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          transformationGroup.get('expression')?.setValue(response.script ?? '', { emitEvent: true });
          transformationGroup.get('expression')?.markAsDirty();
          this.setGroovyAssistantState(key, {
            generating: false,
            summary: response.summary ?? undefined,
            error: undefined
          });
        },
        error: (error) => {
          const message =
            typeof error?.error === 'string' ? error.error : 'Unable to generate Groovy script.';
          this.setGroovyAssistantState(key, {
            generating: false,
            summary: undefined,
            error: message
          });
        }
      });
  }

  private getOrCreateGroovyAssistantState(key: string): GroovyAssistantState {
    if (!this.groovyAssistantState[key]) {
      this.groovyAssistantState[key] = { prompt: '', generating: false };
    }
    return this.groovyAssistantState[key];
  }

  private setGroovyAssistantState(key: string, updates: Partial<GroovyAssistantState>): void {
    const current = this.getOrCreateGroovyAssistantState(key);
    Object.assign(current, updates);
  }

  private coerceSampleValue(value: unknown, dataType: FieldDataType | null): unknown {
    if (value === undefined) {
      return undefined;
    }
    if (value === null) {
      return null;
    }
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (trimmed.length === 0 || trimmed.toLowerCase() === 'null') {
        return null;
      }
      if (!dataType) {
        const numeric = Number(trimmed);
        if (!Number.isNaN(numeric)) {
          return numeric;
        }
        if (trimmed.toLowerCase() === 'true' || trimmed.toLowerCase() === 'false') {
          return trimmed.toLowerCase() === 'true';
        }
        return trimmed;
      }
      switch (dataType) {
        case 'INTEGER': {
          const parsed = Number.parseInt(trimmed, 10);
          return Number.isNaN(parsed) ? trimmed : parsed;
        }
        case 'DECIMAL': {
          const parsed = Number(trimmed);
          return Number.isNaN(parsed) ? trimmed : parsed;
        }
        case 'DATE':
        default:
          return trimmed;
      }
    }
    return value;
  }

  private transformationKey(fieldIndex: number, mappingIndex: number, transformationIndex: number): string {
    return `${fieldIndex}:${mappingIndex}:${transformationIndex}`;
  }

  private extractColumnValue(rawRecord: Record<string, unknown>, column: string): unknown {
    if (!column) {
      return undefined;
    }
    const normalisedColumn = column.replace(/\s+/g, '').toLowerCase();
    const matchedKey = Object.keys(rawRecord).find(
      (key) => key.replace(/\s+/g, '').toLowerCase() === normalisedColumn
    );
    return matchedKey ? rawRecord[matchedKey] : undefined;
  }

  sampleUploadStateFor(fieldIndex: number, mappingIndex: number): SampleUploadUiState {
    const key = this.previewKey(fieldIndex, mappingIndex);
    if (!this.sampleUploadState[key]) {
      this.sampleUploadState[key] = this.createDefaultSampleUploadState(fieldIndex, mappingIndex);
    }
    return this.sampleUploadState[key];
  }

  onSourceColumnChange(fieldIndex: number, mappingIndex: number, value: string): void {
    this.invalidateMappingPreview(fieldIndex, mappingIndex, { valueColumn: value });
  }

  setSampleUploadOption(
    fieldIndex: number,
    mappingIndex: number,
    option: keyof SampleUploadUiState,
    rawValue: unknown
  ): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    const state = this.sampleUploadStateFor(fieldIndex, mappingIndex);
    let value: unknown = rawValue;
    if (option === 'limit') {
      const numeric = Number(rawValue);
      value = Number.isFinite(numeric) ? Math.min(Math.max(Math.trunc(numeric), 1), 10) : 10;
    }
    if (option === 'valueColumn' || option === 'delimiter' || option === 'sheetName' || option === 'recordPath' || option === 'encoding') {
      value = this.normalize(typeof rawValue === 'string' ? rawValue : '') || undefined;
    }
    const next: SampleUploadUiState = {
      ...state,
      [option]: value as SampleUploadUiState[typeof option]
    };
    if (option === 'fileType') {
      const fileType = value as TransformationSampleFileType;
      const previousFileType = state.fileType;
      if (fileType === 'CSV') {
        next.delimiter = state.delimiter && state.delimiter.length > 0 ? state.delimiter : ',';
        next.hasHeader = true;
      } else if (fileType === 'DELIMITED') {
        const preservedDelimiter =
          previousFileType === 'DELIMITED' && state.delimiter && state.delimiter.length > 0
            ? state.delimiter
            : undefined;
        next.delimiter = preservedDelimiter;
        next.hasHeader = false;
      } else {
        next.delimiter = undefined;
      }
      if (fileType !== 'EXCEL') {
        next.sheetName = '';
      }
      if (!['JSON', 'XML'].includes(fileType)) {
        next.recordPath = '';
      }
      if (fileType === 'JSON' || fileType === 'XML') {
        next.hasHeader = false;
      }
    }
    if (!['rows', 'uploading', 'error'].includes(option)) {
      next.confirmed = false;
      next.stale = true;
      next.error = undefined;
      this.previewConfirmations.delete(key);
      this.previewStaleKeys.add(key);
    }
    this.sampleUploadState[key] = next;
  }

  onSampleFileSelected(fieldIndex: number, mappingIndex: number, event: Event): void {
    const target = event.target as HTMLInputElement | null;
    const file = target?.files && target.files.length > 0 ? target.files[0] : undefined;
    const key = this.previewKey(fieldIndex, mappingIndex);
    const state = this.sampleUploadStateFor(fieldIndex, mappingIndex);
    if (!file) {
      this.sampleUploadState[key] = {
        ...state,
        file: undefined,
        fileName: undefined
      };
      return;
    }
    this.sampleUploadState[key] = {
      ...state,
      file,
      fileName: file.name,
      confirmed: false,
      stale: true,
      error: undefined
    };
    this.previewConfirmations.delete(key);
    this.previewStaleKeys.add(key);
  }

  uploadSampleFile(fieldIndex: number, mappingIndex: number): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    const state = this.sampleUploadStateFor(fieldIndex, mappingIndex);
    const file = state.file;
    if (!file) {
      this.sampleUploadState[key] = {
        ...state,
        error: 'Select a sample file to preview transformations.'
      };
      return;
    }

    const transformations = this.buildTransformationsPayload(fieldIndex, mappingIndex);
    if (transformations.length === 0) {
      this.sampleUploadState[key] = {
        ...state,
        error: 'Add at least one transformation before running a preview.'
      };
      return;
    }

    this.sampleUploadState[key] = {
      ...state,
      uploading: false,
      error: 'Field-level sample previews have moved to the Transformations step.',
      rows: [],
      confirmed: false,
      stale: true
    };
    this.previewConfirmations.delete(key);
    this.previewStaleKeys.add(key);
  }

  isMappingPreviewConfirmed(fieldIndex: number, mappingIndex: number): boolean {
    return this.previewConfirmations.has(this.previewKey(fieldIndex, mappingIndex));
  }

  isMappingPreviewStale(fieldIndex: number, mappingIndex: number): boolean {
    return this.previewStaleKeys.has(this.previewKey(fieldIndex, mappingIndex));
  }

  private markPreviewConfirmed(key: string): void {
    this.previewConfirmations.add(key);
    this.previewStaleKeys.delete(key);
    const state = this.sampleUploadState[key];
    if (state) {
      this.sampleUploadState[key] = {
        ...state,
        confirmed: true,
        stale: false,
        error: undefined
      };
    }
  }

  invalidateMappingPreview(
    fieldIndex: number,
    mappingIndex: number,
    options?: { valueColumn?: string }
  ): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    const state = this.sampleUploadStateFor(fieldIndex, mappingIndex);
    this.previewConfirmations.delete(key);
    this.previewStaleKeys.add(key);
    this.sampleUploadState[key] = {
      ...state,
      confirmed: false,
      stale: true,
      valueColumn:
        options && Object.prototype.hasOwnProperty.call(options, 'valueColumn')
          ? this.normalize(options.valueColumn ?? '') ?? undefined
          : state.valueColumn
    };
  }

  private createDefaultSampleUploadState(fieldIndex: number, mappingIndex: number): SampleUploadUiState {
    const mappingGroup = this.sourceMappings(fieldIndex).at(mappingIndex) as FormGroup;
    const valueColumn = this.normalize(mappingGroup.get('sourceColumn')?.value) ?? undefined;
    return {
      fileType: 'CSV',
      hasHeader: true,
      delimiter: ',',
      sheetName: '',
      recordPath: '',
      encoding: '',
      limit: 10,
      valueColumn,
      uploading: false,
      rows: [],
      error: undefined,
      confirmed: false,
      stale: false
    };
  }

  stringifyResult(value: unknown): string {
    if (value === null || value === undefined) {
      return 'null';
    }
    if (typeof value === 'object') {
      try {
        return JSON.stringify(value);
      } catch (error) {
        return String(value);
      }
    }
    return String(value);
  }

  save(): void {
    if (!this.validateStep(this.currentStep)) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.buildRequest();
    this.isSaving = true;
    this.optimisticLockError = null;

    const save$ = this.mode === 'edit' && this.definitionId
      ? this.state.updateDefinition(this.definitionId, request)
      : this.state.createDefinition(request);

    save$
      .pipe(take(1))
      .subscribe({
        next: (detail) => {
          this.isSaving = false;
          this.router.navigate(['/admin', detail.id]);
        },
        error: (error) => {
          this.isSaving = false;
          if (error?.status === 409) {
            this.optimisticLockError =
              'The reconciliation was updated by someone else. Refresh to obtain the latest version and try again.';
          }
        }
      });
  }

  private patchFromDetail(detail: AdminReconciliationDetail): void {
    this.definitionGroup.patchValue({
      id: detail.id,
      code: detail.code,
      name: detail.name,
      description: detail.description,
      owner: detail.owner ?? '',
      makerCheckerEnabled: detail.makerCheckerEnabled,
      notes: detail.notes,
      status: detail.status,
      autoTriggerEnabled: detail.autoTriggerEnabled,
      autoTriggerCron: detail.autoTriggerCron ?? '',
      autoTriggerTimezone: detail.autoTriggerTimezone ?? '',
      autoTriggerGraceMinutes: detail.autoTriggerGraceMinutes ?? null,
      version: detail.version
    });

    this.sources.clear();
    detail.sources.forEach((source) => this.addSource(source));

    this.canonicalFields.clear();
    detail.canonicalFields.forEach((field) => this.addCanonicalField(field));

    this.reportTemplates.clear();
    detail.reportTemplates.forEach((report) => this.addReportTemplate(report));

    this.accessControlEntries.clear();
    detail.accessControlEntries.forEach((entry) => this.addAccessEntry(entry));
  }

  private patchFromTemplate(detail: AdminReconciliationDetail): void {
    this.definitionGroup.patchValue({
      id: null,
      code: '',
      name: detail.name,
      description: detail.description,
      owner: detail.owner ?? '',
      makerCheckerEnabled: detail.makerCheckerEnabled,
      notes: detail.notes,
      status: 'DRAFT',
      autoTriggerEnabled: detail.autoTriggerEnabled,
      autoTriggerCron: detail.autoTriggerCron ?? '',
      autoTriggerTimezone: detail.autoTriggerTimezone ?? '',
      autoTriggerGraceMinutes: detail.autoTriggerGraceMinutes ?? null,
      version: null
    });

    this.sources.clear();
    detail.sources.forEach((source) =>
      this.addSource({
        ...source,
        id: null
      })
    );

    this.canonicalFields.clear();
    detail.canonicalFields.forEach((field) =>
      this.addCanonicalField({
        ...field,
        id: null,
        mappings:
          field.mappings?.map((mapping) => ({
            ...mapping,
            id: null,
            transformations:
              mapping.transformations?.map((transformation) => ({
                ...transformation,
                id: null
              })) ?? []
          })) ?? []
      })
    );

    this.reportTemplates.clear();
    detail.reportTemplates.forEach((report) =>
      this.addReportTemplate({
        ...report,
        id: null,
        columns: report.columns?.map((column) => ({ ...column, id: null })) ?? []
      })
    );

    this.accessControlEntries.clear();
    detail.accessControlEntries.forEach((entry) =>
      this.addAccessEntry({
        ...entry,
        id: null
      })
    );

    this.patchedFromDetail = false;
  }

  private createSourceGroup(source?: AdminSource): FormGroup {
    const group = this.fb.group({
      id: [source?.id ?? null],
      code: [source?.code ?? '', Validators.required],
      displayName: [source?.displayName ?? '', Validators.required],
      adapterType: [source?.adapterType ?? 'CSV_FILE', Validators.required],
      anchor: [source?.anchor ?? false],
      description: [source?.description ?? ''],
      connectionConfig: [source?.connectionConfig ?? ''],
      arrivalExpectation: [source?.arrivalExpectation ?? ''],
      arrivalTimezone: [source?.arrivalTimezone ?? ''],
      arrivalSlaMinutes: [source?.arrivalSlaMinutes ?? null, [Validators.min(0)]],
      adapterOptions: [source?.adapterOptions ?? ''],
      llmOptions: this.createLlmAdapterOptionsGroup(),
      transformationPlan: this.createTransformationPlanGroup(source?.transformationPlan ?? null)
    });
    this.setupLlmAdapterOptions(group, source?.adapterOptions ?? null, source?.adapterType ?? 'CSV_FILE');
    const codeControl = group.get('code');
    let previousCode = source?.code ?? '';
    codeControl
      ?.valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((nextCode: string | null) => {
        const coerced = nextCode ?? '';
        this.onSourceCodeChanged(previousCode, coerced);
        previousCode = coerced;
      });
    return group;
  }

  private createTransformationPlanGroup(plan?: SourceTransformationPlan | null): FormGroup {
    return this.fb.group({
      datasetGroovyScript: [plan?.datasetGroovyScript ?? ''],
      rowOperations: this.fb.array<FormGroup>(
        (plan?.rowOperations ?? []).map((operation) => this.createRowOperationGroup(operation))
      ),
      columnOperations: this.fb.array<FormGroup>(
        (plan?.columnOperations ?? []).map((operation) => this.createColumnOperationGroup(operation))
      )
    });
  }

  private createRowOperationGroup(operation?: SourceRowOperationConfig): FormGroup {
    return this.fb.group({
      type: [operation?.type ?? ('FILTER' as SourceRowOperationType), Validators.required],
      filter: this.createRowFilterGroup(operation?.filter),
      aggregate: this.createRowAggregateGroup(operation?.aggregate),
      split: this.createRowSplitGroup(operation?.split)
    });
  }

  private createRowFilterGroup(filter?: SourceRowFilterOperation): FormGroup {
    return this.fb.group({
      column: [filter?.column ?? ''],
      mode: [filter?.mode ?? ('RETAIN_MATCHING' as SourceRowFilterMode)],
      operator: [filter?.operator ?? ('EQUALS' as SourceRowComparisonOperator)],
      value: [filter?.value ?? ''],
      valuesText: [filter?.values ? filter.values.join(', ') : ''],
      caseInsensitive: [filter?.caseInsensitive ?? false]
    });
  }

  private createRowAggregateGroup(aggregate?: SourceRowAggregateOperation): FormGroup {
    return this.fb.group({
      groupByText: [aggregate?.groupBy?.join(', ') ?? ''],
      aggregations: this.fb.array<FormGroup>(
        (aggregate?.aggregations ?? []).map((agg) => this.createAggregationGroup(agg))
      ),
      retainColumnsText: [aggregate?.retainColumns?.join(', ') ?? ''],
      sortByGroup: [aggregate?.sortByGroup ?? false]
    });
  }

  private createAggregationGroup(aggregation?: SourceAggregationDefinition): FormGroup {
    return this.fb.group({
      sourceColumn: [aggregation?.sourceColumn ?? ''],
      resultColumn: [aggregation?.resultColumn ?? ''],
      function: [aggregation?.function ?? ('SUM' as SourceAggregationFunction)],
      scale: [aggregation?.scale ?? null],
      roundingMode: [aggregation?.roundingMode ?? ('HALF_UP' as SourceRoundingMode)]
    });
  }

  private createRowSplitGroup(split?: SourceRowSplitOperation): FormGroup {
    return this.fb.group({
      sourceColumn: [split?.sourceColumn ?? ''],
      targetColumn: [split?.targetColumn ?? ''],
      delimiter: [split?.delimiter ?? '|'],
      trimValues: [split?.trimValues ?? true],
      dropEmptyValues: [split?.dropEmptyValues ?? true]
    });
  }

  private createColumnOperationGroup(operation?: SourceColumnOperationConfig): FormGroup {
    return this.fb.group({
      type: [operation?.type ?? ('COMBINE' as SourceColumnOperationType), Validators.required],
      combine: this.createColumnCombineGroup(operation?.combine),
      pipeline: this.createColumnPipelineGroup(operation?.pipeline),
      round: this.createColumnRoundGroup(operation?.round)
    });
  }

  private createColumnCombineGroup(combine?: SourceColumnCombineOperation): FormGroup {
    return this.fb.group({
      targetColumn: [combine?.targetColumn ?? ''],
      sourcesText: [combine?.sources ? combine.sources.join(', ') : ''],
      delimiter: [combine?.delimiter ?? '|'],
      skipBlanks: [combine?.skipBlanks ?? true],
      prefix: [combine?.prefix ?? ''],
      suffix: [combine?.suffix ?? '']
    });
  }

  private createColumnPipelineGroup(pipeline?: SourceColumnPipelineOperation): FormGroup {
    return this.fb.group({
      targetColumn: [pipeline?.targetColumn ?? ''],
      sourceColumn: [pipeline?.sourceColumn ?? ''],
      configuration: [pipeline?.configuration ?? '']
    });
  }

  private createColumnRoundGroup(round?: SourceColumnRoundOperation): FormGroup {
    return this.fb.group({
      targetColumn: [round?.targetColumn ?? ''],
      sourceColumn: [round?.sourceColumn ?? ''],
      scale: [round?.scale ?? 2],
      roundingMode: [round?.roundingMode ?? ('HALF_UP' as SourceRoundingMode)]
    });
  }

  private onSourceCodeChanged(previousCode: string, nextCode: string): void {
    if (!previousCode || previousCode === nextCode) {
      return;
    }
    this.canonicalFields.controls.forEach((fieldGroup) => {
      const mappings = fieldGroup.get('mappings') as FormArray<FormGroup>;
      mappings.controls.forEach((mappingGroup) => {
        const codeControl = mappingGroup.get('sourceCode');
        if (codeControl?.value === previousCode) {
          codeControl.setValue(nextCode, { emitEvent: false });
        }
      });
    });
  }

  transformationPlanGroup(sourceIndex: number): FormGroup {
    return this.sources.at(sourceIndex).get('transformationPlan') as FormGroup;
  }

  rowOperations(sourceIndex: number): FormArray<FormGroup> {
    return this.transformationPlanGroup(sourceIndex).get('rowOperations') as FormArray<FormGroup>;
  }

  columnOperations(sourceIndex: number): FormArray<FormGroup> {
    return this.transformationPlanGroup(sourceIndex).get('columnOperations') as FormArray<FormGroup>;
  }

  aggregationsFor(sourceIndex: number, operationIndex: number): FormArray<FormGroup> {
    return this.rowOperations(sourceIndex)
      .at(operationIndex)
      .get('aggregate')
      ?.get('aggregations') as FormArray<FormGroup>;
  }

  addRowOperation(sourceIndex: number, type: SourceRowOperationType = 'FILTER'): void {
    const group = this.createRowOperationGroup({ type });
    group.get('type')?.setValue(type, { emitEvent: false });
    if (type === 'AGGREGATE') {
      const aggregations = group.get('aggregate')?.get('aggregations') as FormArray<FormGroup>;
      if (aggregations.length === 0) {
        aggregations.push(this.createAggregationGroup());
      }
    }
    this.rowOperations(sourceIndex).push(group);
  }

  removeRowOperation(sourceIndex: number, operationIndex: number): void {
    this.rowOperations(sourceIndex).removeAt(operationIndex);
  }

  addAggregation(sourceIndex: number, operationIndex: number): void {
    this.aggregationsFor(sourceIndex, operationIndex).push(this.createAggregationGroup());
  }

  removeAggregation(sourceIndex: number, operationIndex: number, aggregationIndex: number): void {
    this.aggregationsFor(sourceIndex, operationIndex).removeAt(aggregationIndex);
  }

  addColumnOperation(sourceIndex: number, type: SourceColumnOperationType = 'COMBINE'): void {
    const group = this.createColumnOperationGroup({ type });
    group.get('type')?.setValue(type, { emitEvent: false });
    this.columnOperations(sourceIndex).push(group);
  }

  removeColumnOperation(sourceIndex: number, operationIndex: number): void {
    this.columnOperations(sourceIndex).removeAt(operationIndex);
  }

  onRowOperationTypeChange(sourceIndex: number, operationIndex: number): void {
    const group = this.rowOperations(sourceIndex).at(operationIndex);
    const type = group.get('type')?.value as SourceRowOperationType;
    if (type === 'AGGREGATE') {
      const aggregations = this.aggregationsFor(sourceIndex, operationIndex);
      if (aggregations.length === 0) {
        aggregations.push(this.createAggregationGroup());
      }
    }
  }

  onColumnOperationTypeChange(sourceIndex: number, operationIndex: number): void {
    const group = this.columnOperations(sourceIndex).at(operationIndex);
    const type = group.get('type')?.value as SourceColumnOperationType;
    if (type === 'PIPELINE') {
      group.get('pipeline')?.get('configuration')?.setValidators([Validators.required]);
    } else {
      group.get('pipeline')?.get('configuration')?.clearValidators();
    }
    group.get('pipeline')?.get('configuration')?.updateValueAndValidity({ emitEvent: false });
  }

  private ensureSourcePreviewState(index: number): SourceTransformationPreviewUiState {
    if (!this.sourcePreviewState[index]) {
      this.sourcePreviewState[index] = {
        fileType: 'CSV',
        hasHeader: true,
        delimiter: ',',
        sheetName: '',
        recordPath: '',
        encoding: '',
        limit: 10,
        uploading: false,
        rawRows: [],
        transformedRows: []
      };
    }
    return this.sourcePreviewState[index];
  }

  setSourcePreviewOption<K extends keyof SourceTransformationPreviewUiState>(
    index: number,
    key: K,
    value: SourceTransformationPreviewUiState[K]
  ): void {
    const state = this.ensureSourcePreviewState(index);
    state[key] = value;
  }

  onSourceSampleFileSelected(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }
    const file = input.files[0];
    const state = this.ensureSourcePreviewState(index);
    state.file = file;
    state.fileName = file.name;
  }

  previewSourceFromFile(index: number): void {
    const state = this.ensureSourcePreviewState(index);
    if (!state.file) {
      state.error = 'Select a sample file to preview transformations.';
      return;
    }
    const planGroup = this.transformationPlanGroup(index);
    const planPayload = this.buildTransformationPlanPayload(planGroup);
    const request: SourceTransformationPreviewUploadRequest = {
      fileType: state.fileType,
      hasHeader: state.hasHeader,
      delimiter: state.delimiter ?? undefined,
      sheetName: state.sheetName ?? undefined,
      recordPath: state.recordPath ?? undefined,
      encoding: state.encoding ?? undefined,
      limit: state.limit,
      transformationPlan: planPayload ?? null
    };
    state.uploading = true;
    state.error = undefined;
    this.state
      .previewSourceTransformationFromFile(request, state.file)
      .pipe(finalize(() => (state.uploading = false)))
      .subscribe({
        next: (response) => {
          state.rawRows = response.rawRows ?? [];
          state.transformedRows = response.transformedRows ?? [];
          state.lastRows = state.rawRows;
        },
        error: () => {
          state.error = 'Unable to preview transformations for the uploaded file.';
        }
      });
  }

  applyPlanToPreview(index: number): void {
    const state = this.ensureSourcePreviewState(index);
    const rows = state.lastRows ?? state.rawRows;
    if (!rows || rows.length === 0) {
      state.error = 'Upload a sample file or load recent rows before applying transformations.';
      return;
    }
    this.applyPlanToRows(index, rows);
  }

  loadSourceSampleRows(index: number): void {
    if (!this.definitionId) {
      const state = this.ensureSourcePreviewState(index);
      state.error = 'Save the reconciliation before loading live samples.';
      return;
    }
    const sourceCode = this.sources.at(index).get('code')?.value;
    if (!sourceCode) {
      const state = this.ensureSourcePreviewState(index);
      state.error = 'Set a source code before loading samples.';
      return;
    }
    const state = this.ensureSourcePreviewState(index);
    state.uploading = true;
    state.error = undefined;
    this.state
      .fetchTransformationSamples(this.definitionId, sourceCode, state.limit)
      .pipe(finalize(() => (state.uploading = false)))
      .subscribe({
        next: (response) => {
          const rows = (response.rows ?? []).map((row) => row.rawRecord ?? {});
          state.rawRows = rows;
          state.lastRows = rows;
          this.applyPlanToRows(index, rows);
        },
        error: () => {
          state.error = 'Unable to load sample rows for this source.';
        }
      });
  }

  private applyPlanToRows(index: number, rows: Record<string, unknown>[]): void {
    const state = this.ensureSourcePreviewState(index);
    if (!rows || rows.length === 0) {
      state.transformedRows = [];
      return;
    }
    const planPayload = this.buildTransformationPlanPayload(this.transformationPlanGroup(index));
    state.uploading = true;
    state.error = undefined;
    this.state
      .applySourceTransformation({ transformationPlan: planPayload ?? null, rows })
      .pipe(finalize(() => (state.uploading = false)))
      .subscribe({
        next: (response) => {
          state.transformedRows = response.transformedRows ?? [];
        },
        error: () => {
          state.error = 'Unable to apply the configured transformations.';
        }
      });
  }

  private lookupSourcePreview(sourceCode: string | null | undefined): SourceTransformationPreviewUiState | undefined {
    if (!sourceCode) {
      return undefined;
    }
    const normalized = sourceCode.trim().toLowerCase();
    for (let index = 0; index < this.sources.length; index++) {
      const candidate = this.normalize(this.sources.at(index).get('code')?.value)?.toLowerCase();
      if (candidate === normalized) {
        return this.sourcePreviewState[index];
      }
    }
    return undefined;
  }

  private buildTransformationPlanPayload(planGroup: FormGroup): SourceTransformationPlan | null {
    if (!planGroup) {
      return null;
    }
    const datasetScript = this.normalize(planGroup.get('datasetGroovyScript')?.value);
    const rowOperationsControl = planGroup.get('rowOperations') as FormArray<FormGroup>;
    const rowOperations = rowOperationsControl?.controls
      .map((operationGroup) => this.buildRowOperationPayload(operationGroup))
      .filter((operation): operation is SourceRowOperationConfig => operation !== null);
    const columnOperationsControl = planGroup.get('columnOperations') as FormArray<FormGroup>;
    const columnOperations = columnOperationsControl?.controls
      .map((operationGroup) => this.buildColumnOperationPayload(operationGroup))
      .filter((operation): operation is SourceColumnOperationConfig => operation !== null);
    const hasScript = !!datasetScript;
    const hasRows = rowOperations && rowOperations.length > 0;
    const hasColumns = columnOperations && columnOperations.length > 0;
    if (!hasScript && !hasRows && !hasColumns) {
      return null;
    }
    return {
      datasetGroovyScript: datasetScript ?? undefined,
      rowOperations: rowOperations ?? [],
      columnOperations: columnOperations ?? []
    };
  }

  private buildRowOperationPayload(group: FormGroup): SourceRowOperationConfig | null {
    const type = group.get('type')?.value as SourceRowOperationType;
    if (!type) {
      return null;
    }
    switch (type) {
      case 'FILTER': {
        const filterGroup = group.get('filter') as FormGroup;
        const column = this.normalize(filterGroup.get('column')?.value);
        if (!column) {
          return null;
        }
        const operator = (filterGroup.get('operator')?.value as SourceRowComparisonOperator) ?? 'EQUALS';
        const mode = (filterGroup.get('mode')?.value as SourceRowFilterMode) ?? 'RETAIN_MATCHING';
        const caseInsensitive = !!filterGroup.get('caseInsensitive')?.value;
        const value = this.normalize(filterGroup.get('value')?.value);
        const values = this.parseCsvList(this.normalize(filterGroup.get('valuesText')?.value));
        const filter: SourceRowFilterOperation = {
          column,
          mode,
          operator,
          caseInsensitive
        };
        if (operator === 'IN' || operator === 'NOT_IN') {
          filter.values = values;
        } else if (value !== null) {
          filter.value = value;
        } else if (values.length > 0) {
          filter.values = values;
        }
        return { type, filter };
      }
      case 'AGGREGATE': {
        const aggregateGroup = group.get('aggregate') as FormGroup;
        const groupBy = this.parseCsvList(this.normalize(aggregateGroup.get('groupByText')?.value));
        const retainColumns = this.parseCsvList(this.normalize(aggregateGroup.get('retainColumnsText')?.value));
        const aggregationsControl = aggregateGroup.get('aggregations') as FormArray<FormGroup>;
        const aggregations = aggregationsControl.controls
          .map((aggGroup) => this.buildAggregationPayload(aggGroup))
          .filter((agg): agg is SourceAggregationDefinition => agg !== null);
        if (groupBy.length === 0 && aggregations.length === 0) {
          return null;
        }
        const aggregate: SourceRowAggregateOperation = {
          groupBy,
          aggregations,
          retainColumns,
          sortByGroup: !!aggregateGroup.get('sortByGroup')?.value
        };
        return { type, aggregate };
      }
      case 'SPLIT': {
        const splitGroup = group.get('split') as FormGroup;
        const sourceColumn = this.normalize(splitGroup.get('sourceColumn')?.value);
        if (!sourceColumn) {
          return null;
        }
        const split: SourceRowSplitOperation = {
          sourceColumn,
          targetColumn: this.normalize(splitGroup.get('targetColumn')?.value) ?? undefined,
          delimiter: this.normalize(splitGroup.get('delimiter')?.value) ?? undefined,
          trimValues: !!splitGroup.get('trimValues')?.value,
          dropEmptyValues: !!splitGroup.get('dropEmptyValues')?.value
        };
        return { type, split };
      }
      default:
        return null;
    }
  }

  private buildAggregationPayload(group: FormGroup): SourceAggregationDefinition | null {
    const sourceColumn = this.normalize(group.get('sourceColumn')?.value);
    const resultColumn = this.normalize(group.get('resultColumn')?.value);
    if (!sourceColumn && !resultColumn) {
      return null;
    }
    const aggregation: SourceAggregationDefinition = {
      sourceColumn: sourceColumn ?? undefined,
      resultColumn: resultColumn ?? undefined,
      function: (group.get('function')?.value as SourceAggregationFunction) ?? 'SUM'
    };
    const scale = this.normalizeNumber(group.get('scale')?.value);
    if (scale !== null && scale !== undefined) {
      aggregation.scale = scale;
    }
    const roundingMode = group.get('roundingMode')?.value as SourceRoundingMode;
    if (roundingMode) {
      aggregation.roundingMode = roundingMode;
    }
    return aggregation;
  }

  private buildColumnOperationPayload(group: FormGroup): SourceColumnOperationConfig | null {
    const type = group.get('type')?.value as SourceColumnOperationType;
    if (!type) {
      return null;
    }
    switch (type) {
      case 'COMBINE': {
        const combineGroup = group.get('combine') as FormGroup;
        const targetColumn = this.normalize(combineGroup.get('targetColumn')?.value);
        if (!targetColumn) {
          return null;
        }
        const sources = this.parseCsvList(this.normalize(combineGroup.get('sourcesText')?.value));
        const combine: SourceColumnCombineOperation = {
          targetColumn,
          sources,
          delimiter: this.normalize(combineGroup.get('delimiter')?.value) ?? undefined,
          skipBlanks: !!combineGroup.get('skipBlanks')?.value,
          prefix: this.normalize(combineGroup.get('prefix')?.value) ?? undefined,
          suffix: this.normalize(combineGroup.get('suffix')?.value) ?? undefined
        };
        return { type, combine };
      }
      case 'PIPELINE': {
        const pipelineGroup = group.get('pipeline') as FormGroup;
        const targetColumn = this.normalize(pipelineGroup.get('targetColumn')?.value);
        const configuration = pipelineGroup.get('configuration')?.value;
        if (!targetColumn || !this.hasText(configuration)) {
          return null;
        }
        const pipeline: SourceColumnPipelineOperation = {
          targetColumn,
          sourceColumn: this.normalize(pipelineGroup.get('sourceColumn')?.value) ?? undefined,
          configuration: configuration as string
        };
        return { type, pipeline };
      }
      case 'ROUND': {
        const roundGroup = group.get('round') as FormGroup;
        const targetColumn = this.normalize(roundGroup.get('targetColumn')?.value);
        if (!targetColumn) {
          return null;
        }
        const round: SourceColumnRoundOperation = {
          targetColumn,
          sourceColumn: this.normalize(roundGroup.get('sourceColumn')?.value) ?? undefined,
          scale: this.normalizeNumber(roundGroup.get('scale')?.value) ?? undefined,
          roundingMode: (roundGroup.get('roundingMode')?.value as SourceRoundingMode) ?? 'HALF_UP'
        };
        return { type, round };
      }
      default:
        return null;
    }
  }

  private parseCsvList(value: string | null | undefined): string[] {
    if (!value) {
      return [];
    }
    return value
      .split(',')
      .map((entry) => entry.trim())
      .filter((entry) => entry.length > 0);
  }

  private createLlmAdapterOptionsGroup(initial?: Partial<LlmAdapterOptionsFormValue>): FormGroup {
    return this.fb.group({
      model: [initial?.model ?? ''],
      promptTemplate: [initial?.promptTemplate ?? ''],
      extractionSchema: [initial?.extractionSchema ?? ''],
      recordPath: [initial?.recordPath ?? ''],
      temperature: [initial?.temperature ?? null],
      maxOutputTokens: [initial?.maxOutputTokens ?? null]
    });
  }

  private hasText(value?: string | null): value is string {
    return value !== undefined && value !== null && value.trim().length > 0;
  }

  private isNumber(value: number | null | undefined): value is number {
    return typeof value === 'number' && !Number.isNaN(value);
  }

  private coerceString(value: unknown): string {
    if (value === undefined || value === null) {
      return '';
    }
    return String(value);
  }

  private coerceNumber(value: unknown): number | null {
    if (typeof value === 'number') {
      return Number.isNaN(value) ? null : value;
    }
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (trimmed.length === 0) {
        return null;
      }
      const parsed = Number(trimmed);
      return Number.isNaN(parsed) ? null : parsed;
    }
    return null;
  }

  private coerceInteger(value: unknown): number | null {
    const parsed = this.coerceNumber(value);
    return parsed === null ? null : Math.trunc(parsed);
  }

  private coerceBoolean(value: unknown, fallback: boolean): boolean {
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'string') {
      const normalized = value.trim().toLowerCase();
      if (normalized === 'true') {
        return true;
      }
      if (normalized === 'false') {
        return false;
      }
    }
    return fallback;
  }

  private parseJsonOrWarn(text: string, warning: string): unknown {
    try {
      return JSON.parse(text);
    } catch (error) {
      console.warn(warning, error);
      return text;
    }
  }

  private parseLlmAdapterOptions(raw?: string | null): LlmAdapterOptionsFormValue {
    const defaults: LlmAdapterOptionsFormValue = {
      model: '',
      promptTemplate: '',
      extractionSchema: '',
      recordPath: '',
      temperature: null,
      maxOutputTokens: null
    };
    if (!raw) {
      return { ...defaults };
    }
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const result: LlmAdapterOptionsFormValue = { ...defaults };
      const model = parsed['model'];
      if (model !== undefined && model !== null) {
        result.model = this.coerceString(model);
      }
      const promptTemplate = parsed['promptTemplate'];
      if (promptTemplate !== undefined && promptTemplate !== null) {
        result.promptTemplate = this.coerceString(promptTemplate);
      }
      const extractionSchema = parsed['extractionSchema'];
      if (extractionSchema !== undefined && extractionSchema !== null) {
        result.extractionSchema = this.stringifyMaybeJson(extractionSchema);
      }
      const recordPath = parsed['recordPath'];
      if (recordPath !== undefined && recordPath !== null) {
        result.recordPath = this.coerceString(recordPath);
      }
      const temperature = parsed['temperature'];
      if (temperature !== undefined && temperature !== null) {
        result.temperature = this.coerceNumber(temperature);
      }
      const maxOutputTokens = parsed['maxOutputTokens'];
      if (maxOutputTokens !== undefined && maxOutputTokens !== null) {
        result.maxOutputTokens = this.coerceInteger(maxOutputTokens);
      }
      return result;
    } catch (error) {
      console.warn('Failed to parse LLM adapter options', error);
      return {
        ...defaults,
        promptTemplate: raw ?? ''
      };
    }
  }

  private buildLlmAdapterOptionsJson(value: LlmAdapterOptionsFormValue): string {
    const payload: Record<string, unknown> = {};
    if (this.hasText(value.model)) {
      payload['model'] = value.model.trim();
    }
    if (this.hasText(value.promptTemplate)) {
      payload['promptTemplate'] = value.promptTemplate;
    }
    if (this.hasText(value.recordPath)) {
      payload['recordPath'] = value.recordPath.trim();
    }
    if (this.isNumber(value.temperature)) {
      payload['temperature'] = value.temperature;
    }
    if (this.isNumber(value.maxOutputTokens)) {
      payload['maxOutputTokens'] = value.maxOutputTokens;
    }
    if (this.hasText(value.extractionSchema)) {
      payload['extractionSchema'] = this.parseJsonOrWarn(
        value.extractionSchema,
        'LLM extraction schema is not valid JSON; storing raw string'
      );
    }
    if (Object.keys(payload).length === 0) {
      return '';
    }
    return JSON.stringify(payload, null, 2);
  }

  private setupLlmAdapterOptions(
    group: FormGroup,
    adapterOptions: string | null,
    adapterType: IngestionAdapterType
  ): void {
    const adapterTypeControl = group.get('adapterType');
    const adapterOptionsControl = group.get('adapterOptions');
    const llmGroup = group.get('llmOptions') as FormGroup;

    const applyOptionsToForm = (raw: string | null) => {
      const parsed = this.parseLlmAdapterOptions(raw);
      llmGroup.patchValue(parsed, { emitEvent: false });
    };

    if (adapterType === 'LLM_DOCUMENT') {
      applyOptionsToForm(adapterOptions);
    } else {
      llmGroup.patchValue(this.createLlmAdapterOptionsGroup().getRawValue(), { emitEvent: false });
    }

    adapterTypeControl
      ?.valueChanges.pipe(takeUntil(this.destroy$))
      .subscribe((nextType: IngestionAdapterType) => {
        if (nextType === 'LLM_DOCUMENT') {
          applyOptionsToForm(adapterOptionsControl?.value ?? null);
        }
      });

    llmGroup.valueChanges.pipe(takeUntil(this.destroy$)).subscribe((value) => {
      const currentType = adapterTypeControl?.value as IngestionAdapterType;
      if (currentType !== 'LLM_DOCUMENT') {
        return;
      }
      const json = this.buildLlmAdapterOptionsJson(value as LlmAdapterOptionsFormValue);
      adapterOptionsControl?.setValue(json, { emitEvent: false });
    });
  }

  private stringifyMaybeJson(schema: unknown): string {
    if (!schema) {
      return '';
    }
    if (typeof schema === 'string') {
      return schema;
    }
    try {
      return JSON.stringify(schema, null, 2);
    } catch (error) {
      return String(schema);
    }
  }

  private createFieldGroup(field?: AdminCanonicalField): FormGroup {
    return this.fb.group({
      id: [field?.id ?? null],
      canonicalName: [field?.canonicalName ?? '', Validators.required],
      displayName: [field?.displayName ?? '', Validators.required],
      role: [field?.role ?? 'COMPARE', Validators.required],
      dataType: [field?.dataType ?? 'STRING', Validators.required],
      comparisonLogic: [field?.comparisonLogic ?? 'EXACT_MATCH', Validators.required],
      thresholdPercentage: [field?.thresholdPercentage ?? null],
      classifierTag: [field?.classifierTag ?? ''],
      formattingHint: [field?.formattingHint ?? ''],
      displayOrder: [field?.displayOrder ?? null],
      required: [field?.required ?? false],
      mappings: this.fb.array<FormGroup>(
        (field?.mappings ?? []).map((mapping) => this.createMappingGroup(mapping))
      )
    });
  }

  private createMappingGroup(mapping?: AdminCanonicalFieldMapping): FormGroup {
    return this.fb.group({
      id: [mapping?.id ?? null],
      sourceCode: [mapping?.sourceCode ?? '', Validators.required],
      sourceColumn: [mapping?.sourceColumn ?? '', Validators.required],
      transformationExpression: [mapping?.transformationExpression ?? ''],
      defaultValue: [mapping?.defaultValue ?? ''],
      ordinalPosition: [mapping?.ordinalPosition ?? null],
      required: [mapping?.required ?? false],
      transformations: this.fb.array<FormGroup>(
        (mapping?.transformations ?? []).map((transformation) => this.createTransformationGroup(transformation))
      )
    });
  }

  private createTransformationGroup(transformation?: AdminCanonicalFieldTransformation): FormGroup {
    const type: TransformationType = transformation?.type ?? 'FUNCTION_PIPELINE';
    const group = this.fb.group({
      id: [transformation?.id ?? null],
      type: [type, Validators.required],
      expression: [transformation?.expression ?? ''],
      configuration: [transformation?.configuration ?? null],
      displayOrder: [transformation?.displayOrder ?? null],
      active: [transformation?.active ?? true],
      validationMessage: [''],
      pipelineSteps: this.fb.array<FormGroup>([])
    });
    if (type === 'FUNCTION_PIPELINE') {
      const steps = this.deserializePipelineConfiguration(transformation?.configuration);
      const target = group.get('pipelineSteps') as FormArray<FormGroup>;
      if (steps.length === 0) {
        target.push(this.createPipelineStepGroup());
      } else {
        steps.forEach((step) => target.push(this.createPipelineStepGroup(step.function, step.args)));
      }
    } else {
      const steps = this.pipelineStepsFromGroup(group);
      if (steps.length > 0) {
        steps.clear();
      }
    }
    return group;
  }

  private createPipelineStepGroup(fn: string = 'TRIM', args: string[] = []): FormGroup {
    return this.fb.group({
      function: [fn, Validators.required],
      args: this.fb.array<FormControl<string | null>>(
        args.length > 0
          ? args.map((value) => this.fb.control(value))
          : [this.fb.control('')]
      )
    });
  }

  private deserializePipelineConfiguration(
    configuration?: string | null
  ): Array<{ function: string; args: string[] }> {
    if (!configuration) {
      return [];
    }
    try {
      const parsed = JSON.parse(configuration) as {
        steps?: Array<{ function?: string; args?: string[] }>;
      };
      if (!parsed || !Array.isArray(parsed.steps)) {
        return [];
      }
      return parsed.steps
        .filter((step) => typeof step.function === 'string')
        .map((step) => ({
          function: step.function as string,
          args: Array.isArray(step.args)
            ? step.args
                .filter((arg) => arg !== undefined && arg !== null)
                .map((arg) => String(arg))
            : []
        }));
    } catch (error) {
      console.warn('Failed to parse pipeline configuration', error);
      return [];
    }
  }

  private pipelineStepsFromGroup(group: FormGroup): FormArray<FormGroup> {
    return group.get('pipelineSteps') as FormArray<FormGroup>;
  }

  private pipelineArgsFromGroup(stepGroup: FormGroup): FormArray<FormControl<string | null>> {
    return stepGroup.get('args') as FormArray<FormControl<string | null>>;
  }

  private serializePipeline(group: FormGroup): string | null {
    const stepsArray = this.pipelineStepsFromGroup(group).controls
      .map((stepGroup) => {
        const fn = stepGroup.get('function')?.value as string;
        const args = this.pipelineArgsFromGroup(stepGroup)
          .controls.map((ctrl) => (ctrl.value ?? '').toString())
          .filter((value) => value.length > 0);
        return fn ? { function: fn, args } : null;
      })
      .filter((step): step is { function: string; args: string[] } => step !== null);

    return JSON.stringify({ steps: stepsArray });
  }

  private buildTransformationValidationPayload(group: FormGroup): TransformationValidationRequest {
    const type = group.get('type')?.value as TransformationType;
    let expression = this.normalize(group.get('expression')?.value);
    let configuration = this.normalize(group.get('configuration')?.value);
    if (type === 'FUNCTION_PIPELINE') {
      configuration = this.serializePipeline(group);
      expression = null;
    }
    return {
      type,
      expression: expression ?? undefined,
      configuration: configuration ?? undefined
    };
  }

  private buildTransformationsPayload(
    fieldIndex: number,
    mappingIndex: number
  ): AdminCanonicalFieldTransformation[] {
    return this.mappingTransformations(fieldIndex, mappingIndex).controls.map((group, index) => {
      return this.toTransformationRequestPayload(group as FormGroup, index);
    });
  }

  private toTransformationRequestPayload(group: FormGroup, fallbackIndex: number): AdminCanonicalFieldTransformation {
    const type = group.get('type')?.value as TransformationType;
    const active = !!group.get('active')?.value;
    const displayOrder = this.normalizeNumber(group.get('displayOrder')?.value) ?? fallbackIndex;
    let expression = this.normalize(group.get('expression')?.value);
    let configuration = this.normalize(group.get('configuration')?.value);
    if (type === 'FUNCTION_PIPELINE') {
      configuration = this.serializePipeline(group);
      expression = null;
    }
    return {
      id: group.get('id')?.value ?? null,
      type,
      expression: expression ?? null,
      configuration: configuration ?? null,
      displayOrder,
      active
    };
  }

  private createReportGroup(report?: AdminReportTemplate): FormGroup {
    return this.fb.group({
      id: [report?.id ?? null],
      name: [report?.name ?? '', Validators.required],
      description: [report?.description ?? '', Validators.required],
      includeMatched: [report?.includeMatched ?? true],
      includeMismatched: [report?.includeMismatched ?? true],
      includeMissing: [report?.includeMissing ?? true],
      highlightDifferences: [report?.highlightDifferences ?? false],
      columns: this.fb.array<FormGroup>(
        (report?.columns ?? []).map((column) => this.createReportColumnGroup(column))
      )
    });
  }

  private createReportColumnGroup(column?: AdminReportColumn): FormGroup {
    return this.fb.group({
      id: [column?.id ?? null],
      header: [column?.header ?? '', Validators.required],
      source: [column?.source ?? 'SOURCE_A', Validators.required],
      sourceField: [column?.sourceField ?? ''],
      displayOrder: [column?.displayOrder ?? 0, Validators.required],
      highlightDifferences: [column?.highlightDifferences ?? false]
    });
  }

  private createAccessGroup(entry?: AdminAccessControlEntry): FormGroup {
    return this.fb.group({
      id: [entry?.id ?? null],
      ldapGroupDn: [entry?.ldapGroupDn ?? '', Validators.required],
      role: [entry?.role ?? 'VIEWER', Validators.required],
      product: [entry?.product ?? ''],
      subProduct: [entry?.subProduct ?? ''],
      entityName: [entry?.entityName ?? ''],
      notifyOnPublish: [entry?.notifyOnPublish ?? false],
      notifyOnIngestionFailure: [entry?.notifyOnIngestionFailure ?? false],
      notificationChannel: [entry?.notificationChannel ?? '']
    });
  }

  private validateStep(index: number): boolean {
    switch (this.steps[index]?.key) {
      case 'definition':
        return this.markGroupAndCheckValid(this.definitionGroup);
      case 'sources':
        return this.markArrayAndCheckValid(this.sources);
      case 'schema':
        return this.markArrayAndCheckValid(this.canonicalFields);
      case 'reports':
        return this.markArrayAndCheckValid(this.reportTemplates);
      case 'access':
        return this.markArrayAndCheckValid(this.accessControlEntries);
      default:
        return true;
    }
  }

  private markGroupAndCheckValid(group: FormGroup): boolean {
    group.markAllAsTouched();
    return group.valid;
  }

  private markArrayAndCheckValid(array: FormArray<FormGroup>): boolean {
    array.controls.forEach((control) => control.markAllAsTouched());
    return array.valid;
  }

  private buildRequest(): AdminReconciliationRequest {
    const definitionValue = this.definitionGroup.value as {
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
    };

    const sourcesPayload = this.sources.controls.map((group) => {
      const rawValue = group.getRawValue() as AdminSource & { llmOptions?: LlmAdapterOptionsFormValue };
      const { llmOptions: _llmOptions, ...value } = rawValue;
      return {
        id: value.id ?? null,
        code: value.code,
        displayName: value.displayName,
        adapterType: value.adapterType,
        anchor: !!value.anchor,
        description: this.normalize(value.description),
        connectionConfig: this.normalize(value.connectionConfig),
      arrivalExpectation: this.normalize(value.arrivalExpectation),
      arrivalTimezone: this.normalize(value.arrivalTimezone),
      arrivalSlaMinutes: this.normalizeNumber(value.arrivalSlaMinutes),
      adapterOptions: this.normalize(value.adapterOptions) ?? null,
      transformationPlan: this.buildTransformationPlanPayload(
        group.get('transformationPlan') as FormGroup
      )
    } as AdminSource;
  });

    const canonicalFieldsPayload = this.canonicalFields.controls.map((fieldGroup, fieldIndex) => {
      const value = fieldGroup.value as AdminCanonicalField;
      return {
        id: value.id ?? null,
        canonicalName: value.canonicalName,
        displayName: value.displayName,
        role: value.role,
        dataType: value.dataType,
        comparisonLogic: value.comparisonLogic,
        thresholdPercentage: this.normalizeNumber(value.thresholdPercentage),
        classifierTag: this.normalize(value.classifierTag),
        formattingHint: this.normalize(value.formattingHint),
        displayOrder: this.normalizeNumber(value.displayOrder),
        required: !!value.required,
        mappings: this.sourceMappings(fieldIndex).controls.map((mappingGroup, mappingIndex) => {
          const mappingValue = mappingGroup.value as AdminCanonicalFieldMapping;
          return {
            id: mappingValue.id ?? null,
            sourceCode: mappingValue.sourceCode,
            sourceColumn: mappingValue.sourceColumn,
            transformationExpression: this.normalize(mappingValue.transformationExpression),
            defaultValue: this.normalize(mappingValue.defaultValue),
            ordinalPosition: this.normalizeNumber(mappingValue.ordinalPosition),
            required: !!mappingValue.required,
            transformations: this.buildTransformationsPayload(fieldIndex, mappingIndex)
          };
        })
      } as AdminCanonicalField;
    });

    const reportTemplatesPayload = this.reportTemplates.controls.map((group, reportIndex) => {
      const value = group.value as AdminReportTemplate;
      return {
        id: value.id ?? null,
        name: value.name,
        description: value.description,
        includeMatched: !!value.includeMatched,
        includeMismatched: !!value.includeMismatched,
        includeMissing: !!value.includeMissing,
        highlightDifferences: !!value.highlightDifferences,
        columns: this.reportColumns(reportIndex).controls.map((columnGroup) => {
          const columnValue = columnGroup.value as AdminReportColumn;
          return {
            id: columnValue.id ?? null,
            header: columnValue.header,
            source: columnValue.source,
            sourceField: this.normalize(columnValue.sourceField),
            displayOrder: columnValue.displayOrder,
            highlightDifferences: !!columnValue.highlightDifferences
          };
        })
      } as AdminReportTemplate;
    });

    const accessEntriesPayload = this.accessControlEntries.controls.map((group) => {
      const value = group.value as AdminAccessControlEntry;
      return {
        id: value.id ?? null,
        ldapGroupDn: value.ldapGroupDn,
        role: value.role,
        product: this.normalize(value.product),
        subProduct: this.normalize(value.subProduct),
        entityName: this.normalize(value.entityName),
        notifyOnPublish: !!value.notifyOnPublish,
        notifyOnIngestionFailure: !!value.notifyOnIngestionFailure,
        notificationChannel: this.normalize(value.notificationChannel)
      } as AdminAccessControlEntry;
    });

    return {
      code: definitionValue.code,
      name: definitionValue.name,
      description: definitionValue.description,
      owner: this.normalize(definitionValue.owner),
      makerCheckerEnabled: definitionValue.makerCheckerEnabled,
      notes: this.normalize(definitionValue.notes),
      status: definitionValue.status,
      autoTriggerEnabled: definitionValue.autoTriggerEnabled,
      autoTriggerCron: this.normalize(definitionValue.autoTriggerCron),
      autoTriggerTimezone: this.normalize(definitionValue.autoTriggerTimezone),
      autoTriggerGraceMinutes: this.normalizeNumber(definitionValue.autoTriggerGraceMinutes),
      version: definitionValue.version ?? null,
      sources: sourcesPayload,
      canonicalFields: canonicalFieldsPayload,
      reportTemplates: reportTemplatesPayload,
      accessControlEntries: accessEntriesPayload
    } as AdminReconciliationRequest;
  }

  private normalize(value: unknown): string | null {
    if (value === undefined || value === null) {
      return null;
    }
    const text = String(value).trim();
    return text.length === 0 ? null : text;
  }

  private normalizeNumber(value: unknown): number | null {
    if (value === undefined || value === null || value === '') {
      return null;
    }
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
  }
}
