import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface AppNotification {
  id: number;
  type: 'info' | 'success' | 'error';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private counter = 0;
  private readonly notificationsSubject = new BehaviorSubject<AppNotification[]>([]);
  readonly notifications$ = this.notificationsSubject.asObservable();

  push(message: string, type: 'info' | 'success' | 'error' = 'info'): void {
    const notification: AppNotification = {
      id: ++this.counter,
      type,
      message
    };
    this.notificationsSubject.next([...this.notificationsSubject.value, notification]);
    window.setTimeout(() => this.dismiss(notification.id), 6000);
  }

  dismiss(id: number): void {
    this.notificationsSubject.next(this.notificationsSubject.value.filter((item) => item.id !== id));
  }

  clear(): void {
    this.notificationsSubject.next([]);
  }
}
