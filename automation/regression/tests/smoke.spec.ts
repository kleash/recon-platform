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
  await expect(page.getByText('No reconciliation runs have been executed yet.')).toBeVisible();
  await expect(page.getByText('Select a break to review details.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recent system activity' })).toBeVisible();
  await expect(page.getByText('Activity will appear here as the platform is used.')).toBeVisible();

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

test.afterAll(async () => {
  await finalizeReport();
});
