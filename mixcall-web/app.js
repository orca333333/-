/* MIXCALL LAB — 앱 로직 (외부 의존성 없음) */
(() => {
  'use strict';
  const M = window.MIXCALL;
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const app = $('#app');
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  /* ───────── 저장소 (실패해도 동작) ───────── */
  const store = {
    get(k, d) { try { const v = localStorage.getItem('mixcall2.' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
    set(k, v) { try { localStorage.setItem('mixcall2.' + k, JSON.stringify(v)); } catch { /* 무시 */ } },
    del(k) { try { localStorage.removeItem('mixcall2.' + k); } catch { /* 무시 */ } },
  };
  const idb = {
    db: null,
    async open() {
      if (this.db) return this.db;
      this.db = await new Promise((res, rej) => {
        const r = indexedDB.open('mixcall2', 1);
        r.onupgradeneeded = () => r.result.createObjectStore('clips');
        r.onsuccess = () => res(r.result);
        r.onerror = () => rej(r.error);
      });
      return this.db;
    },
    async op(mode, fn) {
      const db = await this.open();
      return new Promise((res, rej) => {
        const t = db.transaction('clips', mode);
        const req = fn(t.objectStore('clips'));
        t.oncomplete = () => res(req && req.result);
        t.onerror = () => rej(t.error);
      });
    },
    put(k, v) { return this.op('readwrite', (s) => s.put(v, k)); },
    del(k) { return this.op('readwrite', (s) => s.delete(k)); },
    keys() { return this.op('readonly', (s) => s.getAllKeys()); },
    get(k) { return this.op('readonly', (s) => s.get(k)); },
  };

  const toast = (msg) => {
    const t = $('#toast'); t.textContent = msg; t.classList.add('show');
    clearTimeout(toast.h); toast.h = setTimeout(() => t.classList.remove('show'), 2400);
  };
  const fmt = (s) => {
    if (s == null || isNaN(s)) return '';
    const m = Math.floor(s / 60), r = s - m * 60;
    return `${m}:${r < 10 ? '0' : ''}${r.toFixed(1)}`;
  };
  const parseT = (v) => {
    if (v == null || String(v).trim() === '') return null;
    const p = String(v).trim().split(':').map(Number);
    if (p.some(isNaN)) return null;
    return p.reduce((a, x) => a * 60 + x, 0);
  };
  const ytId = (s) => {
    if (!s) return '';
    const m = String(s).match(/(?:v=|youtu\.be\/|embed\/|shorts\/|live\/)([\w-]{11})/);
    if (m) return m[1];
    return /^[\w-]{11}$/.test(String(s).trim()) ? String(s).trim() : '';
  };

  /* ───────── 테마 ───────── */
  const applyTheme = (t) => { if (t) document.documentElement.dataset.theme = t; else delete document.documentElement.dataset.theme; };
  applyTheme(store.get('theme', null));
  $('#themeBtn').addEventListener('click', () => {
    const cur = document.documentElement.dataset.theme || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    const next = cur === 'dark' ? 'light' : 'dark';
    applyTheme(next); store.set('theme', next);
  });

  /* ───────── 데이터 헬퍼 ───────── */
  const mixById = (id) => M.MIXES.find((m) => m.id === id);
  const songById = (id) => M.SONGS.find((s) => s.id === id);
  const fam = (f) => M.FAMILIES[f] || M.FAMILIES.aux;
  const nodeLines = (n) => {
    if (n.lines && n.lines.length) return n.lines;
    const mx = n.mix && mixById(n.mix);
    if (!mx) return [];
    if (mx.chain) return mx.chain.flatMap((id) => (mixById(id) || { lines: [] }).lines);
    return mx.lines;
  };
  const oshi = () => store.get('oshi', '');
  const showName = (t) => { const o = oshi(); return o ? String(t).replace(/○○/g, o) : t; };
  const confBadge = (c) => `<span class="badge b-${c}">${esc(M.CONF[c] || c)}</span>`;
  const songUses = (mixId) => {
    const out = [];
    M.SONGS.forEach((s) => s.nodes.forEach((n, i) => { if (n.mix === mixId) out.push({ s, n, i }); }));
    return out;
  };

  /* ───────── 음성: AI 클론 클립 → 일본어 TTS 폴백 ───────── */
  const Voice = {
    clips: new Map(),     // key -> objectURL
    audio: null,
    voice: null,
    async init() {
      try {
        const r = await fetch('voices/voices.json');
        if (r.ok) for (const k of await r.json()) this.clips.set(k, `voices/${encodeURIComponent(k)}.mp3`);
      } catch { /* 없음 */ }
      try {
        for (const k of await idb.keys()) {
          const rec = await idb.get(k);
          if (rec && rec.blob) this.clips.set(k, URL.createObjectURL(rec.blob));
        }
      } catch { /* IndexedDB 미지원 */ }
    },
    voices() { return 'speechSynthesis' in window ? speechSynthesis.getVoices().filter((v) => /^ja/i.test(v.lang)) : []; },
    pick() {
      const want = store.get('ttsVoice', '');
      const vs = this.voices();
      this.voice = vs.find((v) => v.name === want) || vs[0] || null;
      return this.voice;
    },
    text(ja) {
      let t = String(ja).replace(/（倍速）|\(倍速\)/g, '').replace(/👏/g, ' ').replace(/〔[^〕]*〕/g, '');
      t = t.replace(/○○/g, oshi() || ' ');
      t = t.replace(/([^\s×！!？?（）()・、]+)[！!]?\s*×\s*(\d+)/g, (m, w, n) => Array(Math.min(+n, 12)).fill(w).join(' '));
      return t.replace(/[（）()「」『』〔〕]/g, ' ').replace(/\s+/g, ' ').trim();
    },
    speak(ja, factor = 1) {
      return new Promise((resolve) => {
        const text = this.text(ja);
        if (!text || !('speechSynthesis' in window)) { setTimeout(resolve, 600); return; }
        const u = new SpeechSynthesisUtterance(text);
        u.lang = 'ja-JP';
        if (this.voice || this.pick()) u.voice = this.voice;
        u.rate = Math.min(2, store.get('ttsRate', 1.15) * factor);
        u.pitch = 1.05;
        let done = false;
        const fin = () => { if (!done) { done = true; clearTimeout(h); resolve(); } };
        const h = setTimeout(fin, Math.max(1800, (text.length * 230) / u.rate));
        u.onend = fin; u.onerror = fin;
        speechSynthesis.speak(u);
      });
    },
    playClip(key) {
      const src = this.clips.get(key);
      if (!src) return null;
      this.stopClip();
      const a = new Audio(src);
      this.audio = a;
      const p = new Promise((resolve) => { a.onended = resolve; a.onerror = resolve; });
      a.play().catch(() => {});
      return { audio: a, done: p };
    },
    stopClip() { if (this.audio) { this.audio.pause(); this.audio = null; } },
    stop() { if ('speechSynthesis' in window) speechSynthesis.cancel(); this.stopClip(); },
  };
  if ('speechSynthesis' in window) speechSynthesis.onvoiceschanged = () => Voice.pick();
  const voiceReady = Voice.init();

  /* ───────── 데모 비트 (Web Audio) ───────── */
  const Beat = {
    ctx: null, noise: null, timer: null, next: 0, n: 0, bpm: 160, running: false,
    ensure() {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return null;
        this.ctx = new AC();
        const len = this.ctx.sampleRate * 0.4;
        this.noise = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
        const d = this.noise.getChannelData(0);
        for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
      }
      if (this.ctx.state === 'suspended') this.ctx.resume();
      return this.ctx;
    },
    kick(t) {
      const c = this.ctx, o = c.createOscillator(), g = c.createGain();
      o.frequency.setValueAtTime(140, t); o.frequency.exponentialRampToValueAtTime(42, t + 0.12);
      g.gain.setValueAtTime(0.7, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.22);
      o.connect(g).connect(c.destination); o.start(t); o.stop(t + 0.24);
    },
    hit(t, type, f, dur, vol) {
      const c = this.ctx, s = c.createBufferSource(), fl = c.createBiquadFilter(), g = c.createGain();
      s.buffer = this.noise; fl.type = type; fl.frequency.value = f;
      g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
      s.connect(fl).connect(g).connect(c.destination); s.start(t); s.stop(t + dur + 0.02);
    },
    click(t, hi) {
      const c = this.ctx, o = c.createOscillator(), g = c.createGain();
      o.frequency.value = hi ? 1760 : 1180;
      g.gain.setValueAtTime(0.35, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.05);
      o.connect(g).connect(c.destination); o.start(t); o.stop(t + 0.06);
    },
    start(bpm, countIn = 0) {
      if (!this.ensure()) return 0;
      this.bpm = bpm;
      if (this.running) return 0;
      this.running = true; this.n = -countIn; this.next = this.ctx.currentTime + 0.06;
      const spb = () => 60 / this.bpm;
      const tick = () => {
        while (this.next < this.ctx.currentTime + 0.12) {
          const t = this.next;
          if (this.n < 0) this.click(t, this.n === -countIn);
          else {
            this.kick(t);
            if (this.n % 2 === 1) this.hit(t, 'bandpass', 1500, 0.14, 0.45);
            this.hit(t, 'highpass', 7500, 0.035, 0.12);
            this.hit(t + spb() / 2, 'highpass', 8200, 0.03, 0.09);
          }
          this.n++; this.next += spb();
        }
      };
      tick(); this.timer = setInterval(tick, 25);
      return countIn * spb() * 1000;
    },
    stop() { this.running = false; clearInterval(this.timer); },
  };

  /* ───────── YouTube (iframe postMessage — 외부 스크립트 없이 제어) ───────── */
  const YT = {
    iframe: null, id: '', time: 0, stamp: 0, state: -1, ready: false,
    mount(box, id, start = 0) {
      if (this.iframe && this.id === id && box.contains(this.iframe)) return;
      this.unmount();
      this.id = id;
      const f = document.createElement('iframe');
      f.src = `https://www.youtube.com/embed/${id}?enablejsapi=1&playsinline=1&rel=0&start=${Math.floor(start)}&origin=${encodeURIComponent(location.origin)}`;
      f.allow = 'autoplay; encrypted-media; picture-in-picture';
      f.title = 'YouTube 음원';
      f.addEventListener('load', () => { this.ready = true; this.post({ event: 'listening', id: 1, channel: 'widget' }); });
      box.innerHTML = ''; box.appendChild(f); this.iframe = f; this.time = start;
    },
    unmount() { if (this.iframe) this.iframe.remove(); this.iframe = null; this.id = ''; this.ready = false; this.state = -1; },
    post(o) { try { this.iframe && this.iframe.contentWindow.postMessage(JSON.stringify(o), '*'); } catch { /* 무시 */ } },
    cmd(func, args = []) { this.post({ event: 'command', func, args, id: 1, channel: 'widget' }); },
    seek(t) { this.cmd('seekTo', [t, true]); this.time = t; this.stamp = performance.now(); },
    play() { this.cmd('playVideo'); this.state = 1; this.stamp = performance.now(); },
    pause() { this.cmd('pauseVideo'); this.time = this.now(); this.state = 2; },
    now() { return this.state === 1 ? this.time + (performance.now() - this.stamp) / 1000 : this.time; },
  };
  window.addEventListener('message', (e) => {
    let host = ''; try { host = new URL(e.origin).hostname; } catch { return; }
    if (!/(^|\.)youtube(-nocookie)?\.com$/.test(host)) return;
    let d; try { d = typeof e.data === 'string' ? JSON.parse(e.data) : e.data; } catch { return; }
    if (!d || !d.info) return;
    if (typeof d.info.currentTime === 'number') { YT.time = d.info.currentTime; YT.stamp = performance.now(); }
    if (typeof d.info.playerState === 'number') YT.state = d.info.playerState;
  });

  /* ───────── 플로우 실행기 ───────── */
  const R = {
    song: null, sel: 0, on: -1, token: 0, playing: false, loop: false, rec: false, recPhase: 'start', done: new Set(),
    get mode() { return store.get('mode.' + this.song.id, this.ytid() ? 'yt' : 'beat'); },
    set mode(v) { store.set('mode.' + this.song.id, v); },
    get voiceOn() { return store.get('voiceOn', true); },
    get bpm() { return store.get('bpm.' + this.song.id, this.song.bpm || 160); },
    set bpm(v) { store.set('bpm.' + this.song.id, v); },
    ytid() { return ytId(store.get('yt.' + this.song.id, '') || this.song.yt); },
    tsAll() { return store.get('ts.' + this.song.id, {}); },
    ts(i) { const t = this.tsAll()[i]; return t && t[0] != null && t[1] != null ? t : null; },
    setTs(i, a, b) {
      const all = this.tsAll();
      if (a == null && b == null) delete all[i]; else all[i] = [a, b];
      store.set('ts.' + this.song.id, all);
    },
    clipKey(i) {
      const n = this.song.nodes[i];
      const own = `song:${this.song.id}:${i}`;
      if (Voice.clips.has(own)) return own;
      if (!n.lines && n.mix && Voice.clips.has(n.mix)) return n.mix;
      return null;
    },
    load(song) { this.stop(true); this.song = song; this.sel = 0; this.on = -1; this.done = new Set(); this.rec = false; this.recPhase = 'start'; },
    stop(silent) {
      this.token++; this.playing = false; this.on = -1;
      Voice.stop(); Beat.stop();
      if (YT.iframe && YT.state === 1) YT.pause();
      if (!silent) UI.refresh();
    },
    async run(from = this.sel, single = false) {
      this.stop(true);
      const my = ++this.token;
      this.playing = true; this.done = new Set();
      UI.refresh();
      let first = true;
      do {
        for (let i = from; i < this.song.nodes.length; i++) {
          if (my !== this.token) return;
          await this.playNode(i, my, first);
          first = false;
          if (my !== this.token) return;
          this.done.add(i);
          if (single) break;
        }
        from = 0; this.done = new Set();
      } while (this.loop && !single && my === this.token);
      if (my === this.token) { this.playing = false; this.on = -1; Beat.stop(); if (YT.state === 1) YT.pause(); UI.refresh(); UI.msg(''); }
    },
    async playNode(i, my, first) {
      const n = this.song.nodes[i];
      this.on = i; this.sel = i;
      UI.refresh(); UI.panel(i); UI.focus(i);
      const lines = nodeLines(n);
      const ts = this.ts(i);
      const factor = { bai: 1.5, san: 1.8, yon: 2 }[n.sp] || 1;
      const useYT = this.mode === 'yt' && ts && YT.iframe;

      if (useYT) {
        Beat.stop();
        YT.seek(ts[0]); YT.play();
        UI.msg(`▶ ${fmt(ts[0])} → ${fmt(ts[1])} 구간 재생`);
        const dur = Math.max(0.3, ts[1] - ts[0]);
        const speakAll = this.voiceOn ? this.say(lines, i, my, factor, dur * 1000) : null;
        while (my === this.token && YT.now() < ts[1]) {
          if (!speakAll) UI.hl(Math.min(lines.length - 1, Math.floor(((YT.now() - ts[0]) / dur) * lines.length)));
          await sleep(80);
        }
        if (my === this.token) YT.pause();
        if (speakAll) await speakAll;
        return;
      }

      if (this.mode === 'yt' && !ts) UI.msg('이 콜은 타임스탬프가 없어 데모 비트로 재생합니다');
      else UI.msg('');
      if (YT.state === 1) YT.pause();
      const wait = Beat.start(this.bpm, first ? 4 : 0);
      if (wait) await sleep(wait);
      if (my !== this.token) return;
      const barMs = (4 * 60000) / this.bpm;
      if (this.voiceOn) await this.say(lines, i, my, factor, null);
      else for (let k = 0; k < lines.length && my === this.token; k++) { UI.hl(k); await sleep((2 * barMs) / factor); }
      if (my === this.token) { UI.hl(lines.length); await sleep(barMs / 2); }
    },
    async say(lines, i, my, factor, durMs) {
      const key = this.clipKey(i);
      if (key) {
        const c = Voice.playClip(key);
        if (c) {
          const t0 = performance.now();
          const tick = setInterval(() => {
            const d = (c.audio.duration || 0) * 1000 || durMs || 4000;
            UI.hl(Math.min(lines.length - 1, Math.floor(((performance.now() - t0) / d) * lines.length)));
          }, 100);
          await c.done; clearInterval(tick);
          return;
        }
      }
      for (let k = 0; k < lines.length && my === this.token; k++) {
        UI.hl(k);
        await Voice.speak(lines[k][0], factor);
      }
    },
    stamp() {
      if (!YT.iframe) { toast('먼저 YouTube 영상을 연결하세요'); return; }
      const t = Math.round(YT.now() * 10) / 10;
      const i = this.sel;
      const cur = this.tsAll()[i] || [null, null];
      if (this.recPhase === 'start') {
        this.setTs(i, t, null); this.recPhase = 'end';
        toast(`${i + 1}번 콜 시작 ${fmt(t)}`);
      } else {
        let a = cur[0] ?? t, b = t;
        if (b < a) [a, b] = [b, a];
        this.setTs(i, a, b); this.recPhase = 'start';
        toast(`${i + 1}번 콜 끝 ${fmt(b)}`);
        if (i < this.song.nodes.length - 1) this.sel = i + 1;
      }
      UI.refresh(); UI.panel(this.sel); UI.focus(this.sel);
    },
  };

  /* ───────── 플로우 화면 UI ───────── */
  const UI = {
    refresh() {
      if (!R.song || !$('#flow')) return;
      $$('#flow .node').forEach((el) => {
        const i = +el.dataset.i;
        el.classList.toggle('sel', i === R.sel);
        el.classList.toggle('on', i === R.on);
        el.classList.toggle('done', R.done.has(i) && i !== R.on);
        const tsDot = $('.ts', el);
        if (tsDot) tsDot.hidden = !R.ts(i);
      });
      const run = $('#runBtn');
      if (run) { run.setAttribute('aria-pressed', String(R.playing)); run.innerHTML = R.playing ? '❚❚ <span>정지</span>' : '▶ <span>실행</span>'; }
      const rec = $('#recBtn');
      if (rec) { rec.setAttribute('aria-pressed', String(R.rec)); }
      const stampBtn = $('#stampBtn');
      if (stampBtn) { stampBtn.hidden = !R.rec; stampBtn.innerHTML = R.recPhase === 'start' ? `<span class="recdot"></span> ${R.sel + 1}번 시작 찍기` : `■ ${R.sel + 1}번 끝 찍기`; }
      const loop = $('#loopBtn'); if (loop) loop.setAttribute('aria-pressed', String(R.loop));
      const count = $('#tsCount'); if (count) count.textContent = `${Object.values(R.tsAll()).filter((t) => t && t[1] != null).length}/${R.song.nodes.length}`;
      if (R.rec) UI.msg(`● 기록 중 — 영상을 재생하고 ${R.sel + 1}번 콜의 ${R.recPhase === 'start' ? '시작' : '끝'}에서 [Space] 또는 버튼`);
    },
    msg(t) { const m = $('#stageMsg'); if (!m) return; m.hidden = !t; m.textContent = t; },
    focus(i) {
      const el = $(`#flow .node[data-i="${i}"]`);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' });
    },
    hl(k) {
      $$('#lines .ln').forEach((el, j) => { el.classList.toggle('on', j === k); el.classList.toggle('done', j < k); });
    },
    panel(i) {
      const p = $('#panel'); if (!p) return;
      if (i == null) { p.hidden = true; return; }
      const n = R.song.nodes[i];
      const f = fam(n.f);
      const mx = n.mix && mixById(n.mix);
      const lines = nodeLines(n);
      const ts = R.tsAll()[i] || [];
      const own = `song:${R.song.id}:${i}`;
      p.hidden = false;
      p.classList.toggle('withyt', !$('#ytcard').hidden);
      p.innerHTML = `
        <button class="btn icon close" id="pClose" aria-label="닫기">✕</button>
        <div class="hint">${i + 1} / ${R.song.nodes.length} · ${esc(n.sec || '')}</div>
        <h2 class="ja">${esc(n.t)}</h2>
        <div class="tags">
          <span class="tag" style="color:${f.color}">● ${esc(f.label)}</span>
          ${n.sp ? `<span class="tag">${esc(M.SPEEDS[n.sp].ja)} ${esc(M.SPEEDS[n.sp].label)}</span>` : ''}
          ${(n.m || []).map((x) => `<span class="tag">${esc(M.METHODS[x].ja)} ${esc(M.METHODS[x].label)}</span>`).join('')}
          ${mx ? `<a class="tag" href="#/mixes?open=${mx.id}" style="text-decoration:none">📣 ${esc(mx.name)}</a>` : ''}
        </div>
        ${n.cue ? `<div class="cue well"><b>큐</b><span class="ja">「${esc(n.cue)}」</span> 다음</div>` : ''}
        <div class="lines" id="lines">${lines.length ? lines.map((l) => `<div class="ln"><div class="j ja">${esc(showName(l[0]))}</div><div class="k">${esc(showName(l[1]))}</div></div>`).join('') : '<p class="hint">〔전문 확인 필요〕 구호 전문을 아직 확인하지 못했습니다.</p>'}</div>
        ${n.note ? `<ul class="notes"><li>${esc(n.note)}</li></ul>` : ''}
        <div class="tsbox well">
          <div class="hint">YouTube 구간 (분:초) — 이 구간만 재생합니다</div>
          <div class="tsrow"><label>시작</label><input id="tsA" value="${esc(fmt(ts[0]))}" inputmode="decimal" placeholder="0:00.0"><button class="btn small" data-now="A">지금</button></div>
          <div class="tsrow"><label>끝</label><input id="tsB" value="${esc(fmt(ts[1]))}" inputmode="decimal" placeholder="0:00.0"><button class="btn small" data-now="B">지금</button><button class="btn small" id="tsClear">지우기</button></div>
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:12px">
          <button class="btn" id="pPlay">▶ 이 콜만</button>
          <label class="btn file-btn">🎙 클론 음성 ${Voice.clips.has(own) ? '교체' : '등록'}<input type="file" accept="audio/*" id="pClip"></label>
          ${Voice.clips.has(own) ? '<button class="btn" id="pClipDel">클립 삭제</button>' : ''}
        </div>`;
      $('#pClose').onclick = () => { p.hidden = true; };
      $('#pPlay').onclick = () => R.run(i, true);
      const saveTs = () => { R.setTs(i, parseT($('#tsA').value), parseT($('#tsB').value)); UI.refresh(); };
      $('#tsA').onchange = saveTs; $('#tsB').onchange = saveTs;
      $$('[data-now]', p).forEach((b) => b.onclick = () => {
        if (!YT.iframe) { toast('먼저 YouTube 영상을 연결하세요'); return; }
        $('#ts' + b.dataset.now).value = fmt(Math.round(YT.now() * 10) / 10); saveTs();
      });
      $('#tsClear').onclick = () => { R.setTs(i, null, null); UI.panel(i); UI.refresh(); };
      $('#pClip').onchange = async (e) => {
        const file = e.target.files[0]; if (!file) return;
        try { await idb.put(own, { blob: file, name: file.name }); } catch { /* 이번 세션만 */ }
        Voice.clips.set(own, URL.createObjectURL(file)); toast('이 콜에 클론 음성을 등록했습니다'); UI.panel(i);
      };
      const del = $('#pClipDel');
      if (del) del.onclick = async () => { try { await idb.del(own); } catch { /* 무시 */ } Voice.clips.delete(own); UI.panel(i); };
    },
  };

  /* ───────── 렌더러 ───────── */
  const miniStrip = (nodes) => `<div class="mini">${nodes.slice(0, 14).map((n, k) => `${k ? '<b></b>' : ''}<i style="color:${fam(n.f).color}"></i>`).join('')}${nodes.length > 14 ? `<span class="hint" style="margin-left:6px">+${nodes.length - 14}</span>` : ''}</div>`;

  const views = {
    songs(q) {
      const groups = [...new Set(M.SONGS.map((s) => s.group))];
      return `
        <div class="pagehead">
          <div class="titlecard fcard"><h1>곡별 콜 플로우</h1></div>
          <div class="search grow"><span aria-hidden="true">🔍</span><input id="q" placeholder="곡 · 그룹 · 콜 이름 검색" value="${esc(q.get('q') || '')}" aria-label="곡 검색"></div>
        </div>
        <p class="lead">곡을 열면 콜이 순서대로 노드로 이어집니다. ▶ 실행을 누르면 YouTube 구간 또는 데모 비트에 맞춰 AI 음성이 콜을 외치고, 콜 가사가 강조됩니다.</p>
        <div class="chips" id="gchips"><span class="lbl">그룹</span><button class="chip" data-g="" aria-pressed="true">전체</button>${groups.map((g) => `<button class="chip" data-g="${esc(g)}" aria-pressed="false">${esc(g)}</button>`).join('')}</div>
        <div class="grid" id="list"></div>`;
    },
    flow(song, q) {
      const ytid = R.ytid();
      const fams = [...new Set(song.nodes.map((n) => n.f))];
      return `
        <div class="flowwrap" id="flowwrap"><div class="flow" id="flow">
          ${song.nodes.map((n, i) => {
            const f = fam(n.f);
            const nx = song.nodes[i + 1];
            return `<button class="node" data-i="${i}" style="--c:${f.color}" aria-label="${i + 1}번 콜 ${esc(n.t)}">
                <span class="orb"><span aria-hidden="true">${f.icon}</span><span class="ix">${i + 1}</span>${n.sp ? `<span class="sp">${esc(M.SPEEDS[n.sp].ja)}</span>` : ''}<span class="ts" hidden>⏱</span></span>
                <span class="nlabel ja">${esc(n.t)}</span>
                <span class="nsub ja">${esc(n.cue ? '「' + n.cue + '」' : (n.sec || ''))}</span>
              </button>${nx ? `<span class="conn" style="--c1:${f.color};--c2:${fam(nx.f).color}" aria-hidden="true"><i></i><i></i><i></i></span>` : ''}`;
          }).join('')}
        </div></div>

        <div class="tl">
          <div class="titlecard fcard"><a class="back" href="${song.practice ? '#/mixes' : '#/'}" aria-label="목록으로">←</a><h1 class="ja">${esc(song.title)}</h1><button class="btn icon" id="setBtn" aria-label="설정" style="box-shadow:none;background:none">⚙</button></div>
          <div class="pill fcard"><span class="spark">✦</span> ${esc(song.ko)} · ${esc(song.group)} <span class="beta">콜 ${song.nodes.length}</span></div>
          <div class="pill fcard" style="gap:8px">${fams.map((f) => `<span style="display:inline-flex;align-items:center;gap:4px"><span style="width:9px;height:9px;border-radius:50%;background:${fam(f).color}"></span>${esc(fam(f).label)}</span>`).join('')}</div>
        </div>

        <div class="tr fcard">
          <span class="hint" style="margin-right:4px">⏱ 음원</span>
          <div class="seg" role="group" aria-label="음원 선택">
            <button data-mode="yt" aria-pressed="${R.mode === 'yt'}">YouTube 구간</button>
            <button data-mode="beat" aria-pressed="${R.mode === 'beat'}">데모 비트</button>
          </div>
          <span class="sep"></span>
          <label class="toggle"><input type="checkbox" id="voiceOn" ${R.voiceOn ? 'checked' : ''}> AI 음성</label>
          <span class="sep"></span>
          <a class="btn icon" href="#/guide" aria-label="도움말" style="box-shadow:none;background:none">?</a>
        </div>

        <div class="stage-msg fcard" id="stageMsg" hidden></div>
        <div class="panel fcard" id="panel" hidden></div>
        <div class="ytcard fcard" id="ytcard" ${R.mode === 'yt' && ytid ? '' : 'hidden'}>
          <div class="vid" id="ytbox"></div>
          <div class="row"><span>구간 기록 <b id="tsCount"></b></span><span class="clock" id="ytclock">0:00.0</span></div>
        </div>

        <div class="pop fcard" id="pop" hidden>
          <div class="field"><label for="ytUrl">YouTube 영상 (라이브 본가 등)</label><input id="ytUrl" value="${esc(store.get('yt.' + song.id, '') || (song.yt ? 'https://youtu.be/' + song.yt : ''))}" placeholder="https://youtu.be/…"></div>
          ${song.live ? `<div class="hint">기본 영상: ${esc(song.live)}</div>` : ''}
          <div class="field"><label for="tsJson">구간 타임스탬프 백업 (JSON)</label><textarea id="tsJson" placeholder='{"0":[12.3,20.1]}'>${esc(JSON.stringify(R.tsAll()))}</textarea></div>
          <div style="display:flex;gap:8px;flex-wrap:wrap"><button class="btn" id="tsImport">가져오기</button><button class="btn" id="tsCopy">복사</button><button class="btn" id="tsReset">전체 지우기</button></div>
        </div>

        <div class="toolbar fcard" role="toolbar" aria-label="실행 도구">
          <button class="btn run" id="runBtn">▶ <span>실행</span></button>
          <span class="sep"></span>
          <button class="btn icon" id="prevBtn" aria-label="이전 콜">◀</button>
          <button class="btn icon" id="nextBtn" aria-label="다음 콜">▶</button>
          <button class="btn icon" id="loopBtn" aria-label="반복" aria-pressed="false">↻</button>
          <span class="sep"></span>
          <button class="btn rec" id="recBtn" aria-pressed="false" title="YouTube를 보며 콜 구간을 기록">● 기록</button>
          <button class="btn" id="stampBtn" hidden></button>
          <span class="sep"></span>
          <span class="bpm">BPM <button class="btn icon small" id="bpmDn" aria-label="BPM 내리기">−</button><b id="bpmV">${R.bpm}</b><button class="btn icon small" id="bpmUp" aria-label="BPM 올리기">+</button></span>
          <span class="sep"></span>
          <button class="btn icon" id="infoBtn" aria-label="콜 정보">ⓘ</button>
        </div>`;
    },
    mixes(q) {
      return `
        <div class="pagehead">
          <div class="titlecard fcard"><h1>MIX 라이브러리</h1></div>
          <div class="search grow"><span aria-hidden="true">🔍</span><input id="q" placeholder="이름 · 구호(예: 虎, 타이가아) 검색" value="${esc(q.get('q') || '')}" aria-label="MIX 검색"></div>
        </div>
        <p class="lead">노션 「MIX 전문집」 기준. 모듈을 누르면 전문(일본어 원문 + 한국어 발음)과 쓰이는 곡을 보고, 단독으로 연습할 수 있습니다.</p>
        <div class="chips" id="fchips"><span class="lbl">계열</span><button class="chip" data-f="" aria-pressed="true">전체</button>${Object.entries(M.FAMILIES).map(([k, f]) => `<button class="chip" data-f="${k}" aria-pressed="false"><span class="dot" style="background:${f.color}"></span>${esc(f.label)}</button>`).join('')}</div>
        <div id="list"></div>`;
    },
    browse(q) {
      const grp = (key, label, map) => `<div class="chips" data-k="${key}"><span class="lbl">${label}</span><button class="chip" data-v="" aria-pressed="${!q.get(key)}">전체</button>${Object.entries(map).map(([k, v]) => `<button class="chip" data-v="${k}" aria-pressed="${q.get(key) === k}">${v.color ? `<span class="dot" style="background:${v.color}"></span>` : ''}${esc(v.ja && key !== 'f' ? v.ja + ' ' + v.label : v.label)}</button>`).join('')}</div>`;
      return `
        <div class="pagehead"><div class="titlecard fcard"><h1>분류별 찾기</h1></div></div>
        <p class="lead">계열(무엇을 외치는가) × 속도 × 치는 방식, 세 축으로 MIX와 곡 속 콜을 찾습니다.</p>
        <div class="neu" style="padding:16px 16px 2px;margin-bottom:20px">
          ${grp('f', '계열', M.FAMILIES)}
          ${grp('sp', '속도', M.SPEEDS)}
          ${grp('m', '치는 방식', M.METHODS)}
        </div>
        <div id="list"></div>`;
    },
    voice() {
      const vs = Voice.voices();
      const withLines = M.MIXES.filter((m) => m.lines.length || m.chain);
      return `
        <div class="pagehead"><div class="titlecard fcard"><h1>나의 AI 음성</h1></div></div>
        <div class="neu prose" style="padding:18px 20px;margin-bottom:22px">
          <p style="margin-top:0"><b>AI 클론 음성</b>은 내 목소리 샘플로 만든 음성 클립을 MIX별(또는 곡의 콜 노드별)로 등록하면, 실행할 때 그 클립을 재생합니다. 등록되지 않은 콜은 브라우저의 <b>일본어 TTS</b>가 대신 외칩니다.</p>
          <p style="margin-bottom:0">클론 음성을 만들려면 조용한 곳에서 녹음한 1분 이상의 내 목소리 샘플이 필요합니다. 샘플을 주시면 콜별 클립을 만들어 <code>voices/</code> 폴더에 넣을 수 있습니다. 클립은 이 브라우저에만 저장됩니다(IndexedDB).</p>
        </div>
        <div class="grid" style="margin-bottom:22px">
          <div class="neu" style="padding:16px;display:grid;gap:12px">
            <div class="field"><label for="oshi">최애 이름 (○○ 자리에 들어감)</label><input id="oshi" value="${esc(oshi())}" placeholder="예: はるか"></div>
            <div class="field"><label for="ttsV">TTS 일본어 음성</label><select id="ttsV">${vs.length ? vs.map((v) => `<option ${store.get('ttsVoice', '') === v.name ? 'selected' : ''}>${esc(v.name)}</option>`).join('') : '<option>일본어 음성 없음 (기기 기본 음성 사용)</option>'}</select></div>
            <div class="field"><label for="ttsR">TTS 속도 <span id="ttsRv">${store.get('ttsRate', 1.15)}</span></label><input type="range" id="ttsR" min="0.7" max="1.8" step="0.05" value="${store.get('ttsRate', 1.15)}"></div>
            <button class="btn" id="ttsTest" style="justify-self:start">🔊 테스트: タイガー ファイヤー</button>
          </div>
        </div>
        <div class="section-title">MIX별 클론 음성 클립</div>
        <div class="neu" style="padding:6px 16px" id="vlist">${withLines.map((m) => `
          <div class="voice-row" data-id="${m.id}">
            <span class="morb" style="--c:${fam(m.family).color};width:34px;height:34px;font-size:14px">${fam(m.family).icon}</span>
            <div class="tx"><b class="ja">${esc(m.name)}</b> <span class="hint">${esc(m.ko)}</span><div class="hint" id="vs-${m.id}">${Voice.clips.has(m.id) ? '✅ 클론 음성 등록됨' : '— TTS 사용'}</div></div>
            <button class="btn small" data-test="${m.id}">▶</button>
            <label class="btn small file-btn">⬆<input type="file" accept="audio/*" data-up="${m.id}"></label>
            <button class="btn small" data-del="${m.id}" ${Voice.clips.has(m.id) ? '' : 'hidden'}>삭제</button>
          </div>`).join('')}</div>`;
    },
    guide() {
      const tbl = (map, withJa) => `<ul>${Object.values(map).map((v) => `<li><b>${withJa ? esc(v.ja) + ' ' : ''}${esc(v.label)}</b> — ${esc(v.desc)}</li>`).join('')}</ul>`;
      return `
        <div class="pagehead"><div class="titlecard fcard"><h1>가이드</h1></div></div>
        <div class="prose">
          <h2>사용 순서</h2>
          <ol>
            <li>곡을 열면 콜이 원형 노드로 이어집니다. 노드를 누르면 오른쪽에 콜 전문과 직전 가사 큐가 나옵니다.</li>
            <li>아래 툴바의 <b>▶ 실행</b>은 선택한 노드부터 끝까지 차례로 재생합니다. ↻ 반복, ◀ ▶ 이동.</li>
            <li><b>YouTube 구간</b> 모드에서는 각 콜의 시작~끝 구간만 재생합니다. 구간이 없는 콜은 데모 비트로 대신 재생합니다.</li>
            <li><b>● 기록</b>을 켜고 영상을 재생하면서 콜이 시작·끝날 때 [Space] 또는 버튼을 누르면 구간이 저장됩니다. 노드 패널에서 직접 고치거나, ⚙ 설정에서 JSON으로 백업할 수 있습니다.</li>
            <li><b>AI 음성</b>이 켜져 있으면 등록한 클론 음성 클립을, 없으면 일본어 TTS를 콜에 맞춰 재생합니다.</li>
          </ol>
          <h2>표기 규칙</h2>
          <ul>
            <li>한 줄에 일본어 원문, 아래 줄에 한국어 발음 (번역 없음)</li>
            <li>장음(ー)은 모음을 한 번 더 적음: タイガー → 타이가아</li>
            <li>ファ·フィ·フェ는 화·휘·훼, ツ는 츠, 촉음(ッ)은 앞 글자 받침 ㅅ</li>
            <li>멤버 이름·애칭은 ○○ (AI 음성 페이지에서 최애 이름을 넣으면 그 이름으로 표시·발음)</li>
            <li>콜 위치는 직전 가사 한 줄의 앞부분만 큐로 표시합니다. 가사 전문과 음원 전체는 사이트에 넣지 않습니다.</li>
            <li>확인하지 못한 구호는 추측하지 않고 〔확인 필요〕로 둡니다.</li>
          </ul>
          <h2>계열 (무엇을 외치는가)</h2>${tbl(M.FAMILIES, true)}
          <h2>속도</h2>${tbl(M.SPEEDS, true)}
          <h2>치는 방식</h2>${tbl(M.METHODS, true)}
          <h2>자료 신뢰도</h2>
          <p>${Object.entries(M.CONF).map(([k, v]) => `<span class="badge b-${k}">${esc(v)}</span>`).join(' ')}</p>
          <p class="hint">출처: 노션 「히로인즈 콜 — 가사·믹스콜 자료」, 「MIX 전문집」 (2026-10-12 기준). 팬 자료 기반이며 현장마다 다를 수 있습니다.</p>
        </div>`;
    },
    notFound() { return `<div class="titlecard fcard"><h1>찾을 수 없어요</h1></div><p class="lead" style="margin-top:16px"><a href="#/">곡 목록으로</a></p>`; },
  };

  /* ───────── 목록 페이지 동작 ───────── */
  const songCard = (s) => {
    const nts = Object.values(store.get('ts.' + s.id, {})).filter((t) => t && t[1] != null).length;
    const yt = ytId(store.get('yt.' + s.id, '') || s.yt);
    return `<a class="scard neu" href="#/song/${s.id}">
      ${miniStrip(s.nodes)}
      <h3 class="ja">${esc(s.title)}</h3>
      <div class="sub">${esc(s.ko)} · ${esc(s.group)}</div>
      <div class="meta"><span class="tag">콜 ${s.nodes.length}</span><span class="tag">${yt ? '▶ YouTube 연결' : '데모 비트'}</span>${nts ? `<span class="tag">⏱ 구간 ${nts}</span>` : ''}</div>
    </a>`;
  };
  const mixTile = (m) => `<button class="mtile neu" data-open="${m.id}" style="--c:${fam(m.family).color}">
      <span class="morb">${fam(m.family).icon}</span>
      <span class="tx"><span class="nm ja">${esc(m.name)}</span><span class="ko">${esc(m.ko)}</span></span>
      ${confBadge(m.conf)}
    </button>`;

  function openDrawer(id) {
    const m = mixById(id); if (!m) return;
    closeDrawer();
    const f = fam(m.family);
    const uses = songUses(id);
    const lines = m.chain ? m.chain.flatMap((c) => mixById(c).lines) : m.lines;
    const sc = document.createElement('div'); sc.className = 'scrim'; sc.id = 'scrim';
    const d = document.createElement('aside'); d.className = 'drawer'; d.id = 'drawer'; d.setAttribute('aria-label', m.name);
    d.innerHTML = `
      <button class="btn icon close" id="dClose" aria-label="닫기" style="float:right">✕</button>
      <span class="morb" style="--c:${f.color}">${f.icon}</span>
      <h2 class="ja">${esc(m.name)}</h2>
      <div class="hint">${esc(m.ko)}${m.alias ? ' · ' + esc(m.alias) : ''}</div>
      <div style="display:flex;flex-wrap:wrap;gap:6px;margin:12px 0">
        ${confBadge(m.conf)}
        <span class="tag" style="color:${f.color}">● ${esc(f.label)}</span>
        ${m.speeds.map((s) => `<span class="tag">${esc(M.SPEEDS[s].ja)}</span>`).join('')}
        ${m.methods.map((s) => `<span class="tag">${esc(M.METHODS[s].ja)}</span>`).join('')}
      </div>
      <div class="lines">${lines.length ? lines.map((l) => `<div class="ln well"><div class="j ja">${esc(showName(l[0]))}</div><div class="k">${esc(showName(l[1]))}</div></div>`).join('') : '<p class="hint">〔전문 확인 필요〕</p>'}</div>
      ${m.notes.length ? `<ul class="notes">${m.notes.map((n) => `<li>※ ${esc(n)}</li>`).join('')}</ul>` : ''}
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin:16px 0">
        ${lines.length ? `<a class="btn run" href="#/practice/${m.id}">▶ 단독 연습</a><button class="btn" id="dSay">🔊 미리 듣기</button>` : ''}
      </div>
      ${uses.length ? `<div class="section-title" style="margin-top:10px">쓰이는 곡</div><div style="display:grid;gap:8px">${uses.map(({ s, n, i }) => `<a class="btn" style="justify-content:space-between" href="#/song/${s.id}?n=${i}"><span class="ja">${esc(s.title)}</span><span class="hint ja">${esc(n.sec || (n.cue ? '「' + n.cue + '」' : ''))}</span></a>`).join('')}</div>` : ''}
      ${m.sources.length ? `<div class="section-title">출처</div><ul class="notes">${m.sources.map(([t, u]) => `<li><a href="${esc(u)}" target="_blank" rel="noopener">${esc(t)}</a></li>`).join('')}</ul>` : ''}`;
    document.body.append(sc, d);
    sc.onclick = closeDrawer; $('#dClose').onclick = closeDrawer;
    const say = $('#dSay');
    if (say) say.onclick = async () => { Voice.stop(); for (const l of lines) await Voice.speak(l[0]); };
  }
  function closeDrawer() { Voice.stop(); $('#drawer')?.remove(); $('#scrim')?.remove(); }

  const binders = {
    songs(q) {
      let g = '';
      const run = () => {
        const s = $('#q').value.trim().toLowerCase();
        const res = M.SONGS.filter((x) => (!g || x.group === g) && (!s || `${x.title} ${x.ko} ${x.group} ${x.nodes.map((n) => n.t).join(' ')}`.toLowerCase().includes(s)));
        $('#list').innerHTML = res.map(songCard).join('') || '<p class="empty">검색 결과 없음</p>';
      };
      $('#gchips').onclick = (e) => { const b = e.target.closest('.chip'); if (!b) return; g = b.dataset.g; $$('#gchips .chip').forEach((c) => c.setAttribute('aria-pressed', String(c === b))); run(); };
      $('#q').oninput = run; run();
    },
    mixes(q) {
      let f = '';
      const run = () => {
        const s = $('#q').value.trim().toLowerCase();
        const res = M.MIXES.filter((m) => (!f || m.family === f) && (!s || `${m.name} ${m.ko} ${m.alias} ${m.lines.flat().join(' ')}`.toLowerCase().includes(s)));
        const byF = Object.keys(M.FAMILIES).map((k) => [k, res.filter((m) => m.family === k)]).filter(([, a]) => a.length);
        $('#list').innerHTML = byF.map(([k, a]) => `<div class="section-title"><span class="dot" style="width:10px;height:10px;border-radius:50%;background:${fam(k).color};display:inline-block"></span>${esc(fam(k).label)} <span class="hint">${esc(fam(k).desc)}</span></div><div class="grid">${a.map(mixTile).join('')}</div>`).join('') || '<p class="empty">검색 결과 없음</p>';
      };
      $('#fchips').onclick = (e) => { const b = e.target.closest('.chip'); if (!b) return; f = b.dataset.f; $$('#fchips .chip').forEach((c) => c.setAttribute('aria-pressed', String(c === b))); run(); };
      $('#q').oninput = run;
      $('#list').onclick = (e) => { const t = e.target.closest('[data-open]'); if (t) openDrawer(t.dataset.open); };
      run();
      if (q.get('open')) openDrawer(q.get('open'));
    },
    browse(q) {
      const sel = { f: q.get('f') || '', sp: q.get('sp') || '', m: q.get('m') || '' };
      const run = () => {
        const mixes = M.MIXES.filter((m) => (!sel.f || m.family === sel.f) && (!sel.sp || m.speeds.includes(sel.sp)) && (!sel.m || m.methods.includes(sel.m)));
        const calls = [];
        M.SONGS.forEach((s) => s.nodes.forEach((n, i) => {
          const mx = n.mix && mixById(n.mix);
          const sp = n.sp || 'tou';
          const ms = [...new Set([...(n.m || []), ...(mx ? mx.methods : [])])];
          if ((!sel.f || n.f === sel.f) && (!sel.sp || sp === sel.sp) && (!sel.m || ms.includes(sel.m))) calls.push({ s, n, i });
        }));
        $('#list').innerHTML = `
          <div class="section-title">MIX ${mixes.length}개</div>
          <div class="grid">${mixes.map(mixTile).join('') || '<p class="empty">없음</p>'}</div>
          <div class="section-title">곡 속 콜 ${calls.length}개</div>
          <div class="grid">${calls.map(({ s, n, i }) => `<a class="mtile neu" style="--c:${fam(n.f).color};text-decoration:none" href="#/song/${s.id}?n=${i}"><span class="morb">${fam(n.f).icon}</span><span class="tx"><span class="nm ja">${esc(n.t)}</span><span class="ko ja">${esc(s.title)} · ${esc(n.cue ? '「' + n.cue + '」' : (n.sec || ''))}</span></span></a>`).join('') || '<p class="empty">없음</p>'}</div>`;
      };
      $$('.chips[data-k]').forEach((g) => g.onclick = (e) => {
        const b = e.target.closest('.chip'); if (!b) return;
        sel[g.dataset.k] = b.dataset.v;
        $$('.chip', g).forEach((c) => c.setAttribute('aria-pressed', String(c === b)));
        run();
      });
      $('#list').onclick = (e) => { const t = e.target.closest('[data-open]'); if (t) openDrawer(t.dataset.open); };
      run();
    },
    voice() {
      $('#oshi').onchange = (e) => store.set('oshi', e.target.value.trim());
      const sel = $('#ttsV'); if (sel) sel.onchange = (e) => { store.set('ttsVoice', e.target.value); Voice.pick(); };
      $('#ttsR').oninput = (e) => { store.set('ttsRate', +e.target.value); $('#ttsRv').textContent = e.target.value; };
      $('#ttsTest').onclick = () => { Voice.stop(); Voice.speak('タイガー ファイヤー'); };
      const list = $('#vlist');
      list.onclick = async (e) => {
        const t = e.target.closest('[data-test]'), d = e.target.closest('[data-del]');
        if (t) {
          Voice.stop();
          const c = Voice.playClip(t.dataset.test);
          if (!c) { const m = mixById(t.dataset.test); const ls = m.chain ? m.chain.flatMap((x) => mixById(x).lines) : m.lines; for (const l of ls) await Voice.speak(l[0]); }
        }
        if (d) {
          const id = d.dataset.del;
          try { await idb.del(id); } catch { /* 무시 */ }
          Voice.clips.delete(id); $('#vs-' + id).textContent = '— TTS 사용'; d.hidden = true;
        }
      };
      list.onchange = async (e) => {
        const inp = e.target.closest('[data-up]'); if (!inp || !inp.files[0]) return;
        const id = inp.dataset.up, file = inp.files[0];
        try { await idb.put(id, { blob: file, name: file.name }); } catch { /* 이번 세션만 */ }
        Voice.clips.set(id, URL.createObjectURL(file));
        $('#vs-' + id).textContent = `✅ 클론 음성 등록됨 (${file.name})`;
        $(`[data-del="${id}"]`).hidden = false;
      };
    },
    flow(song, q) {
      const wrap = $('#flowwrap');
      // 드래그로 캔버스 이동
      let drag = null;
      wrap.addEventListener('pointerdown', (e) => {
        if (e.target.closest('.node') || e.pointerType === 'touch') return;
        drag = { x: e.clientX, y: e.clientY, l: wrap.scrollLeft, t: wrap.scrollTop }; wrap.classList.add('dragging');
      });
      window.addEventListener('pointermove', onMove);
      window.addEventListener('pointerup', onUp);
      function onMove(e) { if (!drag) return; wrap.scrollLeft = drag.l - (e.clientX - drag.x); wrap.scrollTop = drag.t - (e.clientY - drag.y); }
      function onUp() { drag = null; wrap.classList.remove('dragging'); }
      cleanup.push(() => { window.removeEventListener('pointermove', onMove); window.removeEventListener('pointerup', onUp); });

      $('#flow').onclick = (e) => {
        const n = e.target.closest('.node'); if (!n) return;
        R.sel = +n.dataset.i; if (R.rec) R.recPhase = 'start';
        UI.refresh(); UI.panel(R.sel);
      };
      $('#flow').ondblclick = (e) => { const n = e.target.closest('.node'); if (n) R.run(+n.dataset.i, true); };

      const mountYT = () => {
        const id = R.ytid(); const card = $('#ytcard');
        card.hidden = !(R.mode === 'yt' && id);
        if (!card.hidden) YT.mount($('#ytbox'), id, song.ytStart || 0);
        $('#panel').classList.toggle('withyt', !card.hidden);
      };
      $$('[data-mode]').forEach((b) => b.onclick = () => {
        if (b.dataset.mode === 'yt' && !R.ytid()) { toast('⚙ 설정에서 YouTube 링크를 먼저 넣어주세요'); $('#pop').hidden = false; return; }
        R.stop(); R.mode = b.dataset.mode;
        $$('[data-mode]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
        mountYT();
      });
      $('#voiceOn').onchange = (e) => store.set('voiceOn', e.target.checked);
      $('#runBtn').onclick = () => { Beat.ensure(); R.playing ? R.stop() : R.run(R.sel); };
      $('#prevBtn').onclick = () => { R.sel = Math.max(0, R.sel - 1); if (R.playing) R.run(R.sel); else { UI.refresh(); UI.panel(R.sel); UI.focus(R.sel); } };
      $('#nextBtn').onclick = () => { R.sel = Math.min(song.nodes.length - 1, R.sel + 1); if (R.playing) R.run(R.sel); else { UI.refresh(); UI.panel(R.sel); UI.focus(R.sel); } };
      $('#loopBtn').onclick = () => { R.loop = !R.loop; UI.refresh(); };
      $('#recBtn').onclick = () => {
        if (!R.rec) {
          if (!R.ytid()) { toast('⚙ 설정에서 YouTube 링크를 먼저 넣어주세요'); $('#pop').hidden = false; return; }
          R.stop(); R.mode = 'yt'; $$('[data-mode]').forEach((x) => x.setAttribute('aria-pressed', String(x.dataset.mode === 'yt'))); mountYT();
          R.rec = true; R.recPhase = 'start';
        } else { R.rec = false; UI.msg(''); }
        UI.refresh();
      };
      $('#stampBtn').onclick = () => R.stamp();
      const bpm = (d) => { R.bpm = Math.max(60, Math.min(260, R.bpm + d)); $('#bpmV').textContent = R.bpm; Beat.bpm = R.bpm; };
      $('#bpmDn').onclick = () => bpm(-5); $('#bpmUp').onclick = () => bpm(5);
      $('#infoBtn').onclick = () => { const p = $('#panel'); if (p.hidden) UI.panel(R.sel); else p.hidden = true; };
      $('#setBtn').onclick = () => { const p = $('#pop'); p.hidden = !p.hidden; $('#tsJson').value = JSON.stringify(R.tsAll()); };
      $('#ytUrl').onchange = (e) => {
        const v = e.target.value.trim();
        if (v && !ytId(v)) { toast('YouTube 링크 형식이 아닙니다'); return; }
        store.set('yt.' + song.id, v); R.stop();
        if (v) { R.mode = 'yt'; $$('[data-mode]').forEach((x) => x.setAttribute('aria-pressed', String(x.dataset.mode === 'yt'))); }
        YT.unmount(); mountYT(); toast(v ? '영상을 연결했습니다' : '기본 영상으로 되돌렸습니다');
      };
      $('#tsImport').onclick = () => {
        try { const o = JSON.parse($('#tsJson').value || '{}'); if (typeof o !== 'object' || Array.isArray(o)) throw 0; store.set('ts.' + song.id, o); UI.refresh(); toast('구간을 가져왔습니다'); }
        catch { toast('JSON 형식이 올바르지 않습니다'); }
      };
      $('#tsCopy').onclick = () => { const t = $('#tsJson'); t.value = JSON.stringify(R.tsAll()); t.select(); try { navigator.clipboard.writeText(t.value); toast('복사했습니다'); } catch { /* 선택만 */ } };
      $('#tsReset').onclick = () => { if (confirm('이 곡의 구간 기록을 모두 지울까요?')) { store.del('ts.' + song.id); $('#tsJson').value = '{}'; UI.refresh(); UI.panel(R.sel); } };

      const onKey = (e) => {
        if (e.target.closest('input, textarea, select')) return;
        if (e.code === 'Space') { e.preventDefault(); if (R.rec) R.stamp(); else $('#runBtn').click(); }
        if (e.key === 'ArrowRight') $('#nextBtn').click();
        if (e.key === 'ArrowLeft') $('#prevBtn').click();
        if (e.key === 'Escape') { $('#panel').hidden = true; $('#pop').hidden = true; }
      };
      document.addEventListener('keydown', onKey);
      cleanup.push(() => document.removeEventListener('keydown', onKey));

      // 시계
      let raf = 0;
      const clock = () => { const c = $('#ytclock'); if (c) c.textContent = fmt(YT.iframe ? YT.now() : 0); raf = requestAnimationFrame(clock); };
      raf = requestAnimationFrame(clock);
      cleanup.push(() => cancelAnimationFrame(raf));

      mountYT();
      const start = Math.min(song.nodes.length - 1, Math.max(0, +(q.get('n') || 0)));
      R.sel = start;
      UI.refresh();
      if (q.has('n')) { UI.panel(start); setTimeout(() => UI.focus(start), 50); }
    },
    guide() {},
  };

  /* ───────── 라우터 ───────── */
  let cleanup = [];
  async function route() {
    cleanup.forEach((f) => f()); cleanup = [];
    closeDrawer();
    if (R.song) R.stop(true);
    const raw = location.hash.replace(/^#\/?/, '');
    const [path, qs] = raw.split('?');
    const q = new URLSearchParams(qs || '');
    const parts = path.split('/').filter(Boolean).map(decodeURIComponent);
    const name = parts[0] || 'songs';
    let html, bind = name, flow = false;
    if (name === 'song' || name === 'practice') {
      let song = null;
      if (name === 'song') song = songById(parts[1]);
      else {
        const m = mixById(parts[1]);
        if (m) {
          const nodes = m.chain
            ? m.chain.map((id) => ({ t: mixById(id).name, f: mixById(id).family, mix: id }))
            : [{ t: m.name, f: m.family, mix: m.id, sp: m.speeds.length === 1 && m.speeds[0] !== 'tou' ? m.speeds[0] : undefined }];
          song = { id: 'mix-' + m.id, title: m.name, ko: m.ko + ' 단독 연습', group: fam(m.family).label, bpm: 160, yt: '', ytStart: 0, nodes, practice: true };
        }
      }
      if (song) { R.load(song); html = views.flow(song, q); bind = 'flow'; flow = true; YT.unmount(); }
      else { html = views.notFound(); bind = null; }
    } else if (views[name] && name !== 'flow' && name !== 'notFound') {
      if (name === 'voice') await voiceReady;
      html = views[name](q);
    } else { html = views.notFound(); bind = null; }
    if (!flow) YT.unmount();
    app.className = 'canvas' + (flow ? ' flowmode' : '');
    app.innerHTML = html;
    const navKey = flow ? (R.song.practice ? 'mixes' : 'songs') : name;
    $$('.rail [data-nav]').forEach((a) => a.classList.toggle('active', a.dataset.nav === navKey));
    if (bind && binders[bind]) binders[bind](bind === 'flow' ? R.song : q, q);
    if (!flow) window.scrollTo(0, 0);
  }
  window.addEventListener('hashchange', route);
  voiceReady.then(() => {});
  route();
})();
