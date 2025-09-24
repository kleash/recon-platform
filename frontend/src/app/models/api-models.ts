import { BreakStatus } from './break-status';

export interface LoginResponse {
  token: string;
  displayName: string;
  groups: string[];
}

export interface ReconciliationListItem {
  id: number;
  code: string;
  name: string;
  description: string;
}

export interface ReconciliationSummary {
  definitionId: number;
  runId: number | null;
  runDateTime: string | null;
  triggerType: string | null;
  triggeredBy: string | null;
  triggerCorrelationId: string | null;
  triggerComments: string | null;
  matched: number;
  mismatched: number;
  missing: number;
}

export interface BreakComment {
  id: number;
  actorDn: string;
  action: string;
  comment: string;
  createdAt: string;
}

export interface BreakHistoryEntry {
  entryType: 'COMMENT' | 'WORKFLOW';
  actorDn: string;
  actorRole?: string | null;
  action: string;
  comment?: string | null;
  previousStatus?: BreakStatus | null;
  newStatus?: BreakStatus | null;
  occurredAt: string;
  correlationId?: string | null;
}

export interface BreakItem {
  id: number;
  breakType: string;
  status: BreakStatus;
  classifications: Record<string, string>;
  allowedStatusTransitions: BreakStatus[];
  detectedAt: string;
  sources: Record<string, Record<string, unknown>>;
  missingSources: string[];
  comments: BreakComment[];
  history: BreakHistoryEntry[];
  submittedByDn?: string | null;
  submittedByGroup?: string | null;
  submittedAt?: string | null;
}

export interface FilterMetadata {
  products: string[];
  subProducts: string[];
  entities: string[];
  statuses: BreakStatus[];
}

export interface RunDetail {
  summary: ReconciliationSummary;
  analytics: RunAnalytics;
  breaks: BreakItem[];
  filters: FilterMetadata;
}

export interface RunAnalytics {
  breaksByStatus: Record<string, number>;
  breaksByType: Record<string, number>;
  breaksByProduct: Record<string, number>;
  breaksByEntity: Record<string, number>;
  openBreaksByAgeBucket: Record<string, number>;
  filteredBreakCount: number;
  totalBreakCount: number;
  totalMatchedCount: number;
}

export interface TriggerRunPayload {
  triggerType: string;
  correlationId?: string;
  comments?: string;
  initiatedBy?: string;
}

export interface BulkBreakUpdatePayload {
  breakIds: number[];
  status?: BreakStatus;
  comment?: string;
  action?: string;
  correlationId?: string;
}

export interface BulkBreakFailure {
  breakId: number;
  reason: string;
}

export interface BulkBreakUpdateResponse {
  successes: BreakItem[];
  failures: BulkBreakFailure[];
}

export interface SystemActivityEntry {
  id: number;
  eventType: string;
  details: string;
  recordedAt: string;
}

