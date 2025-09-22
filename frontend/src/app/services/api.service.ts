import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BreakItem,
  LoginResponse,
  ReconciliationListItem,
  RunDetail,
  SystemActivityEntry,
  TriggerRunPayload,
  BulkBreakUpdatePayload
} from '../models/api-models';
import { BreakStatus } from '../models/break-status';
import { environment } from '../../environments/environment';
import { BreakFilter } from '../models/break-filter';

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

  updateStatus(breakId: number, status: BreakStatus): Observable<BreakItem> {
    return this.http.patch<BreakItem>(`${BASE_URL}/breaks/${breakId}/status`, { status });
  }

  bulkUpdateBreaks(payload: BulkBreakUpdatePayload): Observable<BreakItem[]> {
    return this.http.post<BreakItem[]>(`${BASE_URL}/breaks/bulk`, payload);
  }

  exportRun(runId: number): Observable<Blob> {
    return this.http.get(`${BASE_URL}/exports/runs/${runId}`, { responseType: 'blob' });
  }

  getSystemActivity(): Observable<SystemActivityEntry[]> {
    return this.http.get<SystemActivityEntry[]>(`${BASE_URL}/activity`);
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
