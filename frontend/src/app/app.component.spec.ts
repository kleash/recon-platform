import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { ApiService } from './services/api.service';
import { SessionService } from './services/session.service';
import { ReconciliationStateService } from './services/reconciliation-state.service';
import { NotificationService } from './services/notification.service';

describe('AppComponent', () => {
  let sessionService: SessionService & {
    isAuthenticated: jasmine.Spy;
    storeSession: jasmine.Spy;
    clear: jasmine.Spy;
    getDisplayName: jasmine.Spy;
    hasAdminRole: jasmine.Spy;
  };
  let stateService: ReconciliationStateService & {
    loadReconciliations: jasmine.Spy;
    reset: jasmine.Spy;
  };
  let apiService: ApiService & { login: jasmine.Spy };
  let notificationService: NotificationService & {
    notifications$: ReturnType<typeof of>;
    dismiss: jasmine.Spy;
    clear: jasmine.Spy;
    push: jasmine.Spy;
  };

  beforeEach(async () => {
    sessionService = {
      isAuthenticated: jasmine.createSpy('isAuthenticated'),
      storeSession: jasmine.createSpy('storeSession'),
      clear: jasmine.createSpy('clear'),
      getDisplayName: jasmine.createSpy('getDisplayName').and.returnValue('Analyst User'),
      hasAdminRole: jasmine.createSpy('hasAdminRole').and.returnValue(false)
    } as unknown as SessionService & {
      isAuthenticated: jasmine.Spy;
      storeSession: jasmine.Spy;
      clear: jasmine.Spy;
      getDisplayName: jasmine.Spy;
      hasAdminRole: jasmine.Spy;
    };

    stateService = {
      loadReconciliations: jasmine.createSpy('loadReconciliations'),
      reset: jasmine.createSpy('reset')
    } as unknown as ReconciliationStateService & {
      loadReconciliations: jasmine.Spy;
      reset: jasmine.Spy;
    };

    apiService = {
      login: jasmine
        .createSpy('login')
        .and.returnValue(of({ token: 'token', displayName: 'Analyst', groups: [] }))
    } as unknown as ApiService & { login: jasmine.Spy };

    notificationService = {
      notifications$: of([]),
      dismiss: jasmine.createSpy('dismiss'),
      clear: jasmine.createSpy('clear'),
      push: jasmine.createSpy('push')
    } as unknown as NotificationService & {
      notifications$: ReturnType<typeof of>;
      dismiss: jasmine.Spy;
      clear: jasmine.Spy;
      push: jasmine.Spy;
    };

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: SessionService, useValue: sessionService },
        { provide: ReconciliationStateService, useValue: stateService },
        { provide: ApiService, useValue: apiService },
        { provide: NotificationService, useValue: notificationService },
        provideRouter([])
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    sessionService.isAuthenticated.and.returnValue(false);

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance).toBeTruthy();
    expect(stateService.loadReconciliations).not.toHaveBeenCalled();
  });

  it('loads reconciliations after a successful login', () => {
    sessionService.isAuthenticated.and.returnValue(false);

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    fixture.componentInstance.handleLogin({ username: 'user', password: 'pass' });

    expect(stateService.loadReconciliations).toHaveBeenCalled();
    expect(sessionService.storeSession).toHaveBeenCalled();
  });
});
