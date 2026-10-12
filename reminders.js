(() => {
  'use strict';

  const MEDICINES_KEY = 'oushodhsathi.medicines.v2';
  const HISTORY_KEY = 'oushodhsathi.history.v1';
  const ENABLED_KEY = 'oushodhsathi.notifications.enabled.v1';
  const VOICE_ENABLED_KEY = 'oushodhsathi.voice.enabled.v1';
  const SNOOZE_MS = 10 * 60 * 1000;
  const DUE_GRACE_MS = 10 * 60 * 1000;
  const POLL_MS = 30 * 1000;
  const MAX_HISTORY = 1500;
  const WEEKDAYS = ['রবি', 'সোম', 'মঙ্গল', 'বুধ', 'বৃহস্পতি', 'শুক্র', 'শনি'];
  const STATUS_TEXT = {pending: 'অপেক্ষায়', taken: 'খেয়েছি', later: 'পরে খাব', unsure: 'নিশ্চিত নই'};
  const toast = document.getElementById('toast');
  let toastTimer;
  let serviceWorkerPromise = null;
  let serviceRegistration = null;
  let reminderTimer = null;
  let checking = false;
  let notificationsEnabled = readEnabled();
  let voiceEnabled = readVoiceEnabled();
  let historyRecords = readHistory();

  function safeGet(key) {
    try { return localStorage.getItem(key); } catch { return null; }
  }
  function safeSet(key, value) {
    try { localStorage.setItem(key, value); return true; }
    catch { return false; }
  }
  function readEnabled() { return safeGet(ENABLED_KEY) === 'true'; }
  function readVoiceEnabled() { return safeGet(VOICE_ENABLED_KEY) === 'true'; }
  function readMedicines() {
    try {
      const value = JSON.parse(localStorage.getItem(MEDICINES_KEY) || '[]');
      return Array.isArray(value) ? value.filter(item => item && typeof item.id === 'string' && typeof item.name === 'string') : [];
    } catch { return []; }
  }
  function readHistory() {
    try {
      const value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
      return Array.isArray(value) ? value.filter(item => item && typeof item.key === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(item.date) && ['pending', 'taken', 'later', 'unsure'].includes(item.status)).slice(-MAX_HISTORY) : [];
    } catch { return []; }
  }
  function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  }
  function localISODate(date = new Date()) {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
  function formatDate(value) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return '';
    const [year, month, day] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('bn-BD', {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'}).format(new Date(year, month - 1, day));
  }
  function formatTime(value) {
    if (!/^\d{2}:\d{2}$/.test(value || '')) return '';
    const [hour, minute] = value.split(':').map(Number);
    const date = new Date();
    date.setHours(hour, minute, 0, 0);
    return new Intl.DateTimeFormat('bn-BD', {hour: 'numeric', minute: '2-digit'}).format(date);
  }
  function showToast(message) {
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 3800);
  }
  function doseKey(date, medicineId, time) { return `${date}|${medicineId}|${time}`; }
  function findRecord(key) { return historyRecords.find(item => item.key === key) || null; }
  function persistHistory(record) {
    const next = historyRecords.filter(item => item.key !== record.key);
    next.push({...record, updatedAt: Date.now()});
    next.sort((a, b) => String(a.date).localeCompare(String(b.date)) || String(a.time).localeCompare(String(b.time)));
    const bounded = next.slice(-MAX_HISTORY);
    if (!safeSet(HISTORY_KEY, JSON.stringify(bounded))) {
      showToast('ইতিহাস সংরক্ষণ করা যায়নি। browser-এর local storage খালি আছে কি না দেখুন।');
      return false;
    }
    historyRecords = bounded;
    return true;
  }
  function newRecord(medicine, date, time, status = 'pending') {
    const key = doseKey(date, medicine.id, time);
    return {
      key,
      medicineId: medicine.id,
      medicineName: String(medicine.name || '').slice(0, 100),
      dose: String(medicine.dose || '').slice(0, 100),
      instruction: String(medicine.instruction || '').slice(0, 160),
      date,
      time,
      status,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      notificationAt: 0,
      notificationClaimedAt: 0,
      voiceAt: 0,
      voiceClaimedAt: 0,
      snoozeUntil: 0,
      snoozeNotifiedAt: 0,
      snoozeClaimedAt: 0,
      snoozeVoiceAt: 0,
      snoozeVoiceClaimedAt: 0
    };
  }
  function recordFor(medicine, date, time) {
    return findRecord(doseKey(date, medicine.id, time));
  }
  function isScheduledOn(medicine, date) {
    if (!medicine || medicine.frequency === 'as-needed' || date < medicine.start || (medicine.end && date > medicine.end)) return false;
    if (medicine.frequency === 'specific-days') {
      const [year, month, day] = date.split('-').map(Number);
      return Array.isArray(medicine.days) && medicine.days.includes(new Date(year, month - 1, day).getDay());
    }
    return true;
  }

  function prepareInterface() {
    const notificationToggle = document.querySelector('#view-settings button[data-stage-one][aria-label="স্থানীয় নোটিফিকেশন"]');
    if (notificationToggle) {
      notificationToggle.removeAttribute('data-stage-one');
      notificationToggle.id = 'notifications-toggle';
      notificationToggle.setAttribute('aria-label', 'স্থানীয় নোটিফিকেশন চালু বা বন্ধ করুন');
      const row = notificationToggle.closest('.setting-row');
      const status = row?.querySelector('.setting-copy small');
      if (status) {
        status.id = 'notification-status';
        status.textContent = 'বন্ধ — চালু করতে চাপলে browser permission চাইবে।';
      }
      if (row) {
        row.insertAdjacentHTML('afterend', `<div class="notification-settings-controls"><p id="notification-limit" class="notification-limit">এটি বিনামূল্যের PWA-এর best-effort reminder। app/browser সক্রিয় থাকলে এবং notification অনুমতি থাকলে সময় মিলিয়ে জানাতে পারে; tab/PWA বন্ধ, Android-এ suspend বা ফোন restart হলে alarm নিশ্চিত নয়। ফোন ভাগাভাগি করলে lock screen-এ ওষুধের নাম দেখা যেতে পারে; notification action সব browser-এ নাও থাকতে পারে—app-এর status button থাকবে।</p><button id="test-notification" class="button button-outline test-notification" type="button" disabled>একটি পরীক্ষা নোটিফিকেশন</button></div>`);
      }
    }

    const voiceToggle = document.querySelector('#view-settings button[data-stage-one][aria-label="বাংলা কণ্ঠস্বর"]');
    if (voiceToggle) {
      voiceToggle.removeAttribute('data-stage-one');
      voiceToggle.id = 'voice-toggle';
      voiceToggle.setAttribute('aria-label', 'বাংলা কণ্ঠস্বর রিমাইন্ডার চালু বা বন্ধ করুন');
      const row = voiceToggle.closest('.setting-row');
      const status = row?.querySelector('.setting-copy small');
      if (status) {
        status.id = 'voice-status';
        status.textContent = 'বন্ধ — local Bengali voice থাকলে চালু করা যাবে।';
      }
      if (row) row.insertAdjacentHTML('afterend', `<div class="voice-settings-controls"><p class="voice-limit">Device-এর locally available Bengali Text-to-Speech voice ব্যবহার হয়; কোনো paid voice API বা voice server নেই। Voice ও উচ্চারণ device/browser অনুযায়ী বদলায়। Reminders শুধু app খোলা ও screen visible থাকলে বলার চেষ্টা করে।</p><button id="test-voice" class="button button-outline test-voice" type="button" disabled>বাংলা কণ্ঠস্বর পরীক্ষা</button></div>`);
    }

    const banner = document.querySelector('.demo-banner span');
    if (banner) banner.textContent = 'ওষুধ ও মজুতের আনুমানিক তথ্য এই browser-এই থাকে। Notification ও বাংলা কণ্ঠস্বর—দুটিই Settings থেকে আলাদাভাবে চালু করতে হয়।';
    const homeNote = document.querySelector('#view-home .stage-note span');
    if (homeNote) homeNote.textContent = 'Notification বা কণ্ঠস্বর—কোনোটিই নিশ্চিত alarm নয়; voice শুধু app খোলা ও screen visible থাকলে চেষ্টা করে। জরুরি সময়সূচির একমাত্র reminder হিসেবে ব্যবহার করবেন না।';
    const formNote = document.querySelector('.form-preview-note span');
    if (formNote) formNote.textContent = 'ওষুধ ও মজুতের তথ্য local storage-এ থাকে। “খেয়েছি” status দিলে stock নিজে কমবে না; মজুত আলাদাভাবে নথিভুক্ত করুন।';
    const version = document.querySelector('.about-block > small');
    if (version) version.textContent = 'Stage 5 • সংস্করণ ০.৫';

    const view = document.getElementById('view-history');
    if (view) {
      view.innerHTML = `<div class="page-heading"><p class="eyebrow">আপনার রেকর্ড</p><h1 id="history-title">দৈনিক ইতিহাস</h1><p class="muted">“খেয়েছি”, “পরে খাব” বা “নিশ্চিত নই”—শুধু আপনার নির্বাচিত status এখানে থাকে। কখনো নিজে থেকে “খেয়েছি” ধরা হয় না।</p></div><div class="history-date-card"><div><p class="eyebrow">নির্বাচিত দিন</p><h2 id="history-display-date"></h2></div><label class="history-date-control"><span class="visually-hidden">ইতিহাসের তারিখ বাছুন</span><input id="history-date" type="date" aria-label="ইতিহাসের তারিখ বাছুন"></label></div><div class="history-summary" aria-label="নির্বাচিত দিনের status-এর হিসাব"><div><strong id="history-count-taken">০</strong><span>খেয়েছি</span></div><div><strong id="history-count-later">০</strong><span>পরে খাব</span></div><div><strong id="history-count-unsure">০</strong><span>নিশ্চিত নই</span></div></div><div id="history-list" class="history-list"></div><div id="history-empty" class="empty-state history-empty"><span class="empty-icon"><svg class="icon"><use href="#icon-clock"/></svg></span><h2>এই দিনে কোনো status নেই</h2><p>আজকের ওষুধে status বেছে নিলে সেটি এই ডিভাইসের দৈনিক ইতিহাসে দেখা যাবে।</p></div><div class="notice-card"><svg class="icon"><use href="#icon-info"/></svg><p>ইতিহাস এই browser-এর local storage-এ থাকে; এটি চিকিৎসাগত প্রমাণ নয় এবং অন্য device-এ sync হয় না।</p></div>`;
      const dateInput = document.getElementById('history-date');
      dateInput.value = localISODate();
      dateInput.addEventListener('change', renderHistory);
    }
  }

  function statusCopy(record) {
    if (!record) return 'status বেছে নিন';
    if (record.status === 'taken') return 'আপনি “খেয়েছি” হিসেবে চিহ্নিত করেছেন';
    if (record.status === 'later') return record.snoozeUntil ? 'পরে খাব — ১০ মিনিটের reminder অপেক্ষায়' : 'আপনি “পরে খাব” হিসেবে চিহ্নিত করেছেন';
    if (record.status === 'unsure') return 'আপনি “নিশ্চিত নই” হিসেবে চিহ্নিত করেছেন';
    return 'status বেছে নিন';
  }
  function actionButton(action, label, medicine, date, time, current) {
    const selected = current === action;
    return `<button class="dose-action dose-action-${action}${selected ? ' is-selected' : ''}" type="button" data-dose-action="${action}" data-medicine-id="${escapeHtml(medicine.id)}" data-dose-date="${date}" data-dose-time="${time}" aria-pressed="${selected}" aria-label="${escapeHtml(medicine.name)} ${formatTime(time)} — ${label} হিসেবে চিহ্নিত করুন">${label}</button>`;
  }
  function renderDashboard() {
    const date = localISODate();
    const dateLabel = document.getElementById('current-date');
    if (dateLabel) dateLabel.textContent = formatDate(date);
    const medicines = readMedicines();
    const count = document.getElementById('active-count');
    if (count) count.textContent = new Intl.NumberFormat('bn-BD').format(medicines.length);
    const host = document.getElementById('today-list');
    if (!host) return;
    const items = medicines.filter(medicine => isScheduledOn(medicine, date)).flatMap(medicine => (Array.isArray(medicine.times) ? medicine.times : []).map(time => ({medicine, time}))).filter(item => /^\d{2}:\d{2}$/.test(item.time)).sort((a, b) => a.time.localeCompare(b.time) || a.medicine.name.localeCompare(b.medicine.name, 'bn'));
    if (!items.length) {
      host.innerHTML = `<div class="empty-state compact-empty"><span class="empty-icon"><svg class="icon"><use href="#icon-pill"/></svg></span><h3>${medicines.length ? 'আজ কোনো নির্ধারিত সময় নেই' : 'এখনো কোনো ওষুধ যোগ হয়নি'}</h3><p>${medicines.length ? 'তারিখ, রুটিন বা সপ্তাহের দিনগুলো দেখে নিন।' : 'ওষুধ যোগ করলে আজকের তালিকা এখানে দেখা যাবে।'}</p></div>`;
      return;
    }
    host.innerHTML = items.map(({medicine, time}) => {
      const record = recordFor(medicine, date, time);
      const current = record?.status || '';
      const instruction = [medicine.dose, medicine.instruction].filter(Boolean).join(' • ');
      return `<article class="reminder-card stage3-reminder-card"><div class="reminder-time"><span class="time-dot ${Number(time.slice(0,2)) < 15 ? 'morning' : 'evening'}"></span><strong>${formatTime(time)}</strong><span class="schedule-day">${WEEKDAYS[new Date().getDay()]}</span></div><div class="medicine-line"><span class="medicine-icon green"><svg class="icon"><use href="#icon-pill"/></svg></span><div class="medicine-info"><h3>${escapeHtml(medicine.name)}</h3><p>${escapeHtml(instruction)}</p></div></div><p class="dose-status-line ${record ? `is-${record.status}` : ''}">${escapeHtml(statusCopy(record))}</p><div class="dose-actions" aria-label="ওষুধ খাওয়ার status বাছুন">${actionButton('taken', 'খেয়েছি', medicine, date, time, current)}${actionButton('later', 'পরে খাব', medicine, date, time, current)}${actionButton('unsure', 'নিশ্চিত নই', medicine, date, time, current)}</div></article>`;
    }).join('');
  }
  function renderHistory() {
    const dateInput = document.getElementById('history-date');
    const list = document.getElementById('history-list');
    const empty = document.getElementById('history-empty');
    if (!dateInput || !list || !empty) return;
    const date = dateInput.value || localISODate();
    const display = document.getElementById('history-display-date');
    if (display) display.textContent = formatDate(date);
    const items = historyRecords.filter(item => item.date === date).sort((a, b) => a.time.localeCompare(b.time) || a.medicineName.localeCompare(b.medicineName, 'bn'));
    for (const status of ['taken', 'later', 'unsure']) {
      const node = document.getElementById(`history-count-${status}`);
      if (node) node.textContent = new Intl.NumberFormat('bn-BD').format(items.filter(item => item.status === status).length);
    }
    empty.hidden = items.length > 0;
    list.innerHTML = items.map(item => {
      const status = STATUS_TEXT[item.status] || STATUS_TEXT.pending;
      const detail = [item.dose, item.instruction].filter(Boolean).join(' • ');
      const className = item.status === 'taken' ? 'status-done' : item.status === 'later' ? 'status-later' : item.status === 'unsure' ? 'status-unknown' : 'status-pending';
      return `<article class="history-item"><span class="history-check ${item.status === 'unsure' ? 'history-unknown' : ''}" aria-hidden="true">${item.status === 'taken' ? '✓' : item.status === 'later' ? '↻' : item.status === 'unsure' ? '?' : '·'}</span><div class="history-text"><strong>${formatTime(item.time)} · ${escapeHtml(item.medicineName)}</strong><p>${escapeHtml(detail)}</p><small>${item.status === 'pending' ? 'এই reminder-এর status এখনো বাছাই করা হয়নি; notification delivery browser permission ও app-এর অবস্থার উপর নির্ভরশীল।' : `আপনি ${escapeHtml(status)} হিসেবে চিহ্নিত করেছেন।`}</small></div><span class="status-chip ${className}">${escapeHtml(status)}</span></article>`;
    }).join('');
  }
  function renderStage3() {
    historyRecords = readHistory();
    renderDashboard();
    renderHistory();
    updateNotificationSettings();
    updateVoiceSettings();
  }

  function updateNotificationSettings() {
    const toggle = document.getElementById('notifications-toggle');
    const state = document.getElementById('notification-status');
    const testButton = document.getElementById('test-notification');
    if (!toggle || !state) return;
    const supported = 'Notification' in window && 'serviceWorker' in navigator && window.isSecureContext;
    const permission = 'Notification' in window ? Notification.permission : 'unsupported';
    toggle.setAttribute('aria-checked', String(notificationsEnabled));
    if (!supported) state.textContent = 'এই browser/origin-এ web notification নেই; Android Chrome-এ HTTPS লিংক ব্যবহার করুন।';
    else if (permission === 'denied') state.textContent = 'Browser permission বন্ধ। চালু করতে browser-এর site settings-এ notification অনুমতি দিন।';
    else if (notificationsEnabled && permission === 'granted') state.textContent = 'চালু — best-effort reminder; app/browser সক্রিয় থাকতে হবে।';
    else if (permission === 'granted') state.textContent = 'Browser অনুমতি আছে, কিন্তু ঔষধসাথী reminder বন্ধ।';
    else state.textContent = 'বন্ধ — চালু করতে চাপলে browser permission চাইবে।';
    if (testButton) testButton.disabled = !(notificationsEnabled && permission === 'granted');
  }
  function localBengaliVoice() {
    if (!('speechSynthesis' in window) || !('SpeechSynthesisUtterance' in window)) return null;
    try {
      return speechSynthesis.getVoices().filter(voice => voice.localService === true && /^bn(?:[-_]|$)/i.test(voice.lang)).sort((a, b) => Number(b.default) - Number(a.default))[0] || null;
    } catch { return null; }
  }
  function updateVoiceSettings() {
    const toggle = document.getElementById('voice-toggle');
    const state = document.getElementById('voice-status');
    const testButton = document.getElementById('test-voice');
    if (!toggle || !state) return;
    const supported = 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window;
    const voice = localBengaliVoice();
    toggle.setAttribute('aria-checked', String(voiceEnabled));
    toggle.disabled = !supported || (!voice && !voiceEnabled);
    if (!supported) state.textContent = 'এই browser-এ device TTS নেই; অন্য Android browser বা phone TTS পরীক্ষা করুন।';
    else if (!voice) state.textContent = 'এই browser-এ local Bengali voice পাওয়া যায়নি। Android Text-to-speech settings-এ Bengali voice data install করুন।';
    else if (voiceEnabled) state.textContent = `চালু — ${voice.lang} voice; app খোলা ও screen visible থাকলে বলা চেষ্টা করবে।`;
    else state.textContent = `বন্ধ — local ${voice.lang} voice পাওয়া গেছে; চালু করতে চাপুন।`;
    if (testButton) testButton.disabled = !(voiceEnabled && !!voice);
  }
  function speakBengali(text, isTest = false) {
    const voice = localBengaliVoice();
    if (!voice || !voiceEnabled) return false;
    try {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.voice = voice;
      utterance.lang = voice.lang || 'bn-BD';
      utterance.rate = 0.92;
      utterance.pitch = 1;
      utterance.volume = 1;
      utterance.onerror = () => showToast('বাংলা কণ্ঠস্বর চালানো যায়নি। ফোনের Text-to-speech ও media volume পরীক্ষা করুন।');
      if (isTest) utterance.onstart = () => showToast('বাংলা কণ্ঠস্বর পরীক্ষা চলছে।');
      speechSynthesis.speak(utterance);
      return true;
    } catch {
      showToast('এই browser-এ বাংলা কণ্ঠস্বর চালু করা যায়নি।');
      return false;
    }
  }
  function toggleVoice() {
    if (voiceEnabled) {
      voiceEnabled = false;
      safeSet(VOICE_ENABLED_KEY, 'false');
      if ('speechSynthesis' in window) speechSynthesis.cancel();
      updateVoiceSettings();
      if (!notificationsEnabled) stopReminderLoop();
      showToast('বাংলা কণ্ঠস্বর বন্ধ হয়েছে।');
      return;
    }
    if (!('speechSynthesis' in window) || !('SpeechSynthesisUtterance' in window)) {
      updateVoiceSettings();
      showToast('এই browser-এ built-in Text-to-Speech নেই।');
      return;
    }
    if (!localBengaliVoice()) {
      updateVoiceSettings();
      showToast('Local Bengali voice পাওয়া যায়নি। Android Text-to-speech settings-এ Bengali voice data যোগ করুন।');
      return;
    }
    voiceEnabled = true;
    safeSet(VOICE_ENABLED_KEY, 'true');
    updateVoiceSettings();
    startReminderLoop();
    showToast('বাংলা কণ্ঠস্বর চালু। শোনার জন্য “বাংলা কণ্ঠস্বর পরীক্ষা” চাপুন।');
  }
  function sendTestVoice() {
    if (!voiceEnabled) return;
    const spoken = speakBengali('আপনার ওষুধ খাওয়ার সময় হয়েছে। অনুগ্রহ করে আপনার নির্ধারিত নির্দেশনা অনুসরণ করুন।', true);
    if (!spoken) showToast('বাংলা voice পাওয়া যায়নি। ফোনের TTS voice data পরীক্ষা করুন।');
  }
  function doseSpeechText(record, isSnooze = false) {
    const details = [record.medicineName, record.dose, record.instruction].filter(Boolean).join('। ');
    return `${isSnooze ? 'আবার মনে করিয়ে দিচ্ছি। ' : ''}আপনার ওষুধ খাওয়ার সময় হয়েছে। ${details ? `${details}। ` : ''}অনুগ্রহ করে আপনার নির্ধারিত নির্দেশনা অনুসরণ করুন।`;
  }
  if ('speechSynthesis' in window) speechSynthesis.addEventListener?.('voiceschanged', updateVoiceSettings);
  async function getServiceWorkerRegistration() {
    if (!('serviceWorker' in navigator) || !window.isSecureContext) return null;
    if (!serviceWorkerPromise) {
      serviceWorkerPromise = navigator.serviceWorker.register('./sw.js?v=stage5-nav1')
        .then(() => navigator.serviceWorker.ready)
        .then(registration => { serviceRegistration = registration; return registration; })
        .catch(() => null);
    }
    return serviceWorkerPromise;
  }
  function startReminderLoop() {
    clearInterval(reminderTimer);
    reminderTimer = setInterval(() => { void checkDueReminders(); }, POLL_MS);
    void checkDueReminders();
  }
  function stopReminderLoop() {
    clearInterval(reminderTimer);
    reminderTimer = null;
  }
  async function toggleNotifications() {
    if (notificationsEnabled) {
      notificationsEnabled = false;
      safeSet(ENABLED_KEY, 'false');
      if (!voiceEnabled) stopReminderLoop();
      updateNotificationSettings();
      showToast('ঔষধসাথীর স্থানীয় notification বন্ধ হয়েছে।');
      return;
    }
    if (!('Notification' in window) || !('serviceWorker' in navigator) || !window.isSecureContext) {
      showToast('এই browser/origin-এ notification নেই। Android Chrome-এ HTTPS preview খুলুন।');
      return;
    }
    try {
      const permission = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission();
      if (permission !== 'granted') {
        notificationsEnabled = false;
        safeSet(ENABLED_KEY, 'false');
        updateNotificationSettings();
        showToast(permission === 'denied' ? 'Browser permission বন্ধ। Site settings থেকে বদলান।' : 'অনুমতি না দেওয়ায় notification চালু হয়নি।');
        return;
      }
      const registration = await getServiceWorkerRegistration();
      if (!registration || typeof registration.showNotification !== 'function') {
        showToast('এই browser-এ service-worker notification চালু করা যায়নি।');
        return;
      }
      notificationsEnabled = true;
      safeSet(ENABLED_KEY, 'true');
      updateNotificationSettings();
      startReminderLoop();
      showToast('Local notification চালু। সময়মতো পেতে ঔষধসাথী app/browser চালু রাখুন।');
    } catch {
      showToast('Notification চালু করা যায়নি। HTTPS ও browser site permission দেখুন।');
    }
  }
  async function sendTestNotification() {
    if (!notificationsEnabled || !('Notification' in window) || Notification.permission !== 'granted') return;
    const registration = await getServiceWorkerRegistration();
    if (!registration) { showToast('Service worker এখন প্রস্তুত নয়।'); return; }
    try {
      await registration.showNotification('ঔষধসাথী পরীক্ষা', {
        body: 'আপনার browser notification permission কাজ করছে। এটি শুধু একটি পরীক্ষা।',
        icon: new URL('./oushodhsathi-icon.svg', location.href).href,
        tag: 'oushodhsathi-test',
        data: {kind: 'test'}
      });
      showToast('পরীক্ষা notification পাঠানো হয়েছে।');
    } catch {
      showToast('পরীক্ষা notification দেখানো যায়নি। Browser permission দেখুন।');
    }
  }
  function claimNotification(key, kind) {
    historyRecords = readHistory();
    const existing = findRecord(key);
    const now = Date.now();
    const isVoice = kind === 'voice' || kind === 'snooze-voice';
    const isSnooze = kind === 'snooze' || kind === 'snooze-voice';
    const claimField = isVoice ? (isSnooze ? 'snoozeVoiceClaimedAt' : 'voiceClaimedAt') : (isSnooze ? 'snoozeClaimedAt' : 'notificationClaimedAt');
    const sentField = isVoice ? (isSnooze ? 'snoozeVoiceAt' : 'voiceAt') : (isSnooze ? 'snoozeNotifiedAt' : 'notificationAt');
    if (existing?.[sentField]) return null;
    if (existing?.[claimField] && now - existing[claimField] < 2 * 60 * 1000) return null;
    const medicine = readMedicines().find(item => item.id === existing?.medicineId);
    if (!existing || !medicine) return null;
    const claimed = {...existing, [claimField]: now};
    return persistHistory(claimed) ? {record: claimed, medicine, claimField, sentField} : null;
  }
  async function displayDoseNotification(medicine, record, kind = 'scheduled') {
    const registration = serviceRegistration || await getServiceWorkerRegistration();
    if (!registration || typeof registration.showNotification !== 'function') return false;
    const isSnooze = kind === 'snooze';
    const instruction = [record.dose, record.instruction].filter(Boolean).join(' • ');
    const body = `${record.medicineName} — ${instruction || 'আপনার নির্ধারিত নির্দেশনা অনুসরণ করুন।'}${isSnooze ? ' • আবার মনে করানো হচ্ছে।' : ''}`;
    const data = {kind: 'dose', key: record.key, medicineId: medicine.id, date: record.date, time: record.time};
    try {
      await registration.showNotification(isSnooze ? 'ঔষধসাথী • আবার মনে করানো হচ্ছে' : 'ঔষধসাথী • ওষুধের সময়', {
        body,
        icon: new URL('./oushodhsathi-icon.svg', location.href).href,
        badge: new URL('./oushodhsathi-icon.svg', location.href).href,
        tag: `oushodhsathi-${record.key}${isSnooze ? '-snooze' : ''}`,
        timestamp: Date.now(),
        renotify: false,
        actions: [{action: 'taken', title: 'খেয়েছি'}, {action: 'snooze', title: '১০ মিনিট পরে'}],
        data
      });
      return true;
    } catch {
      return false;
    }
  }
  function updateClaim(key, claimField, sentField, sent) {
    historyRecords = readHistory();
    const current = findRecord(key);
    if (!current) return;
    const updated = {...current, [claimField]: 0};
    if (sent) updated[sentField] = Date.now();
    if (persistHistory(updated)) renderStage3();
  }
  async function checkDueReminders() {
    const canNotify = notificationsEnabled && 'Notification' in window && Notification.permission === 'granted';
    const canSpeak = voiceEnabled && !document.hidden && !!localBengaliVoice();
    if (checking || (!canNotify && !canSpeak)) return;
    checking = true;
    try {
      const registration = canNotify ? (serviceRegistration || await getServiceWorkerRegistration()) : null;
      const allowNotifications = canNotify && !!registration;
      const allowVoice = canSpeak;
      if (!allowNotifications && !allowVoice) return;
      const now = Date.now();
      const today = localISODate();
      const medicines = readMedicines();
      historyRecords = readHistory();

      for (const oldRecord of [...historyRecords]) {
        if (oldRecord.status !== 'later' || !oldRecord.snoozeUntil) continue;
        if (now < oldRecord.snoozeUntil || now > oldRecord.snoozeUntil + DUE_GRACE_MS) continue;
        if (allowNotifications && !oldRecord.snoozeNotifiedAt) {
          const claim = claimNotification(oldRecord.key, 'snooze');
          if (claim) {
            const shown = await displayDoseNotification(claim.medicine, claim.record, 'snooze');
            updateClaim(oldRecord.key, claim.claimField, claim.sentField, shown);
          }
        }
        if (allowVoice && !oldRecord.snoozeVoiceAt) {
          const claim = claimNotification(oldRecord.key, 'snooze-voice');
          if (claim) {
            const spoken = speakBengali(doseSpeechText(claim.record, true));
            updateClaim(oldRecord.key, claim.claimField, claim.sentField, spoken);
          }
        }
      }

      historyRecords = readHistory();
      for (const medicine of medicines) {
        if (!isScheduledOn(medicine, today) || !Array.isArray(medicine.times)) continue;
        for (const time of medicine.times) {
          if (!/^\d{2}:\d{2}$/.test(time)) continue;
          const [hour, minute] = time.split(':').map(Number);
          const due = new Date();
          due.setHours(hour, minute, 0, 0);
          const dueAt = due.getTime();
          if (now < dueAt || now > dueAt + DUE_GRACE_MS) continue;
          const key = doseKey(today, medicine.id, time);
          const existing = findRecord(key);
          if (existing && ['taken', 'unsure', 'later'].includes(existing.status)) continue;
          const needsNotification = allowNotifications && !existing?.notificationAt && !existing?.snoozeNotifiedAt;
          const needsVoice = allowVoice && !existing?.voiceAt;
          if (!needsNotification && !needsVoice) continue;
          const record = existing || newRecord(medicine, today, time, 'pending');
          if (!existing && !persistHistory(record)) continue;
          if (needsNotification) {
            const claim = claimNotification(key, 'scheduled');
            if (claim) {
              const shown = await displayDoseNotification(medicine, claim.record, 'scheduled');
              updateClaim(key, claim.claimField, claim.sentField, shown);
            }
          }
          if (needsVoice) {
            const claim = claimNotification(key, 'voice');
            if (claim) {
              const spoken = speakBengali(doseSpeechText(claim.record));
              updateClaim(key, claim.claimField, claim.sentField, spoken);
            }
          }
        }
      }
    } catch (error) {
      console.error('ঔষধসাথী reminder check failed.', error);
    } finally {
      checking = false;
    }
  }
  function updateDoseStatus(medicineId, date, time, status) {
    if (!['taken', 'later', 'unsure'].includes(status) || !/^\d{4}-\d{2}-\d{2}$/.test(date || '') || !/^\d{2}:\d{2}$/.test(time || '')) return;
    const medicines = readMedicines();
    const medicine = medicines.find(item => item.id === medicineId);
    const key = doseKey(date, medicineId, time);
    const existing = findRecord(key);
    if (!medicine && !existing) { showToast('এই ওষুধের তথ্য আর তালিকায় নেই।'); return; }
    const base = existing || newRecord(medicine, date, time, 'pending');
    const record = {...base, medicineId, medicineName: medicine?.name || base.medicineName, dose: medicine?.dose || base.dose, instruction: medicine?.instruction || base.instruction, date, time, status, updatedAt: Date.now()};
    if (status === 'later' && date === localISODate()) {
      record.snoozeUntil = Date.now() + SNOOZE_MS;
      record.snoozeNotifiedAt = 0;
      record.snoozeClaimedAt = 0;
      record.snoozeVoiceAt = 0;
      record.snoozeVoiceClaimedAt = 0;
    } else {
      record.snoozeUntil = 0;
      record.snoozeNotifiedAt = 0;
      record.snoozeClaimedAt = 0;
      record.snoozeVoiceAt = 0;
      record.snoozeVoiceClaimedAt = 0;
    }
    if (!persistHistory(record)) return;
    renderStage3();
    if (status === 'taken') showToast('আপনার নির্বাচিত “খেয়েছি” status সংরক্ষণ হয়েছে।');
    else if (status === 'later') showToast((notificationsEnabled || voiceEnabled) ? '“পরে খাব” সংরক্ষিত; ১০ মিনিট পরে best-effort reminder চেষ্টা হবে।' : '“পরে খাব” সংরক্ষিত; notification ও voice দুটিই বন্ধ—১০ মিনিটের alert আসবে না।');
    else showToast('আপনার নির্বাচিত “নিশ্চিত নই” status সংরক্ষণ হয়েছে।');
    if (notificationsEnabled) void checkDueReminders();
  }
  function processNotificationAction(payload) {
    if (!payload || !payload.medicineId || !payload.date || !payload.time) return;
    if (payload.action === 'taken') updateDoseStatus(payload.medicineId, payload.date, payload.time, 'taken');
    else if (payload.action === 'snooze') updateDoseStatus(payload.medicineId, payload.date, payload.time, 'later');
    else {
      renderStage3();
      showToast('নোটিফিকেশন খুলেছেন। আজকের তালিকায় status বেছে নিতে পারেন।');
    }
  }
  function consumeNotificationHash() {
    if (!location.hash) return;
    const raw = new URLSearchParams(location.hash.slice(1)).get('doseAction');
    if (!raw) return;
    try {
      const payload = JSON.parse(raw);
      history.replaceState(null, '', `${location.pathname}${location.search}`);
      setTimeout(() => processNotificationAction(payload), 0);
    } catch {
      history.replaceState(null, '', `${location.pathname}${location.search}`);
    }
  }

  prepareInterface();
  const notificationToggle = document.getElementById('notifications-toggle');
  notificationToggle?.addEventListener('click', () => { void toggleNotifications(); });
  document.getElementById('test-notification')?.addEventListener('click', () => { void sendTestNotification(); });
  document.getElementById('voice-toggle')?.addEventListener('click', toggleVoice);
  document.getElementById('test-voice')?.addEventListener('click', sendTestVoice);
  document.getElementById('history-list')?.addEventListener('click', event => {
    const button = event.target.closest('[data-dose-action]');
    if (button) updateDoseStatus(button.dataset.medicineId, button.dataset.doseDate, button.dataset.doseTime, button.dataset.doseAction);
  });
  document.getElementById('today-list')?.addEventListener('click', event => {
    const button = event.target.closest('[data-dose-action]');
    if (button) updateDoseStatus(button.dataset.medicineId, button.dataset.doseDate, button.dataset.doseTime, button.dataset.doseAction);
  });
  document.getElementById('medicine-form')?.addEventListener('submit', () => setTimeout(renderStage3, 0));
  document.getElementById('confirm-delete')?.addEventListener('click', () => setTimeout(renderStage3, 0));
  window.addEventListener('storage', event => {
    if ([MEDICINES_KEY, HISTORY_KEY, ENABLED_KEY, VOICE_ENABLED_KEY].includes(event.key)) {
      historyRecords = readHistory();
      notificationsEnabled = readEnabled();
      voiceEnabled = readVoiceEnabled();
      setTimeout(renderStage3, 0);
      if ((notificationsEnabled && 'Notification' in window && Notification.permission === 'granted') || voiceEnabled) startReminderLoop();
      else stopReminderLoop();
    }
  });
  navigator.serviceWorker?.addEventListener('message', event => {
    if (event.data?.type === 'oushodhsathi-notification-action') processNotificationAction(event.data.payload);
  });
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) { renderStage3(); void checkDueReminders(); }
  });
  window.addEventListener('focus', () => { void checkDueReminders(); });

  function initialize() {
    historyRecords = readHistory();
    if (!('Notification' in window) || Notification.permission !== 'granted') {
      notificationsEnabled = false;
      safeSet(ENABLED_KEY, 'false');
    }
    renderStage3();
    updateNotificationSettings();
    updateVoiceSettings();
    consumeNotificationHash();
    if (notificationsEnabled || voiceEnabled) {
      void getServiceWorkerRegistration().then(registration => {
        if ((registration && notificationsEnabled) || voiceEnabled) startReminderLoop();
      });
    }
  }
  if (document.readyState === 'loading' || document.readyState === 'interactive') document.addEventListener('DOMContentLoaded', initialize, {once: true});
  else setTimeout(initialize, 0);
})();
