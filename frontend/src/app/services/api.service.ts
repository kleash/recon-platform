import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  BreakItem,
  LoginResponse,
  ReconciliationListItem,
  RunDetail
} from '../models/api-models';

const BASE_URL = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${BASE_URL}/auth/login`, { username, password });
  }

  getReconciliations(): Observable<ReconciliationListItem[]> {
    return this.http.get<ReconciliationListItem[]>(`${BASE_URL}/reconciliations`);
  }

  triggerRun(reconciliationId: number): Observable<RunDetail> {
    return this.http.post<RunDetail>(`${BASE_URL}/reconciliations/${reconciliationId}/run`, {});
  }

  getLatestRun(reconciliationId: number): Observable<RunDetail> {
    return this.http.get<RunDetail>(`${BASE_URL}/reconciliations/${reconciliationId}/runs/latest`);
  }

  addComment(breakId: number, comment: string, action: string): Observable<BreakItem> {
    return this.http.post<BreakItem>(`${BASE_URL}/breaks/${breakId}/comments`, { comment, action });
  }

  updateStatus(breakId: number, status: string): Observable<BreakItem> {
    return this.http.patch<BreakItem>(`${BASE_URL}/breaks/${breakId}/status`, { status });
  }

  exportRun(runId: number): Observable<Blob> {
    return this.http.get(`${BASE_URL}/exports/runs/${runId}`, { responseType: 'blob' });
  }
}
