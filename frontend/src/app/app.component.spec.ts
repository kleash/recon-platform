import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { ApiService } from './services/api.service';
import { SessionService } from './services/session.service';
import { ReconciliationStateService } from './services/reconciliation-state.service';

describe('AppComponent', () => {
  let sessionService: SessionService & {
    isAuthenticated: jasmine.Spy;
    storeSession: jasmine.Spy;
    clear: jasmine.Spy;
    getDisplayName: jasmine.Spy;
  };
  let stateService: ReconciliationStateService & {
    loadReconciliations: jasmine.Spy;
    reset: jasmine.Spy;
    selectReconciliation: jasmine.Spy;
    triggerRun: jasmine.Spy;
    selectBreak: jasmine.Spy;
    addComment: jasmine.Spy;
    updateStatus: jasmine.Spy;
    updateFilter: jasmine.Spy;
    bulkUpdateBreaks: jasmine.Spy;
    exportLatestRun: jasmine.Spy;
    getCurrentRunDetail: jasmine.Spy;
  };
  let apiService: ApiService & { login: jasmine.Spy };

  beforeEach(async () => {
    sessionService = {
      isAuthenticated: jasmine.createSpy('isAuthenticated'),
      storeSession: jasmine.createSpy('storeSession'),
      clear: jasmine.createSpy('clear'),
      getDisplayName: jasmine.createSpy('getDisplayName').and.returnValue('Analyst User')
    } as unknown as SessionService & {
      isAuthenticated: jasmine.Spy;
      storeSession: jasmine.Spy;
      clear: jasmine.Spy;
      getDisplayName: jasmine.Spy;
    };

    stateService = {
      reconciliations$: of([]),
      selectedReconciliation$: of(null),
      runDetail$: of(null),
      selectedBreak$: of(null),
      filter$: of({}),
      filterMetadata$: of(null),
      activity$: of([]),
      loadReconciliations: jasmine.createSpy('loadReconciliations'),
      reset: jasmine.createSpy('reset'),
      selectReconciliation: jasmine.createSpy('selectReconciliation'),
      triggerRun: jasmine.createSpy('triggerRun'),
      selectBreak: jasmine.createSpy('selectBreak'),
      addComment: jasmine.createSpy('addComment'),
      updateStatus: jasmine.createSpy('updateStatus'),
      updateFilter: jasmine.createSpy('updateFilter'),
      bulkUpdateBreaks: jasmine.createSpy('bulkUpdateBreaks'),
      exportLatestRun: jasmine.createSpy('exportLatestRun').and.returnValue(of(new Blob())),
      getCurrentRunDetail: jasmine.createSpy('getCurrentRunDetail').and.returnValue(null)
    } as unknown as ReconciliationStateService & {
      loadReconciliations: jasmine.Spy;
      reset: jasmine.Spy;
      selectReconciliation: jasmine.Spy;
      triggerRun: jasmine.Spy;
      selectBreak: jasmine.Spy;
      addComment: jasmine.Spy;
      updateStatus: jasmine.Spy;
      updateFilter: jasmine.Spy;
      bulkUpdateBreaks: jasmine.Spy;
      exportLatestRun: jasmine.Spy;
      getCurrentRunDetail: jasmine.Spy;
    };

    apiService = {
      login: jasmine
        .createSpy('login')
        .and.returnValue(of({ token: 'token', displayName: 'Analyst', groups: [] }))
    } as unknown as ApiService & { login: jasmine.Spy };

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: SessionService, useValue: sessionService },
        { provide: ReconciliationStateService, useValue: stateService },
        { provide: ApiService, useValue: apiService }
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

  it('loads reconciliations when the user session is already authenticated', () => {
    sessionService.isAuthenticated.and.returnValue(true);

    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(stateService.loadReconciliations).toHaveBeenCalled();
  });
});
