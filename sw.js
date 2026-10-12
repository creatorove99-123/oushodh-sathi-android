const CACHE_NAME = 'oushodhsathi-stage4-v5';
const APP_SHELL = ['./', './index.html', './styles.css?v=stage4-5', './app.js?v=stage4-5', './reminders.js?v=stage4-5', './manifest.json?v=stage4-5', './oushodhsathi-icon.svg'];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(Promise.all([
    caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key)))),
    self.clients.claim()
  ]));
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET') return;
  const requestURL = new URL(event.request.url);
  if (requestURL.origin !== self.location.origin) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME);
    try {
      const response = await fetch(event.request);
      if (response && response.ok) await cache.put(event.request, response.clone());
      return response;
    } catch {
      const cached = await cache.match(event.request) || await caches.match(event.request);
      if (cached) return cached;
      return new Response('Offline', {status: 503, headers: {'Content-Type': 'text/plain; charset=utf-8'}});
    }
  })());
});

self.addEventListener('notificationclick', event => {
  const notification = event.notification;
  const data = notification.data || {};
  const action = event.action;
  notification.close();
  event.waitUntil((async () => {
    const payload = {medicineId: data.medicineId, date: data.date, time: data.time, action};
    const isDoseAction = data.kind === 'dose' && ['taken', 'snooze'].includes(action);
    const windows = await self.clients.matchAll({type: 'window', includeUncontrolled: true});
    const appWindow = windows.find(client => client.url.startsWith(self.registration.scope));
    if (appWindow) {
      await appWindow.focus();
      if (isDoseAction) appWindow.postMessage({type: 'oushodhsathi-notification-action', payload});
      return;
    }
    const url = new URL(self.registration.scope);
    if (isDoseAction) url.hash = new URLSearchParams({doseAction: JSON.stringify(payload)}).toString();
    await self.clients.openWindow(url.href);
  })());
});
