import { CommonModule, DatePipe } from '@angular/common';
import { Component, Input } from '@angular/core';
import { SystemActivityEntry } from '../../models/api-models';

@Component({
  selector: 'urp-system-activity',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './system-activity.component.html',
  styleUrls: ['./system-activity.component.css']
})
export class SystemActivityComponent {
  @Input() activity: SystemActivityEntry[] = [];
}

