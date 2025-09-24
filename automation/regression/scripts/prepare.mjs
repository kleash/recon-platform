import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const currentDir = fileURLToPath(new URL('.', import.meta.url));
const automationDir = resolve(currentDir, '..');
const rootDir = resolve(automationDir, '..', '..');
const backendDir = resolve(rootDir, 'backend');
const frontendDir = resolve(rootDir, 'frontend');
const mvnwPath = resolve(backendDir, 'mvnw');
const backendPom = resolve(backendDir, 'pom.xml');
const npmCommand = process.env.CI ? 'ci' : 'install';

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    stdio: 'inherit',
    ...options
  });

  if (result.status !== 0) {
    throw new Error(`Command failed: ${command} ${args.join(' ')}`);
  }
}

console.log('> Building backend application');
run(mvnwPath, ['-f', backendPom, 'clean', 'package', '-DskipTests'], { cwd: backendDir });

console.log('> Installing frontend dependencies');
run('npm', [npmCommand, '--no-fund', '--no-audit'], { cwd: frontendDir });

console.log('> Building frontend bundle');
run('npm', ['run', 'build'], { cwd: frontendDir });

console.log('> Ensuring Playwright browsers are installed');
run('npx', ['--yes', 'playwright', 'install', '--with-deps', 'chromium'], { cwd: automationDir });

console.log('> Environment ready for regression tests');
