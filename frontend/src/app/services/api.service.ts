import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BreakItem,
  BulkBreakUpdatePayload,
  BulkBreakUpdateResponse,
  LoginResponse,
  ReconciliationListItem,
  RunDetail,
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
