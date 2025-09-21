import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe, JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from './services/api.service';
import { SessionService } from './services/session.service';
import {
  BreakItem,
  ReconciliationListItem,
  RunDetail
} from './models/api-models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, JsonPipe],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'Universal Reconciliation Platform';

  username = 'ops1';
  password = 'password';
  loginError: string | null = null;
  isLoading = false;

  reconciliations: ReconciliationListItem[] = [];
  selectedReconciliation: ReconciliationListItem | null = null;
  runDetail: RunDetail | null = null;
  selectedBreak: BreakItem | null = null;

  commentText = '';
  commentAction = 'INVESTIGATION_NOTE';

  constructor(private readonly api: ApiService, public readonly session: SessionService) {}

  ngOnInit(): void {
    if (this.session.isAuthenticated()) {
      this.loadReconciliations();
    }
  }

  login(): void {
    this.isLoading = true;
    this.loginError = null;
    this.api.login(this.username, this.password).subscribe({
      next: (response) => {
        this.session.storeSession(response);
        this.isLoading = false;
        this.loadReconciliations();
      },
      error: () => {
        this.loginError = 'Login failed. Please verify your credentials.';
        this.isLoading = false;
      }
    });
  }

  logout(): void {
    this.session.clear();
    this.reconciliations = [];
    this.selectedReconciliation = null;
    this.runDetail = null;
    this.selectedBreak = null;
  }

  loadReconciliations(): void {
    this.api.getReconciliations().subscribe((data) => {
      this.reconciliations = data;
      if (data.length > 0) {
        this.selectReconciliation(data[0]);
      }
    });
  }

  selectReconciliation(reconciliation: ReconciliationListItem): void {
    this.selectedReconciliation = reconciliation;
    this.selectedBreak = null;
    this.api.getLatestRun(reconciliation.id).subscribe((detail) => {
      this.runDetail = detail;
    });
  }

  triggerRun(): void {
    if (!this.selectedReconciliation) {
      return;
    }
    this.api.triggerRun(this.selectedReconciliation.id).subscribe((detail) => {
      this.runDetail = detail;
    });
  }

  selectBreak(breakItem: BreakItem): void {
    this.selectedBreak = breakItem;
    this.commentText = '';
  }

  addComment(): void {
    if (!this.selectedBreak || !this.commentText.trim()) {
      return;
    }
    this.api.addComment(this.selectedBreak.id, this.commentText, this.commentAction).subscribe((updated) => {
      this.refreshBreak(updated);
      this.commentText = '';
    });
  }

  updateStatus(status: string): void {
    if (!this.selectedBreak) {
      return;
    }
    this.api.updateStatus(this.selectedBreak.id, status).subscribe((updated) => {
      this.refreshBreak(updated);
    });
  }

  exportRun(): void {
    if (!this.runDetail || !this.runDetail.summary.runId) {
      return;
    }
    this.api.exportRun(this.runDetail.summary.runId).subscribe((blob) => {
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `reconciliation-run-${this.runDetail?.summary.runId}.xlsx`;
      anchor.click();
      window.URL.revokeObjectURL(url);
    });
  }

  private refreshBreak(updated: BreakItem): void {
    if (!this.runDetail) {
      return;
    }
    this.runDetail = {
      ...this.runDetail,
      breaks: this.runDetail.breaks.map((item) => (item.id === updated.id ? updated : item))
    };
    this.selectedBreak = updated;
  }
}
