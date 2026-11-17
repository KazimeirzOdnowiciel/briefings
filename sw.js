/* Service worker for the briefing site and the app in ./app/.
   - App shell is precached so the installed app opens instantly and offline.
   - Pages, feed.json and archive.html are network-first with a short timeout,
     so a fresh edition wins but a bad signal falls back to the last copy.
   - editions/*.json are stale-while-revalidate: instant, refreshed in background.
   - Article images and fonts are cache-first, capped so storage stays small.
   - Audio is never intercepted (range requests). */
const VERSION = 'v2';
const SHELL = 'bf-shell-' + VERSION;
const PAGES = 'bf-pages';
const DATA = 'bf-data';
const IMG = 'bf-img';
const FONT = 'bf-font';
const KEEP = [SHELL, PAGES, DATA, IMG, FONT];
const IMG_MAX = 80;

const scoped = (p) => new URL(p, self.registration.scope).href;
const SHELL_FILES = ['app/', 'app/app.css', 'app/app.js', 'app/icons/icon-192.png', 'app/icons/icon-512.png', 'site.webmanifest'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL)
      .then((c) => c.addAll(SHELL_FILES.map(scoped)))
      .catch(() => {})
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => !KEEP.includes(k)).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

function networkFirst(req, cacheName, fallbackUrl, timeoutMs = 3500) {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (res) => { if (!settled && res) { settled = true; resolve(res); } };
    const fromCache = () =>
      caches.match(req).then((hit) => hit || (fallbackUrl ? caches.match(fallbackUrl) : undefined));
    const timer = setTimeout(() => { fromCache().then(finish); }, timeoutMs);
    fetch(req)
      .then((res) => {
        clearTimeout(timer);
        if (res && res.ok) {
          const copy = res.clone();
          caches.open(cacheName).then((c) => c.put(req, copy)).catch(() => {});
        }
        finish(res);
      })
      .catch(() => {
        clearTimeout(timer);
        fromCache().then((hit) => finish(hit || Response.error()));
      });
  });
}

function staleWhileRevalidate(req, cacheName) {
  return caches.open(cacheName).then((c) =>
    c.match(req).then((hit) => {
      const net = fetch(req)
        .then((res) => { if (res && res.ok) c.put(req, res.clone()).catch(() => {}); return res; })
        .catch(() => hit || Response.error());
      return hit || net;
    })
  );
}

async function trim(cacheName, max) {
  const c = await caches.open(cacheName);
  const keys = await c.keys();
  for (let i = 0; i < keys.length - max; i++) await c.delete(keys[i]);
}

function cacheFirst(req, cacheName, max) {
  return caches.open(cacheName).then((c) =>
    c.match(req).then((hit) => {
      if (hit) return hit;
      return fetch(req).then((res) => {
        if (res && (res.ok || res.type === 'opaque')) {
          c.put(req, res.clone()).then(() => (max ? trim(cacheName, max) : null)).catch(() => {});
        }
        return res;
      });
    })
  );
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  if (url.origin === self.location.origin) {
    if (url.pathname.endsWith('.mp3')) return;
    if (/\/editions\/\d{4}-\d{2}-\d{2}\.json$/.test(url.pathname)) {
      event.respondWith(staleWhileRevalidate(req, DATA));
      return;
    }
    const inApp = url.pathname.startsWith(new URL(scoped('app/')).pathname);
    if (req.mode === 'navigate') {
      event.respondWith(networkFirst(req, PAGES, inApp ? scoped('app/') : scoped('index.html')));
      return;
    }
    event.respondWith(networkFirst(req, inApp ? SHELL : PAGES));
    return;
  }

  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    event.respondWith(cacheFirst(req, FONT));
    return;
  }
  if (req.destination === 'image') {
    event.respondWith(cacheFirst(req, IMG, IMG_MAX).catch(() => Response.error()));
  }
  // everything else cross-origin (weather, GitHub API) goes straight to the network
});

/* Web push: the 08:45 canary tells us when the day's edition is live. */
self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch (e) {
    payload = { body: event.data ? event.data.text() : '' };
  }
  const title = payload.title || 'Daily Briefing';
  event.waitUntil(
    self.registration.showNotification(title, {
      body: payload.body || 'A new edition is ready.',
      icon: scoped('app/icons/icon-192.png'),
      badge: scoped('app/icons/badge-96.png'),
      tag: 'briefing',
      renotify: true,
      data: { url: payload.url || scoped('app/') },
    })
  );
});

/* Notification links written for the old pages (index.html, YYYY-MM-DD.html)
   open the matching view in the app instead. */
function appTarget(raw) {
  const u = new URL(raw || 'app/', self.registration.scope);
  const m = u.pathname.match(/(\d{4}-\d{2}-\d{2})\.html$/);
  if (m) return scoped('app/#/today/' + m[1]);
  if (/\/(index\.html)?$/.test(u.pathname) && !u.pathname.includes('/app/')) return scoped('app/');
  return u.href;
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = appTarget(event.notification.data && event.notification.data.url);
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
      for (const client of list) {
        if (client.url.indexOf(self.registration.scope) === 0 && 'focus' in client) {
          return client.focus().then((c) => (c && 'navigate' in c ? c.navigate(target) : c));
        }
      }
      return self.clients.openWindow(target);
    })
  );
});
