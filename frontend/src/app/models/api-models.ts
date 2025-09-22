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
  runId: number | null;
  runDateTime: string | null;
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
  breaks: BreakItem[];
  filters: FilterMetadata;
}

export interface SystemActivityEntry {
  id: number;
  eventType: string;
  details: string;
  recordedAt: string;
}

