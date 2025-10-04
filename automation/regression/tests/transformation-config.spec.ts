import { expect, test } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

type SourcePlan = {
  code: string;
  transformationPlan?: {
    datasetGroovyScript?: string | null;
    rowOperations?: Array<{ type?: string | null }> | null;
    columnOperations?: Array<{ type?: string | null }> | null;
  } | null;
};

type PayloadDefinition = {
  code: string;
  sources?: SourcePlan[];
};

const repoRoot = resolve(__dirname, '..', '..', '..');

const PAYLOADS: Array<{ label: string; path: string }> = [
  {
    label: 'historical seed',
    path: resolve(repoRoot, 'scripts', 'seed-historical', 'payloads', 'global_multi_asset_history.json'),
  },
  {
    label: 'integration harness',
    path: resolve(repoRoot, 'examples', 'integration-harness', 'payloads', 'global-multi-asset.json'),
  },
];

const hasEntries = (value: unknown[]): value is unknown[] => Array.isArray(value) && value.length > 0;

const hasText = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0;

test.describe('global multi-asset transformation payloads', () => {
  for (const payload of PAYLOADS) {
    test(`ensure ${payload.label} exposes scripts, row filters, and column operations`, async () => {
      const raw = await readFile(payload.path, 'utf8');
      const definition = JSON.parse(raw) as PayloadDefinition;
      expect(definition.code, `payload ${payload.path} must define a reconciliation code`).toBeTruthy();
      expect(Array.isArray(definition.sources) && definition.sources.length).toBeGreaterThan(0);

      for (const source of definition.sources ?? []) {
        expect(source.code, `source missing code in ${payload.label}`).toBeTruthy();
        const plan = source.transformationPlan;
        expect(plan, `transformation plan absent for ${source.code} in ${payload.label}`).toBeTruthy();

        expect(
          hasText(plan?.datasetGroovyScript ?? ''),
          `dataset script missing for ${source.code} in ${payload.label}`,
        ).toBeTruthy();

        expect(
          hasEntries(plan?.rowOperations ?? []),
          `row operations missing for ${source.code} in ${payload.label}`,
        ).toBeTruthy();
        expect(
          (plan?.rowOperations ?? []).every((operation) => hasText(operation?.type ?? '')),
          `row operation type missing for ${source.code} in ${payload.label}`,
        ).toBeTruthy();

        expect(
          hasEntries(plan?.columnOperations ?? []),
          `column operations missing for ${source.code} in ${payload.label}`,
        ).toBeTruthy();
        expect(
          (plan?.columnOperations ?? []).every((operation) => hasText(operation?.type ?? '')),
          `column operation type missing for ${source.code} in ${payload.label}`,
        ).toBeTruthy();
      }
    });
  }
});
