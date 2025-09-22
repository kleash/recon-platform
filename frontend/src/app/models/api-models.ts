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

export interface BreakItem {
  id: number;
  breakType: string;
  status: BreakStatus;
  product: string | null;
  subProduct: string | null;
  entity: string | null;
  allowedStatusTransitions: BreakStatus[];
  detectedAt: string;
  sourceA: Record<string, unknown>;
  sourceB: Record<string, unknown>;
  comments: BreakComment[];
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
}

export interface SystemActivityEntry {
  id: number;
  eventType: string;
  details: string;
  recordedAt: string;
}

