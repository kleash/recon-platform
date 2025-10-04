import { AsyncPipe, CommonModule, NgClass, NgFor, NgIf } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { ApiService } from './services/api.service';
import { SessionService } from './services/session.service';
import { ReconciliationStateService } from './services/reconciliation-state.service';
import { NotificationService } from './services/notification.service';
import { LoginResponse } from './models/api-models';
import { LoginComponent } from './components/login/login.component';

@Component({
    selector: 'app-root',
    imports: [
        CommonModule,
        AsyncPipe,
        NgIf,
        NgFor,
        NgClass,
        RouterOutlet,
        RouterLink,
        RouterLinkActive,
        LoginComponent
    ],
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'Universal Reconciliation Platform';

  readonly notifications$ = this.notifications.notifications$;

  loginError: string | null = null;
  isLoading = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly api: ApiService,
    public readonly session: SessionService,
    private readonly state: ReconciliationStateService,
    public readonly notifications: NotificationService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    if (this.session.isAuthenticated()) {
      // Ensure router initializes to the existing URL (default workspace if none)
      if (this.router.url === '/' || this.router.url === '') {
        // no-op, but ensures initial navigation for router-outlet
        this.router.navigateByUrl(this.session.hasAdminRole() ? this.router.url || '/' : '/');
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  handleLogin(credentials: { username: string; password: string }): void {
    this.isLoading = true;
    this.loginError = null;
    this.api
      .login(credentials.username, credentials.password)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response: LoginResponse) => {
          this.session.storeSession(response);
          this.isLoading = false;
          this.state.loadReconciliations();
          this.router.navigateByUrl('/');
        },
        error: () => {
          this.loginError = 'Login failed. Please verify your credentials.';
          this.isLoading = false;
        }
      });
  }

  logout(): void {
    this.session.clear();
    this.state.reset();
    this.loginError = null;
    this.notifications.clear();
    this.router.navigateByUrl('/');
  }

  dismissNotification(id: number): void {
    this.notifications.dismiss(id);
  }
}
