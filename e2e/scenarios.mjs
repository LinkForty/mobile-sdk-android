// End-to-end scenarios shared by the iOS and Android harnesses.
//
// Each scenario drives the test app on a simulator or emulator against a real
// LinkForty server and checks two things: what the SDK handed the app (read from
// the app's event log) and, when a database URL is given, what the server stored.
// The expectations are the documented behavior: README, API.md and the SDK spec.

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

export const scenarios = [
  {
    name: 'Attributed install delivers the deferred deep link on the first launch only',
    async run(t) {
      const link = await t.link({
        deepLinkPath: '/product/42',
        deepLinkParameters: { productId: '42', ref: 'e2e' },
      });
      await t.fresh();
      await t.click(link);
      await t.dbCheck('the browser click is recorded', async () =>
        (await t.count(`SELECT count(*) FROM click_events WHERE link_id = ${t.q(link.id)}`)) >= 1);

      const first = await t.launch();
      const init = first.one('initResult');
      const deferred = first.all('deferred');
      t.check('isFirstLaunch() is true before initialize', first.one('isFirstLaunchBeforeInit')?.value === true);
      t.check('initialize succeeds', !!init, first.one('initError')?.error);
      t.check('the install is attributed', init?.attributed === true);
      t.check('onDeferredDeepLink fires once', deferred.length === 1, `fired ${deferred.length} times`);
      const data = deferred[0]?.deepLinkData;
      t.check('the deferred deep link is the clicked link', data?.shortCode === link.shortCode,
        `got ${data?.shortCode}, clicked ${link.shortCode}`);
      t.check('deep link parameters arrive', data?.customParameters?.productId === '42',
        JSON.stringify(data?.customParameters));
      t.check('deepLinkPath arrives', data?.deepLinkPath === '/product/42', data?.deepLinkPath);
      t.check('clickedAt is set', !!data?.clickedAt, JSON.stringify(data));
      t.check('the callback runs on the main thread', deferred[0]?.thread === 'main', deferred[0]?.thread);
      t.check('isFirstLaunch() stays true after initialize', first.one('isFirstLaunchAfterInit')?.value === true);
      await t.dbCheck('the install is stored against the clicked link', async () =>
        (await t.value(`SELECT link_id FROM install_events WHERE id = ${t.q(init?.installId)}`)) === link.id);
      await t.dbCheck('the configured attribution window (24h) is sent', async () =>
        (await t.value(`SELECT attribution_window_hours FROM install_events WHERE id = ${t.q(init?.installId)}`)) === '24',
      async () => `stored ${await t.value(`SELECT attribution_window_hours FROM install_events WHERE id = ${t.q(init?.installId)}`)}`);

      for (let n = 2; n <= 4; n++) {
        const later = await t.launch();
        const laterInit = later.one('initResult');
        t.check(`launch ${n}: isFirstLaunch() is false`, later.one('isFirstLaunchBeforeInit')?.value === false);
        t.check(`launch ${n}: same install id`, laterInit?.installId === init?.installId,
          `${laterInit?.installId} vs ${init?.installId}`);
        t.check(`launch ${n}: no deferred deep link`, later.all('deferred').length === 0,
          `fired ${later.all('deferred').length} times`);
      }
      await t.dbCheck('four launches store one install', async () => (await t.installsSinceStart()) === 1,
        async () => `${await t.installsSinceStart()} installs stored`);
    },
  },

  {
    name: 'Organic install delivers null to onDeferredDeepLink',
    needs: ['organicAppToken'],
    async run(t) {
      await t.fresh({ appToken: t.env.organicAppToken });
      const launch = await t.launch();
      t.check('initialize succeeds', !!launch.one('initResult'), launch.one('initError')?.error);
      t.check('the install is not attributed', launch.one('initResult')?.attributed === false);
      const deferred = launch.all('deferred');
      t.check('onDeferredDeepLink fires once', deferred.length === 1, `fired ${deferred.length} times`);
      t.check('it receives null', deferred.length === 1 && deferred[0].deepLinkData === null);
    },
  },

  {
    name: 'Callbacks registered before initialize receive the deferred deep link',
    async run(t) {
      const link = await t.link({ deepLinkParameters: { productId: '7' } });
      await t.fresh();
      await t.click(link);
      const launch = await t.launch({ order: 'before' });
      t.check('the install is attributed', launch.one('initResult')?.attributed === true);
      const deferred = launch.all('deferred');
      t.check('onDeferredDeepLink fires once', deferred.length === 1, `fired ${deferred.length} times`);
      t.check('with the clicked link', deferred[0]?.deepLinkData?.shortCode === link.shortCode);
    },
  },

  {
    name: 'Deep link opened while the app is running',
    async run(t) {
      const link = await t.link({ deepLinkPath: '/product/9', deepLinkParameters: { productId: '9' } });
      await t.fresh();
      await t.launch();
      const entries = await t.command('handle', { url: `${link.appUrl}?campaign=spring` }, 'deepLink');
      const opened = entries.all('deepLink');
      const data = opened[0]?.deepLinkData;
      t.check('onDeepLink fires once', opened.length === 1, `fired ${opened.length} times`);
      t.check('with the link', data?.shortCode === link.shortCode, data?.shortCode);
      t.check('resolved by the server (linkId set)', data?.linkId === link.id, data?.linkId);
      t.check('deepLinkPath arrives', data?.deepLinkPath === '/product/9', data?.deepLinkPath);
      t.check('stored parameters arrive', data?.customParameters?.productId === '9');
      t.check('parameters on the tapped URL arrive', data?.customParameters?.campaign === 'spring');
      t.check('the callback runs on the main thread', opened[0]?.thread === 'main', opened[0]?.thread);
    },
  },

  {
    name: 'Deep link that cold-starts the app',
    async run(t) {
      const link = await t.link({ deepLinkParameters: { productId: '11' } });
      await t.fresh();
      await t.launch();
      const entries = await t.coldOpen(link.appUrl, 'deepLink');
      const opened = entries.all('deepLink');
      t.check('onDeepLink fires once', opened.length === 1, `fired ${opened.length} times`);
      t.check('with the link', opened[0]?.deepLinkData?.shortCode === link.shortCode);
    },
  },

  {
    name: 'Events are stored once each',
    async run(t) {
      await t.fresh();
      const launch = await t.launch();
      const installId = launch.one('initResult')?.installId;
      const tracked = await t.command('track', { name: 'e2e_event', count: '3' }, 'queued');
      const results = tracked.all('trackResult');
      t.check('trackEvent succeeds', results.length === 3 && results.every((r) => r.ok),
        results.map((r) => r.error ?? 'ok').join(' | '));
      const revenue = await t.command('revenue', { amount: '9.99' }, 'queued');
      t.check('trackRevenue succeeds', revenue.one('revenueResult')?.ok === true, revenue.one('revenueResult')?.error);
      await t.command('flush', {}, 'queued');
      const flushed = await t.command('flush', {}, 'queued');
      t.check('nothing is left queued', flushed.one('queued')?.count === 0, `${flushed.one('queued')?.count} queued`);
      await t.dbCheck('4 events tracked, 4 stored', async () =>
        (await t.count(`SELECT count(*) FROM in_app_events WHERE install_id = ${t.q(installId)}`)) === 4,
      async () => `${await t.count(`SELECT count(*) FROM in_app_events WHERE install_id = ${t.q(installId)}`)} stored`);
    },
  },

  {
    name: 'A first launch that fails offline recovers in the same session',
    async run(t) {
      await t.fresh();
      const offline = await t.launch({ baseURL: t.env.unreachableURL });
      t.check('initialize fails while the server is unreachable', !!offline.one('initError'));
      const retry = await t.command('reinit', { baseURL: t.env.deviceBaseURL }, 'isFirstLaunchAfterInit');
      t.check('a retry of initialize succeeds', !!retry.one('initResult'), retry.one('initError')?.error);
      const tracked = await t.command('track', { name: 'after_retry' }, 'queued');
      t.check('events work after the retry', tracked.one('trackResult')?.ok === true, tracked.one('trackResult')?.error);
      await t.launch();
      await t.dbCheck('one install stored', async () => (await t.installsSinceStart()) === 1,
        async () => `${await t.installsSinceStart()} installs stored`);
    },
  },

  {
    name: 'Two concurrent initialize calls report one install',
    async run(t) {
      await t.fresh();
      const launch = await t.launch({ order: 'concurrent' }, 2);
      t.check('one call succeeds', launch.all('initResult').length >= 1, launch.all('initError').map((e) => e.error).join(' | '));
      await t.dbCheck('one install stored', async () => (await t.installsSinceStart()) === 1,
        async () => `${await t.installsSinceStart()} installs stored`);
    },
  },

  {
    name: 'createLink returns a working link and deduplicates per user',
    needs: ['apiKey'],
    async run(t) {
      await t.fresh();
      await t.launch();
      const user = `e2e-user-${Date.now()}`;
      const query = { externalUserId: user, params: JSON.stringify({ productId: '77' }), title: 'e2e share' };
      const first = (await t.command('createLink', query, 'createLinkResult')).one('createLinkResult');
      t.check('createLink succeeds', first?.ok === true, first?.error);
      t.check('it returns a URL, short code and link id', !!(first?.url && first?.shortCode && first?.linkId));
      const again = (await t.command('createLink', query, 'createLinkResult')).one('createLinkResult');
      t.check('the same user and parameters return the same link', again?.shortCode === first?.shortCode,
        `${again?.shortCode} vs ${first?.shortCode}`);
      t.check('marked deduplicated', again?.deduplicated === true, String(again?.deduplicated));
      const resolved = await t.server.resolve(first?.shortCode);
      t.check('the link resolves with its parameters', resolved?.customParameters?.productId === '77',
        JSON.stringify(resolved));

      if (t.env.templateId) {
        const withTemplate = (await t.command('createLink', { ...query, templateId: t.env.templateId,
          templateSlug: t.env.templateSlug ?? '' }, 'createLinkResult')).one('createLinkResult');
        t.check('createLink with a templateId succeeds', withTemplate?.ok === true, withTemplate?.error);
        const withTemplateAgain = (await t.command('createLink', { ...query, templateId: t.env.templateId,
          templateSlug: t.env.templateSlug ?? '' }, 'createLinkResult')).one('createLinkResult');
        t.check('with a templateId, the same user and parameters return the same link',
          withTemplateAgain?.shortCode === withTemplate?.shortCode && withTemplateAgain?.deduplicated === true,
          `${withTemplateAgain?.shortCode} vs ${withTemplate?.shortCode}, deduplicated ${withTemplateAgain?.deduplicated}`);
      }
    },
  },

  {
    name: 'Deep link parameters that are not strings',
    async run(t) {
      const link = await t.link({ deepLinkParameters: { productId: 42, premium: true } });
      await t.fresh();
      await t.click(link);
      const launch = await t.launch();
      t.check('the install is attributed', launch.one('initResult')?.attributed === true);
      const deferred = launch.all('deferred');
      t.check('onDeferredDeepLink fires once', deferred.length === 1, `fired ${deferred.length} times`);
      t.check('the deferred deep link carries the parameters',
        deferred[0]?.deepLinkData?.customParameters?.productId != null, JSON.stringify(deferred[0]?.deepLinkData));
      const opened = (await t.command('handle', { url: link.appUrl }, 'deepLink')).all('deepLink');
      t.check('an open of the link is resolved by the server (linkId set)',
        opened[0]?.deepLinkData?.linkId === link.id, JSON.stringify(opened[0]?.deepLinkData));
    },
  },

  {
    name: 'clearData leaves the SDK working and the next launch is a first launch',
    async run(t) {
      const link = await t.link({ deepLinkParameters: { productId: '5' } });
      await t.fresh();
      await t.launch();
      await t.command('clearData', {}, 'cleared');
      const opened = (await t.command('handle', { url: link.appUrl }, 'deepLink')).all('deepLink');
      t.check('onDeepLink still fires after clearData', opened.length === 1, `fired ${opened.length} times`);
      const next = await t.launch();
      t.check('the next launch is a first launch', next.one('isFirstLaunchBeforeInit')?.value === true);
      await t.dbCheck('the next launch reports a new install', async () => (await t.installsSinceStart()) === 2,
        async () => `${await t.installsSinceStart()} installs stored`);
    },
  },
];

export { wait };
