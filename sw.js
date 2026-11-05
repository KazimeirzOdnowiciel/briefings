/* Service worker so the briefing installs to the home screen and opens offline.
   Network-first with a cache fallback: a fresh edition always wins, an old one
   still works in the tube with no signal. */
const CACHE = 'briefing-v1';

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((c) => c.addAll(['./index.html', './site.webmanifest', './icon-512.png']))
      .catch(() => {})
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    fetch(event.request)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(event.request, copy)).catch(() => {});
        return res;
      })
      .catch(() =>
        caches.match(event.request).then((hit) => hit || caches.match('./index.html'))
      )
  );
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
      icon: './icon-192.png',
      badge: './icon-192.png',
      tag: 'briefing',
      renotify: true,
      data: { url: payload.url || './index.html' },
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || './index.html';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
      for (const client of list) {
        if (client.url.indexOf(self.registration.scope) === 0) return client.focus();
      }
      return self.clients.openWindow(url);
    })
  );
});
