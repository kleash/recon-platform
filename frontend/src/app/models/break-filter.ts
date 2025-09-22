import { BreakStatus } from './break-status';

export interface BreakFilter {
  product?: string | null;
  subProduct?: string | null;
  entity?: string | null;
  statuses?: BreakStatus[];
}

