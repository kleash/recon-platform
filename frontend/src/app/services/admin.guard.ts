import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { SessionService } from './session.service';

export const adminGuard: CanMatchFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (session.isAuthenticated() && session.hasAdminRole()) {
    return true;
  }

  router.navigateByUrl('/');
  return false;
};
