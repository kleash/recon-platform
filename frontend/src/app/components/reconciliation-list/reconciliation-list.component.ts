import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReconciliationListItem } from '../../models/api-models';

@Component({
  selector: 'urp-reconciliation-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './reconciliation-list.component.html',
  styleUrls: ['./reconciliation-list.component.css']
})
export class ReconciliationListComponent {
  @Input() reconciliations: ReconciliationListItem[] = [];
  @Input() selectedReconciliation: ReconciliationListItem | null = null;
  @Input() canExport = false;

  @Output() selectReconciliation = new EventEmitter<ReconciliationListItem>();
  @Output() triggerRun = new EventEmitter<void>();
  @Output() exportRun = new EventEmitter<void>();

  onSelect(reconciliation: ReconciliationListItem): void {
    this.selectReconciliation.emit(reconciliation);
  }

  onTriggerRun(): void {
    this.triggerRun.emit();
  }

  onExport(): void {
    this.exportRun.emit();
  }
}
