# End-to-end tests

Runs the SDK in a real app on an Android emulator against a real LinkForty
server. Where the unit and instrumented tests stub the server, these check what
the SDK hands an app and, given a database URL, what the server stored: one
install per installation, one row per tracked event, the deferred deep link on
the first launch only.

- `../e2e-app` is the test app. It integrates the SDK the way the README does
  (initialize from `Application.onCreate`, hand intents to `handleDeepLink` from
  `MainActivity`) and logs every callback and result to `files/lfe2e.jsonl`.
  It is minified like a release build, so the SDK's consumer R8 rules are
  exercised.
- `scenarios.mjs` holds the scenarios and their expectations, taken from the
  README, API.md and the SDK specification. The iOS SDK runs the same file.
- `harness.mjs` runs them: creates links, opens them in the emulator's browser,
  launches the app, sends it commands and reads its log.
- `run.mjs` is the Android device layer (`adb`).

## Requirements

- A LinkForty server (Core or Cloud) and an API key for it.
- The server must take the client IP from `X-Forwarded-For` (Fastify
  `trustProxy`, or Core's `TRUSTED_CLIENT_IP_HEADER=x-forwarded-for`).
  Attribution ignores loopback and private addresses, so the harness puts a
  small proxy between the device and the server that gives each scenario its
  own public-looking address.
- One emulator or device attached (`ANDROID_SERIAL` picks one), with Chrome.
  Open Chrome once by hand and dismiss its first-run screens; until then it
  never loads the link.
- Node 18 or later. `psql` on the `PATH` for the database checks.

The device reaches the proxy as `localhost` through `adb reverse`, which
`run.mjs` sets up.

## Running

```sh
export LF_E2E_BASE_URL=http://localhost:3000       # the server
export LF_E2E_API_KEY=...                          # creates the test links
export LF_E2E_APP_TOKEN=...                        # optional: scopes installs to a workspace
export LF_E2E_ORGANIC_APP_TOKEN=...                # optional: a workspace with no clicks, for the organic scenario
export LF_E2E_DATABASE_URL=postgresql://...        # optional: enables the database checks
node e2e/run.mjs
```

`run.mjs` builds the app first; pass `--no-build` to reuse the last build.

| Variable | Default | |
|---|---|---|
| `LF_E2E_BASE_URL` | `http://localhost:3000` | Server the proxy forwards to |
| `LF_E2E_API_KEY` | | Creates links; also used by `createLink` |
| `LF_E2E_APP_TOKEN` | | Sent as `appToken` |
| `LF_E2E_ORGANIC_APP_TOKEN` | | Without it the organic scenario is skipped |
| `LF_E2E_TEMPLATE_ID` / `LF_E2E_TEMPLATE_SLUG` | the server's default template | For servers whose links belong to a template |
| `LF_E2E_DATABASE_URL` | | Without it the database checks are skipped |
| `LF_E2E_PROXY_PORT` | `3999` | Port of the forwarding proxy |
| `LF_E2E_ONLY` | | Runs scenarios whose name contains this text |
| `LF_E2E_BROWSER` | `com.android.chrome` | Browser package that opens links |

Results print as they run; the full report is written to
`e2e-app/build/e2e-report.json`. The run exits non-zero if any check fails.

Each scenario uninstalls and reinstalls the app. The links and installs it
creates stay on the server.
