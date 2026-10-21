#!/usr/bin/env node
// Runs the end-to-end scenarios on an Android emulator. See e2e/README.md.

import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readEnv, runScenarios } from './harness.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const pkg = 'com.linkforty.e2e';
const apk = join(root, 'e2e-app/build/outputs/apk/debug/e2e-app-debug.apk');
const adbPath = process.env.ANDROID_HOME ? join(process.env.ANDROID_HOME, 'platform-tools/adb') : 'adb';

// ANDROID_SERIAL picks the device when more than one is attached.
const adb = (args, input, timeout = 60000) =>
  execFileSync(adbPath, args, { encoding: 'utf8', input, timeout, stdio: ['pipe', 'pipe', 'pipe'] });
const quiet = (args) => {
  try {
    return adb(args);
  } catch {
    return '';
  }
};

function build() {
  execFileSync('./gradlew', [':e2e-app:assembleDebug', '-q'], { cwd: root, stdio: 'inherit' });
}

function androidDevice(env) {
  const appFile = (name) => `files/${name}`;
  const writeFile = (name, text, append = false) =>
    adb(['exec-in', 'run-as', pkg, 'sh', '-c', `mkdir -p files && cat ${append ? '>>' : '>'} ${appFile(name)}`], text);

  return {
    platform: 'Android',
    sdkName: 'android',
    async reinstall() {
      quiet(['uninstall', pkg]);
      adb(['install', '-r', apk]);
    },
    async writeConfig(config) {
      writeFile('lfe2e-config.json', JSON.stringify(config));
    },
    async launch(args) {
      const { openURL, ...rest } = args;
      const extras = Object.entries(rest).flatMap(([k, v]) => ['--es', `lf${k[0].toUpperCase()}${k.slice(1)}`, String(v)]);
      // A link that cold-starts the app arrives as the launch intent's data,
      // as an App Link does.
      const data = openURL ? ['-a', 'android.intent.action.VIEW', '-d', openURL] : [];
      const start = ['shell', 'am', 'start', '-W', '-n', `${pkg}/.MainActivity`, ...data, ...extras];
      try {
        adb(start, undefined, 20000);
      } catch {
        // Right after a reinstall the system can kill the new process while it
        // removes the old task, and the launch never completes. Launch again.
        quiet(['shell', 'am', 'force-stop', pkg]);
        adb(start, undefined, 20000);
      }
    },
    async terminate() {
      quiet(['shell', 'am', 'force-stop', pkg]);
    },
    async sendCommand(command) {
      writeFile('lfe2e-commands.jsonl', `${JSON.stringify(command)}\n`, true);
    },
    async openInBrowser(url) {
      adb(['shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', url, env.browserPackage]);
    },
    async closeBrowser() {
      quiet(['shell', 'am', 'force-stop', env.browserPackage]);
    },
    async readLog() {
      const text = quiet(['exec-out', 'run-as', pkg, 'cat', appFile('lfe2e.jsonl')]);
      return text.split('\n').filter((line) => line.startsWith('{')).map((line) => JSON.parse(line));
    },
  };
}

const env = readEnv();
env.browserPackage = process.env.LF_E2E_BROWSER ?? 'com.android.chrome';
if (!process.argv.includes('--no-build')) build();
adb(['wait-for-device']);
// The device reaches the harness proxy on the host as localhost.
adb(['reverse', `tcp:${env.proxyPort}`, `tcp:${env.proxyPort}`]);
const ok = await runScenarios({
  env,
  device: androidDevice(env),
  reportPath: join(root, 'e2e-app/build/e2e-report.json'),
});
process.exit(ok ? 0 : 1);
