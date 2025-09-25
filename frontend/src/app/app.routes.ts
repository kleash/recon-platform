import { Routes } from '@angular/router';
import { AnalystWorkspaceComponent } from './components/analyst-workspace/analyst-workspace.component';
import { adminGuard } from './services/admin.guard';

export const appRoutes: Routes = [
  {
    path: '',
    component: AnalystWorkspaceComponent
  },
  {
    path: 'admin',
    canMatch: [adminGuard],
    loadChildren: () => import('./components/admin/admin.routes').then((m) => m.ADMIN_ROUTES)
  },
  { path: '**', redirectTo: '' }
];
