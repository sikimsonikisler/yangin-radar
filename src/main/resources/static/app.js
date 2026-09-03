/* ============================================================
   YanginRadar - telefon oncelikli arayuz mantigi
   ============================================================ */
(() => {
  'use strict';

  /**
   * Bu tarayiciya ait, kisisel bilgi tasimayan kimlik.
   *
   * Oy ucu bunu ister: onsuz ayni kisi bir ihbari sinirsiz kez destekleyip guven
   * puanini sisirebiliyordu. Kararli bir kimlik dogrulamasi degildir - depolama
   * temizlenirse yenisi uretilir; gundelik tekrar oyu keser, kararli saldirganı degil.
   */
  const CIHAZ_KIMLIGI = (() => {
    const ANAHTAR = 'yanginradar-cihaz-kimligi';
    try {
      let v = localStorage.getItem(ANAHTAR);
      if (!v) {
        v = (crypto.randomUUID && crypto.randomUUID())
            || (Date.now().toString(36) + Math.random().toString(36).slice(2));
        localStorage.setItem(ANAHTAR, v);
      }
      return v;
    } catch (e) {
      // Gizli sekmede localStorage kapali olabilir; oturumluk kimlik yeterli.
      return 'gecici-' + Math.random().toString(36).slice(2);
    }
  })();

  const TURKIYE = [39.0, 35.2];
  const TURKIYE_SINIRI = [[35.8, 25.6], [42.2, 44.8]];

  const DURUM_RENK = {
    BEKLIYOR: '#f5a524',
    INCELENIYOR: '#4aa8ff',
    DOGRULANDI: '#ff4d4d',
    SONDURULDU: '#3fb950',
    REDDEDILDI: '#6e7681'
  };

  const BUYUKLUK = {
    DUMAN_GORULDU:   { cap: 14, ad: 'Duman görüldü' },
    KUCUK_YANGIN:    { cap: 18, ad: 'Küçük yangın' },
    BUYUK_YANGIN:    { cap: 24, ad: 'Büyük yangın' },
    HIZLA_YAYILIYOR: { cap: 30, ad: 'Hızla yayılıyor' }
  };

  /** id -> { veri, marker } */
  const kayitlar = new Map();

  let harita, katman, secimHarita, secimIsaret;
  let secilenNokta = null;
  let secilenBuyukluk = 'KUCUK_YANGIN';
  let aktifDurumFiltre = 'hepsi';
  let kurulumOlayi = null;

  // ───────────────────────────────────────────────── yardımcılar

  const $ = (s) => document.querySelector(s);
  const $$ = (s) => Array.from(document.querySelectorAll(s));

  const kacar = (s) => String(s ?? '').replace(/[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function gecenSure(iso) {
    const dk = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
    if (dk < 1) return 'az önce';
    if (dk < 60) return dk + ' dk önce';
    const saat = Math.round(dk / 60);
    if (saat < 24) return saat + ' saat önce';
    return Math.round(saat / 24) + ' gün önce';
  }

  function tost(mesaj, tur = '') {
    const el = document.createElement('div');
    el.className = tur;
    el.textContent = mesaj;
    $('#tost').appendChild(el);
    setTimeout(() => el.remove(), 5000);
  }

  async function istek(url, secenek = {}) {
    const yanit = await fetch(url, secenek);
    const govde = yanit.status === 204 ? null : await yanit.json().catch(() => null);
    if (!yanit.ok) {
      throw new Error((govde && (govde.hata || govde.message)) || ('HTTP ' + yanit.status));
    }
    return govde;
  }

  // ───────────────────────────────────────────────── sekmeler

  function sekmeAc(ad) {
    $$('.sayfa').forEach((s) => s.classList.toggle('etkin', s.id === 'sayfa-' + ad));
    $$('.sekme').forEach((b) => b.classList.toggle('etkin', b.dataset.sayfa === ad));

    // Leaflet gizliyken boyut hesaplayamaz; gorunur olunca yeniden olcmeli
    if (ad === 'harita' && harita) {
      setTimeout(() => harita.invalidateSize({ animate: false }), 60);
    }
    if (ad === 'bildir') {
      secimHaritasiHazirla();
      setTimeout(() => secimHarita && secimHarita.invalidateSize(), 60);
    }
    if (ad === 'liste') {
      kartlariCiz();
    }
  }

  // ───────────────────────────────────────────────── ana harita

  function dokuKatmanlari(hedef) {
    const sokak = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap',
      className: 'koyu-doku',
      maxZoom: 19
    });
    const uydu = L.tileLayer(
      'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      { attribution: 'Uydu &copy; Esri', maxZoom: 18 });

    sokak.addTo(hedef);
    L.control.layers({ 'Harita': sokak, 'Uydu': uydu }, null, { position: 'topright' })
      .addTo(hedef);
  }

  function haritaKur() {
    harita = L.map('harita', {
      center: TURKIYE,
      zoom: 6,
      minZoom: 4,
      zoomSnap: 0.25,      // tam sayi yakinlastirma cerceveyi asagi yuvarlayip bosluk birakiyor
      zoomControl: true,
      attributionControl: true
    });
    dokuKatmanlari(harita);
    katman = L.layerGroup().addTo(harita);
    cerceveyeOturt(harita);
  }

  /**
   * Turkiye'yi ekrana sigdirir.
   *
   * Leaflet, konteyner daha olculmeden acilirsa yanlis boyut varsayar; sonuc
   * kenarlarda bos bant ve yanlis yakinlastirma olur. Bu yuzden once boyut
   * yeniden olculur, sonra cerceve oturtulur - yerlesim gec bitse de dogru kalsin
   * diye birkac kez tekrarlanir.
   */
  function cerceveyeOturt(hedef, sinir = TURKIYE_SINIRI, dolgu = 12) {
    const uygula = () => {
      hedef.invalidateSize({ animate: false });
      hedef.fitBounds(sinir, { padding: [dolgu, dolgu], animate: false });
    };
    uygula();
    requestAnimationFrame(uygula);
    setTimeout(uygula, 250);
    window.addEventListener('load', uygula, { once: true });
  }

  function isaretYap(v) {
    const renk = DURUM_RENK[v.status] || DURUM_RENK.BEKLIYOR;
    const cap = (BUYUKLUK[v.severity] || BUYUKLUK.KUCUK_YANGIN).cap;
    const canli = v.status === 'DOGRULANDI' || v.status === 'BEKLIYOR';

    const marker = L.marker([v.lat, v.lon], {
      icon: L.divIcon({
        className: '',
        html: `<div class="yangin-isareti ${canli ? 'canlandir' : ''}"
                    style="width:${cap}px;height:${cap}px;background:${renk}"></div>`,
        iconSize: [cap, cap],
        iconAnchor: [cap / 2, cap / 2]
      })
    });
    marker.on('click', () => detayAc(v.id));
    return marker;
  }

  function haritayiCiz() {
    if (!katman) return;
    katman.clearLayers();
    for (const k of kayitlar.values()) {
      k.marker.addTo(katman);
    }
    $('#haritaSayac').textContent = kayitlar.size + ' kayıt';
  }

  function kayitYaz(v) {
    const eski = kayitlar.get(v.id);
    if (eski && katman) katman.removeLayer(eski.marker);
    kayitlar.set(v.id, { veri: v, marker: isaretYap(v) });
    haritayiCiz();
    kartlariCiz();
  }

  // ───────────────────────────────────────────────── kart listesi

  function kartlariCiz() {
    const kap = $('#kartlar');
    if (!kap) return;

    let liste = Array.from(kayitlar.values()).map((k) => k.veri);
    if (aktifDurumFiltre !== 'hepsi') {
      liste = liste.filter((v) => v.status === aktifDurumFiltre);
    }
    liste.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

    if (!liste.length) {
      kap.innerHTML = '<p class="bos-durum">Bu filtrede kayıt yok.</p>';
      return;
    }

    kap.innerHTML = liste.map((v) => {
      const yer = [v.il, v.ilce].filter(Boolean).join(' / ')
        || `${v.lat.toFixed(3)}, ${v.lon.toFixed(3)}`;
      const renk = DURUM_RENK[v.status];
      return `
        <article class="kart" data-id="${v.id}" style="border-left-color:${renk}">
          <div class="kart-ust">
            <h3>${kacar(BUYUKLUK[v.severity]?.ad || v.severityLabel)}</h3>
            <span class="etiket e-${v.status}">${kacar(v.statusLabel)}</span>
          </div>
          <p class="kart-yer">${kacar(yer)} · ${gecenSure(v.createdAt)}</p>
          <div class="guven-cubuk"><i style="width:${v.confidence}%"></i></div>
          <div class="kart-alt">
            <span>Güven <b>${v.confidence}</b>/100</span>
            <span>${v.photoCount ? '📷 ' + v.photoCount : 'fotoğrafsız'}</span>
            <span>${kacar(v.sourceLabel)}</span>
          </div>
        </article>`;
    }).join('');
  }

  // ───────────────────────────────────────────────── detay sayfası

  function detayAc(id) {
    const kayit = kayitlar.get(id);
    if (!kayit) return;
    const v = kayit.veri;

    const yer = [v.il, v.ilce].filter(Boolean).join(' / ') || 'Girilmemiş';

    const fotograflar = (v.photos || []).map((p) => `
      <figure>
        <a href="${p.url}" target="_blank" rel="noopener">
          <img src="${p.url}" alt="İhbar fotoğrafı" loading="lazy"></a>
        <figcaption class="v-${p.verdict}">${kacar(p.verdictLabel)}<br>${p.score}/100</figcaption>
      </figure>`).join('');

    const rapor = (v.photos || []).map((p, i) =>
      `— ${i + 1}. fotoğraf —\n` + (p.checks || []).map(kacar).join('\n')).join('\n\n');

    $('#detayGovde').innerHTML = `
      <div class="kart-ust" style="margin-bottom:10px">
        <h3 style="margin:0;font-size:19px">${kacar(BUYUKLUK[v.severity]?.ad || v.severityLabel)}</h3>
        <span class="etiket e-${v.status}">${kacar(v.statusLabel)}</span>
      </div>

      <div class="guven-cubuk"><i style="width:${v.confidence}%"></i></div>
      <p class="ipucu" style="margin-top:6px">Güven puanı ${v.confidence}/100</p>

      ${v.aciklama ? `<p style="font-size:14.5px">${kacar(v.aciklama)}</p>` : ''}

      ${fotograflar ? `<div class="detay-foto">${fotograflar}</div>`
                    : '<p class="ipucu">Bu ihbara fotoğraf eklenmemiş.</p>'}

      ${rapor ? `<details class="rapor">
          <summary>Fotoğraf doğrulama raporu</summary>${kacar(rapor)}</details>` : ''}

      <div class="satir"><span>Yer</span><span>${kacar(yer)}</span></div>
      <div class="satir"><span>Konum</span><span>${v.lat.toFixed(5)}, ${v.lon.toFixed(5)}</span></div>
      <div class="satir"><span>Kaynak</span><span>${kacar(v.sourceLabel)}</span></div>
      <div class="satir"><span>Bildiren</span><span>${kacar(v.reporterAlias || 'Anonim')}</span></div>
      <div class="satir"><span>Zaman</span><span>${gecenSure(v.createdAt)}</span></div>
      <div class="satir"><span>Destek / ret</span><span>${v.confirmCount} / ${v.denyCount}</span></div>

      <div class="oy-satiri">
        <button class="dugme" data-oy="1" data-id="${v.id}">✅ Doğru</button>
        <button class="dugme" data-oy="0" data-id="${v.id}">🚫 Yanlış alarm</button>
      </div>

      <a class="dugme genis acil-dugme" href="tel:112">📞 Acil durumda 112'yi arayın</a>
      <div style="height:20px"></div>`;

    $('#detay').classList.remove('gizli');
  }

  function detayKapat() {
    $('#detay').classList.add('gizli');
  }

  async function oyVer(id, dogru) {
    try {
      const kayit = await istek(
        `/api/reports/${id}/vote?confirm=${dogru}&voterId=${encodeURIComponent(CIHAZ_KIMLIGI)}`,
        { method: 'POST' });
      kayitYaz(kayit);
      detayAc(id);
      tost(dogru ? 'Desteğiniz kaydedildi.' : 'Yanlış alarm bildiriminiz kaydedildi.', 'iyi');
      sayaclariYenile();
    } catch (e) {
      tost('Oy kaydedilemedi: ' + e.message, 'kotu');
    }
  }

  // ───────────────────────────────────────────────── bildir sihirbazı

  function secimHaritasiHazirla() {
    if (secimHarita) return;
    secimHarita = L.map('secimHarita', {
      center: TURKIYE, zoom: 6, minZoom: 4, zoomSnap: 0.25, attributionControl: false
    });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      className: 'koyu-doku', maxZoom: 19
    }).addTo(secimHarita);
    cerceveyeOturt(secimHarita, TURKIYE_SINIRI, 6);

    secimHarita.on('click', (e) => noktaSec(e.latlng.lat, e.latlng.lng));
  }

  function noktaSec(lat, lon, yakinlas = false) {
    secilenNokta = { lat, lon };

    if (secimIsaret) secimHarita.removeLayer(secimIsaret);
    secimIsaret = L.marker([lat, lon], {
      icon: L.divIcon({
        className: '',
        html: '<div style="font-size:30px;line-height:1;transform:translate(-50%,-100%)">📍</div>',
        iconSize: [0, 0]
      })
    }).addTo(secimHarita);

    if (yakinlas) secimHarita.setView([lat, lon], 13);

    $('#secilenKonum').textContent = `${lat.toFixed(5)}, ${lon.toFixed(5)}`;
    $('#adim1Ileri').disabled = false;
  }

  function adimGoster(n) {
    $$('.adim-sayfa').forEach((s) => s.classList.toggle('etkin', s.dataset.adim === String(n)));
    $$('.adim').forEach((a) => {
      const no = Number(a.dataset.adim);
      a.classList.toggle('etkin', no === n);
      a.classList.toggle('tamam', no < n);
    });
    $('#bildirGovde').scrollTop = 0;
    if (n === 1) setTimeout(() => secimHarita && secimHarita.invalidateSize(), 60);
  }

  function onizlemeCiz() {
    const dosyalar = Array.from($('#fotografGirdi').files || []);
    const kap = $('#onizleme');
    kap.innerHTML = '';
    dosyalar.forEach((d) => {
      const img = document.createElement('img');
      img.src = URL.createObjectURL(d);
      img.onload = () => URL.revokeObjectURL(img.src);
      kap.appendChild(img);
    });
    if (dosyalar.length) {
      $('#kameraKutu').querySelector('.kamera-yazi').textContent =
        dosyalar.length + ' fotoğraf seçildi — değiştirmek için dokunun';
    }
  }

  function formuSifirla() {
    secilenNokta = null;
    secilenBuyukluk = 'KUCUK_YANGIN';
    if (secimIsaret && secimHarita) { secimHarita.removeLayer(secimIsaret); secimIsaret = null; }
    $('#secilenKonum').textContent = 'henüz seçilmedi';
    $('#adim1Ileri').disabled = true;
    $('#aciklamaAlani').value = '';
    $('#ilAlani').value = '';
    $('#ilceAlani').value = '';
    $('#adAlani').value = '';
    $('#fotografGirdi').value = '';
    $('#onizleme').innerHTML = '';
    $('#kameraKutu').querySelector('.kamera-yazi').textContent = 'Fotoğraf çek veya seç';
    $$('#buyuklukSecimi .secenek').forEach((b) =>
      b.classList.toggle('etkin', b.dataset.deger === 'KUCUK_YANGIN'));
    $('#bildirHata').classList.add('gizli');
    adimGoster(1);
  }

  async function ihbarGonder() {
    if (!secilenNokta) {
      adimGoster(1);
      tost('Önce haritada konum seçin.', 'kotu');
      return;
    }

    const dugme = $('#ihbarGonder');
    const digeri = $('#fotografsizGonder');
    const hataKutusu = $('#bildirHata');
    hataKutusu.classList.add('gizli');
    dugme.disabled = digeri.disabled = true;
    const eskiYazi = dugme.textContent;
    dugme.textContent = 'Gönderiliyor…';

    try {
      let kayit = await istek('/api/reports', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          lat: secilenNokta.lat,
          lon: secilenNokta.lon,
          il: $('#ilAlani').value.trim() || null,
          ilce: $('#ilceAlani').value.trim() || null,
          aciklama: $('#aciklamaAlani').value.trim() || null,
          severity: secilenBuyukluk,
          reporterAlias: $('#adAlani').value.trim() || null
        })
      });

      const dosyalar = Array.from($('#fotografGirdi').files || []);
      for (let i = 0; i < dosyalar.length; i++) {
        dugme.textContent = `Fotoğraf ${i + 1}/${dosyalar.length}…`;
        const paket = new FormData();
        paket.append('file', dosyalar[i]);
        try {
          kayit = await istek(`/api/reports/${kayit.id}/photos`, { method: 'POST', body: paket });
        } catch (e) {
          tost('Fotoğraf yüklenemedi: ' + e.message, 'kotu');
        }
      }

      kayitYaz(kayit);
      formuSifirla();
      sekmeAc('liste');
      sayaclariYenile();

      const foto = (kayit.photos || []).slice(-1)[0];
      tost(foto
        ? `İhbar alındı. Fotoğraf: ${foto.verdictLabel} (${foto.score}/100). Güven ${kayit.confidence}/100.`
        : `İhbar alındı. Güven ${kayit.confidence}/100 — fotoğraf eklerseniz yükselir.`, 'iyi');
      setTimeout(() => detayAc(kayit.id), 400);
    } catch (e) {
      hataKutusu.textContent = e.message;
      hataKutusu.classList.remove('gizli');
    } finally {
      dugme.disabled = digeri.disabled = false;
      dugme.textContent = eskiYazi;
    }
  }

  // ───────────────────────────────────────────────── canlı akış

  function akisaBaglan() {
    const kaynak = new EventSource('/api/stream');
    const rozet = $('#baglantiRozet');
    const yazi = rozet.querySelector('span');

    kaynak.onopen = () => { rozet.className = 'rozet canli'; yazi.textContent = 'canlı'; };
    kaynak.onerror = () => { rozet.className = 'rozet kopuk'; yazi.textContent = 'bağlanıyor'; };

    const bildirim = {
      'yeni-ihbar': '🆕 Yeni ihbar',
      'uydu-noktasi': '🛰️ Uydu ısı noktası',
      'fotograf-eklendi': '📷 Fotoğraf eklendi',
      'durum-degisti': '🔄 Durum değişti'
    };

    ['yeni-ihbar', 'uydu-noktasi', 'fotograf-eklendi', 'oy-verildi',
     'durum-degisti', 'yetkiliye-iletildi'].forEach((tur) => {
      kaynak.addEventListener(tur, (olay) => {
        const veri = JSON.parse(olay.data).data;
        kayitYaz(veri);
        sayaclariYenile();
        if (bildirim[tur]) {
          const yer = [veri.il, veri.ilce].filter(Boolean).join(' / ') || 'konum';
          tost(`${bildirim[tur]} — ${yer}`);
        }
      });
    });
  }

  async function sayaclariYenile() {
    try {
      const s = await istek('/api/stats');
      $('#sToplam').textContent = s.toplam;
      $('#sDogrulanan').textContent = s.dogrulanan;
      $('#sBekleyen').textContent = s.bekleyen;
      $('#sUydu').textContent = s.uyduKaydi;
    } catch (e) { /* sayaclar kritik degil */ }
  }

  // ───────────────────────────────────────────────── başlangıç

  function olaylariBagla() {
    $$('.sekme').forEach((b) => (b.onclick = () => sekmeAc(b.dataset.sayfa)));

    $$('#durumCipleri .cip').forEach((c) => (c.onclick = () => {
      $$('#durumCipleri .cip').forEach((x) => x.classList.remove('etkin'));
      c.classList.add('etkin');
      aktifDurumFiltre = c.dataset.durum;
      kartlariCiz();
    }));

    $('#kartlar').addEventListener('click', (e) => {
      const kart = e.target.closest('.kart');
      if (kart) detayAc(Number(kart.dataset.id));
    });

    $('#detayKapat').onclick = detayKapat;
    $('#detayGovde').addEventListener('click', (e) => {
      const d = e.target.closest('button[data-oy]');
      if (d) oyVer(Number(d.dataset.id), d.dataset.oy === '1');
    });

    $('#adim1Ileri').onclick = () => adimGoster(2);
    $('#adim2Ileri').onclick = () => adimGoster(3);
    $$('button[data-geri]').forEach((b) =>
      (b.onclick = () => adimGoster(Number(b.dataset.geri))));

    $$('#buyuklukSecimi .secenek').forEach((b) => (b.onclick = () => {
      $$('#buyuklukSecimi .secenek').forEach((x) => x.classList.remove('etkin'));
      b.classList.add('etkin');
      secilenBuyukluk = b.dataset.deger;
    }));

    $('#kameraKutu').onclick = () => $('#fotografGirdi').click();
    $('#fotografGirdi').onchange = onizlemeCiz;
    $('#ihbarGonder').onclick = ihbarGonder;
    $('#fotografsizGonder').onclick = ihbarGonder;

    $('#konumumuKullan').onclick = () => {
      if (!navigator.geolocation) {
        tost('Bu tarayıcı konum desteklemiyor.', 'kotu');
        return;
      }
      tost('Konum alınıyor…');
      navigator.geolocation.getCurrentPosition(
        (k) => noktaSec(k.coords.latitude, k.coords.longitude, true),
        () => tost('Konum alınamadı. Haritadan elle seçebilirsiniz. '
                 + '(Telefonda konum için https gerekir.)', 'kotu'),
        { enableHighAccuracy: true, timeout: 10000 });
    };

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') detayKapat();
    });
  }

  function kurulumuHazirla() {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      kurulumOlayi = e;
      $('#kurulumKart').hidden = false;
    });

    $('#kurDugmesi').onclick = async () => {
      if (!kurulumOlayi) return;
      kurulumOlayi.prompt();
      await kurulumOlayi.userChoice;
      kurulumOlayi = null;
      $('#kurulumKart').hidden = true;
    };

    // iOS'ta beforeinstallprompt yok; elle yonlendirme gosterilir
    const iosMu = /iphone|ipad|ipod/i.test(navigator.userAgent);
    const kuruluMu = window.matchMedia('(display-mode: standalone)').matches
      || window.navigator.standalone === true;
    if (iosMu && !kuruluMu) {
      $('#kurulumKart').hidden = false;
      $('#kurDugmesi').outerHTML =
        '<p class="ipucu">Safari\'de alttaki <strong>Paylaş</strong> düğmesine dokunun → '
        + '<strong>Ana Ekrana Ekle</strong>.</p>';
    }
  }

  async function baslat() {
    haritaKur();
    olaylariBagla();
    kurulumuHazirla();

    try {
      const liste = await istek('/api/reports');
      liste.forEach((v) => kayitlar.set(v.id, { veri: v, marker: isaretYap(v) }));
      haritayiCiz();
      kartlariCiz();
    } catch (e) {
      tost('Kayıtlar yüklenemedi: ' + e.message, 'kotu');
    }

    sayaclariYenile();
    akisaBaglan();
    setInterval(sayaclariYenile, 30000);

    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => {});
    }
  }

  document.addEventListener('DOMContentLoaded', baslat);
})();
