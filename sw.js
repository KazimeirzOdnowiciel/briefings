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
