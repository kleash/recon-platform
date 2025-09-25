import { AsyncPipe, CommonModule, NgFor, NgIf } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminReconciliationStateService } from '../../services/admin-reconciliation-state.service';
import { ReconciliationLifecycleStatus } from '../../models/admin-api-models';

@Component({
  selector: 'urp-admin-workspace-shell',
  standalone: true,
  imports: [CommonModule, NgIf, NgFor, AsyncPipe, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-workspace-shell.component.html',
  styleUrls: ['./admin-workspace-shell.component.css']
})
export class AdminWorkspaceShellComponent implements OnInit {
  readonly loading$ = this.state.loading$;
  readonly filters$ = this.state.filters$;

  readonly statuses: { label: string; value?: ReconciliationLifecycleStatus }[] = [
    { label: 'All' },
    { label: 'Draft', value: 'DRAFT' },
    { label: 'Published', value: 'PUBLISHED' },
    { label: 'Retired', value: 'RETIRED' }
  ];

  constructor(private readonly state: AdminReconciliationStateService) {}

  ngOnInit(): void {
    this.state.loadSummaries();
  }

  applyStatusFilter(status?: ReconciliationLifecycleStatus): void {
    this.state.loadSummaries({ status, page: 0 });
  }
}
