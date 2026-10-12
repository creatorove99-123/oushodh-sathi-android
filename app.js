(() => {
  'use strict';
  const STORAGE_KEY = 'oushodhsathi.medicines.v2';
  const WEEKDAYS = ['রবি', 'সোম', 'মঙ্গল', 'বুধ', 'বৃহস্পতি', 'শুক্র', 'শনি'];
  const views = [...document.querySelectorAll('.view')];
  const navItems = [...document.querySelectorAll('.nav-item')];
  const medicineDialog = document.getElementById('medicine-dialog');
  const deleteDialog = document.getElementById('delete-dialog');
  const stockDialog = document.getElementById('stock-dialog');
  const medicineForm = document.getElementById('medicine-form');
  const stockForm = document.getElementById('stock-form');
  const timeList = document.getElementById('time-list');
  const toast = document.getElementById('toast');
  let medicines = [];
  let editingId = null;
  let deletingId = null;
  let stockMedicineId = null;
  let toastTimer;
  let storageAvailable = true;

  const bnNumber = value => new Intl.NumberFormat('bn-BD').format(value);
  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 3200);
  }
  function localISODate(date = new Date()) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
  function safeNormalize(item) {
    if (!item || typeof item !== 'object' || !String(item.name || '').trim()) return null;
    const frequency = ['daily', 'specific-days', 'as-needed'].includes(item.frequency) ? item.frequency : 'daily';
    const times = Array.isArray(item.times) ? [...new Set(item.times.filter(t => typeof t === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(t)))].sort() : [];
    const days = Array.isArray(item.days) ? [...new Set(item.days.map(Number).filter(day => Number.isInteger(day) && day >= 0 && day <= 6))].sort() : [];
    const stockRemaining = item.stockRemaining === null || item.stockRemaining === undefined || item.stockRemaining === '' ? null : Number(item.stockRemaining);
    const stockLowAt = item.stockLowAt === null || item.stockLowAt === undefined || item.stockLowAt === '' ? 5 : Number(item.stockLowAt);
    const stockLog = Array.isArray(item.stockLog) ? item.stockLog.filter(entry => entry && ['used', 'set'].includes(entry.type) && Number.isFinite(Number(entry.amount)) && Number(entry.amount) >= 0 && Number.isFinite(Number(entry.remaining)) && Number(entry.remaining) >= 0).slice(-30).map(entry => ({type: entry.type, amount: Number(entry.amount), remaining: Number(entry.remaining), at: Number(entry.at) || Date.now()})) : [];
    return {
      id: String(item.id || makeId()),
      name: String(item.name).trim().slice(0, 100),
      dose: String(item.dose || '').trim().slice(0, 100),
      instruction: String(item.instruction || '').trim().slice(0, 160),
      frequency,
      times: frequency === 'as-needed' ? [] : times,
      days: frequency === 'specific-days' ? days : [],
      start: /^\d{4}-\d{2}-\d{2}$/.test(item.start || '') ? item.start : localISODate(),
      end: /^\d{4}-\d{2}-\d{2}$/.test(item.end || '') ? item.end : '',
      notes: String(item.notes || '').trim().slice(0, 300),
      stockEnabled: item.stockEnabled === true,
      stockRemaining: Number.isFinite(stockRemaining) && stockRemaining >= 0 ? stockRemaining : null,
      stockUnit: String(item.stockUnit || 'টি').trim().slice(0, 16) || 'টি',
      stockLowAt: Number.isFinite(stockLowAt) && stockLowAt >= 0 ? stockLowAt : 5,
      stockLog,
      createdAt: Number(item.createdAt) || Date.now(),
      updatedAt: Number(item.updatedAt) || Date.now()
    };
  }
  function makeId() {
    return globalThis.crypto?.randomUUID ? crypto.randomUUID() : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  }
  function readSaved() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) throw new Error('Invalid saved data');
      return parsed.map(safeNormalize).filter(Boolean);
    } catch (error) {
      storageAvailable = false;
      console.warn('ঔষধসাথী local storage could not be read.', error);
      setTimeout(() => showToast('এই browser-এর স্থানীয় তথ্য পড়া যাচ্ছে না। browser permission পরীক্ষা করুন।'), 300);
      return [];
    }
  }
  function persist(next) {
    if (!storageAvailable) {
      showToast('এই browser-এ local storage ব্যবহার করা যাচ্ছে না; কিছু সংরক্ষণ করা হয়নি।');
      return false;
    }
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      return true;
    } catch (error) {
      console.error('ঔষধসাথী local save failed.', error);
      showToast('সংরক্ষণ করা যায়নি। browser storage খালি আছে কি না পরীক্ষা করুন।');
      return false;
    }
  }
  function navigate(viewName) {
    const target = document.getElementById(`view-${viewName}`);
    if (!target) return;
    views.forEach(view => {
      const active = view === target;
      view.classList.toggle('active', active);
      view.hidden = !active;
    });
    navItems.forEach(item => {
      const active = item.dataset.view === viewName;
      item.classList.toggle('active', active);
      if (active) item.setAttribute('aria-current', 'page');
      else item.removeAttribute('aria-current');
    });
    window.scrollTo({top: 0, behavior: 'smooth'});
  }
  navItems.forEach(item => item.addEventListener('click', () => navigate(item.dataset.view)));
  document.querySelectorAll('[data-go]').forEach(item => item.addEventListener('click', () => navigate(item.dataset.go)));

  function setFormValue(name, value) {
    const field = medicineForm.elements.namedItem(name);
    if (field) field.value = value ?? '';
  }
  function refreshTimeControls() {
    const rows = [...timeList.querySelectorAll('.time-row')];
    rows.forEach((row, index) => {
      row.querySelector('input').setAttribute('aria-label', `ওষুধ খাওয়ার সময় ${bnNumber(index + 1)}`);
      const remove = row.querySelector('.remove-time');
      remove.disabled = rows.length <= 1;
      remove.setAttribute('aria-label', `সময় ${bnNumber(index + 1)} মুছুন`);
    });
  }
  function addTimeInput(value = '') {
    const current = timeList.querySelectorAll('.time-row').length;
    if (current >= 8) {
      showToast('একটি ওষুধের জন্য সর্বোচ্চ ৮টি সময় যোগ করা যাবে।');
      return;
    }
    const row = document.createElement('div');
    row.className = 'time-row';
    const label = document.createElement('label');
    label.className = 'time-field';
    const input = document.createElement('input');
    input.type = 'time';
    input.name = 'times[]';
    input.className = 'time-input';
    input.required = true;
    input.value = value;
    label.append(input);
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'remove-time';
    remove.textContent = '−';
    row.append(label, remove);
    timeList.append(row);
    refreshTimeControls();
  }
  function updateFrequencyFields() {
    const isAsNeeded = medicineForm.elements.namedItem('frequency').value === 'as-needed';
    const isSpecific = medicineForm.elements.namedItem('frequency').value === 'specific-days';
    document.getElementById('times-section').hidden = isAsNeeded;
    document.getElementById('weekday-picker').hidden = !isSpecific;
    timeList.querySelectorAll('input').forEach(input => { input.disabled = isAsNeeded; });
  }
  function updateStockFields() {
    const enabled = medicineForm.elements.namedItem('stockEnabled').checked;
    document.getElementById('stock-fields').hidden = !enabled;
    for (const name of ['stockRemaining', 'stockUnit', 'stockLowAt']) {
      const input = medicineForm.elements.namedItem(name);
      input.disabled = !enabled;
      input.required = enabled;
    }
  }
  document.getElementById('frequency').addEventListener('change', updateFrequencyFields);
  document.getElementById('stock-enabled').addEventListener('change', updateStockFields);
  document.getElementById('add-time').addEventListener('click', () => addTimeInput());
  timeList.addEventListener('click', event => {
    const button = event.target.closest('.remove-time');
    if (!button || timeList.querySelectorAll('.time-row').length <= 1) return;
    button.closest('.time-row').remove();
    refreshTimeControls();
  });

  function openMedicineForm(medicine = null) {
    editingId = medicine?.id || null;
    medicineForm.reset();
    document.getElementById('dialog-kicker').textContent = editingId ? 'তালিকা হালনাগাদ' : 'নতুন তালিকা';
    document.getElementById('dialog-title').textContent = editingId ? 'ওষুধ সম্পাদনা' : 'ওষুধ যোগ করুন';
    document.getElementById('save-medicine').textContent = editingId ? 'পরিবর্তন সংরক্ষণ করুন' : 'সংরক্ষণ করুন';
    document.getElementById('form-error').hidden = true;
    setFormValue('id', medicine?.id || '');
    setFormValue('name', medicine?.name || '');
    setFormValue('dose', medicine?.dose || '');
    setFormValue('instruction', medicine?.instruction || '');
    setFormValue('frequency', medicine?.frequency || 'daily');
    setFormValue('start', medicine?.start || localISODate());
    setFormValue('end', medicine?.end || '');
    setFormValue('notes', medicine?.notes || '');
    medicineForm.elements.namedItem('stockEnabled').checked = !!medicine?.stockEnabled;
    setFormValue('stockRemaining', medicine?.stockRemaining ?? '');
    setFormValue('stockUnit', medicine?.stockUnit || 'টি');
    setFormValue('stockLowAt', medicine?.stockLowAt ?? 5);
    timeList.replaceChildren();
    const initialTimes = medicine?.times?.length ? medicine.times : ['08:00'];
    initialTimes.forEach(time => addTimeInput(time));
    document.querySelectorAll('input[name="days"]').forEach(input => { input.checked = !!medicine?.days?.includes(Number(input.value)); });
    updateFrequencyFields();
    updateStockFields();
    if (!medicineDialog.open) medicineDialog.showModal();
    medicineForm.elements.namedItem('name').focus();
  }
  document.querySelectorAll('[data-open-medicine]').forEach(button => button.addEventListener('click', () => openMedicineForm()));
  document.getElementById('close-medicine-dialog').addEventListener('click', () => medicineDialog.close());
  document.getElementById('cancel-medicine').addEventListener('click', () => medicineDialog.close());

  function setFormError(message) {
    const error = document.getElementById('form-error');
    error.textContent = message;
    error.hidden = !message;
  }
  function collectFormMedicine() {
    const formData = new FormData(medicineForm);
    const frequency = String(formData.get('frequency') || 'daily');
    const enteredTimes = [...timeList.querySelectorAll('.time-input')].filter(input => !input.disabled).map(input => input.value).filter(Boolean);
    const times = [...new Set(enteredTimes)].sort();
    if (frequency !== 'as-needed' && times.length === 0) throw new Error('অন্তত একটি ওষুধ খাওয়ার সময় যোগ করুন।');
    if (frequency !== 'as-needed' && times.length !== enteredTimes.length) throw new Error('একই সময় একাধিকবার দেওয়া হয়েছে—সময়গুলো ঠিক করুন।');
    const days = [...document.querySelectorAll('input[name="days"]:checked')].map(input => Number(input.value)).sort();
    if (frequency === 'specific-days' && days.length === 0) throw new Error('সপ্তাহের অন্তত একটি দিন বেছে নিন।');
    const start = String(formData.get('start') || '');
    const end = String(formData.get('end') || '');
    if (end && start && end < start) throw new Error('শেষের তারিখ শুরুর তারিখের আগে হতে পারবে না।');
    const existing = editingId ? medicines.find(item => item.id === editingId) : null;
    const stockEnabled = medicineForm.elements.namedItem('stockEnabled').checked;
    const stockRaw = medicineForm.elements.namedItem('stockRemaining').value;
    const stockRemaining = stockRaw === '' ? (existing?.stockRemaining ?? null) : Number(stockRaw);
    const stockUnit = String(medicineForm.elements.namedItem('stockUnit').value || existing?.stockUnit || 'টি').trim().slice(0, 16);
    const stockLowRaw = medicineForm.elements.namedItem('stockLowAt').value;
    const stockLowAt = stockLowRaw === '' ? (existing?.stockLowAt ?? 5) : Number(stockLowRaw);
    if (stockEnabled && (!Number.isFinite(stockRemaining) || stockRemaining < 0)) throw new Error('মজুতের বর্তমান পরিমাণ লিখুন।');
    if (stockEnabled && (!stockUnit || !Number.isFinite(stockLowAt) || stockLowAt < 0)) throw new Error('মজুতের একক ও কম-মজুত সীমা ঠিক করুন।');
    return {
      id: editingId || makeId(),
      name: String(formData.get('name') || '').trim(),
      dose: String(formData.get('dose') || '').trim(),
      instruction: String(formData.get('instruction') || '').trim(),
      frequency,
      times: frequency === 'as-needed' ? [] : times,
      days: frequency === 'specific-days' ? days : [],
      start,
      end,
      notes: String(formData.get('notes') || '').trim(),
      stockEnabled,
      stockRemaining,
      stockUnit: stockUnit || 'টি',
      stockLowAt: Number.isFinite(stockLowAt) && stockLowAt >= 0 ? stockLowAt : 5,
      stockLog: existing?.stockLog || [],
      createdAt: existing?.createdAt || Date.now(),
      updatedAt: Date.now()
    };
  }
  medicineForm.addEventListener('submit', event => {
    event.preventDefault();
    setFormError('');
    if (!medicineForm.reportValidity()) return;
    let record;
    try { record = collectFormMedicine(); }
    catch (error) { setFormError(error.message); return; }
    const next = editingId ? medicines.map(item => item.id === editingId ? record : item) : [...medicines, record];
    if (!persist(next)) return;
    medicines = next;
    medicineDialog.close();
    renderAll();
    showToast(editingId ? 'পরিবর্তন সংরক্ষণ হয়েছে।' : 'ওষুধ তালিকায় যোগ হয়েছে।');
    editingId = null;
  });

  const medicineList = document.getElementById('medicine-list');
  medicineList.addEventListener('click', event => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const medicine = medicines.find(item => item.id === button.dataset.id);
    if (!medicine) return;
    if (button.dataset.action === 'edit') openMedicineForm(medicine);
    if (button.dataset.action === 'delete') openDeleteDialog(medicine);
    if (button.dataset.action === 'stock') openStockDialog(medicine);
  });
  function openDeleteDialog(medicine) {
    deletingId = medicine.id;
    document.getElementById('delete-copy').textContent = `“${medicine.name}” এই ডিভাইসের তালিকা থেকে মুছে যাবে। এই কাজটি ফিরিয়ে আনা যাবে না।`;
    deleteDialog.showModal();
  }
  function closeDeleteDialog() { deletingId = null; deleteDialog.close(); }
  document.getElementById('close-delete-dialog').addEventListener('click', closeDeleteDialog);
  document.getElementById('cancel-delete').addEventListener('click', closeDeleteDialog);
  document.getElementById('confirm-delete').addEventListener('click', () => {
    if (!deletingId) return closeDeleteDialog();
    const next = medicines.filter(item => item.id !== deletingId);
    if (!persist(next)) return;
    medicines = next;
    closeDeleteDialog();
    renderAll();
    showToast('ওষুধটি তালিকা থেকে মুছে ফেলা হয়েছে।');
  });

  function isLowStock(medicine) {
    return medicine.stockEnabled && Number.isFinite(medicine.stockRemaining) && medicine.stockRemaining <= medicine.stockLowAt;
  }
  function formatAmount(value) {
    return new Intl.NumberFormat('bn-BD', {maximumFractionDigits: 2}).format(Number(value) || 0);
  }
  function renderLowStockBanner() {
    const banner = document.getElementById('low-stock-banner');
    if (!banner) return;
    const low = medicines.filter(isLowStock);
    banner.hidden = low.length === 0;
    banner.innerHTML = low.length ? `<div class="low-stock-alert"><strong>মজুত কম: ${bnNumber(low.length)}টি ওষুধ</strong><span>${low.slice(0, 3).map(item => `${escapeHtml(item.name)} — ${formatAmount(item.stockRemaining)} ${escapeHtml(item.stockUnit)}`).join(' · ')}${low.length > 3 ? ` · আরও ${bnNumber(low.length - 3)}টি` : ''}</span><small>এটি আপনার লেখা আনুমানিক হিসাব; তালিকা থেকে পরিমাণ মিলিয়ে নিন।</small></div>` : '';
  }
  function renderStockSummary() {
    const host = document.getElementById('stock-summary');
    if (!host) return;
    const tracked = medicines.filter(item => item.stockEnabled);
    const low = tracked.filter(isLowStock).length;
    host.innerHTML = `<div class="stock-card"><div class="stock-icon"><svg class="icon"><use href="#icon-pill"/></svg></div><div><h2>ওষুধের মজুত</h2><p>${tracked.length ? `${bnNumber(tracked.length)}টি ওষুধের ব্যবহারকারী-লেখা হিসাব · ${bnNumber(low)}টি কম মজুত` : 'যে ওষুধের হিসাব রাখতে চান, সেটি যোগ বা সম্পাদনা করে মজুত চালু করুন।'} এটি যাচাইকৃত inventory নয়।</p></div><span class="stock-summary-tag">আনুমানিক</span></div>`;
  }
  function openStockDialog(medicine) {
    if (!medicine.stockEnabled || !Number.isFinite(medicine.stockRemaining)) {
      showToast('আগে সম্পাদনা করে মজুতের বর্তমান পরিমাণ লিখুন।');
      return;
    }
    stockMedicineId = medicine.id;
    stockForm.reset();
    stockForm.elements.namedItem('mode').value = 'used';
    stockForm.elements.namedItem('amount').value = '';
    document.getElementById('stock-copy').textContent = `${medicine.name} · এখন আনুমানিক ${formatAmount(medicine.stockRemaining)} ${medicine.stockUnit} বাকি।`;
    document.getElementById('stock-current').textContent = `বর্তমান বাকি: ${formatAmount(medicine.stockRemaining)} ${medicine.stockUnit}`;
    document.getElementById('stock-error').hidden = true;
    updateStockMode();
    if (!stockDialog.open) stockDialog.showModal();
  }
  function updateStockMode() {
    const used = stockForm.elements.namedItem('mode').value === 'used';
    document.getElementById('stock-amount-label').firstChild.textContent = used ? 'ব্যবহৃত পরিমাণ ' : 'বর্তমানে বাকি পরিমাণ ';
    document.getElementById('stock-mode-help').textContent = used ? 'পরিমাণটি বর্তমান আনুমানিক মজুত থেকে বাদ যাবে।' : 'এই পরিমাণটি বর্তমান মজুতের নতুন হিসাব হিসেবে রাখা হবে।';
  }
  document.getElementById('stock-mode').addEventListener('change', updateStockMode);
  document.getElementById('close-stock-dialog').addEventListener('click', () => stockDialog.close());
  document.getElementById('cancel-stock').addEventListener('click', () => stockDialog.close());
  stockForm.addEventListener('submit', event => {
    event.preventDefault();
    const error = document.getElementById('stock-error');
    error.hidden = true;
    if (!stockForm.reportValidity()) return;
    const medicine = medicines.find(item => item.id === stockMedicineId);
    if (!medicine) { stockDialog.close(); showToast('ওষুধটি তালিকায় নেই।'); return; }
    const amount = Number(stockForm.elements.namedItem('amount').value);
    const mode = stockForm.elements.namedItem('mode').value;
    if (!Number.isFinite(amount) || amount < 0 || (mode === 'used' && amount <= 0)) {
      error.textContent = 'ব্যবহৃত পরিমাণ শূন্যের বেশি হতে হবে; বাকি পরিমাণ শূন্যও হতে পারে।'; error.hidden = false; return;
    }
    if (mode === 'used' && amount > medicine.stockRemaining) {
      error.textContent = `বর্তমান হিসাব ${formatAmount(medicine.stockRemaining)} ${medicine.stockUnit}; এর চেয়ে বেশি ব্যবহার লিখবেন না।`; error.hidden = false; return;
    }
    const remaining = mode === 'used' ? Math.max(0, medicine.stockRemaining - amount) : amount;
    const entry = {type: mode, amount, remaining, at: Date.now()};
    const updated = {...medicine, stockRemaining: remaining, stockLog: [...(medicine.stockLog || []), entry].slice(-30), updatedAt: Date.now()};
    const next = medicines.map(item => item.id === medicine.id ? updated : item);
    if (!persist(next)) return;
    medicines = next;
    stockDialog.close();
    stockMedicineId = null;
    renderAll();
    showToast(isLowStock(updated) ? `মজুত কম: আনুমানিক ${formatAmount(remaining)} ${updated.stockUnit} বাকি।` : 'মজুতের আনুমানিক হিসাব আপডেট হয়েছে।');
  });

  function formatTime(value) {
    if (!value || !/^\d{2}:\d{2}$/.test(value)) return '';
    const [hour, minute] = value.split(':').map(Number);
    const date = new Date();
    date.setHours(hour, minute, 0, 0);
    return new Intl.DateTimeFormat('bn-BD', {hour: 'numeric', minute: '2-digit'}).format(date);
  }
  function formatDate(value) {
    if (!value) return '';
    const [y, m, d] = value.split('-').map(Number);
    return new Intl.DateTimeFormat('bn-BD', {day: 'numeric', month: 'short', year: 'numeric'}).format(new Date(y, m - 1, d));
  }
  function frequencyLabel(medicine) {
    if (medicine.frequency === 'as-needed') return 'প্রয়োজন অনুযায়ী';
    if (medicine.frequency === 'specific-days') return medicine.days.map(day => WEEKDAYS[day]).join(', ');
    return 'প্রতিদিন';
  }
  function scheduledToday(medicine, today = new Date()) {
    const current = localISODate(today);
    if (current < medicine.start || (medicine.end && current > medicine.end)) return false;
    if (medicine.frequency === 'as-needed') return false;
    if (medicine.frequency === 'specific-days') return medicine.days.includes(today.getDay());
    return true;
  }
  function renderDashboard() {
    document.getElementById('current-date').textContent = new Intl.DateTimeFormat('bn-BD', {weekday: 'long', day: 'numeric', month: 'long'}).format(new Date());
    document.getElementById('active-count').textContent = bnNumber(medicines.length);
    const host = document.getElementById('today-list');
    renderLowStockBanner();
    const scheduled = medicines.filter(item => scheduledToday(item)).flatMap(item => item.times.map(time => ({medicine: item, time}))).sort((a, b) => a.time.localeCompare(b.time));
    if (!scheduled.length) {
      const title = medicines.length ? 'আজ কোনো নির্ধারিত সময় নেই' : 'এখনো কোনো ওষুধ যোগ হয়নি';
      const copy = medicines.length ? 'আপনার ওষুধের তারিখ ও সাপ্তাহিক দিন দেখে নিন।' : 'ওষুধ যোগ করলে আজকের তালিকা এখানে দেখা যাবে।';
      host.innerHTML = `<div class="empty-state compact-empty"><span class="empty-icon"><svg class="icon"><use href="#icon-pill"/></svg></span><h3>${title}</h3><p>${copy}</p><button class="small-link" type="button" data-open-medicine><svg class="icon"><use href="#icon-plus"/></svg>ওষুধ যোগ করুন</button></div>`;
      host.querySelector('[data-open-medicine]').addEventListener('click', () => openMedicineForm());
      return;
    }
    host.innerHTML = scheduled.map(({medicine, time}) => `<article class="reminder-card"><div class="reminder-time"><span class="time-dot ${Number(time.slice(0,2)) < 15 ? 'morning' : 'evening'}"></span><strong>${escapeHtml(formatTime(time))}</strong></div><div class="medicine-line"><span class="medicine-icon green"><svg class="icon"><use href="#icon-pill"/></svg></span><div class="medicine-info"><h3>${escapeHtml(medicine.name)}</h3><p>${escapeHtml(medicine.dose)}${medicine.instruction ? ` • ${escapeHtml(medicine.instruction)}` : ''}</p></div></div></article>`).join('');
  }
  function renderMedicineList() {
    const host = document.getElementById('medicine-list');
    const empty = document.getElementById('medicine-empty');
    document.getElementById('medicine-count').textContent = bnNumber(medicines.length);
    empty.hidden = medicines.length > 0;
    document.querySelector('.add-wide').hidden = medicines.length === 0;
    renderStockSummary();
    const sorted = [...medicines].sort((a, b) => a.name.localeCompare(b.name, 'bn'));
    host.innerHTML = sorted.map(medicine => {
      const timeText = medicine.frequency === 'as-needed' ? 'নির্দিষ্ট সময় নেই' : medicine.times.map(formatTime).join(' · ');
      const duration = `${formatDate(medicine.start)} থেকে${medicine.end ? ` ${formatDate(medicine.end)} পর্যন্ত` : ' চলমান'}`;
      const notes = medicine.notes ? `<p class="medicine-note-text">নোট: ${escapeHtml(medicine.notes)}</p>` : '';
      const instruction = medicine.instruction ? `<p class="medicine-instruction">${escapeHtml(medicine.instruction)}</p>` : '';
      return `<article class="medicine-full-card"><div class="medicine-full-top"><span class="medicine-icon ${medicine.frequency === 'as-needed' ? 'gold' : 'green'}"><svg class="icon"><use href="#icon-pill"/></svg></span><div class="medicine-info"><h2>${escapeHtml(medicine.name)}</h2><p>${escapeHtml(medicine.dose)}</p></div></div><div class="medicine-meta"><span><svg class="icon"><use href="#icon-clock"/></svg>${escapeHtml(timeText)}</span><span><svg class="icon"><use href="#icon-sun"/></svg>${escapeHtml(frequencyLabel(medicine))}</span></div>${instruction}<div class="medicine-dates">${escapeHtml(duration)}</div>${notes}<div class="card-bottom-line"><span class="status-chip status-on">এই ডিভাইসে সংরক্ষিত</span><div class="medicine-actions"><button class="small-link" type="button" data-action="edit" data-id="${escapeHtml(medicine.id)}" aria-label="${escapeHtml(medicine.name)} সম্পাদনা"><svg class="icon"><use href="#icon-edit"/></svg>সম্পাদনা</button><button class="small-link danger-link" type="button" data-action="delete" data-id="${escapeHtml(medicine.id)}" aria-label="${escapeHtml(medicine.name)} মুছুন"><svg class="icon"><use href="#icon-trash"/></svg>মুছুন</button></div></div></article>`;
    }).join('');
    for (const editButton of host.querySelectorAll('[data-action="edit"]')) {
      const medicine = medicines.find(item => item.id === editButton.dataset.id);
      if (!medicine?.stockEnabled) continue;
      const card = editButton.closest('.medicine-full-card');
      const block = document.createElement('div');
      block.className = `medicine-stock${isLowStock(medicine) ? ' is-low' : ''}`;
      const info = document.createElement('div'); info.className = 'medicine-stock-copy';
      const title = document.createElement('strong'); title.textContent = 'মজুতের আনুমানিক হিসাব';
      const amount = document.createElement('span');
      amount.textContent = Number.isFinite(medicine.stockRemaining) ? `${formatAmount(medicine.stockRemaining)} ${medicine.stockUnit} বাকি · কম হলে ${formatAmount(medicine.stockLowAt)} ${medicine.stockUnit}` : 'পরিমাণ যোগ করতে সম্পাদনা করুন';
      info.append(title, amount);
      const last = medicine.stockLog?.at(-1);
      if (last) {
        const log = document.createElement('small');
        log.textContent = last.type === 'used' ? `শেষ নথি: ${formatAmount(last.amount)} ${medicine.stockUnit} ব্যবহার` : `শেষ নথি: বাকি ${formatAmount(last.remaining)} ${medicine.stockUnit} সেট`;
        info.append(log);
      }
      const update = document.createElement('button'); update.type = 'button'; update.className = 'stock-update-button'; update.dataset.action = 'stock'; update.dataset.id = medicine.id; update.textContent = 'পরিমাণ আপডেট';
      block.append(info, update);
      card.querySelector('.card-bottom-line')?.before(block);
    }
  }
  function renderAll() {
    renderDashboard();
    renderMedicineList();
  }

  document.querySelectorAll('[data-stage-one]').forEach(button => button.addEventListener('click', () => showToast('রিমাইন্ডার চিকিৎসক বা ফার্মাসিস্টের পরামর্শের বিকল্প নয়।')));
  const today = new Intl.DateTimeFormat('bn-BD', {weekday: 'long', day: 'numeric', month: 'long'}).format(new Date());
  document.getElementById('history-title').setAttribute('aria-label', `দৈনিক ইতিহাস, ${today}`);

  let installPrompt = null;
  window.addEventListener('beforeinstallprompt', event => { event.preventDefault(); installPrompt = event; });
  document.querySelector('.install-button').addEventListener('click', async () => {
    if (!installPrompt) { showToast('ব্রাউজারের ⋮ মেনু থেকে “হোম স্ক্রিনে যোগ করুন” বেছে নিন।'); return; }
    installPrompt.prompt();
    await installPrompt.userChoice;
    installPrompt = null;
  });
  document.getElementById('export-backup')?.addEventListener('click', () => {
    try {
      const backup = {
        schema: 'oushodhsathi.backup.v1',
        exportedAt: new Date().toISOString(),
        medicines: JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]'),
        history: JSON.parse(localStorage.getItem('oushodhsathi.history.v1') || '[]')
      };
      if (!Array.isArray(backup.medicines) || !Array.isArray(backup.history)) throw new Error('Invalid local backup data');
      const blob = new Blob([JSON.stringify(backup, null, 2)], {type: 'application/json'});
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `oushodhsathi-backup-${localISODate()}.json`;
      link.hidden = true;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 2000);
      showToast('Backup আপনার device-এ download হয়েছে। এটি ব্যক্তিগত স্বাস্থ্যতথ্য—সাবধানে রাখুন।');
    } catch (error) {
      console.warn('ঔষধসাথী backup export failed.', error);
      showToast('Backup download করা যায়নি। Browser storage permission পরীক্ষা করুন।');
    }
  });
  window.addEventListener('storage', event => {
    if (event.key !== STORAGE_KEY) return;
    medicines = readSaved();
    renderAll();
    showToast('অন্য tab থেকে তালিকা হালনাগাদ হয়েছে।');
  });
  medicines = readSaved();
  renderAll();
  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
    window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js?v=stage4-5').catch(() => {}));
  }
})();
