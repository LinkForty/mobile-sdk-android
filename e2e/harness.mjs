// Scenario runner shared by the iOS and Android harnesses: the test context
// (links, clicks, launches, commands, database checks) and the report.

import { execFileSync } from 'node:child_process';
import { createServer, request as httpRequest } from 'node:http';
import { writeFileSync } from 'node:fs';
import { scenarios, wait } from './scenarios.mjs';

export function readEnv() {
  const env = {
    baseURL: (process.env.LF_E2E_BASE_URL ?? 'http://localhost:3000').replace(/\/$/, ''),
    apiKey: process.env.LF_E2E_API_KEY,
    appToken: process.env.LF_E2E_APP_TOKEN,
    organicAppToken: process.env.LF_E2E_ORGANIC_APP_TOKEN,
    templateId: process.env.LF_E2E_TEMPLATE_ID,
    templateSlug: process.env.LF_E2E_TEMPLATE_SLUG,
    databaseURL: process.env.LF_E2E_DATABASE_URL,
    // A port nothing listens on, for the offline scenario.
    unreachableURL: process.env.LF_E2E_UNREACHABLE_URL ?? 'http://localhost:9',
    only: process.env.LF_E2E_ONLY,
    proxyPort: Number(process.env.LF_E2E_PROXY_PORT ?? 3999),
  };
  return env;
}

/**
 * The device reaches the server through this proxy, which stamps every request
 * with `X-Forwarded-For`. Attribution ignores loopback and private addresses, so
 * a simulator talking to a local server would never match a click to an install;
 * each scenario gets its own public-looking address instead, which also keeps one
 * scenario's clicks from matching another's install. The server must trust the
 * header (Fastify `trustProxy`, or Core's `TRUSTED_CLIENT_IP_HEADER`).
 */
function startProxy(env) {
  const target = new URL(env.baseURL);
  const proxy = { ip: '203.0.113.10' };
  const server = createServer((req, res) => {
    const upstream = httpRequest({
      hostname: target.hostname,
      port: target.port,
      path: req.url,
      method: req.method,
      headers: { ...req.headers, host: target.host, 'x-forwarded-for': proxy.ip },
    }, (response) => {
      res.writeHead(response.statusCode, response.headers);
      response.pipe(res);
    });
    upstream.on('error', () => res.writeHead(502).end());
    req.pipe(upstream);
  });
  server.listen(env.proxyPort);
  proxy.close = () => server.close();
  proxy.url = `http://localhost:${env.proxyPort}`;
  return proxy;
}

class Server {
  constructor(env) {
    this.env = env;
  }

  async request(path, init = {}) {
    const response = await fetch(`${this.env.baseURL}${path}`, {
      ...init,
      headers: {
        'content-type': 'application/json',
        ...(this.env.apiKey && { authorization: `Bearer ${this.env.apiKey}` }),
        ...init.headers,
      },
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(`${init.method ?? 'GET'} ${path} → ${response.status} ${JSON.stringify(body)}`);
    return body;
  }

  /** Fills in the template from the server's default when none is configured. */
  async discoverTemplate() {
    if (this.env.templateId || !this.env.apiKey) return;
    const body = await this.request('/api/templates').catch(() => null);
    const templates = Array.isArray(body) ? body : body?.templates ?? [];
    const template = templates.find((x) => x.isDefault ?? x.is_default) ?? templates[0];
    if (template) {
      this.env.templateId = template.id;
      this.env.templateSlug = template.slug;
    }
  }

  async createLink(fields) {
    const body = await this.request('/api/links', {
      method: 'POST',
      body: JSON.stringify({
        title: 'e2e',
        originalUrl: 'https://example.com/e2e',
        iosAppStoreUrl: 'https://apps.apple.com/app/id000000000',
        androidAppStoreUrl: 'https://play.google.com/store/apps/details?id=com.linkforty.e2e',
        ...(this.env.templateId && { templateId: this.env.templateId }),
        ...fields,
      }),
    });
    const shortCode = body.shortCode ?? body.short_code;
    const path = this.env.templateSlug ? `${this.env.templateSlug}/${shortCode}` : shortCode;
    const url = `${this.env.deviceBaseURL}/${path}`;
    return { id: body.id, shortCode, clickUrl: url, appUrl: url };
  }

  async resolve(shortCode) {
    if (!shortCode) return null;
    const path = this.env.templateSlug ? `${this.env.templateSlug}/${shortCode}` : shortCode;
    return this.request(`/api/sdk/v1/resolve/${path}`).catch(() => this.request(`/api/sdk/v1/resolve/${shortCode}`))
      .catch(() => null);
  }
}

/** The log entries written during one step, with lookups by event name. */
class Entries {
  constructor(list) {
    this.list = list;
  }
  all(event) {
    return this.list.filter((e) => e.event === event);
  }
  one(event) {
    return this.all(event)[0];
  }
}

class Context {
  constructor({ env, device, server, scenario, proxy }) {
    Object.assign(this, { env, device, server, scenario, proxy });
    this.results = [];
    this.seen = 0;
  }

  q(value) {
    return value == null ? 'NULL' : `'${String(value).replace(/'/g, "''")}'`;
  }

  sql(query) {
    return execFileSync('psql', [this.env.databaseURL, '-Atq', '-c', query], { encoding: 'utf8' }).trim();
  }
  async value(query) {
    return this.sql(query) || null;
  }
  async count(query) {
    return Number(this.sql(query));
  }
  async installsSinceStart() {
    return this.count(`SELECT count(*) FROM install_events WHERE created_at BETWEEN ${this.q(this.startedAt)}::timestamp AND now() AT TIME ZONE 'utc' AND sdk_name = ${this.q(this.device.sdkName)}`);
  }

  check(name, ok, detail) {
    this.results.push({ name, status: ok ? 'pass' : 'fail', detail: ok ? undefined : detail });
  }

  async dbCheck(name, predicate, describe) {
    if (!this.env.databaseURL) {
      this.results.push({ name, status: 'skip', detail: 'LF_E2E_DATABASE_URL not set' });
      return;
    }
    const ok = await predicate();
    this.check(name, ok, ok ? undefined : await describe?.());
  }

  async begin() {
    this.proxy.ip = `203.0.${Math.floor(Math.random() * 250) + 1}.${Math.floor(Math.random() * 250) + 1}`;
    if (this.env.databaseURL) this.startedAt = this.sql(`SELECT (now() AT TIME ZONE 'utc')::text`);
  }

  // -- Server --

  link(fields) {
    return this.server.createLink(fields);
  }

  /** Opens the link in the device's browser, as a user tapping it would. */
  async click(link) {
    await this.device.openInBrowser(link.clickUrl);
    // A browser starting cold can take several seconds to load the link; with a
    // database, wait for the click itself.
    const recorded = async () => this.env.databaseURL
      && (await this.count(`SELECT count(*) FROM click_events WHERE link_id = ${this.q(link.id)}`)) > 0;
    const deadline = Date.now() + 20000;
    await wait(2000);
    while (this.env.databaseURL && !(await recorded()) && Date.now() < deadline) await wait(500);
    if (!this.env.databaseURL) await wait(4000);
    await this.device.closeBrowser();
  }

  // -- App --

  /** Uninstalls and reinstalls the app, then writes its config. */
  async fresh(overrides = {}) {
    await this.device.reinstall();
    await this.device.writeConfig({
      baseURL: this.env.deviceBaseURL,
      apiKey: this.env.apiKey,
      appToken: this.env.appToken,
      attributionWindowHours: 24,
      ...overrides,
    });
    this.seen = 0;
  }

  async newEntries() {
    const log = await this.device.readLog();
    const fresh = log.slice(this.seen);
    this.seen = log.length;
    return fresh;
  }

  /** Waits until `done(entries)` holds, then lets callbacks settle. */
  async collect(done, timeout = 20000, settle = 2000) {
    const collected = [];
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
      collected.push(...(await this.newEntries()));
      if (done(new Entries(collected))) break;
      await wait(300);
    }
    await wait(settle);
    collected.push(...(await this.newEntries()));
    return new Entries(collected);
  }

  /** Kills the app and launches it again; returns this launch's log. */
  async launch(args = {}, initCalls = 1) {
    await this.device.terminate();
    await this.collect(() => false, 0, 0);
    await this.device.launch(args);
    return this.collect((e) => e.all('initResult').length + e.all('initError').length >= initCalls);
  }

  /** Sends a command to the running app and waits for `until` to be logged. */
  async command(name, query, until) {
    await this.device.sendCommand({ name, query });
    return this.collect((e) => e.all(until).length > 0);
  }

  /** Launches the app with a link, as when a link cold-starts it. */
  async coldOpen(url, until) {
    await this.device.terminate();
    await this.collect(() => false, 0, 0);
    await this.device.launch({ openURL: url });
    return this.collect((e) => e.all(until).length > 0);
  }
}

export async function runScenarios({ env, device, reportPath }) {
  const proxy = startProxy(env);
  env.deviceBaseURL = proxy.url;
  const server = new Server(env);
  await server.discoverTemplate();
  const report = [];

  for (const scenario of scenarios) {
    if (env.only && !scenario.name.toLowerCase().includes(env.only.toLowerCase())) continue;
    if (scenario.platforms && !scenario.platforms.includes(device.platform)) {
      report.push({ scenario: scenario.name, results: [{ name: 'scenario', status: 'skip', detail: `not applicable on ${device.platform}` }] });
      continue;
    }
    const missing = (scenario.needs ?? []).filter((key) => !env[key]);
    if (missing.length) {
      report.push({ scenario: scenario.name, results: [{ name: 'scenario', status: 'skip', detail: `needs ${missing.join(', ')}` }] });
      continue;
    }

    const t = new Context({ env, device, server, scenario, proxy });
    process.stdout.write(`\n▶ ${scenario.name}\n`);
    try {
      await t.begin();
      await scenario.run(t);
    } catch (error) {
      t.results.push({ name: 'scenario ran to completion', status: 'fail', detail: error.message });
    }
    for (const r of t.results) {
      const mark = { pass: '✓', fail: '✗', skip: '–' }[r.status];
      process.stdout.write(`  ${mark} ${r.name}${r.detail ? `  (${r.detail})` : ''}\n`);
    }
    report.push({ scenario: scenario.name, results: t.results });
  }

  const all = report.flatMap((s) => s.results);
  const tally = (status) => all.filter((r) => r.status === status).length;
  process.stdout.write(`\n${tally('pass')} passed, ${tally('fail')} failed, ${tally('skip')} skipped\n`);
  proxy.close();
  if (reportPath) writeFileSync(reportPath, JSON.stringify({ platform: device.platform, report }, null, 2));
  return tally('fail') === 0;
}
