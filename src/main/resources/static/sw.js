/*
 * Servis calisani: uygulamanin ana ekrana kurulabilmesi ve internet kesildiginde
 * en azindan kabugun acilabilmesi icin.
 *
 * Strateji bilerek "once ag": her istek once sunucudan denenir, sadece
 * baglanti yoksa onbellege dusulur. Boylece gelistirirken eski dosya takilip
 * kalmaz - yangin verisi de her zaman tazedir.
 */
const ONBELLEK = 'yanginradar-v1';

const KABUK = [
  '/',
  '/index.html',
  '/styles.css',
  '/app.js',
  '/manifest.json',
  '/icon-192.png',
  '/icon-512.png'
];

self.addEventListener('install', (olay) => {
  olay.waitUntil(
    caches.open(ONBELLEK)
      .then((c) => c.addAll(KABUK))
      .then(() => self.skipWaiting())
      .catch(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (olay) => {
  olay.waitUntil(
    caches.keys()
      .then((adlar) => Promise.all(
        adlar.filter((a) => a !== ONBELLEK).map((a) => caches.delete(a))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (olay) => {
  const istek = olay.request;
  if (istek.method !== 'GET') return;

  // Canli akis (SSE) asla onbelleklenmez
  if (istek.url.includes('/api/stream')) return;

  olay.respondWith(
    fetch(istek)
      .then((yanit) => {
        if (yanit.ok && istek.url.startsWith(self.location.origin)) {
          const kopya = yanit.clone();
          caches.open(ONBELLEK).then((c) => c.put(istek, kopya)).catch(() => {});
        }
        return yanit;
      })
      .catch(() => caches.match(istek).then((v) => v || caches.match('/index.html')))
  );
});
