const MONTHS_TR = ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"];
const DB_NAME = "dayatlas";
const STORE = "days";
const PREFS_KEY = "dayatlas_prefs_v1";

const $ = (id) => document.getElementById(id);

const els = {
  dayTitle: $("dayTitle"),
  status: $("status"),
  distance: $("distance"),
  lastPoint: $("lastPoint"),
  pointCount: $("pointCount"),
  btnToggle: $("btnToggle"),
  hint: $("hint"),
  mapDayTitle: $("mapDayTitle"),
  btnPrev: $("btnPrev"),
  btnNext: $("btnNext"),
  btnToday: $("btnToday"),
  emptyState: $("emptyState"),
  btnExport: $("btnExport"),
  btnSettings: $("btnSettings"),
  settingsDialog: $("settingsDialog"),
  dailyMode: $("dailyMode"),
  exportDialog: $("exportDialog"),
  exportFrom: $("exportFrom"),
  exportTo: $("exportTo"),
  exportToWrap: $("exportToWrap"),
  exportName: $("exportName"),
  btnDoExport: $("btnDoExport"),
};

let prefs = loadPrefs();
let mapDate = todayIso();
let map;
let poly;
let startMarker;
let endMarker;
let sampleTimer = null;
let sampling = false;

function todayIso(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function parseIso(iso) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function formatTitle(iso) {
  const d = parseIso(iso);
  return `Günlük ${d.getDate()} ${MONTHS_TR[d.getMonth()]} ${d.getFullYear()}`;
}

function formatDistance(meters) {
  if (!meters || meters < 1) return "—";
  if (meters < 1000) return `${Math.round(meters)} m`;
  return `${(meters / 1000).toLocaleString("tr-TR", { maximumFractionDigits: 1 })} km`;
}

function formatTime(ms) {
  const d = new Date(ms);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

function haversine(a, b) {
  const R = 6371000;
  const toRad = (x) => (x * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLon = toRad(b.lon - a.lon);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

function pathLength(points) {
  let sum = 0;
  for (let i = 1; i < points.length; i++) sum += haversine(points[i - 1], points[i]);
  return sum;
}

function loadPrefs() {
  try {
    return {
      dailyMode: false,
      tracking: false,
      intervalSeconds: 60,
      ...JSON.parse(localStorage.getItem(PREFS_KEY) || "{}"),
    };
  } catch {
    return { dailyMode: false, tracking: false, intervalSeconds: 60 };
  }
}

function savePrefs() {
  localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
}

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: "date" });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function loadDay(iso) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const req = tx.objectStore(STORE).get(iso);
    req.onsuccess = () => {
      resolve(
        req.result || {
          date: iso,
          title: formatTitle(iso),
          points: [],
          distanceMeters: 0,
        },
      );
    };
    req.onerror = () => reject(req.error);
  });
}

async function saveDay(record) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).put(record);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function listDays() {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readonly");
    const req = tx.objectStore(STORE).getAll();
    req.onsuccess = () => resolve(req.result || []);
    req.onerror = () => reject(req.error);
  });
}

async function appendPoint(coords) {
  const iso = todayIso();
  const day = await loadDay(iso);
  const point = {
    t: Date.now(),
    lat: coords.latitude,
    lon: coords.longitude,
    acc: coords.accuracy ?? null,
  };
  day.points = [...day.points, point];
  day.distanceMeters = pathLength(day.points.map((p) => ({ lat: p.lat, lon: p.lon })));
  day.title = formatTitle(iso);
  await saveDay(day);
  await refresh();
}

function requestSample() {
  if (!navigator.geolocation) {
    alert("Bu tarayıcı konum desteklemiyor.");
    return;
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      appendPoint(pos.coords).catch(console.error);
    },
    (err) => {
      console.warn(err);
      if (err.code === err.PERMISSION_DENIED) {
        alert("Konum izni gerekli. Safari Ayarları → DayAtlas / site → Konum.");
        stopSampling(true);
      }
    },
    { enableHighAccuracy: true, maximumAge: 15_000, timeout: 20_000 },
  );
}

function startSampling() {
  prefs.tracking = true;
  savePrefs();
  sampling = true;
  requestSample();
  clearInterval(sampleTimer);
  sampleTimer = setInterval(requestSample, Math.max(10, prefs.intervalSeconds) * 1000);
  refreshChrome();
}

function stopSampling(clearDaily = false) {
  prefs.tracking = false;
  if (clearDaily) prefs.dailyMode = false;
  savePrefs();
  sampling = false;
  clearInterval(sampleTimer);
  sampleTimer = null;
  refreshChrome();
}

function syncSamplingFromPrefs() {
  const want = prefs.dailyMode || prefs.tracking;
  if (want && !sampling) startSampling();
  if (!want && sampling) stopSampling();
}

function initMap() {
  map = L.map("map", { zoomControl: false, attributionControl: true });
  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap",
  }).addTo(map);
  map.setView([41.01, 29.0], 11);
  L.control.zoom({ position: "bottomright" }).addTo(map);
}

function showOnMap(points) {
  if (poly) {
    map.removeLayer(poly);
    poly = null;
  }
  if (startMarker) {
    map.removeLayer(startMarker);
    startMarker = null;
  }
  if (endMarker) {
    map.removeLayer(endMarker);
    endMarker = null;
  }
  const empty = !points.length;
  els.emptyState.hidden = !empty;
  if (empty) return;

  const latlngs = points.map((p) => [p.lat, p.lon]);
  poly = L.polyline(latlngs, { color: "#1F6F5B", weight: 5 }).addTo(map);
  startMarker = L.circleMarker(latlngs[0], {
    radius: 7,
    color: "#1b7a3a",
    fillColor: "#2ecc71",
    fillOpacity: 1,
  }).addTo(map);
  endMarker = L.circleMarker(latlngs[latlngs.length - 1], {
    radius: 7,
    color: "#8b1a1a",
    fillColor: "#e74c3c",
    fillOpacity: 1,
  }).addTo(map);
  map.fitBounds(poly.getBounds(), { padding: [40, 40] });
}

function refreshChrome() {
  const recording = prefs.dailyMode || prefs.tracking;
  els.status.textContent = prefs.dailyMode
    ? "Günlük mod — otomatik kayıt"
    : recording
      ? "Kayıt açık"
      : "Kayıt kapalı";
  els.status.classList.toggle("on", recording);
  els.status.classList.toggle("off", !recording);
  els.btnToggle.hidden = prefs.dailyMode;
  els.btnToggle.textContent = prefs.tracking ? "Durdur" : "Başlat";
  els.hint.textContent = prefs.dailyMode
    ? "Günlük mod: bu sekme / PWA açıkken örneklenir. iPhone Safari arka planda sabit GPS aralığına izin vermez — kilidi açık tutun veya ara sıra açın."
    : "Günlük mod kapalı. Başlat ile ön planda örnekleme. Ana ekrana eklemek için Ayarlar.";
}

async function refresh() {
  const today = todayIso();
  if (mapDate > today) mapDate = today;
  const todayRec = await loadDay(today);
  els.dayTitle.textContent = todayRec.title;
  els.distance.textContent = formatDistance(todayRec.distanceMeters);
  els.pointCount.textContent = String(todayRec.points.length);
  els.lastPoint.textContent = todayRec.points.length
    ? formatTime(todayRec.points[todayRec.points.length - 1].t)
    : "—";
  refreshChrome();

  els.mapDayTitle.textContent = formatTitle(mapDate);
  els.btnNext.disabled = mapDate >= today;
  els.btnToday.hidden = mapDate === today;
  const mapRec = await loadDay(mapDate);
  showOnMap(mapRec.points);
  setTimeout(() => map.invalidateSize(), 50);
}

function shiftDay(delta) {
  const d = parseIso(mapDate);
  d.setDate(d.getDate() + delta);
  const next = todayIso(d);
  const today = todayIso();
  if (next > today) return;
  mapDate = next;
  refresh();
}

function escapeXml(s) {
  return String(s)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function formatGpxTime(ms) {
  return new Date(ms).toISOString().replace(/\.\d{3}Z$/, "Z");
}

function toGpx(records, exportName) {
  const withPoints = records.filter((r) => r.points.length);
  let body = `<?xml version="1.0" encoding="UTF-8"?>\n`;
  body += `<gpx version="1.1" creator="DayAtlas Web" xmlns="http://www.topografix.com/GPX/1/1">\n`;
  for (const record of withPoints) {
    const name =
      withPoints.length === 1 && exportName
        ? exportName
        : exportName && withPoints.length > 1
          ? `${exportName} — ${record.title}`
          : record.title;
    body += `  <trk>\n    <name>${escapeXml(name)}</name>\n    <trkseg>\n`;
    for (const p of record.points) {
      body += `      <trkpt lat="${p.lat}" lon="${p.lon}">\n`;
      body += `        <time>${formatGpxTime(p.t)}</time>\n`;
      if (p.acc != null) body += `        <hdop>${p.acc}</hdop>\n`;
      body += `      </trkpt>\n`;
    }
    body += `    </trkseg>\n  </trk>\n`;
  }
  body += `</gpx>\n`;
  return body;
}

function sanitizeStem(raw) {
  const trimmed = (raw || "DayAtlas").trim().replace(/\.gpx$/i, "");
  const cleaned = trimmed.replace(/[\\/:*?"<>|]/g, "-").replace(/\s+/g, " ").trim().slice(0, 80);
  return cleaned || "DayAtlas";
}

async function exportGpx() {
  const mode = document.querySelector('input[name="mode"]:checked')?.value || "single";
  const from = els.exportFrom.value;
  let to = mode === "range" ? els.exportTo.value : from;
  if (!from) return;
  if (to < from) {
    alert("Bitiş günü başlangıçtan önce olamaz.");
    return;
  }
  const days = [];
  let cursor = parseIso(from);
  const end = parseIso(to);
  while (cursor <= end) {
    days.push(await loadDay(todayIso(cursor)));
    cursor.setDate(cursor.getDate() + 1);
  }
  if (!days.some((d) => d.points.length)) {
    alert("Seçilen gün(ler)de paylaşılacak nokta yok.");
    return;
  }
  const stem = sanitizeStem(els.exportName.value);
  const gpx = toGpx(days, stem);
  const file = new File([gpx], `${stem}.gpx`, { type: "application/gpx+xml" });
  if (navigator.share && navigator.canShare?.({ files: [file] })) {
    await navigator.share({ files: [file], title: `${stem}.gpx` });
  } else {
    const url = URL.createObjectURL(file);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${stem}.gpx`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

function bindUi() {
  els.btnToggle.addEventListener("click", () => {
    if (prefs.tracking) stopSampling();
    else startSampling();
  });
  els.btnPrev.addEventListener("click", () => shiftDay(-1));
  els.btnNext.addEventListener("click", () => shiftDay(1));
  els.btnToday.addEventListener("click", () => {
    mapDate = todayIso();
    refresh();
  });
  els.btnSettings.addEventListener("click", () => {
    els.dailyMode.checked = prefs.dailyMode;
    for (const input of document.querySelectorAll('input[name="interval"]')) {
      input.checked = Number(input.value) === prefs.intervalSeconds;
    }
    els.settingsDialog.showModal();
  });
  els.dailyMode.addEventListener("change", () => {
    prefs.dailyMode = els.dailyMode.checked;
    if (prefs.dailyMode) prefs.tracking = true;
    savePrefs();
    syncSamplingFromPrefs();
    refreshChrome();
  });
  document.getElementById("intervalGroup").addEventListener("change", (e) => {
    if (e.target.name !== "interval") return;
    prefs.intervalSeconds = Number(e.target.value);
    savePrefs();
    if (sampling) {
      clearInterval(sampleTimer);
      sampleTimer = setInterval(requestSample, prefs.intervalSeconds * 1000);
    }
  });

  els.btnExport.addEventListener("click", () => {
    els.exportFrom.value = mapDate;
    els.exportTo.value = mapDate;
    els.exportName.value = `DayAtlas-${mapDate}`;
    els.exportToWrap.hidden = true;
    document.querySelector('input[name="mode"][value="single"]').checked = true;
    els.exportDialog.showModal();
  });
  document.querySelectorAll('input[name="mode"]').forEach((el) => {
    el.addEventListener("change", () => {
      const range = document.querySelector('input[name="mode"][value="range"]').checked;
      els.exportToWrap.hidden = !range;
      const from = els.exportFrom.value;
      const to = range ? els.exportTo.value || from : from;
      els.exportName.value =
        from === to ? `DayAtlas-${from}` : `DayAtlas-${from}_${to}`;
    });
  });
  els.exportFrom.addEventListener("change", () => {
    if (!document.querySelector('input[name="mode"][value="range"]').checked) {
      els.exportName.value = `DayAtlas-${els.exportFrom.value}`;
    }
  });
  els.btnDoExport.addEventListener("click", (e) => {
    e.preventDefault();
    exportGpx()
      .then(() => els.exportDialog.close())
      .catch((err) => {
        console.error(err);
        alert("Dışa aktarma başarısız.");
      });
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      syncSamplingFromPrefs();
      refresh();
    }
  });
}

async function main() {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.register("./sw.js").catch(console.warn);
  }
  initMap();
  bindUi();
  syncSamplingFromPrefs();
  await refresh();
}

main().catch(console.error);
