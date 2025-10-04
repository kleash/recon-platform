import { defineConfig } from '@playwright/test';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

const rootDir = resolve(__dirname, '..', '..');
const execJar = resolve(rootDir, 'backend', 'target', 'reconciliation-platform-0.1.0-exec.jar');
const bootJar = resolve(rootDir, 'backend', 'target', 'reconciliation-platform-0.1.0.jar');
const backendJar = existsSync(execJar) ? execJar : bootJar;

export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: false,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry'
  },
  webServer: [
    {
      command: `java -jar "${backendJar}"`,
      cwd: rootDir,
      env: {
        JWT_SECRET: 'MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=',
        APP_ALLOWED_ORIGINS: 'http://localhost:4200',
        SPRING_PROFILES_ACTIVE: 'example-harness'
      },
      url: 'http://localhost:8080/actuator/health',
      timeout: 180_000,
      reuseExistingServer: !process.env.CI
    },
    {
      command: 'npm run start',
      cwd: resolve(rootDir, 'frontend'),
      url: 'http://localhost:4200',
      timeout: 180_000,
      reuseExistingServer: !process.env.CI
    }
  ]
});
