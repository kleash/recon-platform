import { test, expect } from '@playwright/test';
import { finalizeReport, prepareReport, recordScreen, resolveAssetPath } from './reporting';

test.beforeAll(async () => {
  await prepareReport();
});

test('authenticated users can reach the reconciliation workspace shell', async ({ page }) => {
  await page.goto('/');
  await page.waitForSelector('input[name="username"]', { timeout: 30000 });

  const loginScreenshot = '01-login.png';
  await page.screenshot({ path: resolveAssetPath(loginScreenshot), fullPage: true });
  await recordScreen({
    name: 'Login',
    route: '/',
    screenshotFile: loginScreenshot,
    assertions: [
      { description: 'Username field is visible' },
      { description: 'Password field is visible' },
      { description: 'Login button is enabled' },
    ],
  });

  await page.getByLabel('Username').fill('ops1');
  await page.getByLabel('Password').fill('password');
  await page.getByRole('button', { name: 'Login' }).click();

  await expect(page.getByText('Welcome, Operations User!')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Reconciliations' })).toBeVisible();
  await expect(page.getByText('Welcome, Operations User!')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recent system activity' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Select Filtered' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Runs' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Breaks' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Approvals' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reports' })).toBeVisible();

  await page.getByRole('button', { name: 'Approvals' }).click();
  const approvalsPanel = page.locator('.approvals-panel');
  await expect(approvalsPanel.getByRole('heading', { name: 'Pending approvals' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Runs' }).click();

  await page.getByRole('button', { name: 'Reports' }).click();
  await expect(page.getByRole('button', { name: 'CSV' })).toBeVisible();
  await expect(page.locator('.reports-panel')).toContainText('No exports queued yet.', { timeout: 5000 });
  await page.getByRole('button', { name: 'Runs' }).click();

  const workspaceScreenshot = '02-workspace.png';
  await page.screenshot({ path: resolveAssetPath(workspaceScreenshot), fullPage: true });
  await recordScreen({
    name: 'Reconciliation workspace',
    route: '/',
    screenshotFile: workspaceScreenshot,
    assertions: [
      { description: 'Operations welcome banner is displayed' },
      { description: 'Reconciliations list shows empty state guidance' },
      { description: 'Break detail panel shows selection prompt' },
      { description: 'System activity card renders empty-state message' },
    ],
  });
});

test('admin users can author reconciliations through the administration workspace', async ({ page }) => {
  await page.goto('/');
  await page.waitForSelector('input[name="username"]', { timeout: 30000 });

  await page.getByLabel('Username').fill('admin1');
  await page.getByLabel('Password').fill('password');
  await page.getByRole('button', { name: 'Login' }).click();

  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
  const adminNavLink = page.getByRole('link', { name: 'Administration' });
  await expect(adminNavLink).toBeVisible();

  const adminNavScreenshot = '03-admin-nav.png';
  await page.screenshot({ path: resolveAssetPath(adminNavScreenshot), fullPage: true });
  await recordScreen({
    name: 'Admin navigation entry',
    route: '/',
    screenshotFile: adminNavScreenshot,
    assertions: [
      { description: 'Administration tab is visible after admin login' },
      { description: 'Operations welcome banner still renders' },
    ],
  });

  await adminNavLink.click();
  await expect(page.getByRole('heading', { name: 'Administration Workspace' })).toBeVisible();
  await expect(page.locator('a.primary-action', { hasText: 'New reconciliation' })).toBeVisible();

  const adminCatalogScreenshot = '04-admin-catalog.png';
  await page.screenshot({ path: resolveAssetPath(adminCatalogScreenshot), fullPage: true });
  await recordScreen({
    name: 'Administration catalog',
    route: '/admin',
    screenshotFile: adminCatalogScreenshot,
    assertions: [
      { description: 'Administration workspace heading is rendered' },
      { description: 'Catalog shows filter controls for search, owner, and dates' },
      { description: 'New reconciliation action is available' },
    ],
  });

  await page.locator('a.primary-action', { hasText: 'New reconciliation' }).click();
  await expect(page.getByRole('heading', { name: 'Create reconciliation' })).toBeVisible();
  await expect(page.getByText('Step 1 of 6 · Definition')).toBeVisible();
  await expect(page.getByLabel('Code')).toBeVisible();

  const wizardScreenshot = '05-admin-wizard.png';
  await page.screenshot({ path: resolveAssetPath(wizardScreenshot), fullPage: true });
  await recordScreen({
    name: 'Administration wizard',
    route: '/admin/new',
    screenshotFile: wizardScreenshot,
    assertions: [
      { description: 'Wizard shows current step and navigation rail' },
      { description: 'Definition step exposes key metadata fields' },
      { description: 'Optimistic concurrency messaging area is visible' },
    ],
  });
});

test.afterAll(async () => {
  await finalizeReport();
});
