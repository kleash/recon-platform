import { Routes } from '@angular/router';
import { AdminWorkspaceShellComponent } from './admin-workspace-shell.component';
import { AdminReconciliationCatalogComponent } from './admin-reconciliation-catalog.component';
import { AdminReconciliationDetailComponent } from './admin-reconciliation-detail.component';
import { AdminReconciliationWizardComponent } from './admin-reconciliation-wizard.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminWorkspaceShellComponent,
    children: [
      { path: '', component: AdminReconciliationCatalogComponent },
      { path: 'new', component: AdminReconciliationWizardComponent },
      { path: ':id', component: AdminReconciliationDetailComponent },
      { path: ':id/edit', component: AdminReconciliationWizardComponent }
    ]
  }
];
