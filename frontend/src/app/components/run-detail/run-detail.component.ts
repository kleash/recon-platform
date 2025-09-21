import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { BreakItem, RunDetail } from '../../models/api-models';

@Component({
  selector: 'urp-run-detail',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './run-detail.component.html',
  styleUrls: ['./run-detail.component.css']
})
export class RunDetailComponent {
  @Input() runDetail: RunDetail | null = null;
  @Input() selectedBreak: BreakItem | null = null;
  @Output() selectBreak = new EventEmitter<BreakItem>();

  onSelectBreak(item: BreakItem): void {
    this.selectBreak.emit(item);
  }
}
