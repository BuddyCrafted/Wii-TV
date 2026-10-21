import { CONFIG } from './config.js';
import * as bridge from './bridge.js';
import * as sound from './sound.js';
import { COLS, PER_PAGE, BAR, pageCount, indexOf, initialState, move, turnPage, clamp, orderApps } from './nav.js';

const $ = (id) => document.getElementById(id);
const stage = $('stage');
const pagesEl = $('pages');
const hand = $('hand');
const balloon = $('balloon');

const SETTINGS_KEY = 'wiitv.settings';
const settings = loadSettings();

let channels = [];       // ordered apps
let pages = 1;
let nav = initialState();
let mode = 'menu';       // menu | channel | options | board | boot
let channelSel = 1;      // 0 = Wii Menu, 1 = Start
let listSel = 0;
let currentApp = null;
let bootTimer = null;

// ---------- settings ----------

function loadSettings() {
  const base = { music: CONFIG.music, sounds: CONFIG.sounds };
  try {
    return { ...base, ...JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}') };
  } catch {
    return base;
  }
}

function saveSettings() {
  try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)); } catch { /* storage unavailable */ }
  sound.configure({ ...settings, musicVolume: CONFIG.musicVolume, soundVolume: CONFIG.soundVolume });
}

// ---------- layout ----------

function fitStage() {
  const s = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
  const x = (window.innerWidth - 1920 * s) / 2;
  const y = (window.innerHeight - 1080 * s) / 2;
  stage.style.transform = `translate(${x}px, ${y}px) scale(${s})`;
}

// ---------- channel art ----------

function hue(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return h % 360;
}

/** Fill `el` with the best art we have for `app`: banner → icon → name. */
function renderArt(el, app, { big = false } = {}) {
  el.innerHTML = '';
  el.classList.remove('color');

  const label = document.createElement('div');
  label.className = 'label';
  label.textContent = app.label;

  const colorFallback = () => {
    el.innerHTML = '';
    el.classList.add('color');
    const c = app.color || `hsl(${hue(app.pkg)} 55% 48%)`;
    // Darkening overlay instead of color-mix(): older TV WebViews lack it.
    el.style.background = `linear-gradient(135deg, rgba(255,255,255,.12), rgba(0,0,0,.4)), ${c}`;
    el.appendChild(label);
  };

  if (app.mock) return colorFallback();

  const iconFallback = () => {
    el.innerHTML = '';
    el.style.background = '';
    const img = new Image();
    img.className = 'icon';
    img.onerror = colorFallback;
    img.src = bridge.iconUrl(app.pkg);
    el.append(img, label);
  };

  if (app.hasBanner) {
    const img = new Image();
    img.className = 'banner';
    img.onerror = iconFallback;
    img.src = bridge.bannerUrl(app.pkg);
    el.appendChild(img);
  } else {
    iconFallback();
  }
  if (big) el.classList.add('big');
}

function buildGrid() {
  pagesEl.innerHTML = '';
  pages = pageCount(channels.length);
  for (let p = 0; p < pages; p++) {
    const page = document.createElement('div');
    page.className = 'page';
    for (let i = 0; i < PER_PAGE; i++) {
      const app = channels[p * PER_PAGE + i];
      const tile = document.createElement('div');
      tile.className = 'tile' + (app ? '' : ' empty');
      tile.dataset.index = String(p * PER_PAGE + i);
      const art = document.createElement('div');
      art.className = 'art';
      if (app) renderArt(art, app);
      tile.appendChild(art);
      tile.addEventListener('mouseenter', () => pointAt({ zone: 'grid', page: p, row: Math.floor(i / COLS), col: i % COLS }));
      tile.addEventListener('click', () => { pointAt({ zone: 'grid', page: p, row: Math.floor(i / COLS), col: i % COLS }); select(); });
      page.appendChild(tile);
    }
    pagesEl.appendChild(page);
  }
  nav = clamp(nav, pages);
  render(false);
}

async function loadChannels() {
  const apps = await bridge.getApps();
  channels = orderApps(apps, CONFIG);
  buildGrid();
}

// ---------- rendering the selection ----------

function stageRect(el) {
  // Element rect in stage coordinates (the stage is CSS-scaled).
  const s = stage.getBoundingClientRect();
  const r = el.getBoundingClientRect();
  const k = s.width / 1920;
  return { left: (r.left - s.left) / k, top: (r.top - s.top) / k, width: r.width / k, height: r.height / k };
}

function selectedEl() {
  if (nav.zone === 'bar') return $(nav.index === 0 ? 'btn-wii' : 'btn-mail');
  return pagesEl.querySelector(`[data-index="${indexOf(nav)}"]`);
}

function render(animate = true) {
  pagesEl.style.transform = `translateX(${-nav.page * 1920}px)`;
  $('arrow-left').classList.toggle('off', nav.page === 0);
  $('arrow-right').classList.toggle('off', nav.page >= pages - 1);

  document.querySelectorAll('.tile.selected, .round-btn.selected').forEach((e) => e.classList.remove('selected'));
  const el = selectedEl();
  if (!el) return;
  el.classList.add('selected');

  // Wait for the page slide before measuring.
  const place = () => {
    const r = stageRect(el);
    moveHand(r.left + r.width * 0.72, r.top + r.height * 0.55);
    const app = nav.zone === 'grid' ? channels[indexOf(nav)] : null;
    const barLabel = nav.zone === 'bar' ? (nav.index === 0 ? 'Wii TV Menu' : 'Message Board') : null;
    const text = app ? app.label : barLabel;
    if (text) {
      balloon.textContent = text;
      balloon.hidden = false;
      balloon.style.left = `${r.left + r.width / 2}px`;
      balloon.style.top = `${r.top - 8}px`;
    } else {
      balloon.hidden = true;
    }
  };
  if (animate) setTimeout(place, 0);
  else place();
}

function moveHand(x, y) {
  hand.style.transform = `translate(${x}px, ${y}px)`;
}

function pointAt(state) {
  if (mode !== 'menu') return;
  const changed = JSON.stringify(state) !== JSON.stringify(nav);
  nav = state;
  if (changed) { sound.play('move'); render(); }
}

// ---------- actions ----------

function step(dir) {
  const { state, effect } = move(nav, dir, pages);
  nav = state;
  sound.play(effect);
  if (effect === 'page') nudgeArrow(dir);
  render();
  // Re-place after the slide transition finishes so the hand lands on the tile.
  if (effect === 'page') setTimeout(() => render(false), 460);
}

function page(delta) {
  const { state, effect } = turnPage(nav, delta, pages);
  nav = state;
  sound.play(effect);
  render();
  if (effect === 'page') setTimeout(() => render(false), 460);
}

function nudgeArrow(dir) {
  const a = $(dir === 'left' ? 'arrow-left' : 'arrow-right');
  a.classList.add('nudge');
  setTimeout(() => a.classList.remove('nudge'), 180);
}

function select() {
  if (nav.zone === 'bar') {
    sound.play('open');
    return BAR[nav.index] === 'wii' ? openOptions() : openBoard();
  }
  const app = channels[indexOf(nav)];
  if (!app) return sound.play('bump');
  sound.play('select');
  openChannel(app);
}

function openChannel(app) {
  currentApp = app;
  const tile = selectedEl();
  const from = stageRect(tile);
  const ghost = document.createElement('div');
  ghost.className = 'zoom-ghost';
  Object.assign(ghost.style, { left: `${from.left}px`, top: `${from.top}px`, width: `${from.width}px`, height: `${from.height}px` });
  const ghostArt = document.createElement('div');
  ghostArt.className = 'art';
  renderArt(ghostArt, app);
  ghost.appendChild(ghostArt);
  stage.appendChild(ghost);
  hand.classList.add('hidden');
  balloon.hidden = true;

  requestAnimationFrame(() => requestAnimationFrame(() => {
    Object.assign(ghost.style, { left: '0px', top: '0px', width: '1920px', height: '860px', borderRadius: '0px' });
  }));

  setTimeout(() => {
    renderArt($('channel-art'), app, { big: true });
    $('channel').hidden = false;
    mode = 'channel';
    channelSel = 1;
    renderChannelButtons();
    ghost.remove();
  }, 430);
}

function renderChannelButtons() {
  document.querySelectorAll('#channel .pill').forEach((b, i) => b.classList.toggle('selected', i === channelSel));
}

function closeChannel() {
  sound.play('back');
  $('channel').hidden = true;
  hand.classList.remove('hidden');
  mode = 'menu';
  render(false);
}

function startChannel() {
  if (!currentApp) return;
  sound.play('start');
  const flash = $('flash');
  flash.classList.add('on');
  setTimeout(() => {
    bridge.launch(currentApp.pkg);
    // Return to the grid underneath so BACK/HOME from the app lands on the menu.
    setTimeout(() => {
      $('channel').hidden = true;
      hand.classList.remove('hidden');
      mode = 'menu';
      flash.classList.remove('on');
      render(false);
    }, 600);
  }, 450);
}

// ---------- panels ----------

function optionsItems() {
  return [
    { label: 'Background Music', value: settings.music ? 'ON' : 'OFF', run: () => { settings.music = !settings.music; saveSettings(); } },
    { label: 'Menu Sounds', value: settings.sounds ? 'ON' : 'OFF', run: () => { settings.sounds = !settings.sounds; saveSettings(); } },
    { label: 'Refresh Channels', value: '', run: () => { loadChannels(); closePanel(); } },
    { label: 'Android TV Settings', value: '', run: () => { closePanel(); bridge.openSystemSettings(); } },
  ];
}

function renderList(listEl, items) {
  listEl.innerHTML = '';
  items.forEach((it, i) => {
    const li = document.createElement('li');
    if (it.html) li.innerHTML = it.html;
    else {
      li.textContent = it.label;
      if (it.value) {
        const v = document.createElement('span');
        v.className = 'value';
        v.textContent = it.value;
        li.appendChild(v);
      }
    }
    li.classList.toggle('selected', i === listSel);
    li.addEventListener('mouseenter', () => { if (listSel !== i) { listSel = i; sound.play('move'); refreshPanel(); } });
    li.addEventListener('click', () => { listSel = i; activateListItem(); });
    listEl.appendChild(li);
  });
}

function boardItems() {
  const shown = channels.slice(0, 3).map((a) => a.label).join(', ');
  return [
    { html: '<b>Welcome to Wii TV!</b><span>Use the arrows on your remote to move between channels and OK to open one.</span>' },
    { html: '<b>Turning pages</b><span>Press RIGHT on the last column (or LEFT on the first) to flip to the next page of channels.</span>' },
    { html: `<b>Your channels</b><span>${channels.length} apps installed${shown ? ` &mdash; starting with ${escapeHtml(shown)}` : ''}.</span>` },
  ];
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function openOptions() {
  mode = 'options';
  listSel = 0;
  $('options').hidden = false;
  refreshPanel();
}

function openBoard() {
  mode = 'board';
  listSel = 0;
  $('board-date').textContent = new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
  $('board').hidden = false;
  refreshPanel();
}

function refreshPanel() {
  if (mode === 'options') renderList($('options-list'), optionsItems());
  if (mode === 'board') renderList($('board-list'), boardItems());
}

function panelLength() {
  return mode === 'options' ? optionsItems().length : boardItems().length;
}

function activateListItem() {
  if (mode !== 'options') return sound.play('bump');
  sound.play('select');
  optionsItems()[listSel].run();
  refreshPanel();
}

function closePanel() {
  $('options').hidden = true;
  $('board').hidden = true;
  mode = 'menu';
  render(false);
}

// ---------- boot notice ----------

function showBoot() {
  mode = 'boot';
  $('boot').hidden = false;
  hand.classList.add('hidden');
  bootTimer = setTimeout(hideBoot, CONFIG.bootScreenTimeoutMs);
}

function hideBoot() {
  clearTimeout(bootTimer);
  if (mode !== 'boot') return;
  $('boot').hidden = true;
  hand.classList.remove('hidden');
  mode = 'menu';
  sound.play('open');
  render(false);
}

// ---------- input ----------

/**
 * Single entry point for remote keys. Called by the Android wrapper and by
 * the keyboard handler. Returns 'handled' or 'unhandled' (BACK on the grid).
 */
function remote(key) {
  sound.unlock();
  switch (mode) {
    case 'boot':
      hideBoot();
      return 'handled';

    case 'menu':
      if (key === 'select') select();
      else if (key === 'back') {
        if (nav.zone === 'bar' || nav.page !== 0 || nav.row !== 0 || nav.col !== 0) {
          nav = initialState();
          sound.play('back');
          render();
          return 'handled';
        }
        return 'unhandled';
      } else if (key === 'menu') { sound.play('open'); openOptions(); }
      else if (key === 'pageUp') page(-1);
      else if (key === 'pageDown') page(1);
      else step(key);
      return 'handled';

    case 'channel':
      if (key === 'left' || key === 'right') {
        const next = key === 'left' ? 0 : 1;
        sound.play(next === channelSel ? 'bump' : 'move');
        channelSel = next;
        renderChannelButtons();
      } else if (key === 'select') {
        if (channelSel === 1) startChannel();
        else closeChannel();
      } else if (key === 'back') closeChannel();
      return 'handled';

    case 'options':
    case 'board':
      if (key === 'up' || key === 'down') {
        const next = Math.min(panelLength() - 1, Math.max(0, listSel + (key === 'up' ? -1 : 1)));
        sound.play(next === listSel ? 'bump' : 'move');
        listSel = next;
        refreshPanel();
      } else if (key === 'select') activateListItem();
      else if (key === 'back' || key === 'menu') { sound.play('back'); closePanel(); }
      return 'handled';
  }
  return 'unhandled';
}

const KEYMAP = {
  ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
  Enter: 'select', ' ': 'select',
  Escape: 'back', Backspace: 'back', BrowserBack: 'back', GoBack: 'back',
  ContextMenu: 'menu', m: 'menu',
  PageUp: 'pageUp', PageDown: 'pageDown',
};

window.addEventListener('keydown', (e) => {
  const k = KEYMAP[e.key];
  if (!k) return;
  e.preventDefault();
  remote(k);
});

window.addEventListener('pointerdown', () => sound.unlock());

$('btn-wii').addEventListener('mouseenter', () => pointAt({ zone: 'bar', page: nav.page, index: 0 }));
$('btn-mail').addEventListener('mouseenter', () => pointAt({ zone: 'bar', page: nav.page, index: 1 }));
$('btn-wii').addEventListener('click', () => { nav = { zone: 'bar', page: nav.page, index: 0 }; select(); });
$('btn-mail').addEventListener('click', () => { nav = { zone: 'bar', page: nav.page, index: 1 }; select(); });
$('arrow-left').addEventListener('click', () => page(-1));
$('arrow-right').addEventListener('click', () => page(1));
document.querySelectorAll('#channel .pill').forEach((b, i) => {
  b.addEventListener('mouseenter', () => { channelSel = i; renderChannelButtons(); });
  b.addEventListener('click', () => { channelSel = i; remote('select'); });
});

// ---------- clock ----------

function tickClock() {
  const now = new Date();
  let h = now.getHours();
  const ampm = h >= 12 ? 'PM' : 'AM';
  h = h % 12 || 12;
  const m = String(now.getMinutes()).padStart(2, '0');
  const dim = now.getSeconds() % 2 === 1 ? ' dim' : '';
  $('clock-hm').innerHTML = `${h}<span class="colon${dim}">:</span>${m}`;
  $('clock-ampm').textContent = ampm;
  const wd = now.toLocaleDateString(undefined, { weekday: 'short' });
  $('date').textContent = `${wd} ${now.getMonth() + 1}/${now.getDate()}`;
}

// ---------- native callbacks ----------

window.wii = {
  remote,
  appsChanged: () => loadChannels(),
  onPause: () => sound.pause(),
  onResume: () => {
    sound.resume();
    tickClock();
    if (mode === 'channel') closeChannel();
  },
  home: () => {
    $('channel').hidden = true;
    $('options').hidden = true;
    $('board').hidden = true;
    hideBoot();
    hand.classList.remove('hidden');
    mode = 'menu';
    nav = initialState();
    render();
  },
};

// ---------- boot ----------

window.addEventListener('resize', () => { fitStage(); render(false); });
fitStage();
tickClock();
setInterval(tickClock, 1000);
saveSettings();
loadChannels().then(() => {
  if (CONFIG.bootScreen && !sessionStorage.getItem('wiitv.booted')) {
    try { sessionStorage.setItem('wiitv.booted', '1'); } catch { /* ignore */ }
    showBoot();
  }
});
