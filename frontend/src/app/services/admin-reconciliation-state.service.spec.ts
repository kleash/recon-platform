import { of } from 'rxjs';
import { AdminReconciliationStateService } from './admin-reconciliation-state.service';
import { ApiService } from './api.service';
import { NotificationService } from './notification.service';
import { AdminReconciliationSummaryPage } from '../models/admin-api-models';

describe('AdminReconciliationStateService', () => {
  let api: jasmine.SpyObj<ApiService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let service: AdminReconciliationStateService;

  const pageResponse: AdminReconciliationSummaryPage = {
    items: [
      {
        id: 1,
        code: 'CUSTODY_GL',
        name: 'Custody vs GL',
        status: 'PUBLISHED',
        makerCheckerEnabled: true,
        updatedAt: '2024-05-01T12:00:00Z',
        owner: 'ops-team',
        updatedBy: 'admin.user',
        lastIngestionAt: '2024-05-01T08:00:00Z'
      }
    ],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20
  };

  beforeEach(() => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['getAdminReconciliations']);
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', ['push']);
    api.getAdminReconciliations.and.returnValue(of(pageResponse));
    service = new AdminReconciliationStateService(api, notifications);
  });

  it('merges filters and resets page when non-pagination filters change', () => {
    service.loadSummaries({ page: 2 });
    expect(api.getAdminReconciliations.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({ page: 2, size: 20 })
    );

    api.getAdminReconciliations.calls.reset();
    api.getAdminReconciliations.and.returnValue(of(pageResponse));

    service.loadSummaries({ owner: 'ops', search: 'custody' });

    expect(api.getAdminReconciliations).toHaveBeenCalledTimes(1);
    expect(api.getAdminReconciliations.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({ owner: 'ops', search: 'custody', page: 0, size: 20 })
    );

    const sub = service.filters$.subscribe((filters) => {
      expect(filters.owner).toBe('ops');
      expect(filters.search).toBe('custody');
      expect(filters.page).toBe(0);
    });
    sub.unsubscribe();
  });

  it('preserves existing filters when changing pagination', () => {
    service.loadSummaries({ owner: 'ops-team' });
    api.getAdminReconciliations.calls.reset();
    api.getAdminReconciliations.and.returnValue(of(pageResponse));

    service.loadSummaries({ page: 3, size: 10 });

    expect(api.getAdminReconciliations.calls.mostRecent().args[0]).toEqual(
      jasmine.objectContaining({ owner: 'ops-team', page: 3, size: 10 })
    );

    const sub = service.filters$.subscribe((filters) => {
      expect(filters.owner).toBe('ops-team');
      expect(filters.page).toBe(3);
      expect(filters.size).toBe(10);
    });
    sub.unsubscribe();
  });
});
