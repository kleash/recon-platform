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
import { Subject, take, takeUntil } from 'rxjs';
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
  TransformationPreviewRequest,
  TransformationPreviewResponse,
  TransformationSampleRow,
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
    'MESSAGE_QUEUE'
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
  readonly transformationTypes: TransformationType[] = [
    'GROOVY_SCRIPT',
    'EXCEL_FORMULA',
    'FUNCTION_PIPELINE'
  ];
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
  previewState: Record<string, { value: string; raw: string; result?: string; error?: string }> = {};
  transformationSamples: Record<string, TransformationSampleRow[]> = {};
  selectedSampleIndex: Record<string, number> = {};
  groovyTestState: Record<string, { running: boolean; result?: string; error?: string }> = {};

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
  }

  removeSource(index: number): void {
    this.sources.removeAt(index);
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
    this.rebuildPreviewState();
  }

  addMapping(fieldIndex: number, mapping?: AdminCanonicalFieldMapping): void {
    const mappings = this.sourceMappings(fieldIndex);
    mappings.push(this.createMappingGroup(mapping));
    this.ensurePreviewState(fieldIndex, mappings.length - 1);
  }

  removeMapping(fieldIndex: number, mappingIndex: number): void {
    this.sourceMappings(fieldIndex).removeAt(mappingIndex);
    this.rebuildPreviewState();
  }

  addTransformation(
    fieldIndex: number,
    mappingIndex: number,
    transformation?: AdminCanonicalFieldTransformation
  ): void {
    this.mappingTransformations(fieldIndex, mappingIndex).push(this.createTransformationGroup(transformation));
  }

  removeTransformation(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    this.mappingTransformations(fieldIndex, mappingIndex).removeAt(transformationIndex);
  }

  addPipelineStep(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    this.pipelineSteps(fieldIndex, mappingIndex, transformationIndex).push(this.createPipelineStepGroup());
  }

  removePipelineStep(fieldIndex: number, mappingIndex: number, transformationIndex: number, stepIndex: number): void {
    this.pipelineSteps(fieldIndex, mappingIndex, transformationIndex).removeAt(stepIndex);
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
  }

  removePipelineArg(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number,
    stepIndex: number,
    argIndex: number
  ): void {
    this.pipelineStepArgs(fieldIndex, mappingIndex, transformationIndex, stepIndex).removeAt(argIndex);
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

  previewMapping(fieldIndex: number, mappingIndex: number): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    this.ensurePreviewState(fieldIndex, mappingIndex);
    const state = this.previewState[key];
    let rawRecord: Record<string, unknown> = {};
    if (state.raw) {
      try {
        rawRecord = state.raw.trim().length === 0 ? {} : (JSON.parse(state.raw) as Record<string, unknown>);
      } catch (error) {
        this.previewState[key] = {
          ...state,
          error: 'Sample row must be valid JSON (e.g. { "column": "value" }).'
        };
        return;
      }
    }

    const transformations = this.buildTransformationsPayload(fieldIndex, mappingIndex);
    const request: TransformationPreviewRequest = {
      value: state.value,
      rawRecord,
      transformations: transformations.map((transformation) => ({
        type: transformation.type,
        expression: transformation.expression ?? undefined,
        configuration: transformation.configuration ?? undefined,
        displayOrder: transformation.displayOrder ?? undefined,
        active: transformation.active
      }))
    };

    this.state
      .previewTransformation(request)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.previewState[key] = {
            ...state,
            result: this.toDisplayResult(response),
            error: undefined
          };
        },
        error: (error) => {
          const details = error?.error?.details ?? error?.error ?? 'Preview failed.';
          this.previewState[key] = { ...state, error: details };
        }
      });
  }

  loadSampleRows(fieldIndex: number, mappingIndex: number): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    this.ensurePreviewState(fieldIndex, mappingIndex);

    this.transformationSamples[key] = [];
    this.selectedSampleIndex[key] = 0;

    if (!this.definitionId) {
      this.previewState[key] = {
        ...this.previewState[key],
        error: 'Save the reconciliation before loading source samples.'
      };
      return;
    }

    const mappingGroup = this.sourceMappings(fieldIndex).at(mappingIndex) as FormGroup;
    const sourceCode = this.normalize(mappingGroup.get('sourceCode')?.value) ?? '';
    if (!sourceCode) {
      this.previewState[key] = {
        ...this.previewState[key],
        error: 'Provide a source code before loading samples.'
      };
      return;
    }

    this.state
      .fetchTransformationSamples(this.definitionId, sourceCode)
      .pipe(take(1))
      .subscribe({
        next: (response) => {
          this.transformationSamples[key] = response.rows ?? [];
          if ((response.rows ?? []).length === 0) {
            this.previewState[key] = {
              ...this.previewState[key],
              error: 'No sample rows found for this source. Ingest data and try again.'
            };
            return;
          }
          this.selectedSampleIndex[key] = 0;
          this.applySampleToPreview(fieldIndex, mappingIndex, 0);
        },
        error: () => {
          // errors are surfaced by the state service via notifications
        }
      });
  }

  onSampleChange(fieldIndex: number, mappingIndex: number, sampleIndexValue: string): void {
    const index = Number(sampleIndexValue);
    if (Number.isNaN(index)) {
      return;
    }
    this.applySampleToPreview(fieldIndex, mappingIndex, index);
  }

  runGroovyTest(fieldIndex: number, mappingIndex: number, transformationIndex: number): void {
    const transformationGroup = this.mappingTransformations(fieldIndex, mappingIndex).at(transformationIndex) as FormGroup;
    const script = this.normalize(transformationGroup.get('expression')?.value);
    if (!script) {
      transformationGroup.get('validationMessage')?.setValue('Script is required before running a test.');
      return;
    }

    const preview = this.previewState[this.previewKey(fieldIndex, mappingIndex)];
    if (!preview) {
      return;
    }

    let rawRecord: Record<string, unknown> = {};
    if (preview.raw) {
      try {
        rawRecord = preview.raw.trim().length === 0 ? {} : (JSON.parse(preview.raw) as Record<string, unknown>);
      } catch (error) {
        this.setGroovyTestState(fieldIndex, mappingIndex, transformationIndex, {
          running: false,
          error: 'Sample row must be valid JSON to run the Groovy test.'
        });
        return;
      }
    }

    const request: GroovyScriptTestRequest = {
      script,
      value: preview.value ?? null,
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

  private rebuildPreviewState(): void {
    const next: Record<string, { value: string; raw: string; result?: string; error?: string }> = {};
    this.canonicalFields.controls.forEach((_, fieldIndex) => {
      this.sourceMappings(fieldIndex).controls.forEach((__, mappingIndex) => {
        const key = this.previewKey(fieldIndex, mappingIndex);
        const previous = this.previewState[key];
        next[key] = {
          value: previous?.value ?? '',
          raw: previous?.raw ?? '{}',
          result: previous?.result,
          error: undefined
        };
      });
    });
    this.previewState = next;
  }

  private applySampleToPreview(fieldIndex: number, mappingIndex: number, sampleIndex: number): void {
    const key = this.previewKey(fieldIndex, mappingIndex);
    const samples = this.transformationSamples[key] ?? [];
    const sample = samples[sampleIndex];
    if (!sample) {
      return;
    }
    this.ensurePreviewState(fieldIndex, mappingIndex);
    const mappingGroup = this.sourceMappings(fieldIndex).at(mappingIndex) as FormGroup;
    const column = this.normalize(mappingGroup.get('sourceColumn')?.value) ?? '';
    const rawRecord = sample.rawRecord ?? {};
    const rawJson = JSON.stringify(rawRecord, null, 2);
    const value = column ? this.extractColumnValue(rawRecord, column) : undefined;
    this.previewState[key] = {
      value: value === undefined || value === null ? '' : String(value),
      raw: rawJson,
      result: undefined,
      error: undefined
    };
    this.selectedSampleIndex[key] = sampleIndex;
  }

  private setGroovyTestState(
    fieldIndex: number,
    mappingIndex: number,
    transformationIndex: number,
    state: { running: boolean; result?: string; error?: string }
  ): void {
    this.groovyTestState[this.transformationKey(fieldIndex, mappingIndex, transformationIndex)] = state;
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

  private stringifyResult(value: unknown): string {
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

    this.rebuildPreviewState();

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

    this.rebuildPreviewState();

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
    return this.fb.group({
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
      adapterOptions: [source?.adapterOptions ?? '']
    });
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

  private toDisplayResult(response: TransformationPreviewResponse): string {
    if (response.result === null || response.result === undefined) {
      return 'null';
    }
    if (typeof response.result === 'object') {
      try {
        return JSON.stringify(response.result);
      } catch (error) {
        return String(response.result);
      }
    }
    return String(response.result);
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
      const value = group.value as AdminSource;
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
        adapterOptions: this.normalize(value.adapterOptions) ?? null
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
