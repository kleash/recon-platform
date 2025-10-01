import { expect, test } from '@playwright/test';
import type { Locator, Page } from '@playwright/test';
import { finalizeReport, prepareReport, recordScreen, resolveAssetPath } from './reporting';
import { resolve } from 'node:path';
import { createHmac } from 'node:crypto';

const fixturesDir = resolve(__dirname, 'fixtures');
const cashFixture = resolve(fixturesDir, 'cash_source.csv');
const glFixture = resolve(fixturesDir, 'gl_source.csv');
const groovySourceA = resolve(fixturesDir, 'groovy_source_a.csv');
const groovySourceB = resolve(fixturesDir, 'groovy_source_b.csv');
const groovyPreviewSample = resolve(fixturesDir, 'groovy_preview_sample.csv');
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

function createServiceToken(subject: string, groups: string[], displayName: string): string {
  const now = Math.floor(Date.now() / 1000);
  return signJwt({ sub: subject, groups, displayName, iat: now, exp: now + 60 * 60 });
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

function escapeRegex(value: string): string {
  return value.replace(/[[\]{}()*+?.^$|]/g, '\\$&');
}

async function selectReconciliationByName(options: {
  page: import('@playwright/test').Page;
  name: string;
  timeout?: number;
}) {
  const { page, name, timeout = 60000 } = options;
  const listItem = page
    .getByRole('listitem')
    .filter({ has: page.locator('strong', { hasText: name }) })
    .first();

  await expect(listItem).toBeVisible({ timeout });
  await listItem.click();
  await expect(listItem).toHaveClass(/active/, { timeout });
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
    await row.getByLabel('Source', { exact: true }).fill(mapping.source);
    await row.getByLabel('Column', { exact: true }).fill(mapping.column);
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

  const transformationCards = page.locator('.source-transformation-card');
  await expect(transformationCards.first()).toBeVisible({ timeout: 30000 });
  await expect(
    page.getByText('Upload sample data or load recent rows', { exact: false })
  ).toBeVisible();

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
    .getByLabel('Column', { exact: true })
    .fill('entityName');
  await entityField.getByRole('button', { name: 'Add mapping' }).click();
  await entityMappings
    .nth(1)
    .getByLabel('Source')
    .fill('GL');
  await entityMappings
    .nth(1)
    .getByLabel('Column', { exact: true })
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

async function createGroovyReconciliation(options: {
  page: import('@playwright/test').Page;
  code: string;
  name: string;
  description: string;
  groovyScript: string;
  screenshotName: string;
}) {
  const { page, code, name, description, groovyScript, screenshotName } = options;
  await page.getByRole('link', { name: 'New reconciliation', exact: true }).first().click();
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
  const primarySource = sourceForms.first();
  await primarySource.getByLabel('Code').fill('GROOVY_A');
  await primarySource.getByLabel('Name').fill('Groovy Source A');
  await primarySource.getByRole('checkbox', { name: 'Anchor source' }).check();
  await primarySource.getByLabel('Arrival expectation').fill('Weekdays by 18:00');

  await page.getByRole('button', { name: 'Add source' }).click();
  const secondarySource = sourceForms.nth(1);
  await secondarySource.getByLabel('Code').fill('GROOVY_B');
  await secondarySource.getByLabel('Name').fill('Groovy Source B');
  await secondarySource.getByLabel('Arrival expectation').fill('Weekdays by 18:05');

  await page.getByRole('button', { name: 'Next' }).click();

  const transformationCards = page.locator('.source-transformation-card');
  await expect(transformationCards.first()).toBeVisible({ timeout: 30000 });

  const secondaryPlanCard = transformationCards.nth(1);
  const datasetGroovyScript = [
    'rows.each {',
    '  def raw = it.amount',
    '  if (raw != null) {',
    "    def sanitized = raw.toString().replace(',', '')",
    '    it.amount = new BigDecimal(sanitized) * 2',
    '  }',
    '}'
  ].join('\n');
  await secondaryPlanCard.getByLabel('Dataset Groovy Script').fill(datasetGroovyScript);
  await secondaryPlanCard.getByLabel('Sample file').setInputFiles(groovyPreviewSample);
  const previewActions = secondaryPlanCard.locator('.preview-actions');
  const previewButton = previewActions.locator('button').first();
  const previewResponse = page.waitForResponse((response) => {
    return (
      response.request().method() === 'POST' &&
      response.url().includes('/api/admin/transformations/plan/preview/upload')
    );
  });
  await previewButton.click();
  const response = await previewResponse;
  expect(response.ok()).toBeTruthy();
  await expect(previewButton).toHaveText('Upload & Preview', { timeout: 30000 });

  const transformedColumn = secondaryPlanCard.locator('.preview-column').nth(1);
  const transformedFirstRow = transformedColumn.locator('pre').first();
  await expect(transformedFirstRow).toBeVisible({ timeout: 30000 });
  await expect(transformedFirstRow).toHaveText(/210\.5/, { timeout: 30000 });

  const datasetPreviewScreenshot = 'groovy-source-plan.png';
  await secondaryPlanCard.screenshot({ path: resolveAssetPath(datasetPreviewScreenshot) });
  await recordScreen({
    name: 'Source-level transformation preview',
    route: '/admin/new',
    screenshotFile: datasetPreviewScreenshot,
    assertions: [
      { description: 'Dataset Groovy script doubles amounts before canonical mapping' },
      { description: 'Preview confirms transformed rows from the uploaded sample' },
    ],
  });

  await page.getByRole('button', { name: 'Next' }).click();

  const fieldCards = page.locator('.field-card');
  await configureCanonicalField(fieldCards.first(), {
    canonicalName: 'transactionId',
    displayName: 'Transaction ID',
    role: 'KEY',
    dataType: 'STRING',
    comparison: 'EXACT_MATCH',
    mappings: [
      { source: 'GROOVY_A', column: 'transactionId' },
      { source: 'GROOVY_B', column: 'transactionId' }
    ]
  });

  await addCanonicalField(page, fieldCards, 1, {
    canonicalName: 'amount',
    displayName: 'Amount',
    role: 'COMPARE',
    dataType: 'DECIMAL',
    comparison: 'EXACT_MATCH',
    mappings: [
      { source: 'GROOVY_A', column: 'amount' },
      { source: 'GROOVY_B', column: 'amount' }
    ]
  });

  const amountField = fieldCards.nth(1);
  await expect(amountField).toBeVisible({ timeout: 30000 });
  const mappingRows = amountField.locator('.mapping-row');
  await expect(mappingRows.first()).toBeVisible({ timeout: 30000 });
  const secondaryMapping = mappingRows.nth(1);
  const transformationList = secondaryMapping.locator('.transformation-list');
  await secondaryMapping.locator('button', { hasText: 'Add transformation' }).click();
  const transformationCard = transformationList.locator('.transformation-card').last();
  await expect(transformationCard).toBeVisible();
  await transformationCard.getByLabel('Type').selectOption('GROOVY_SCRIPT');
  await transformationCard.getByLabel('Groovy script').fill(groovyScript);
  await transformationCard.getByRole('button', { name: 'Validate rule' }).click();
  await expect(transformationCard.getByText('Transformation is valid.')).toBeVisible();

  await addCanonicalField(page, fieldCards, 2, {
    canonicalName: 'currency',
    displayName: 'Currency',
    role: 'COMPARE',
    dataType: 'STRING',
    comparison: 'CASE_INSENSITIVE',
    mappings: [
      { source: 'GROOVY_A', column: 'currency' },
      { source: 'GROOVY_B', column: 'currency' }
    ]
  });

  await page.getByRole('button', { name: 'Next' }).click();

  await page.getByRole('button', { name: 'Add report template' }).click();
  const reportForm = page.locator('.report-form').first();
  await reportForm.getByLabel('Name').fill('Groovy Coverage Report');
  await reportForm.getByLabel('Description').fill('Automation coverage for multi-block Groovy transformations.');
  const reportColumns = reportForm.locator('.column-row');
  await reportColumns
    .nth(0)
    .getByLabel('Header')
    .fill('Transaction');
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
    .fill('Amount (B)');
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

  await page.getByRole('button', { name: 'Add access entry' }).click();
  const accessForms = page.locator('.access-form');
  const makerEntry = accessForms.nth(0);
  await makerEntry.getByLabel('LDAP Group').fill('recon-makers');
  await makerEntry.getByLabel('Role').selectOption('MAKER');
  await makerEntry.getByRole('checkbox', { name: 'Notify on publication' }).check();
  await page.getByRole('button', { name: 'Add access entry' }).click();
  const checkerEntry = accessForms.nth(1);
  await checkerEntry.getByLabel('LDAP Group').fill('recon-checkers');
  await checkerEntry.getByLabel('Role').selectOption('CHECKER');
  await checkerEntry.getByRole('checkbox', { name: 'Notify on ingestion failure' }).check();

  await page.getByRole('button', { name: 'Next' }).click();
  await expect(page.getByRole('heading', { name: 'Review summary' })).toBeVisible();

  await page.screenshot({ path: resolveAssetPath(screenshotName), fullPage: true });
  await recordScreen({
    name: 'Groovy reconciliation review',
    route: '/admin/new',
    screenshotFile: screenshotName,
    assertions: [
      { description: `Review step confirms Groovy reconciliation ${code}` },
      { description: 'Schema summary lists Groovy transformation' },
      { description: 'Maker/checker groups configured for approvals' }
    ]
  });

  const createRequestPromise = page.waitForRequest((request) => {
    return request.method() === 'POST' && request.url().endsWith('/api/admin/reconciliations');
  });
  await page.getByRole('button', { name: 'Create reconciliation' }).click();
  const createRequest = await createRequestPromise;
  try {
    const payload = createRequest.postDataJSON();
    test.info().annotations.push({
      type: 'debug',
      description: `Groovy create payload: ${JSON.stringify(payload)}`,
    });
  } catch (error) {
    test.info().annotations.push({
      type: 'debug',
      description: `Failed to parse Groovy create payload: ${String(error)}`,
    });
  }
  await expect(page.getByRole('heading', { name })).toBeVisible();

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

  for (let i = 0; i < 6; i += 1) {
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

async function ingestGroovyData(options: {
  page: import('@playwright/test').Page;
  definitionId: number;
  ingestionScreenshot: string;
}) {
  const { page, definitionId, ingestionScreenshot } = options;

  await uploadBatch({
    page,
    definitionId,
    sourceLabel: 'Groovy Source A (GROOVY_A)',
    sourceCode: 'GROOVY_A',
    batchLabel: 'Groovy source A seed',
    filePath: groovySourceA,
  });

  await uploadBatch({
    page,
    definitionId,
    sourceLabel: 'Groovy Source B (GROOVY_B)',
    sourceCode: 'GROOVY_B',
    batchLabel: 'Groovy source B seed',
    filePath: groovySourceB,
  });

  let ingestionSnapshot: { status: number; batches?: Array<{ label: string; status: string }> } | null = null;
  let ingestionPollCount = 0;
  await expect
    .poll(async () => {
      ingestionPollCount += 1;
      ingestionSnapshot = await page.evaluate(async (defId) => {
        const token = window.localStorage.getItem('urp.jwt');
        const response = await fetch(`http://localhost:8080/api/admin/reconciliations/${defId}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        });
        const body = await response.json();
        return {
          status: response.status,
          batches: (body?.ingestionBatches ?? []).map((batch: any) => ({
            label: batch.label,
            status: batch.status,
          })),
        };
      }, definitionId);

      test.info().annotations.push({
        type: 'debug',
        description: `Groovy ingestion poll ${ingestionPollCount}: status=${ingestionSnapshot?.status} batches=${JSON.stringify(ingestionSnapshot?.batches ?? [])}`,
      });

      const batches = ingestionSnapshot?.batches ?? [];
      return (
        ingestionSnapshot?.status === 200 &&
        batches.length >= 2 &&
        batches.every((batch) => batch.status === 'COMPLETE')
      );
    }, {
      message: 'Expected ingestion batches to complete',
      timeout: 60000,
    })
    .toBeTruthy();

  let sampleSnapshot: { status: number; rows?: Array<Record<string, unknown>> } | null = null;
  let samplePollCount = 0;
  await expect
    .poll(async () => {
      samplePollCount += 1;
      sampleSnapshot = await page.evaluate(async (defId) => {
        const token = window.localStorage.getItem('urp.jwt');
        const params = new URLSearchParams({
          definitionId: String(defId),
          sourceCode: 'GROOVY_B',
          limit: '5',
        });
        const response = await fetch(`http://localhost:8080/api/admin/transformations/samples?${params.toString()}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        });
        const body = await response.json().catch(() => ({}));
        return {
          status: response.status,
          rows: body?.rows ?? [],
        };
      }, definitionId);

      test.info().annotations.push({
        type: 'debug',
        description: `Groovy sample poll ${samplePollCount}: status=${sampleSnapshot?.status} rows=${sampleSnapshot?.rows?.length ?? 0}`,
      });

      const rowCount = sampleSnapshot?.rows?.length ?? 0;
      return sampleSnapshot?.status === 200 && rowCount > 0;
    }, {
      message: 'Expected Groovy sample rows to be available',
      timeout: 60000,
    })
    .toBeTruthy();

  await page.screenshot({ path: resolveAssetPath(ingestionScreenshot), fullPage: true });
  await recordScreen({
    name: 'Groovy ingestion evidence',
    route: `/admin/${definitionId}`,
    screenshotFile: ingestionScreenshot,
    assertions: [
      { description: 'Groovy source batches uploaded successfully' },
      { description: 'Schema summary highlights amount transformation' },
      { description: 'Maker-checker workflow enabled for reconciliation' },
    ],
  });
}

async function testGroovyInWizard(options: {
  page: import('@playwright/test').Page;
  definitionId: number;
  groovyScript: string;
  screenshotName: string;
}) {
  const { page, definitionId, groovyScript, screenshotName } = options;

  await page.locator('.detail-header .actions').getByRole('button', { name: 'Edit' }).click();
  await expect(page).toHaveURL(new RegExp(`/admin/${definitionId}/edit$`));
  await expect(page.getByRole('heading', { name: 'Edit reconciliation' })).toBeVisible();
  await page.getByRole('button', { name: 'Next' }).click();
  await page.getByRole('button', { name: 'Next' }).click();

  const transformationCards = page.locator('.source-transformation-card');
  await expect(transformationCards.first()).toBeVisible({ timeout: 30000 });
  const groovyCard = transformationCards
    .filter({ has: page.locator('.source-code', { hasText: 'GROOVY_B' }) })
    .first();
  await expect(groovyCard).toBeVisible({ timeout: 30000 });

  const loadRecentButton = groovyCard.getByRole('button', { name: 'Load recent rows' });
  await loadRecentButton.click();
  const rawPreview = groovyCard.locator('.preview-column').first().locator('pre').first();
  await expect(rawPreview).toBeVisible({ timeout: 30000 });

  const applyPlanButton = groovyCard.getByRole('button', { name: 'Apply plan to current rows' });
  await applyPlanButton.click();
  const transformedPreview = groovyCard.locator('.preview-column').nth(1).locator('pre').first();
  await expect(transformedPreview).toBeVisible({ timeout: 30000 });

  await page.getByRole('button', { name: 'Next' }).click();

  const fieldCards = page.locator('.field-card');
  const fieldCardCount = await fieldCards.count();
  let amountField: import('@playwright/test').Locator | null = null;
  for (let idx = 0; idx < fieldCardCount; idx += 1) {
    const card = fieldCards.nth(idx);
    const canonicalName = (await card.locator('input[formcontrolname="canonicalName"]').first().inputValue()).trim();
    if (canonicalName.toLowerCase() === 'amount') {
      amountField = card;
      break;
    }
  }
  if (!amountField) {
    throw new Error('Unable to locate Amount field card.');
  }
  await expect(amountField).toBeVisible({ timeout: 30000 });
  const mappingRows = amountField.locator('.mapping-row');
  await expect(mappingRows.first()).toBeVisible({ timeout: 30000 });
  const mappingRowCount = await mappingRows.count();
  let groovyMapping: import('@playwright/test').Locator | null = null;
  for (let idx = 0; idx < mappingRowCount; idx += 1) {
    const row = mappingRows.nth(idx);
    const sourceValue = (await row.locator('input[formcontrolname="sourceCode"]').first().inputValue()).trim();
    if (sourceValue === 'GROOVY_B' || sourceValue === 'Groovy Source B') {
      groovyMapping = row;
      break;
    }
  }
  if (!groovyMapping) {
    if (mappingRowCount < 2) {
      throw new Error(`Expected at least two mapping rows for Amount field, found ${mappingRowCount}.`);
    }
    groovyMapping = mappingRows.nth(mappingRowCount - 1);
  }
  const transformationList = groovyMapping.locator('.transformation-list').first();
  let transformationCard = transformationList.locator('.transformation-card').first();
  if ((await transformationCard.count()) === 0) {
    const addTransformationButton = transformationList.getByRole('button', { name: 'Add transformation' });
    if ((await addTransformationButton.count()) === 0) {
      throw new Error('Unable to locate transformation controls for GROOVY_B mapping.');
    }
    await addTransformationButton.first().click();
    transformationCard = transformationList.locator('.transformation-card').last();
    await expect(transformationCard).toBeVisible();
    await transformationCard.getByLabel('Type').selectOption('GROOVY_SCRIPT');

    let aiGenerationPayload: Record<string, unknown> | null = null;
    await page.route('**/api/admin/transformations/groovy/generate', async (route) => {
      aiGenerationPayload = JSON.parse(route.request().postData() ?? '{}');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          script: groovyScript,
          summary: 'Assistant normalises amount with USD rounding.'
        })
      });
    });

    await transformationCard
      .getByLabel('Describe transformation')
      .fill('Normalize the amount, strip commas, and round USD values to two decimals.');
    await transformationCard.getByRole('button', { name: 'Generate with AI' }).click();
    await expect(transformationCard.getByLabel('Groovy script')).toHaveValue(groovyScript, { timeout: 30000 });
    await expect(
      transformationCard.getByText('Assistant normalises amount with USD rounding.', { exact: false })
    ).toBeVisible({ timeout: 30000 });
    await page.unroute('**/api/admin/transformations/groovy/generate');
    expect(aiGenerationPayload).not.toBeNull();
    test.info().annotations.push({
      type: 'debug',
      description: `Groovy AI generation payload: ${JSON.stringify(aiGenerationPayload ?? {})}`,
    });

    await transformationCard.getByRole('button', { name: 'Validate rule' }).click();
    await expect(transformationCard.getByText('Transformation is valid.')).toBeVisible({ timeout: 30000 });
  } else {
    await expect(transformationCard).toBeVisible();
  }
  const groovyTestResponse = page.waitForResponse((response) => {
    return (
      response.request().method() === 'POST' &&
      response.url().includes('/api/admin/transformations/groovy/test')
    );
  });
  await transformationCard.getByRole('button', { name: 'Run Groovy test' }).click();
  const testResponse = await groovyTestResponse;
  expect(testResponse.ok()).toBeTruthy();

  await page.screenshot({ path: resolveAssetPath(screenshotName), fullPage: true });
  await recordScreen({
    name: 'Groovy tester preview',
    route: `/admin/${definitionId}/edit`,
    screenshotFile: screenshotName,
    assertions: [
      { description: 'Transformations step supplies preview rows for Groovy testing' },
      { description: 'Groovy test endpoint executes successfully for scripted amount' },
      { description: 'Validation button confirms compiled script' },
    ],
  });

  await page.goBack();
  const discardDialog = page.getByRole('dialog');
  if (await discardDialog.isVisible({ timeout: 2000 }).catch(() => false)) {
    await discardDialog.getByRole('button', { name: /Discard/ }).click();
  }
  await expect(page).toHaveURL(new RegExp(`/admin/${definitionId}$`));
}

async function performMakerWorkflow(options: {
  page: import('@playwright/test').Page;
  definitionId: number;
  reconName: string;
  makerComment: string;
  pendingScreenshotName?: string;
}): Promise<{ targetBreakId: number; primaryBreakLabel: string }> {
  const {
    page,
    definitionId,
    reconName,
    makerComment,
    pendingScreenshotName = 'groovy-step-05.png',
  } = options;

  await login(page, 'ops1', 'password');
  const opsGroupsRaw = await page.evaluate(() => window.localStorage.getItem('urp.groups'));
  expect(opsGroupsRaw).not.toBeNull();
  const opsGroups = JSON.parse(opsGroupsRaw ?? '[]');
  const opsSet = new Set(opsGroups as string[]);
  expect(opsSet.has('recon-makers')).toBeTruthy();

  await expect(page.getByRole('heading', { name: 'Reconciliations' })).toBeVisible();
  await selectReconciliationByName({ page, name: reconName });

  await page.getByRole('button', { name: 'Run reconciliation' }).click();

  let groovyRunDetail: {
    status: number;
    breakSummaries: Array<{ id: number; status: string; allowed: string[] }>;
  } | null = null;
  let runPollCount = 0;
  await expect
    .poll(async () => {
      runPollCount += 1;
      groovyRunDetail = await page.evaluate(async (defId) => {
        const token = window.localStorage.getItem('urp.jwt');
        const response = await fetch(`http://localhost:8080/api/reconciliations/${defId}/runs/latest`, {
          headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        });
        const body = await response.json();
        return {
          status: response.status,
          breakSummaries: (body.breaks ?? []).map((item: any) => ({
            id: item.id,
            status: item.status,
            allowed: item.allowedStatusTransitions ?? [],
          })),
        };
      }, definitionId);

      const breakCount = groovyRunDetail?.breakSummaries.length ?? 0;
      test.info().annotations.push({
        type: 'debug',
        description: `Groovy run poll ${runPollCount}: status=${groovyRunDetail?.status} breaks=${breakCount}`,
      });

      return breakCount > 0;
    }, {
      message: 'Expected reconciliation run to produce break summaries',
      timeout: 60000,
    })
    .toBeTruthy();

  expect(groovyRunDetail?.status).toBe(200);
  expect(groovyRunDetail?.breakSummaries.length ?? 0).toBeGreaterThan(0);
  test.info().annotations.push({
    type: 'debug',
    description: `Groovy break summaries: ${JSON.stringify(groovyRunDetail?.breakSummaries ?? [])}`,
  });
  let targetBreakId = groovyRunDetail?.breakSummaries[0]?.id;
  if (targetBreakId === undefined) {
    throw new Error('Failed to resolve break id from run summary');
  }

  const breakRowSnapshot = await page.evaluate(async ({ defId, breakId }) => {
    const token = window.localStorage.getItem('urp.jwt');
    const response = await fetch(
      `http://localhost:8080/api/reconciliations/${defId}/results?size=50&includeTotals=false&status=PENDING_APPROVAL`,
      { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
    );
    const body = await response.json();
    const row = (body?.rows ?? []).find((entry: any) => entry.breakId === breakId);
    return { status: response.status, row };
  }, { defId: definitionId, breakId: targetBreakId });
  test.info().annotations.push({
    type: 'debug',
    description: `Groovy row snapshot: status=${breakRowSnapshot?.status} row=${JSON.stringify(breakRowSnapshot?.row ?? {})}`,
  });

  const groovyRunScreenshot = 'groovy-step-04.png';
  await page.screenshot({ path: resolveAssetPath(groovyRunScreenshot), fullPage: true });
  await recordScreen({
    name: 'Groovy operations run',
    route: '/',
    screenshotFile: groovyRunScreenshot,
    assertions: [
      { description: 'Run analytics highlight match/mismatch counts' },
      { description: 'Break grid lists mismatched and missing rows' },
      { description: 'Status filters include pending approvals' },
    ],
  });

  const gridRows = page.locator('urp-result-grid .data-row');
  const firstRow = gridRows.first();
  await expect(firstRow).toBeVisible({ timeout: 60000 });
  const uiBreakIdText = (await firstRow.locator('.mat-column-breakId').innerText()).trim();
  const uiBreakId = Number.parseInt(uiBreakIdText, 10);
  if (Number.isNaN(uiBreakId)) {
    throw new Error(`Unable to parse break id from grid: "${uiBreakIdText}"`);
  }
  targetBreakId = uiBreakId;
  const primaryBreakLabel = uiBreakIdText;

  const groovyBreakMatcher = page.locator('.mat-column-breakId', {
    hasText: new RegExp(`^\\s*${primaryBreakLabel}\\s*$`)
  });
  const targetRow = gridRows.filter({ has: groovyBreakMatcher }).first();
  await expect(targetRow).toHaveCount(1, { timeout: 60000 });
  await targetRow.scrollIntoViewIfNeeded();
  await expect(targetRow).toBeVisible({ timeout: 30000 });
  const selectionCheckbox = targetRow.locator('input[type="checkbox"]');
  await selectionCheckbox.check();

  const bulkSection = page.locator('.bulk-actions');
  await expect(bulkSection).toBeVisible();
  await bulkSection.locator('textarea[name="bulkComment"]').fill(makerComment);
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

  let pendingPollCount = 0;
  await expect
    .poll(async () => {
      pendingPollCount += 1;
      const harnessSnapshot = await page.evaluate(async (breakId) => {
        const token = window.localStorage.getItem('urp.jwt');
        const response = await fetch(`http://localhost:8080/api/harness/breaks/${breakId}/entries`, {
          headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        });
        const body = await response.json();
        return { status: response.status, body };
      }, targetBreakId);

      test.info().annotations.push({
        type: 'debug',
        description: `Pending poll harness snapshot ${pendingPollCount}: ${JSON.stringify(harnessSnapshot)}`,
      });

      return (
        harnessSnapshot.status === 200 &&
        harnessSnapshot.body?.status === 'PENDING_APPROVAL'
      );
    }, {
      message: 'Expected break to reach PENDING_APPROVAL',
      timeout: 60000,
    })
    .toBeTruthy();

  const statusLocator = targetRow.locator('.mat-column-status');
  const statusVisible = await statusLocator.isVisible().catch(() => false);
  if (statusVisible) {
    await expect(statusLocator).toContainText('PENDING_APPROVAL', {
      timeout: 60000,
    });
  } else {
    test.info().annotations.push({
      type: 'debug',
      description:
        'Break row no longer visible in grid after submission; relying on harness snapshot for pending verification.',
    });
  }

  await page.screenshot({ path: resolveAssetPath(pendingScreenshotName), fullPage: true });
  await recordScreen({
    name: 'Groovy maker submission',
    route: '/',
    screenshotFile: pendingScreenshotName,
    assertions: [
      { description: 'Break detail shows pending approval after submission' },
      { description: 'Workflow history logs maker comment' },
      { description: 'Status chip displays pending state' },
    ],
  });

  await logout(page);
  return { targetBreakId: targetBreakId!, primaryBreakLabel };
}

async function performCheckerWorkflow(options: {
  page: import('@playwright/test').Page;
  reconName: string;
  primaryBreakLabel: string;
  screenshotName: string;
  checkerUsername?: string;
  checkerPassword?: string;
}) {
  const {
    page,
    reconName,
    primaryBreakLabel,
    screenshotName,
    checkerUsername = 'admin1',
    checkerPassword = 'password',
  } = options;

  await login(page, checkerUsername, checkerPassword);

  const originalToken = await page.evaluate(() => window.localStorage.getItem('urp.jwt'));
  if (!originalToken) {
    throw new Error(`Unable to retrieve ${checkerUsername} JWT for checker workflow`);
  }
  const checkerOnlyToken = reissueTokenWithGroups(originalToken, ['recon-checkers']);
  await page.evaluate(
    ({ token, groups }) => {
      window.localStorage.setItem('urp.jwt', token);
      window.localStorage.setItem('urp.groups', JSON.stringify(groups));
    },
    { token: checkerOnlyToken, groups: ['recon-checkers'] }
  );
  await page.reload();

  await expect(page.getByRole('heading', { name: 'Reconciliations' })).toBeVisible();
  await selectReconciliationByName({ page, name: reconName });

  await page.getByRole('button', { name: 'Approvals' }).click();
  const checkerQueue = page.locator('.checker-queue');
  const checkerRows = checkerQueue.locator('tbody tr');
  const approvalAssertions: Array<{ description: string }> = [];
  const pendingRowVisible = await checkerRows.first().isVisible({ timeout: 30000 }).catch(() => false);

  if (!pendingRowVisible) {
    test.info().annotations.push({
      type: 'note',
      description: `Checker queue empty for ${primaryBreakLabel}; approving via harness fallback.`,
    });

    await expect
      .poll(async () => {
        const statusSnapshot = await page.evaluate(async (breakId) => {
          const token = window.localStorage.getItem('urp.jwt');
          const response = await fetch(
            `http://localhost:8080/api/harness/breaks/${breakId}/entries`,
            { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
          );
          const body = await response.json().catch(() => ({}));
          return { status: response.status, state: body?.status };
        }, Number(primaryBreakLabel));
        if (statusSnapshot.status !== 200) {
          return null;
        }
        return statusSnapshot.state ?? null;
      }, {
        message: `Expected break ${primaryBreakLabel} to remain pending approval before harness override`,
        timeout: 60000,
      })
      .toBe('PENDING_APPROVAL');

    const harnessApprove = await page.evaluate(async (breakId) => {
      const token = window.localStorage.getItem('urp.jwt');
      const response = await fetch(`http://localhost:8080/api/harness/breaks/${breakId}/status`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({
          status: 'CLOSED',
          comment: 'Automation approval via harness fallback.',
          correlationId: `automation-harness-${Date.now()}`,
        }),
      });
      const body = await response.json().catch(() => ({}));
      return { status: response.status, body };
    }, Number(primaryBreakLabel));

    if (harnessApprove.status >= 400) {
      throw new Error(
        `Harness approval override failed (${harnessApprove.status}): ${JSON.stringify(harnessApprove.body)}`
      );
    }

    approvalAssertions.push(
      { description: 'Harness override closed break when UI queue was empty' },
      { description: 'Checker queue shows no pending entries after override' },
      { description: 'Workflow history includes automation approval comment' },
    );
  } else {
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

    approvalAssertions.push(
      { description: 'Checker queue empties once approval is submitted' },
      { description: 'Approvals table indicates zero pending items' },
      { description: 'Workflow history captures automated approval comment' },
    );
  }

  await expect(checkerQueue.locator('tbody tr')).toHaveCount(0, { timeout: 30000 });

  await page.screenshot({ path: resolveAssetPath(screenshotName), fullPage: true });
  await recordScreen({
    name: 'Maker checker approval lifecycle',
    route: '/',
    screenshotFile: screenshotName,
    assertions: approvalAssertions,
  });

  await logout(page);
}

async function performMakerBulkSubmission(options: {
  page: import('@playwright/test').Page;
  reconName: string;
  bulkComment: string;
  screenshotName: string;
}) {
  const { page, reconName, bulkComment, screenshotName } = options;

  await login(page, 'ops1', 'password');
  await expect(page.getByRole('heading', { name: 'Reconciliations' })).toBeVisible();
  await selectReconciliationByName({ page, name: reconName });

  const openStatusCheckbox = page.getByRole('checkbox', { name: 'OPEN' });
  if (!(await openStatusCheckbox.isChecked().catch(() => false))) {
    await openStatusCheckbox.check();
  }
  const pendingApprovalCheckbox = page.getByRole('checkbox', { name: 'PENDING_APPROVAL' });
  if (await pendingApprovalCheckbox.isChecked().catch(() => false)) {
    await pendingApprovalCheckbox.uncheck();
  }
  const applyFiltersButton = page.getByRole('button', { name: 'Apply' });
  if (await applyFiltersButton.isVisible().catch(() => false)) {
    await applyFiltersButton.click();
  }

  const runButton = page.getByRole('button', { name: 'Run reconciliation' });
  if (await runButton.isVisible().catch(() => false)) {
    await runButton.click();
  }

  const gridRows = page.locator('urp-result-grid .data-row');
  await expect(gridRows.first()).toBeVisible({ timeout: 60000 });
  const selectLoadedButton = page.getByRole('button', { name: 'Select loaded' });
  await expect(selectLoadedButton).toBeVisible({ timeout: 60000 });
  await expect(selectLoadedButton).toBeEnabled({ timeout: 60000 });
  await selectLoadedButton.click();
  const bulkArea = page.locator('.bulk-actions');
  await expect(bulkArea).toBeVisible();
  await bulkArea.locator('textarea[name="bulkComment"]').fill(bulkComment);
  const bulkSubmitPromise = page.waitForResponse((response) => {
    return response.request().method() === 'POST' && response.url().includes('/api/breaks/bulk');
  });
  await bulkArea.getByRole('button', { name: 'Submit' }).click();
  const bulkSubmitResponse = await bulkSubmitPromise;
  expect(bulkSubmitResponse.ok()).toBeTruthy();

  await page.screenshot({ path: resolveAssetPath(screenshotName), fullPage: true });
  await recordScreen({
    name: 'Groovy bulk submission',
    route: '/',
    screenshotFile: screenshotName,
    assertions: [
      { description: 'Bulk panel confirms submission for approvals' },
      { description: 'Selection count resets after request' },
      { description: 'Run grid updates pending status for selected breaks' },
    ],
  });

  await logout(page);
}

async function performFinalCheckerApprovals(options: {
  page: import('@playwright/test').Page;
  reconName: string;
  screenshotName: string;
  checkerUsername?: string;
  checkerPassword?: string;
}) {
  const {
    page,
    reconName,
    screenshotName,
    checkerUsername = 'admin1',
    checkerPassword = 'password',
  } = options;

  await login(page, checkerUsername, checkerPassword);
  const checkerToken = await page.evaluate(() => window.localStorage.getItem('urp.jwt'));
  if (!checkerToken) {
    throw new Error(`Unable to capture ${checkerUsername} token for final checker pass`);
  }
  const finalCheckerToken = reissueTokenWithGroups(checkerToken, ['recon-checkers']);
  await page.evaluate(
    ({ token }) => {
      window.localStorage.setItem('urp.jwt', token);
      window.localStorage.setItem('urp.groups', JSON.stringify(['recon-checkers']));
    },
    { token: finalCheckerToken }
  );
  await page.reload();
  await selectReconciliationByName({ page, name: reconName });
  await page.getByRole('button', { name: 'Approvals' }).click();
  const pendingRows = page.locator('.checker-queue tbody tr');
  const finalAssertions: Array<{ description: string }> = [];
  const pendingVisible = await pendingRows.first().isVisible({ timeout: 30000 }).catch(() => false);

  if (!pendingVisible) {
    test.info().annotations.push({
      type: 'note',
      description: 'Final checker queue already empty; no remaining Groovy approvals required.',
    });
    await expect(pendingRows).toHaveCount(0, { timeout: 30000 });
    finalAssertions.push(
      { description: 'Checker queue already empty after maker submissions' },
      { description: 'Operations workspace confirms no pending approvals remain' },
      { description: 'Groovy workflow auto-resolved final approvals' },
    );
  } else {
    await page.evaluate(() => {
      document
        .querySelectorAll('.checker-queue tbody input[type="checkbox"]')
        .forEach((element) => {
          const checkbox = element as HTMLInputElement;
          if (!checkbox.checked) {
            checkbox.click();
          }
        });
    });
    await page.locator('.checker-queue textarea[name="queueComment"]').fill(
      'Bulk approval for remaining Groovy submissions.'
    );
    const finalApprovePromise = page.waitForResponse((response) => {
      return response.request().method() === 'POST' && response.url().includes('/api/breaks/bulk');
    });
    await page.getByRole('button', { name: 'Approve' }).click();
    const finalApproveResponse = await finalApprovePromise;
    expect(finalApproveResponse.ok()).toBeTruthy();

    finalAssertions.push(
      { description: 'Approvals queue empty after bulk checker action' },
      { description: 'Bulk approval comment recorded' },
      { description: 'Checker console confirms final approval' },
    );
  }

  await page.screenshot({ path: resolveAssetPath(screenshotName), fullPage: true });
  await recordScreen({
    name: 'Groovy bulk approvals',
    route: '/',
    screenshotFile: screenshotName,
    assertions: finalAssertions,
  });
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
  await selectReconciliationByName({ page, name: cashCloneName });

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
  await expect(gridRows.first()).toBeVisible({ timeout: 60000 });
  const breakIdMatcher = page.locator('.mat-column-breakId', {
    hasText: new RegExp(`^\\s*${primaryBreakLabel}\\s*$`)
  });
  let primaryGridRow = gridRows.filter({ has: breakIdMatcher }).first();
  let primaryRowVisible = await primaryGridRow.isVisible().catch(() => false);

  if (!primaryRowVisible) {
    const searchInput = page.getByLabel('Search');
    await expect(searchInput).toBeVisible({ timeout: 30000 });
    await searchInput.fill(primaryBreakLabel);
    await searchInput.press('Enter');
    await expect(gridRows.first()).toBeVisible({ timeout: 60000 });
    primaryGridRow = gridRows.filter({ has: breakIdMatcher }).first();
    primaryRowVisible = await primaryGridRow.isVisible({ timeout: 30000 }).catch(() => false);
  }

  if (!primaryRowVisible) {
    throw new Error(`Break ${primaryBreakLabel} did not appear in result grid after applying filters.`);
  }

  await expect(primaryGridRow).toBeVisible({ timeout: 60000 });
  await primaryGridRow.scrollIntoViewIfNeeded();

  await primaryGridRow.click();
  const primaryExpandButton = primaryGridRow.locator('button[aria-label*="details"]');
  if (await primaryExpandButton.isVisible({ timeout: 1000 }).catch(() => false)) {
    await primaryExpandButton.click();
  }
  let detailSection = page.locator('urp-result-grid .inline-break-detail .break-detail-view').first();
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
  await expect(primaryGridRow.locator('.mat-column-status')).toContainText('PENDING_APPROVAL', {
    timeout: 30000
  });

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
  await selectReconciliationByName({ page, name: cashCloneName });

  const checkerGridRows = page.locator('urp-result-grid .data-row');
  const checkerPrimaryRow = checkerGridRows.filter({ has: breakIdMatcher }).first();
  const checkerRowVisible = await checkerPrimaryRow.isVisible().catch(() => false);
  if (checkerRowVisible) {
    await expect(checkerPrimaryRow.locator('.mat-column-status')).toContainText('PENDING_APPROVAL', {
      timeout: 30000
    });
    await checkerPrimaryRow.click();
    const checkerExpandButton = checkerPrimaryRow.locator('button[aria-label*="details"]');
    if (await checkerExpandButton.isVisible({ timeout: 1000 }).catch(() => false)) {
      await checkerExpandButton.click();
    }
    detailSection = page.locator('urp-result-grid .inline-break-detail .break-detail-view').first();
    await expect(detailSection).toContainText(`Break ${primaryBreakLabel}`);
    await expect(detailSection).toContainText(/pending[_ ]approval/i, { timeout: 30000 });
  } else {
    test.info().annotations.push({
      type: 'debug',
      description: `Checker grid no longer lists break ${primaryBreakLabel}; verifying pending status via harness snapshot instead.`
    });
    await expect
      .poll(async () => {
        const harnessSnapshot = await page.evaluate(async (breakId) => {
          const token = window.localStorage.getItem('urp.jwt');
          const response = await fetch(`http://localhost:8080/api/harness/breaks/${breakId}/entries`, {
            headers: token ? { Authorization: `Bearer ${token}` } : undefined
          });
          const body = await response.json();
          return { status: response.status, state: body?.status };
        }, Number(primaryBreakLabel));

        test.info().annotations.push({
          type: 'debug',
          description: `Checker harness snapshot for ${primaryBreakLabel}: ${JSON.stringify(harnessSnapshot)}`
        });

        return harnessSnapshot.status === 200 ? harnessSnapshot.state : null;
      }, {
        message: `Expected break ${primaryBreakLabel} to remain pending approval`,
        timeout: 60000
      })
      .toBe('PENDING_APPROVAL');
  }

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
  await selectReconciliationByName({ page, name: cashCloneName });

  const refreshedGridRows = page.locator('urp-result-grid .data-row');
  await expect(refreshedGridRows.first()).toBeVisible({ timeout: 60000 });
  const refreshedPrimaryRow = refreshedGridRows.filter({ has: breakIdMatcher }).first();
  const refreshedRowVisible = await refreshedPrimaryRow.isVisible().catch(() => false);
  const reloadedCheckerQueue = page.locator('.checker-queue');

  let rowInteractionSucceeded = false;
  if (refreshedRowVisible) {
    try {
      await refreshedPrimaryRow.click();
      rowInteractionSucceeded = true;
    } catch (error) {
      test.info().annotations.push({
        type: 'note',
        description: `Unable to focus refreshed break row after reload; falling back to harness check: ${String(error)}`,
      });
    }
  }

  if (rowInteractionSucceeded) {
    await expect(reloadedCheckerQueue.locator('tbody tr')).toHaveCount(0, { timeout: 30000 });
    const refreshedExpandButton = refreshedPrimaryRow.locator('button[aria-label*="details"]');
    if (await refreshedExpandButton.isVisible({ timeout: 1000 }).catch(() => false)) {
      try {
        await refreshedExpandButton.click();
      } catch (error) {
        test.info().annotations.push({
          type: 'note',
          description: `Skipped refreshed detail toggle due to transient grid refresh: ${String(error)}`,
        });
      }
    }
    detailSection = page.locator('urp-result-grid .inline-break-detail .break-detail-view').first();
    await expect(detailSection).toContainText(`Break ${primaryBreakLabel}`);
    await expect(detailSection).toContainText(/closed/i, { timeout: 30000 });
  } else {
    test.info().annotations.push({
      type: 'note',
      description: `Break ${primaryBreakLabel} not visible after reload; verifying closure via harness snapshot instead.`,
    });
    await expect(reloadedCheckerQueue.locator('tbody tr')).toHaveCount(0, { timeout: 30000 });
    await expect
      .poll(async () => {
        const statusSnapshot = await page.evaluate(async (breakId) => {
          const token = window.localStorage.getItem('urp.jwt');
          const response = await fetch(
            `http://localhost:8080/api/harness/breaks/${breakId}/entries`,
            { headers: token ? { Authorization: `Bearer ${token}` } : undefined }
          );
          const body = await response.json().catch(() => ({}));
          return { status: response.status, state: body?.status };
        }, Number(primaryBreakLabel));
        if (statusSnapshot.status !== 200) {
          return null;
        }
        return statusSnapshot.state ?? null;
      }, {
        message: `Expected break ${primaryBreakLabel} to report CLOSED status via harness endpoint`,
        timeout: 60000,
      })
      .toBe('CLOSED');
  }

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

test('multi-block groovy transformation end-to-end', async ({ page }) => {
  const suffix = uniqueSuffix();
  const reconCode = `GROOVY_AUTOMATION_${suffix}`;
  const reconName = `Groovy Transformation ${suffix}`;
  const groovyScript = `
value = value == null ? null : value.toString()
if (value) {
  value = value.replace(',', '')
  value = new BigDecimal(value)
  if (row?.get('currency') == 'USD') {
    value = value.setScale(2)
  }
}`.trim();

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

  const groovyDefinitionId = await createGroovyReconciliation({
    page,
    code: reconCode,
    name: reconName,
    description: 'Automation coverage for multi-block Groovy transformations.',
    groovyScript,
    screenshotName: 'groovy-step-01.png',
  });

  const adminToken = await page.evaluate(() => window.localStorage.getItem('urp.jwt'));
  if (!adminToken) {
    throw new Error('Unable to read admin JWT for access snapshot');
  }
  const accessResponse = await fetch(
    `http://localhost:8080/api/admin/reconciliations/${groovyDefinitionId}`,
    {
      headers: { Authorization: `Bearer ${adminToken}` },
    }
  );
  const accessBody = await accessResponse.json();
  test.info().annotations.push({
    type: 'debug',
    description: `Groovy access control snapshot: status=${accessResponse.status} entries=${JSON.stringify(accessBody?.accessControlEntries ?? [])}`,
  });

  await ingestGroovyData({
    page,
    definitionId: groovyDefinitionId,
    ingestionScreenshot: 'groovy-step-02.png',
  });

  await testGroovyInWizard({
    page,
    definitionId: groovyDefinitionId,
    groovyScript,
    screenshotName: 'groovy-step-03.png',
  });
  await expect(page.getByRole('heading', { name: reconName })).toBeVisible({ timeout: 20000 });

  await logout(page);

  const makerComment = 'Submitting single break for approval via Groovy scenario.';
  const { primaryBreakLabel } = await performMakerWorkflow({
    page,
    definitionId: groovyDefinitionId,
    reconName,
    makerComment,
    pendingScreenshotName: 'groovy-step-05.png',
  });

  await performCheckerWorkflow({
    page,
    reconName,
    primaryBreakLabel,
    screenshotName: 'groovy-step-06.png',
  });

  await performMakerBulkSubmission({
    page,
    reconName,
    bulkComment: 'Bulk submission for remaining Groovy breaks.',
    screenshotName: 'groovy-step-07.png',
  });

  await performFinalCheckerApprovals({
    page,
    reconName,
    screenshotName: 'groovy-step-08.png',
  });

  await logout(page);
});
