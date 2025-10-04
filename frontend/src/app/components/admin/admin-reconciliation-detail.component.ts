import { AsyncPipe, CommonModule, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, take, takeUntil } from 'rxjs';
import { AdminReconciliationStateService } from '../../services/admin-reconciliation-state.service';
import { AdminReconciliationDetail, AdminSource } from '../../models/admin-api-models';

@Component({
    selector: 'urp-admin-reconciliation-detail',
    imports: [CommonModule, FormsModule, NgIf, NgFor, AsyncPipe, DatePipe],
    templateUrl: './admin-reconciliation-detail.component.html',
    styleUrls: ['./admin-reconciliation-detail.component.css']
})
export class AdminReconciliationDetailComponent implements OnInit, OnDestroy {
  readonly detail$ = this.state.selected$;
  readonly activity$ = this.state.activity$;

  uploadLabels: Record<string, string> = {};
  private uploadFiles: Record<string, File | null> = {};

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly state: AdminReconciliationStateService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('id');
      const id = idParam ? Number(idParam) : NaN;
      if (!Number.isNaN(id)) {
        this.state.loadDefinition(id);
      }
    });

    this.detail$.pipe(takeUntil(this.destroy$)).subscribe((detail) => {
      if (detail) {
        this.state.loadActivityForDefinition(detail.code);
        this.initializeUploadState(detail);
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  handleFileChange(event: Event, sourceCode: string): void {
    const input = event.target as HTMLInputElement;
    if (input?.files && input.files.length > 0) {
      this.uploadFiles[sourceCode] = input.files[0];
    }
  }

  submitBatch(detail: AdminReconciliationDetail, source: AdminSource): void {
    const file = this.uploadFiles[source.code];
    if (!file) {
      return;
    }

    const metadata = {
      adapterType: source.adapterType,
      label: this.uploadLabels[source.code] || undefined
    };

    this.state
      .uploadBatch(detail.id, source.code, file, metadata)
      .pipe(take(1))
      .subscribe({
        next: () => {
          this.uploadLabels[source.code] = '';
          this.uploadFiles[source.code] = null;
        },
        error: () => {
          // error surfaced via notification service
        }
      });
  }

  downloadSchema(detail: AdminReconciliationDetail): void {
    this.state
      .exportSchema(detail.id)
      .pipe(take(1))
      .subscribe((schema) => {
        const blob = new Blob([JSON.stringify(schema, null, 2)], {
          type: 'application/json'
        });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${detail.code}-schema.json`;
        anchor.click();
        URL.revokeObjectURL(url);
      });
  }

  navigateToEdit(detail: AdminReconciliationDetail): void {
    this.router.navigate(['/admin', detail.id, 'edit']);
  }

  retire(detail: AdminReconciliationDetail): void {
    const confirmed = window.confirm(
      `Retire ${detail.name}? Analysts will no longer see this reconciliation.`
    );
    if (!confirmed) {
      return;
    }
    this.state.deleteDefinition(detail.id);
    this.router.navigate(['/admin']);
  }

  ingestionSnippet(detail: AdminReconciliationDetail): string {
    return `curl -X POST \\\n  ${window.location.origin}/api/admin/reconciliations/${detail.id}/sources/{sourceCode}/batches \\\n  -H "Authorization: Bearer <token>" \\\n  -F "file=@/path/to/batch.csv" \\\n  -F 'metadata={"adapterType":"${detail.sources[0]?.adapterType ?? 'CSV_FILE'}"};type=application/json'`;
  }

  private initializeUploadState(detail: AdminReconciliationDetail): void {
    this.uploadLabels = {};
    this.uploadFiles = {};
    detail.sources.forEach((source) => {
      this.uploadLabels[source.code] = '';
      this.uploadFiles[source.code] = null;
    });
  }
}
