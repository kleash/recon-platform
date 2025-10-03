import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BreakItem,
  BreakSearchResponse,
  BreakSelectionResponse,
  ApprovalQueueResponse,
  BulkBreakUpdatePayload,
  BulkBreakUpdateResponse,
  ExportJobRequestPayload,
  ExportJobSummary,
  LoginResponse,
  ReconciliationListItem,
  RunDetail,
  SavedView,
  SavedViewRequestPayload,
  SystemActivityEntry,
  TriggerRunPayload
} from '../models/api-models';
import { BreakStatus } from '../models/break-status';
import { environment } from '../../environments/environment';
import { BreakFilter } from '../models/break-filter';
import {
  AdminIngestionRequestMetadata,
  AdminIngestionBatch,
  AdminReconciliationDetail,
  AdminReconciliationRequest,
  AdminReconciliationSchema,
  AdminReconciliationSummaryPage,
  GroovyScriptGenerationRequest,
  GroovyScriptGenerationResponse,
  GroovyScriptTestRequest,
  GroovyScriptTestResponse,
  SourceTransformationPreviewUploadRequest,
  SourceTransformationPreviewResponse,
  SourceTransformationApplyRequest,
  SourceTransformationApplyResponse,
  TransformationSampleResponse,
  TransformationValidationRequest,
  TransformationValidationResponse,
  ReconciliationLifecycleStatus
} from '../models/admin-api-models';

const BASE_URL = environment.apiUrl;

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${BASE_URL}/auth/login`, { username, password });
  }

  getReconciliations(): Observable<ReconciliationListItem[]> {
    return this.http.get<ReconciliationListItem[]>(`${BASE_URL}/reconciliations`);
  }

  triggerRun(reconciliationId: number, payload: TriggerRunPayload): Observable<RunDetail> {
    return this.http.post<RunDetail>(`${BASE_URL}/reconciliations/${reconciliationId}/run`, payload);
  }

  getLatestRun(reconciliationId: number, filter?: BreakFilter): Observable<RunDetail> {
    return this.http.get<RunDetail>(`${BASE_URL}/reconciliations/${reconciliationId}/runs/latest`, {
      params: this.buildFilterParams(filter)
    });
  }

  getApprovalQueue(reconciliationId: number): Observable<ApprovalQueueResponse> {
    return this.http.get<ApprovalQueueResponse>(`${BASE_URL}/reconciliations/${reconciliationId}/approvals`);
  }

  searchBreakResults(
    reconciliationId: number,
    query: Record<string, string | number | boolean | Array<string | number>>
  ): Observable<BreakSearchResponse> {
    const params = this.buildQueryParams(query);
    return this.http.get<BreakSearchResponse>(`${BASE_URL}/reconciliations/${reconciliationId}/results`, {
      params
    });
  }

  selectBreakIds(
    reconciliationId: number,
    query: Record<string, string | number | boolean | Array<string | number>>
  ): Observable<BreakSelectionResponse> {
    const params = this.buildQueryParams(query);
    return this.http.get<BreakSelectionResponse>(
      `${BASE_URL}/reconciliations/${reconciliationId}/results/ids`,
      { params }
    );
  }

  getRun(runId: number, filter?: BreakFilter): Observable<RunDetail> {
    return this.http.get<RunDetail>(`${BASE_URL}/reconciliations/runs/${runId}`, {
      params: this.buildFilterParams(filter)
    });
  }

  addComment(breakId: number, comment: string, action: string): Observable<BreakItem> {
    return this.http.post<BreakItem>(`${BASE_URL}/breaks/${breakId}/comments`, { comment, action });
  }

  updateStatus(
    breakId: number,
    payload: { status: BreakStatus; comment?: string; correlationId?: string | null }
  ): Observable<BreakItem> {
    return this.http.patch<BreakItem>(`${BASE_URL}/breaks/${breakId}/status`, payload);
  }

  bulkUpdateBreaks(payload: BulkBreakUpdatePayload): Observable<BulkBreakUpdateResponse> {
    return this.http.post<BulkBreakUpdateResponse>(`${BASE_URL}/breaks/bulk`, payload);
  }

  exportRun(runId: number): Observable<Blob> {
    return this.http.get(`${BASE_URL}/exports/runs/${runId}`, { responseType: 'blob' });
  }

  getSystemActivity(): Observable<SystemActivityEntry[]> {
    return this.http.get<SystemActivityEntry[]>(`${BASE_URL}/activity`);
  }

  listSavedViews(reconciliationId: number): Observable<SavedView[]> {
    return this.http.get<SavedView[]>(`${BASE_URL}/reconciliations/${reconciliationId}/saved-views`);
  }

  createSavedView(reconciliationId: number, payload: SavedViewRequestPayload): Observable<SavedView> {
    return this.http.post<SavedView>(`${BASE_URL}/reconciliations/${reconciliationId}/saved-views`, payload);
  }

  updateSavedView(
    reconciliationId: number,
    viewId: number,
    payload: SavedViewRequestPayload
  ): Observable<SavedView> {
    return this.http.put<SavedView>(`${BASE_URL}/reconciliations/${reconciliationId}/saved-views/${viewId}`, payload);
  }

  deleteSavedView(reconciliationId: number, viewId: number): Observable<void> {
    return this.http.delete<void>(`${BASE_URL}/reconciliations/${reconciliationId}/saved-views/${viewId}`);
  }

  setDefaultSavedView(reconciliationId: number, viewId: number): Observable<void> {
    return this.http.post<void>(
      `${BASE_URL}/reconciliations/${reconciliationId}/saved-views/${viewId}/default`,
      {}
    );
  }

  resolveSharedView(token: string): Observable<SavedView> {
    return this.http.get<SavedView>(`${BASE_URL}/saved-views/shared/${encodeURIComponent(token)}`);
  }

  listExportJobs(reconciliationId: number): Observable<ExportJobSummary[]> {
    return this.http.get<ExportJobSummary[]>(`${BASE_URL}/reconciliations/${reconciliationId}/export-jobs`);
  }

  queueExportJob(reconciliationId: number, payload: ExportJobRequestPayload): Observable<ExportJobSummary> {
    return this.http.post<ExportJobSummary>(`${BASE_URL}/reconciliations/${reconciliationId}/export-jobs`, payload);
  }

  getExportJob(jobId: number): Observable<ExportJobSummary> {
    return this.http.get<ExportJobSummary>(`${BASE_URL}/export-jobs/${jobId}`);
  }

  downloadExportJob(jobId: number): Observable<Blob> {
    return this.http.get(`${BASE_URL}/export-jobs/${jobId}/download`, { responseType: 'blob' });
  }

  getAdminReconciliations(filters?: {
    status?: ReconciliationLifecycleStatus;
    owner?: string;
    updatedAfter?: string;
    updatedBefore?: string;
    search?: string;
    page?: number;
    size?: number;
  }): Observable<AdminReconciliationSummaryPage> {
    let params = new HttpParams();
    if (filters?.status) {
      params = params.set('status', filters.status);
    }
    if (filters?.owner) {
      params = params.set('owner', filters.owner);
    }
    if (filters?.updatedAfter) {
      params = params.set('updatedAfter', filters.updatedAfter);
    }
    if (filters?.updatedBefore) {
      params = params.set('updatedBefore', filters.updatedBefore);
    }
    if (filters?.search) {
      params = params.set('search', filters.search);
    }
    if (typeof filters?.page === 'number') {
      params = params.set('page', String(filters.page));
    }
    if (typeof filters?.size === 'number') {
      params = params.set('size', String(filters.size));
    }
    return this.http.get<AdminReconciliationSummaryPage>(`${BASE_URL}/admin/reconciliations`, { params });
  }

  getAdminReconciliation(id: number): Observable<AdminReconciliationDetail> {
    return this.http.get<AdminReconciliationDetail>(`${BASE_URL}/admin/reconciliations/${id}`);
  }

  createAdminReconciliation(request: AdminReconciliationRequest): Observable<AdminReconciliationDetail> {
    return this.http.post<AdminReconciliationDetail>(`${BASE_URL}/admin/reconciliations`, request);
  }

  updateAdminReconciliation(
    id: number,
    request: AdminReconciliationRequest
  ): Observable<AdminReconciliationDetail> {
    return this.http.put<AdminReconciliationDetail>(`${BASE_URL}/admin/reconciliations/${id}`, request);
  }

  patchAdminReconciliation(
    id: number,
    payload: Partial<Pick<AdminReconciliationRequest, 'notes' | 'makerCheckerEnabled' | 'status'>> & {
      version?: number | null;
    }
  ): Observable<AdminReconciliationDetail> {
    return this.http.patch<AdminReconciliationDetail>(`${BASE_URL}/admin/reconciliations/${id}`, payload);
  }

  deleteAdminReconciliation(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE_URL}/admin/reconciliations/${id}`);
  }

  exportAdminReconciliationSchema(id: number): Observable<AdminReconciliationSchema> {
    return this.http.get<AdminReconciliationSchema>(`${BASE_URL}/admin/reconciliations/${id}/schema`);
  }

  uploadAdminIngestionBatch(
    definitionId: number,
    sourceCode: string,
    file: File,
    metadata: AdminIngestionRequestMetadata
  ): Observable<AdminIngestionBatch> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    return this.http.post<AdminIngestionBatch>(
      `${BASE_URL}/admin/reconciliations/${definitionId}/sources/${encodeURIComponent(sourceCode)}/batches`,
      formData
    );
  }

  validateTransformation(
    payload: TransformationValidationRequest
  ): Observable<TransformationValidationResponse> {
    return this.http.post<TransformationValidationResponse>(
      `${BASE_URL}/admin/transformations/validate`,
      payload
    );
  }

  fetchTransformationSamples(
    definitionId: number,
    sourceCode: string,
    limit = 5
  ): Observable<TransformationSampleResponse> {
    const params = new HttpParams()
      .set('definitionId', String(definitionId))
      .set('sourceCode', sourceCode)
      .set('limit', String(limit));
    return this.http.get<TransformationSampleResponse>(`${BASE_URL}/admin/transformations/samples`, { params });
  }

  generateGroovyScript(
    payload: GroovyScriptGenerationRequest
  ): Observable<GroovyScriptGenerationResponse> {
    return this.http.post<GroovyScriptGenerationResponse>(
      `${BASE_URL}/admin/transformations/groovy/generate`,
      payload
    );
  }

  testGroovyScript(payload: GroovyScriptTestRequest): Observable<GroovyScriptTestResponse> {
    return this.http.post<GroovyScriptTestResponse>(`${BASE_URL}/admin/transformations/groovy/test`, payload);
  }

  previewSourceTransformationFromFile(
    payload: SourceTransformationPreviewUploadRequest,
    file: File
  ): Observable<SourceTransformationPreviewResponse> {
    const formData = new FormData();
    formData.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    formData.append('file', file);
    return this.http.post<SourceTransformationPreviewResponse>(
      `${BASE_URL}/admin/transformations/plan/preview/upload`,
      formData
    );
  }

  listPreviewSheetNames(file: File): Observable<string[]> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<string[]>(`${BASE_URL}/admin/transformations/plan/preview/sheets`, formData);
  }

  applySourceTransformation(
    payload: SourceTransformationApplyRequest
  ): Observable<SourceTransformationApplyResponse> {
    return this.http.post<SourceTransformationApplyResponse>(
      `${BASE_URL}/admin/transformations/plan/apply`,
      payload
    );
  }

  private buildQueryParams(
    query: Record<string, string | number | boolean | Array<string | number>> | undefined
  ): HttpParams {
    let params = new HttpParams();
    Object.entries(query || {}).forEach(([key, value]) => {
      if (value === undefined || value === null) {
        return;
      }
      if (Array.isArray(value)) {
        value.forEach((entry) => {
          params = params.append(key, String(entry));
        });
      } else {
        params = params.set(key, String(value));
      }
    });
    return params;
  }

  private buildFilterParams(filter?: BreakFilter): HttpParams {
    if (!filter) {
      return new HttpParams();
    }
    let params = new HttpParams();
    if (filter.product) {
      params = params.set('product', filter.product);
    }
    if (filter.subProduct) {
      params = params.set('subProduct', filter.subProduct);
    }
    if (filter.entity) {
      params = params.set('entity', filter.entity);
    }
    if (filter.statuses && filter.statuses.length > 0) {
      filter.statuses.forEach((status) => {
        params = params.append('status', status);
      });
    }
    return params;
  }
}
