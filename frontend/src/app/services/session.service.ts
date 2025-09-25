import { Injectable } from '@angular/core';
import { BreakItem, LoginResponse } from '../models/api-models';
import { BreakStatus } from '../models/break-status';

const TOKEN_KEY = 'urp.jwt';
const DISPLAY_NAME_KEY = 'urp.displayName';
const GROUPS_KEY = 'urp.groups';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private token: string | null = null;
  private displayName: string | null = null;
  private groups: string[] = [];
  private readonly adminRoleTokens = ['ROLE_RECON_ADMIN', 'RECON_ADMIN'];

  constructor() {
    this.token = localStorage.getItem(TOKEN_KEY);
    this.displayName = localStorage.getItem(DISPLAY_NAME_KEY);
    const storedGroups = localStorage.getItem(GROUPS_KEY);
    this.groups = storedGroups ? JSON.parse(storedGroups) : [];
  }

  storeSession(response: LoginResponse): void {
    this.token = response.token;
    this.displayName = response.displayName;
    this.groups = response.groups;
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(DISPLAY_NAME_KEY, response.displayName);
    localStorage.setItem(GROUPS_KEY, JSON.stringify(response.groups));
  }

  clear(): void {
    this.token = null;
    this.displayName = null;
    this.groups = [];
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(DISPLAY_NAME_KEY);
    localStorage.removeItem(GROUPS_KEY);
  }

  getToken(): string | null {
    return this.token;
  }

  getDisplayName(): string | null {
    return this.displayName;
  }

  getGroups(): string[] {
    return this.groups;
  }

  isAuthenticated(): boolean {
    return !!this.token;
  }

  hasGroup(name: string): boolean {
    if (!name) {
      return false;
    }
    const target = name.toLowerCase();
    return this.groups.some((group) => group.toLowerCase() === target);
  }

  hasGroupContaining(fragment: string): boolean {
    if (!fragment) {
      return false;
    }
    const target = fragment.toLowerCase();
    return this.groups.some((group) => group.toLowerCase().includes(target));
  }

  hasMakerRole(): boolean {
    return this.hasGroupContaining('maker');
  }

  hasAdminRole(): boolean {
    return this.groups.some((group) =>
      this.adminRoleTokens.some((token) => group.toUpperCase() === token.toUpperCase())
    );
  }

  hasCheckerRole(breaks?: BreakItem[] | null): boolean {
    if (!breaks || breaks.length === 0) {
      return false;
    }

    return breaks.some((item) =>
      (item.allowedStatusTransitions || []).some((status) =>
        status === BreakStatus.Closed || status === BreakStatus.Rejected
      )
    );
  }
}
