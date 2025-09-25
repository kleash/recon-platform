import { AsyncPipe, CommonModule, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  FormArray,
  FormBuilder,
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
  ReconciliationLifecycleStatus
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
    }
  }

  removeCanonicalField(index: number): void {
    this.canonicalFields.removeAt(index);
  }

  addMapping(fieldIndex: number, mapping?: AdminCanonicalFieldMapping): void {
    this.sourceMappings(fieldIndex).push(this.createMappingGroup(mapping));
  }

  removeMapping(fieldIndex: number, mappingIndex: number): void {
    this.sourceMappings(fieldIndex).removeAt(mappingIndex);
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
        mappings: field.mappings?.map((mapping) => ({ ...mapping, id: null })) ?? []
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
      required: [mapping?.required ?? false]
    });
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
      autoTriggerGraceMinutes: this.normalizeNumber(
        definitionValue.autoTriggerGraceMinutes
      ),
      version: definitionValue.version ?? null,
      sources: this.sources.value.map((source) => ({
        ...source,
        description: this.normalize(source.description),
        connectionConfig: this.normalize(source.connectionConfig),
        arrivalExpectation: this.normalize(source.arrivalExpectation),
        arrivalTimezone: this.normalize(source.arrivalTimezone),
        arrivalSlaMinutes: this.normalizeNumber(source.arrivalSlaMinutes),
        adapterOptions: this.normalize(source.adapterOptions)
      })),
      canonicalFields: this.canonicalFields.value.map((field) => ({
        ...field,
        classifierTag: this.normalize(field.classifierTag),
        formattingHint: this.normalize(field.formattingHint),
        thresholdPercentage: this.normalizeNumber(field.thresholdPercentage),
        displayOrder: this.normalizeNumber(field.displayOrder),
        mappings: field.mappings?.map((mapping: AdminCanonicalFieldMapping) => ({
          ...mapping,
          transformationExpression: this.normalize(mapping.transformationExpression),
          defaultValue: this.normalize(mapping.defaultValue),
          ordinalPosition: this.normalizeNumber(mapping.ordinalPosition)
        })) ?? []
      })),
      reportTemplates: this.reportTemplates.value.map((report) => ({
        ...report,
        columns: report.columns?.map((column: AdminReportColumn) => ({
          ...column,
          sourceField: this.normalize(column.sourceField)
        })) ?? []
      })),
      accessControlEntries: this.accessControlEntries.value.map((entry) => ({
        ...entry,
        product: this.normalize(entry.product),
        subProduct: this.normalize(entry.subProduct),
        entityName: this.normalize(entry.entityName),
        notificationChannel: this.normalize(entry.notificationChannel)
      }))
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
