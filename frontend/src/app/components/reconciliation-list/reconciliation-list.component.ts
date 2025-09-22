import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReconciliationListItem, TriggerRunPayload } from '../../models/api-models';

@Component({
  selector: 'urp-reconciliation-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reconciliation-list.component.html',
  styleUrls: ['./reconciliation-list.component.css']
})
export class ReconciliationListComponent {
  @Input() reconciliations: ReconciliationListItem[] = [];
  @Input() selectedReconciliation: ReconciliationListItem | null = null;
  @Input() canExport = false;

  @Output() selectReconciliation = new EventEmitter<ReconciliationListItem>();
  @Output() triggerRun = new EventEmitter<TriggerRunPayload>();
  @Output() exportRun = new EventEmitter<void>();

  triggerType = 'MANUAL_API';
  correlationId = '';
  comments = '';
  initiatedBy = '';

  onSelect(reconciliation: ReconciliationListItem): void {
    this.selectReconciliation.emit(reconciliation);
  }

  onTriggerRun(): void {
    const payload: TriggerRunPayload = {
      triggerType: this.triggerType,
      correlationId: this.correlationId.trim() || undefined,
      comments: this.comments.trim() || undefined,
      initiatedBy: this.initiatedBy.trim() || undefined
    };
    this.triggerRun.emit(payload);
  }

  onExport(): void {
    this.exportRun.emit();
  }
}
