import { AsyncPipe, CommonModule, DatePipe, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, takeUntil } from 'rxjs';
import { AdminReconciliationStateService } from '../../services/admin-reconciliation-state.service';

@Component({
  selector: 'urp-admin-reconciliation-catalog',
  standalone: true,
  imports: [CommonModule, NgIf, NgFor, AsyncPipe, DatePipe, RouterLink, ReactiveFormsModule],
  templateUrl: './admin-reconciliation-catalog.component.html',
  styleUrls: ['./admin-reconciliation-catalog.component.css']
})
export class AdminReconciliationCatalogComponent implements OnInit, OnDestroy {
  readonly reconciliations$ = this.state.reconciliations$;
  readonly filters$ = this.state.filters$;
  readonly listMeta$ = this.state.listMeta$;
  readonly loading$ = this.state.loading$;

  readonly filterForm = this.fb.group({
    search: [''],
    owner: [''],
    updatedAfter: [''],
    updatedBefore: ['']
  });

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly state: AdminReconciliationStateService,
    private readonly fb: FormBuilder,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.filters$.pipe(takeUntil(this.destroy$)).subscribe((filters) => {
      this.filterForm.patchValue(
        {
          search: filters.search ?? '',
          owner: filters.owner ?? '',
          updatedAfter: filters.updatedAfter ? filters.updatedAfter.slice(0, 10) : '',
          updatedBefore: filters.updatedBefore ? filters.updatedBefore.slice(0, 10) : ''
        },
        { emitEvent: false }
      );
    });

    this.filterForm.valueChanges
      .pipe(debounceTime(300), takeUntil(this.destroy$))
      .subscribe((values) => {
        this.state.loadSummaries({
          search: values.search ? values.search.trim() : undefined,
          owner: values.owner ? values.owner.trim() : undefined,
          updatedAfter: values.updatedAfter ? new Date(values.updatedAfter).toISOString() : undefined,
          updatedBefore: values.updatedBefore ? new Date(values.updatedBefore).toISOString() : undefined,
          page: 0
        });
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  clearFilters(): void {
    this.filterForm.reset({ search: '', owner: '', updatedAfter: '', updatedBefore: '' }, { emitEvent: false });
    this.state.loadSummaries({ search: undefined, owner: undefined, updatedAfter: undefined, updatedBefore: undefined, page: 0 });
  }

  changePage(page: number): void {
    if (page < 0) {
      return;
    }
    this.state.loadSummaries({ page });
  }

  duplicate(id: number): void {
    this.router.navigate(['/admin/new'], { queryParams: { from: id } });
  }

  retire(id: number, name: string): void {
    const confirmed = window.confirm(`Retire ${name}? Analysts will lose access to this reconciliation.`);
    if (!confirmed) {
      return;
    }
    this.state.deleteDefinition(id);
  }
}
