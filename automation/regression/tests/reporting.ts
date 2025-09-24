import { mkdir, rm, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

export interface ScreenAssertion {
  description: string;
}

export interface ScreenCoverageEntry {
  name: string;
  route: string;
  screenshot: string;
  assertions: ScreenAssertion[];
}

const reportsRoot = resolve(__dirname, '..', 'reports');
const latestRunDir = resolve(reportsRoot, 'latest');
const assetsDir = resolve(latestRunDir, 'assets');

let initialized = false;
let generatedAt = '';
const coverageEntries: ScreenCoverageEntry[] = [];

async function ensureInitialized() {
  if (initialized) {
    return;
  }

  await rm(latestRunDir, { recursive: true, force: true });
  await mkdir(assetsDir, { recursive: true });
  generatedAt = new Date().toISOString();
  initialized = true;
}

export async function prepareReport() {
  await ensureInitialized();
}

export function resolveAssetPath(fileName: string): string {
  return resolve(assetsDir, fileName);
}

export function relativeAssetPath(fileName: string): string {
  return `assets/${fileName}`;
}

export async function recordScreen(entry: Omit<ScreenCoverageEntry, 'screenshot'> & { screenshotFile: string }) {
  await ensureInitialized();

  const coverageEntry: ScreenCoverageEntry = {
    name: entry.name,
    route: entry.route,
    screenshot: relativeAssetPath(entry.screenshotFile),
    assertions: entry.assertions,
  };

  coverageEntries.push(coverageEntry);
}

function buildMarkdownReport(entries: ScreenCoverageEntry[]): string {
  const header = ['| Screen | Route | Verified checks |', '| --- | --- | --- |'];
  const rows = entries.map((entry) => {
    const checks = entry.assertions.map((assertion) => `- ${assertion.description}`).join('<br/>');
    return `| ${entry.name} | ${entry.route} | ${checks} |`;
  });

  const gallery = entries
    .map((entry) => `![${entry.name}](${entry.screenshot})`)
    .join('\n\n');

  return `# Regression Coverage Report\n\nGenerated: ${generatedAt}\n\n${[...header, ...rows].join('\n')}\n\n${gallery}\n`;
}

export async function finalizeReport() {
  if (!initialized) {
    return;
  }

  const summary = {
    generatedAt,
    screens: coverageEntries,
  };

  await writeFile(resolve(latestRunDir, 'coverage.json'), JSON.stringify(summary, null, 2));
  await writeFile(resolve(latestRunDir, 'report.md'), buildMarkdownReport(coverageEntries));
}
