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
  status: string;
  detectedAt: string;
  sourceA: Record<string, unknown>;
  sourceB: Record<string, unknown>;
  comments: BreakComment[];
}

export interface RunDetail {
  summary: ReconciliationSummary;
  breaks: BreakItem[];
}
