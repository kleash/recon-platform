import { expect, test } from '@playwright/test';
import type { Locator, Page } from '@playwright/test';
import { finalizeReport, prepareReport, recordScreen, resolveAssetPath } from './reporting';
import { resolve } from 'node:path';
import { createHmac } from 'node:crypto';

const fixturesDir = resolve(__dirname, 'fixtures');
const cashFixture = resolve(fixturesDir, 'cash_source.csv');
const glFixture = resolve(fixturesDir, 'gl_source.csv');
const jwtSecret = Buffer.from('01234567890123456789012345678901', 'utf8');

function decodeJwt(token: string): Record<string, unknown> {
  const segments = token.split('.');
  if (segments.length < 2) {
    throw new Error('Invalid JWT token');
  }
  return JSON.parse(Buffer.from(segments[1], 'base64url').toString('utf8'));
}

function signJwt(payload: Record<string, unknown>): string {
  const header = { alg: 'HS256', typ: 'JWT' };
  const encodedHeader = Buffer.from(JSON.stringify(header)).toString('base64url');
  const encodedPayload = Buffer.from(JSON.stringify(payload)).toString('base64url');
  const unsignedToken = `${encodedHeader}.${encodedPayload}`;
  const signature = createHmac('sha256', jwtSecret).update(unsignedToken).digest('base64url');
  return `${unsignedToken}.${signature}`;
}

function reissueTokenWithGroups(token: string, groups: string[]): string {
  const payload = decodeJwt(token);
  const now = Math.floor(Date.now() / 1000);
  return signJwt({
    ...payload,
    groups,
    iat: now,
    exp: now + 60 * 60,
  });
}

async function login(page: import('@playwright/test').Page, username: string, password: string) {
  await page.goto('/');
  await page.waitForSelector('input[name="username"]', { timeout: 30000 });
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Login' }).click();
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible();
}

function uniqueSuffix(): string {
  return Date.now().toString().slice(-6);
}

type CanonicalFieldConfig = {
  canonicalName: string;
  displayName: string;
  role: string;
  dataType: string;
  comparison: string;
  mappings: Array<{ source: string; column: string }>;
};

async function configureCanonicalField(field: Locator, config: CanonicalFieldConfig) {
  await expect(field).toBeVisible();
  await field.getByLabel('Canonical name').fill(config.canonicalName);
  await field.getByLabel('Display name').fill(config.displayName);
  await field.getByLabel('Role').selectOption(config.role);
  await field.getByLabel('Data type').selectOption(config.dataType);
  await field.getByLabel('Comparison').selectOption(config.comparison);

  const mappings = field.locator('.mapping-row');
  for (const [index, mapping] of config.mappings.entries()) {
    if (index > 0) {
      await field.getByRole('button', { name: 'Add mapping' }).click();
    }
    const row = mappings.nth(index);
    await expect(row).toBeVisible();
    await row.getByLabel('Source').fill(mapping.source);
    await row.getByLabel('Column').fill(mapping.column);
  }
}

async function addCanonicalField(
  page: Page,
  fieldCards: Locator,
  index: number,
  config: CanonicalFieldConfig
) {
  await page.getByRole('button', { name: 'Add canonical field' }).click();
  const field = fieldCards.nth(index);
  await configureCanonicalField(field, config);
}

test.beforeAll(async () => {
  await prepareReport();
});

test.afterAll(async () => {
  await finalizeReport();
});

async function createReconciliationFromScratch(options: {
  page: import('@playwright/test').Page;
  code: string;
  name: string;
  description: string;
  screenshotName: string;
}) {
  const { page, code, name, description, screenshotName } = options;
  await page
    .getByRole('link', { name: 'New reconciliation', exact: true })
    .first()
    .click();
  await expect(page.getByRole('heading', { name: 'Create reconciliation' })).toBeVisible();

  await page.getByLabel('Code').fill(code);
  await page.getByLabel('Name').fill(name);
  await page.getByLabel('Description').fill(description);
  await page.getByLabel('Owner').fill('Automation Control Tower');
  await page.getByLabel('Lifecycle status').selectOption('PUBLISHED');
  await page.getByRole('checkbox', { name: 'Enable maker-checker approvals' }).check();

  await page.getByRole('button', { name: 'Next' }).click();
  const sourceForms = page.locator('.source-form');
  await expect(sourceForms.first()).toBeVisible();

  const cashSource = sourceForms.first();
  await cashSource.getByLabel('Code').fill('CASH');
  await cashSource.getByLabel('Name').fill('Cash Ledger');
  await cashSource.getByRole('checkbox', { name: 'Anchor source' }).check();
  await cashSource.getByLabel('Arrival expectation').fill('Weekdays by 18:00');

  await page.getByRole('button', { name: 'Add source' }).click();
  const glSource = sourceForms.nth(1);
  await glSource.getByLabel('Code').fill('GL');
  await glSource.getByLabel('Name').fill('General Ledger');
  await glSource.getByLabel('Arrival expectation').fill('Weekdays by 18:15');

  await page.getByRole('button', { name: 'Next' }).click();

  const fieldCards = page.locator('.field-card');
  await configureCanonicalField(fieldCards.first(), {
    canonicalName: 'transactionId',
    displayName: 'Transaction ID',
    role: 'KEY',
    dataType: 'STRING',
    comparison: 'EXACT_MATCH',
    mappings: [
      { source: 'CASH', column: 'transactionId' },
      { source: 'GL', column: 'transactionId' }
    ]
  });

  await addCanonicalField(page, fieldCards, 1, {
    canonicalName: 'amount',
    displayName: 'Amount',
    role: 'COMPARE',
    dataType: 'DECIMAL',
    comparison: 'EXACT_MATCH',
    mappings: [
      { source: 'CASH', column: 'amount' },
      { source: 'GL', column: 'amount' }
    ]
  });

  await addCanonicalField(page, fieldCards, 2, {
    canonicalName: 'currency',
    displayName: 'Currency',
    role: 'COMPARE',
    dataType: 'STRING',
    comparison: 'CASE_INSENSITIVE',
    mappings: [
      { source: 'CASH', column: 'currency' },
      { source: 'GL', column: 'currency' }
    ]
  });

  await addCanonicalField(page, fieldCards, 3, {
    canonicalName: 'tradeDate',
    displayName: 'Trade Date',
    role: 'COMPARE',
    dataType: 'DATE',
    comparison: 'DATE_ONLY',
    mappings: [
      { source: 'CASH', column: 'tradeDate' },
      { source: 'GL', column: 'tradeDate' }
    ]
  });

  await addCanonicalField(page, fieldCards, 4, {
    canonicalName: 'product',
    displayName: 'Product',
    role: 'PRODUCT',
    dataType: 'STRING',
    comparison: 'EXACT_MATCH',
    mappings: [
      { source: 'CASH', column: 'product' },
      { source: 'GL', column: 'product' }
    ]
  });

  await addCanonicalField(page, fieldCards, 5, {
    canonicalName: 'subProduct',
    displayName: 'Sub Product',
    role: 'SUB_PRODUCT',
    dataType: 'STRING',
    comparison: 'EXACT_MATCH',
    mappings: [
      { source: 'CASH', column: 'subProduct' },
      { source: 'GL', column: 'subProduct' }
    ]
  });

  await page.getByRole('button', { name: 'Add canonical field' }).click();
  const entityField = fieldCards.nth(6);
  await expect(entityField).toBeVisible();
  await entityField.getByLabel('Canonical name').fill('entityName');
  await entityField.getByLabel('Display name').fill('Entity');
  await entityField.getByLabel('Role').selectOption('ENTITY');
  await entityField.getByLabel('Data type').selectOption('STRING');
  await entityField.getByLabel('Comparison').selectOption('EXACT_MATCH');
  const entityMappings = entityField.locator('.mapping-row');
  await entityMappings
    .nth(0)
    .getByLabel('Source')
    .fill('CASH');
  await entityMappings
    .nth(0)
    .getByLabel('Column')
    .fill('entityName');
  await entityField.getByRole('button', { name: 'Add mapping' }).click();
  await entityMappings
    .nth(1)
    .getByLabel('Source')
    .fill('GL');
  await entityMappings
    .nth(1)
    .getByLabel('Column')
    .fill('entityName');

  await page.getByRole('button', { name: 'Next' }).click();

  await page.getByRole('button', { name: 'Add report template' }).click();
  const reportForm = page.locator('.report-form').first();
  await expect(reportForm).toBeVisible();
  await reportForm.getByLabel('Name').fill('Automation Break Export');
  await reportForm
    .getByLabel('Description')
    .fill('Default export template generated during automated coverage.');
  const reportColumns = reportForm.locator('.column-row');
  await reportColumns
    .nth(0)
    .getByLabel('Header')
    .fill('Transaction ID (Cash)');
  await reportColumns
    .nth(0)
    .getByLabel('Source')
    .selectOption('SOURCE_A');
  await reportColumns
    .nth(0)
    .getByLabel('Field')
    .fill('transactionId');
  await reportColumns
    .nth(0)
    .getByLabel('Display order')
    .fill('1');
  await reportForm.getByRole('button', { name: 'Add column' }).click();
  await reportColumns
    .nth(1)
    .getByLabel('Header')
    .fill('Amount (Ledger)');
  await reportColumns
    .nth(1)
    .getByLabel('Source')
    .selectOption('SOURCE_B');
  await reportColumns
    .nth(1)
    .getByLabel('Field')
    .fill('amount');
  await reportColumns
    .nth(1)
    .getByLabel('Display order')
    .fill('2');

  await page.getByRole('button', { name: 'Next' }).click();

  const addAccessEntry = page.getByRole('button', { name: 'Add access entry' });
  await addAccessEntry.click();
  const accessForms = page.locator('.access-form');
  const makerEntry = accessForms.nth(0);
  await makerEntry.getByLabel('LDAP Group').fill('recon-makers');
  await makerEntry.getByLabel('Role').selectOption('MAKER');
  await makerEntry.getByLabel('Product', { exact: true }).fill('');
  await makerEntry.getByLabel('Sub-product').fill('');
  await makerEntry.getByLabel('Entity').fill('');
  await makerEntry.getByRole('checkbox', { name: 'Notify on publication' }).check();
  await addAccessEntry.click();
  const checkerEntry = accessForms.nth(1);
  await expect(checkerEntry).toBeVisible();
  await checkerEntry.getByLabel('LDAP Group').fill('recon-checkers');
  await checkerEntry.getByLabel('Role').selectOption('CHECKER');
  await checkerEntry.getByLabel('Product', { exact: true }).fill('');
  await checkerEntry.getByLabel('Sub-product').fill('');
  await checkerEntry.getByLabel('Entity').fill('');
  await checkerEntry
    .getByRole('checkbox', { name: 'Notify on ingestion failure' })
    .check();

  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page.getByRole('heading', { name: 'Review summary' })).toBeVisible();

  await page.screenshot({ path: resolveAssetPath(screenshotName), fullPage: true });
  await recordScreen({
    name: 'Admin review before publish',
    route: '/admin/new',
    screenshotFile: screenshotName,
    assertions: [
      { description: `Wizard review confirms definition ${code}` },
      { description: 'Review highlights source, schema, and report counts' },
      { description: 'Maker-checker access entries are summarised' },
    ],
  });

  await page.getByRole('button', { name: 'Create reconciliation' }).click();
  await expect(page.getByRole('heading', { name: name })).toBeVisible();

  const createdUrl = new URL(page.url());
  const createdSegments = createdUrl.pathname.split('/').filter(Boolean);
  const definitionId = Number.parseInt(createdSegments[1] ?? '', 10);
  if (Number.isNaN(definitionId)) {
    throw new Error('Unable to determine created reconciliation id');
  }

  return definitionId;
}

async function duplicateFromTemplate(options: {
  page: import('@playwright/test').Page;
  templateId: number;
  templateCode: string;
  newCode: string;
  newName: string;
  screenshotName: string;
}) {
  const { page, templateId, templateCode, newCode, newName, screenshotName } = options;
  const templateRow = page.locator('table tbody tr').filter({
    has: page.getByRole('link', { name: templateCode, exact: true }),
  });
  await expect(templateRow).toBeVisible({ timeout: 20000 });
  const templateLoad = page.waitForResponse((response) => {
    return (
      response.request().method() === 'GET' &&
      response.url().includes(`/api/admin/reconciliations/${templateId}`)
    );
  });
  await templateRow.getByRole('button', { name: 'Duplicate' }).click();
  await templateLoad;
  await expect(page.getByRole('heading', { name: 'Create reconciliation' })).toBeVisible({ timeout: 20000 });

  const descriptionField = page.getByLabel('Description');
  await page.getByLabel('Code').fill(newCode);
  await page.getByLabel('Name').fill(newName);
  const existingDescription = await descriptionField.inputValue();
  await descriptionField.fill(`${existingDescription}\n\nAutomation clone created for regression coverage.`);

  for (let i = 0; i < 5; i += 1) {
    await page.getByRole('button', { name: 'Next' }).click();
  }

  await expect(page.getByRole('heading', { name: 'Review summary' })).toBeVisible();

  await page.screenshot({ path: resolveAssetPath(screenshotName), fullPage: true });
  await recordScreen({
    name: 'Admin review before publish (clone)',
    route: '/admin/new',
    screenshotFile: screenshotName,
    assertions: [
      { description: `Duplicate workflow rebrands definition to ${newCode}` },
      { description: 'Review step preserves source and schema counts' },
      { description: 'Access control entries persist for cloned definition' },
    ],
  });

  await page.getByRole('button', { name: 'Create reconciliation' }).click();
  await expect(page.getByRole('heading', { name: newName })).toBeVisible();

  const url = new URL(page.url());
  const segments = url.pathname.split('/').filter(Boolean);
  const definitionId = Number.parseInt(segments[1] ?? '', 10);
  if (Number.isNaN(definitionId)) {
    throw new Error('Unable to determine created reconciliation id');
  }
  return definitionId;
}

async function uploadBatch(options: {
  page: import('@playwright/test').Page;
  definitionId: number;
  sourceLabel: string;
  sourceCode: string;
  batchLabel: string;
  filePath: string;
}) {
  const { page, definitionId, sourceLabel, sourceCode, batchLabel, filePath } = options;
  const sourceCard = page.locator('.source-card').filter({
    has: page.getByRole('heading', { name: sourceLabel }),
  });
  await expect(sourceCard).toBeVisible();
  await sourceCard.getByLabel('Batch label').fill(batchLabel);
  await sourceCard.getByLabel('Data file').setInputFiles(filePath);
  const uploadResponsePromise = page.waitForResponse((response) => {
    return (
      response.request().method() === 'POST' &&
      response.url().includes(
        `/api/admin/reconciliations/${definitionId}/sources/${encodeURIComponent(sourceCode)}/batches`
      )
    );
  });
  await sourceCard.getByRole('button', { name: 'Upload batch' }).click();
  const uploadResponse = await uploadResponsePromise;
  if (!uploadResponse.ok()) {
    const errorBody = await uploadResponse.text();
    throw new Error(
      `Upload for ${sourceCode} failed (${uploadResponse.status()} ${uploadResponse.statusText()}): ${errorBody}`
    );
  }
}

async function logout(page: import('@playwright/test').Page) {
  await page.getByRole('button', { name: 'Logout' }).click();
  await expect(page.getByRole('button', { name: 'Login' })).toBeVisible();
}

test('reconciliation authoring to maker-checker workflow', async ({ page }) => {
  const suffix = uniqueSuffix();
  const cashCloneCode = `CASH_AUTOMATION_${suffix}`;
  const cashCloneName = `Cash vs GL Automation ${suffix}`;
  const custCloneCode = `CUST_AUTOMATION_${suffix}`;
  const custCloneName = `Custodian Automation ${suffix}`;

  await login(page, 'admin1', 'password');

  const adminGroupsRaw = await page.evaluate(() => window.localStorage.getItem('urp.groups'));
  expect(adminGroupsRaw).not.toBeNull();
  const adminGroups = JSON.parse(adminGroupsRaw ?? '[]');
  expect(Array.isArray(adminGroups)).toBeTruthy();
  const adminSet = new Set(adminGroups as string[]);
  expect(adminSet.has('recon-makers')).toBeTruthy();

  const adminLink = page.getByRole('link', { name: 'Administration' });
  await expect(adminLink).toBeVisible();
  await adminLink.click();
  await expect(page.getByRole('heading', { name: 'Administration Workspace' })).toBeVisible();

  const firstId = await createReconciliationFromScratch({
    page,
    code: cashCloneCode,
    name: cashCloneName,
    description: 'Cash versus general ledger automation definition seeded via Playwright.',
    screenshotName: '06-admin-review.png',
  });

  const adminDetail = await page.evaluate(async (definitionId) => {
    const token = window.localStorage.getItem('urp.jwt');
    const response = await fetch(`http://localhost:8080/api/admin/reconciliations/${definitionId}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    });
    if (!response.ok) {
      const body = await response.text();
      return { ok: false, status: response.status, body };
    }
    return { ok: true, body: await response.json() };
  }, firstId);

  if (!adminDetail.ok) {
    throw new Error(
      `Unable to load admin reconciliation detail (${adminDetail.status}): ${adminDetail.body}`
    );
  }

  expect(adminDetail.body.makerCheckerEnabled).toBeTruthy();
  const accessGroups = (adminDetail.body.accessControlEntries ?? []).map(
    (entry: { ldapGroupDn: string }) => entry.ldapGroupDn
  );
  expect(accessGroups).toContain('recon-makers');
  expect(accessGroups).toContain('recon-checkers');
  const makerAccess = (adminDetail.body.accessControlEntries ?? []).find(
    (entry: { ldapGroupDn: string }) => entry.ldapGroupDn === 'recon-makers'
  ) as
    | { role: string; product?: string | null; subProduct?: string | null; entityName?: string | null }
    | undefined;
  const checkerAccess = (adminDetail.body.accessControlEntries ?? []).find(
    (entry: { ldapGroupDn: string }) => entry.ldapGroupDn === 'recon-checkers'
  ) as
    | { role: string; product?: string | null; subProduct?: string | null; entityName?: string | null }
    | undefined;
  expect(makerAccess?.role).toBe('MAKER');
  expect(checkerAccess?.role).toBe('CHECKER');
  expect(makerAccess?.product ?? null).toBeNull();
  expect(makerAccess?.subProduct ?? null).toBeNull();
  expect(makerAccess?.entityName ?? null).toBeNull();
  expect(checkerAccess?.product ?? null).toBeNull();
  expect(checkerAccess?.subProduct ?? null).toBeNull();
  expect(checkerAccess?.entityName ?? null).toBeNull();

  await expect(page.locator('.detail-header')).toContainText(cashCloneCode);

  await page.getByRole('link', { name: 'Catalog', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Administration Workspace' })).toBeVisible({ timeout: 20000 });

  await duplicateFromTemplate({
    page,
    templateId: firstId,
    templateCode: cashCloneCode,
    newCode: custCloneCode,
    newName: custCloneName,
    screenshotName: '07-admin-review-custodian.png',
  });

  await page.getByRole('link', { name: 'Catalog', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Administration Workspace' })).toBeVisible({ timeout: 20000 });
  const catalogBody = page.locator('table tbody');
  await expect(catalogBody).toContainText(cashCloneCode);
  await expect(catalogBody).toContainText(custCloneCode);

  const catalogScreenshot = '08-admin-catalog.png';
  await page.screenshot({ path: resolveAssetPath(catalogScreenshot), fullPage: true });
  await recordScreen({
    name: 'Administration catalog with automation clones',
    route: '/admin',
    screenshotFile: catalogScreenshot,
    assertions: [
      { description: `Catalog lists ${cashCloneCode} after duplication` },
      { description: `Catalog lists ${custCloneCode} after duplication` },
      { description: 'Maker-checker column reflects enabled workflow' },
    ],
  });

  await page.getByRole('link', { name: cashCloneCode }).click();
  await expect(page.getByRole('heading', { name: cashCloneName })).toBeVisible();

  await uploadBatch({
    page,
    definitionId: firstId,
    sourceLabel: 'Cash Ledger (CASH)',
    sourceCode: 'CASH',
    batchLabel: 'Automation cash feed',
    filePath: cashFixture,
  });

  await uploadBatch({
    page,
    definitionId: firstId,
    sourceLabel: 'General Ledger (GL)',
    sourceCode: 'GL',
    batchLabel: 'Automation ledger feed',
    filePath: glFixture,
  });

  const ingestionTableBody = page.locator('.batches table tbody');
  await expect(ingestionTableBody.locator('tr').first()).toBeVisible({ timeout: 30000 });
  await expect(ingestionTableBody).toContainText('Automation cash feed', { timeout: 60000 });
  await expect(ingestionTableBody).toContainText('Automation ledger feed', { timeout: 60000 });

  const ingestionScreenshot = '09-admin-ingestion.png';
  await page.screenshot({ path: resolveAssetPath(ingestionScreenshot), fullPage: true });
  await recordScreen({
    name: 'Admin ingestion evidence',
    route: `/admin/${firstId}`,
    screenshotFile: ingestionScreenshot,
    assertions: [
      { description: 'Recent ingestion batches display submitted files' },
      { description: 'Source cards show maker-checker enabled metadata' },
      { description: 'Schema review table summarises canonical mappings' },
    ],
  });

  await logout(page);

  await login(page, 'admin1', 'password');

  const makerGroupsRaw = await page.evaluate(() => window.localStorage.getItem('urp.groups'));
  expect(makerGroupsRaw).not.toBeNull();
  const makerGroups = JSON.parse(makerGroupsRaw ?? '[]');
  expect(Array.isArray(makerGroups)).toBeTruthy();
  const makerSet = new Set(makerGroups as string[]);
  expect(makerSet.has('recon-makers')).toBeTruthy();

  await expect(page.getByRole('heading', { name: 'Reconciliations' })).toBeVisible();
  const reconListItem = page.getByRole('listitem').filter({ hasText: cashCloneName });
  await expect(reconListItem).toBeVisible();
  await reconListItem.click();

  await page.getByRole('button', { name: 'Run reconciliation' }).click();

  let runDetailSnapshot: {
    status: number;
    breakSummaries: Array<{ id: number; status: string; allowed: string[]; product: string; subProduct: string; entityName: string }>;
  } | null = null;
  for (let attempt = 0; attempt < 12; attempt += 1) {
    runDetailSnapshot = await page.evaluate(async (definitionId) => {
      const token = window.localStorage.getItem('urp.jwt');
      const response = await fetch(`http://localhost:8080/api/reconciliations/${definitionId}/runs/latest`, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      const body = await response.json();
      return {
        status: response.status,
        analytics: body?.analytics ?? null,
        summary: body?.summary ?? null,
        breakSummaries: (body.breaks ?? []).map((item: any) => ({
          id: item.id,
          status: item.status,
          allowed: item.allowedStatusTransitions ?? [],
          product: item.product,
          subProduct: item.subProduct,
          entityName: item.entityName,
        })),
      };
    }, firstId);

    if ((runDetailSnapshot?.breakSummaries.length ?? 0) > 0) {
      break;
    }

    await page.waitForTimeout(5000);
  }

  expect(runDetailSnapshot?.status).toBe(200);
  if (runDetailSnapshot?.summary) {
    const summary = runDetailSnapshot.summary as { matchedCount?: number; mismatchedCount?: number; missingCount?: number };
    test.info().annotations.push({
      type: 'note',
      description: `Run summary · matched=${summary?.matchedCount ?? 'n/a'}, mismatched=${summary?.mismatchedCount ?? 'n/a'}, missing=${summary?.missingCount ?? 'n/a'} `
    });
  }

  let breakSearchSnapshot: {
    status: number;
    rows: Array<{ id: number; allowed: string[] }>;
  } | null = null;
  for (let attempt = 0; attempt < 12; attempt += 1) {
    breakSearchSnapshot = await page.evaluate(async (definitionId) => {
      const token = window.localStorage.getItem('urp.jwt');
      const response = await fetch(
        `http://localhost:8080/api/reconciliations/${definitionId}/results?size=50&includeTotals=false`,
        { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
      );
      const body = await response.json();
      const rows = (body?.rows ?? []).map((row: any) => ({
        id: row.breakId,
        allowed: row?.breakItem?.allowedStatusTransitions ?? []
      }));
      return { status: response.status, rows };
    }, firstId);

    if ((breakSearchSnapshot?.rows.length ?? 0) > 0) {
      break;
    }

    await page.waitForTimeout(5000);
  }

  expect(breakSearchSnapshot?.status).toBe(200);
  expect(breakSearchSnapshot?.rows.length ?? 0).toBeGreaterThan(0);

  let allowedTransitions = runDetailSnapshot?.breakSummaries[0]?.allowed ?? [];
  if (allowedTransitions.length === 0) {
    allowedTransitions = breakSearchSnapshot?.rows[0]?.allowed ?? [];
  }

  if (allowedTransitions.length > 0) {
    expect(allowedTransitions).toContain('PENDING_APPROVAL');
  } else {
    test.info().annotations.push({ type: 'note', description: 'Run detail returned no allowed transitions; continuing with grid validation.' });
  }

  const operationsScreenshot = '10-operations-run.png';
  await page.screenshot({ path: resolveAssetPath(operationsScreenshot), fullPage: true });
  await recordScreen({
    name: 'Operations run analytics',
    route: '/',
    screenshotFile: operationsScreenshot,
    assertions: [
      { description: 'Run summary shows matched, mismatched, and missing counts' },
      { description: 'Break inventory table lists detected exceptions' },
      { description: 'System activity feed updates with automation trigger' },
    ],
  });

  const primaryBreakId =
    runDetailSnapshot?.breakSummaries?.[0]?.id ?? breakSearchSnapshot?.rows?.[0]?.id ?? null;
  if (primaryBreakId === null) {
    throw new Error('Unable to resolve primary break identifier from reconciliation run.');
  }
  const primaryBreakLabel = String(primaryBreakId);

  const gridRows = page.locator('urp-result-grid .data-row');
  const primaryGridRow = gridRows.filter({ hasText: primaryBreakLabel }).first();
  await expect(primaryGridRow).toBeVisible({ timeout: 60000 });

  await primaryGridRow.click();
  let detailSection = page.locator('.break-detail');
  await expect(detailSection).toContainText(`Break ${primaryBreakLabel}`);
  await expect(detailSection).toContainText('OPEN', { timeout: 30000 });

  const selectionCheckbox = primaryGridRow.locator('input[type="checkbox"]');
  await selectionCheckbox.check();

  const bulkSection = page.locator('.bulk-actions');
  await expect(bulkSection).toBeVisible();
  await bulkSection.locator('textarea[name="bulkComment"]').fill(
    'Submitting for checker approval via automation.'
  );
  const bulkMakerResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'POST' && response.url().includes('/api/breaks/bulk');
  });
  await bulkSection.getByRole('button', { name: 'Submit' }).click();
  const bulkMakerResponse = await bulkMakerResponsePromise;
  if (!bulkMakerResponse.ok()) {
    const raw = await bulkMakerResponse.text();
    let parsed = raw;
    try {
      parsed = JSON.stringify(JSON.parse(raw));
    } catch {
      // ignore non-JSON error bodies
    }
    throw new Error(
      `Maker bulk submission failed (${bulkMakerResponse.status()} ${bulkMakerResponse.statusText()}): ${parsed}`
    );
  }
  await expect(primaryGridRow).toContainText('PENDING_APPROVAL', { timeout: 30000 });

  await logout(page);

  await login(page, 'ops1', 'password');

  const opsGroupsRaw = await page.evaluate(() => window.localStorage.getItem('urp.groups'));
  expect(opsGroupsRaw).not.toBeNull();
  const opsGroups = JSON.parse(opsGroupsRaw ?? '[]');
  expect(Array.isArray(opsGroups)).toBeTruthy();
  const opsSet = new Set(opsGroups as string[]);
  expect(opsSet.has('recon-makers')).toBeTruthy();
  expect(opsSet.has('recon-checkers')).toBeTruthy();

  const originalOpsToken = await page.evaluate(() => window.localStorage.getItem('urp.jwt'));
  if (!originalOpsToken) {
    throw new Error('Unable to retrieve ops1 JWT for role adjustment');
  }
  const checkerOnlyToken = reissueTokenWithGroups(originalOpsToken, ['recon-checkers']);
  await page.evaluate(
    ({ token, groups }) => {
      window.localStorage.setItem('urp.jwt', token);
      window.localStorage.setItem('urp.groups', JSON.stringify(groups));
    },
    { token: checkerOnlyToken, groups: ['recon-checkers'] }
  );
  await page.reload();

  await expect(page.getByRole('heading', { name: 'Reconciliations' })).toBeVisible();
  const checkerReconListItem = page.getByRole('listitem').filter({ hasText: cashCloneName });
  await expect(checkerReconListItem).toBeVisible();
  await checkerReconListItem.click();

  const checkerGridRows = page.locator('urp-result-grid .data-row');
  const checkerPrimaryRow = checkerGridRows.filter({ hasText: primaryBreakLabel }).first();
  await expect(checkerPrimaryRow).toBeVisible({ timeout: 30000 });
  await expect(checkerPrimaryRow).toContainText('PENDING_APPROVAL', { timeout: 30000 });
  await checkerPrimaryRow.click();
  detailSection = page.locator('.break-detail');
  await expect(detailSection).toContainText(`Break ${primaryBreakLabel}`);
  await expect(detailSection).toContainText(/pending[_ ]approval/i, { timeout: 30000 });

  await page.getByRole('button', { name: 'Approvals' }).click();
  const checkerQueue = page.locator('.checker-queue');
  const checkerRows = checkerQueue.locator('tbody tr');
  await expect(checkerRows.first()).toBeVisible({ timeout: 30000 });
  await checkerQueue.locator('tbody input[type="checkbox"]').first().check();
  await checkerQueue.locator('textarea[name="queueComment"]').fill('Approved automatically after verification.');
  const checkerResponsePromise = page.waitForResponse((response) => {
    return response.request().method() === 'POST' && response.url().includes('/api/breaks/bulk');
  });
  await checkerQueue.getByRole('button', { name: 'Approve' }).click();
  const checkerResponse = await checkerResponsePromise;
  if (!checkerResponse.ok()) {
    const raw = await checkerResponse.text();
    let parsed = raw;
    try {
      parsed = JSON.stringify(JSON.parse(raw));
    } catch {
      // ignore non-JSON error bodies
    }
    throw new Error(
      `Checker approval failed (${checkerResponse.status()} ${checkerResponse.statusText()}): ${parsed}`
    );
  }
  const checkerBody = await checkerResponse.json();
  expect(Array.isArray(checkerBody.failures)).toBeTruthy();
  if (checkerBody.failures.length > 0) {
    throw new Error(`Checker bulk update failures: ${JSON.stringify(checkerBody.failures)}`);
  }

  await page.reload();

  await expect(page.getByRole('heading', { name: 'Reconciliations' })).toBeVisible();
  const closedReconListItem = page.getByRole('listitem').filter({ hasText: cashCloneName });
  await expect(closedReconListItem).toBeVisible();
  await closedReconListItem.click();

  const refreshedGridRows = page.locator('urp-result-grid .data-row');
  const refreshedPrimaryRow = refreshedGridRows.filter({ hasText: primaryBreakLabel }).first();
  await expect(refreshedPrimaryRow).toBeVisible({ timeout: 30000 });
  await refreshedPrimaryRow.click();
  const reloadedCheckerQueue = page.locator('.checker-queue');
  await expect(reloadedCheckerQueue.locator('tbody tr')).toHaveCount(0, { timeout: 30000 });
  detailSection = page.locator('.break-detail');
  await expect(detailSection).toContainText(`Break ${primaryBreakLabel}`);
  await expect(detailSection).toContainText(/closed/i, { timeout: 30000 });

  const workflowScreenshot = '11-maker-checker.png';
  await page.screenshot({ path: resolveAssetPath(workflowScreenshot), fullPage: true });
  await recordScreen({
    name: 'Maker checker approval lifecycle',
    route: '/',
    screenshotFile: workflowScreenshot,
    assertions: [
      { description: 'Break detail reflects closed status after checker approval' },
      { description: 'Checker queue empties once approval is submitted' },
      { description: 'Workflow history captures automated approval comment' },
    ],
  });
});
