/* MIXCALL LAB — 앱 로직 (의존성 없음, 정적 호스팅용) */
(() => {
  'use strict';
  const D = window.MIXCALL;
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const app = $('#app');
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /* ---------- 저장소 (실패해도 동작) ---------- */
  const store = {
    get(k, d) { try { const v = localStorage.getItem('mixcall.' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
    set(k, v) { try { localStorage.setItem('mixcall.' + k, JSON.stringify(v)); } catch { /* 무시 */ } },
  };

  const idb = {
    db: null,
    async open() {
      if (this.db) return this.db;
      this.db = await new Promise((res, rej) => {
        const r = indexedDB.open('mixcall', 1);
        r.onupgradeneeded = () => r.result.createObjectStore('voices');
        r.onsuccess = () => res(r.result);
        r.onerror = () => rej(r.error);
      });
      return this.db;
    },
    async tx(mode, fn) {
      const db = await this.open();
      return new Promise((res, rej) => {
        const t = db.transaction('voices', mode);
        const req = fn(t.objectStore('voices'));
        t.oncomplete = () => res(req && req.result);
        t.onerror = () => rej(t.error);
      });
    },
    put(k, v) { return this.tx('readwrite', (s) => s.put(v, k)); },
    del(k) { return this.tx('readwrite', (s) => s.delete(k)); },
    get(k) { return this.tx('readonly', (s) => s.get(k)); },
  };

  /* ---------- 테마 ---------- */
  const applyTheme = (t) => { if (t) document.documentElement.dataset.theme = t; else delete document.documentElement.dataset.theme; };
  applyTheme(store.get('theme', null));
  $('#themeBtn').addEventListener('click', () => {
    const dark = document.documentElement.dataset.theme
      ? document.documentElement.dataset.theme === 'dark'
      : matchMedia('(prefers-color-scheme: dark)').matches;
    const next = dark ? 'light' : 'dark';
    applyTheme(next); store.set('theme', next);
  });

  /* ---------- 데이터 헬퍼 ---------- */
  const mixById = (id) => D.MIXES.find((m) => m.id === id);
  const mixLen = (m) => Math.max(...m.words.map((w) => w.b + w.len));
  const userSongs = () => store.get('userSongs', []);
  const allSongs = () => [...D.SONGS, ...userSongs()];
  const songById = (id) => allSongs().find((s) => s.id === id);
  const oshi = () => store.get('oshi', '');
  const showText = (t) => { const o = oshi(); return o ? t.replace(/（推し）|\(최애 이름\)|\(최애\)/g, o) : t; };
  const confBadge = (c) => ({
    cross: '<span class="badge b-cross">교차 확인</span>',
    single: '<span class="badge b-single">단일 출처</span>',
    unverified: '<span class="badge b-unverified">확인 필요</span>',
  }[c] || '');
  const ytId = (s) => {
    if (!s) return '';
    const m = String(s).match(/(?:v=|youtu\.be\/|embed\/|shorts\/|live\/)([\w-]{11})/);
    if (m) return m[1];
    return /^[\w-]{11}$/.test(s.trim()) ? s.trim() : '';
  };
  const fmt = (sec) => { const s = Math.max(0, sec); return `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`; };

  /* ---------- AI 음성 (클론 클립 → TTS 폴백) ---------- */
  const Voice = {
    clips: {},        // mixId -> objectURL / 경로
    jaVoice: null,
    async loadClips() {
      // 저장소 배포용: voices/voices.json 에 적힌 콜 ID → voices/<id>.mp3
      try {
        const r = await fetch('voices/voices.json');
        if (r.ok) for (const id of await r.json()) this.clips[id] = `voices/${id}.mp3`;
      } catch { /* 없음 */ }
      // 브라우저에 업로드한 클립이 우선
      for (const m of D.MIXES) {
        try {
          const rec = await idb.get(m.id);
          if (rec && rec.blob) this.clips[m.id] = URL.createObjectURL(rec.blob);
        } catch { /* IndexedDB 미지원 */ }
      }
    },
    pickVoice() {
      if (!('speechSynthesis' in window)) return null;
      const vs = speechSynthesis.getVoices();
      this.jaVoice = vs.find((v) => /ja[-_]JP/i.test(v.lang)) || vs.find((v) => /^ja/i.test(v.lang)) || null;
      return this.jaVoice;
    },
    speechText(ja) {
      if (/［/.test(ja)) return '';
      let t = ja.replace(/[^（）\s]+（([ぁ-んー\s]+)）/g, '$1');
      t = t.replace(/（推し）/g, oshi() || 'おし');
      t = t.replace(/（[^）]*）/g, '');
      return t.trim();
    },
    speak(ja, bpm) {
      if (!('speechSynthesis' in window)) return;
      const text = this.speechText(ja);
      if (!text) return;
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = 'ja-JP';
      if (this.jaVoice || this.pickVoice()) u.voice = this.jaVoice;
      u.rate = Math.min(2, Math.max(0.9, (bpm / 120) * 1.15));
      u.pitch = 1.05;
      speechSynthesis.speak(u);
    },
    audio: null,
    playClip(mixId) {
      const src = this.clips[mixId];
      if (!src) return false;
      if (this.audio) this.audio.pause();
      this.audio = new Audio(src);
      this.audio.play().catch(() => {});
      return true;
    },
    stop() {
      if ('speechSynthesis' in window) speechSynthesis.cancel();
      if (this.audio) { this.audio.pause(); this.audio = null; }
    },
  };
  if ('speechSynthesis' in window) speechSynthesis.onvoiceschanged = () => Voice.pickVoice();
  Voice.loadClips();

  /* ---------- 데모 비트 (Web Audio) ---------- */
  const Beat = {
    ctx: null, noise: null,
    ensure() {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        this.ctx = new AC();
        const len = this.ctx.sampleRate * 0.5;
        this.noise = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
        const d = this.noise.getChannelData(0);
        for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
      }
      if (this.ctx.state === 'suspended') this.ctx.resume();
      return this.ctx;
    },
    kick(t, vol = 0.9) {
      const c = this.ctx, o = c.createOscillator(), g = c.createGain();
      o.frequency.setValueAtTime(150, t); o.frequency.exponentialRampToValueAtTime(40, t + 0.12);
      g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.25);
      o.connect(g).connect(c.destination); o.start(t); o.stop(t + 0.26);
    },
    noiseHit(t, type, freq, dur, vol) {
      const c = this.ctx, s = c.createBufferSource(), f = c.createBiquadFilter(), g = c.createGain();
      s.buffer = this.noise; f.type = type; f.frequency.value = freq;
      g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
      s.connect(f).connect(g).connect(c.destination); s.start(t); s.stop(t + dur + 0.02);
    },
    click(t, hi) {
      const c = this.ctx, o = c.createOscillator(), g = c.createGain();
      o.frequency.value = hi ? 1800 : 1200;
      g.gain.setValueAtTime(0.4, t); g.gain.exponentialRampToValueAtTime(0.001, t + 0.06);
      o.connect(g).connect(c.destination); o.start(t); o.stop(t + 0.07);
    },
    hit(n, t) {
      if (n < 0) { this.click(t, n === -4); return; }
      this.kick(t);
      if (n % 2 === 1) this.noiseHit(t, 'bandpass', 1500, 0.16, 0.6);   // 클랩 (2·4박)
      this.noiseHit(t + 0, 'highpass', 7000, 0.04, 0.18);                 // 하이햇
    },
    offHat(t) { this.noiseHit(t, 'highpass', 8000, 0.03, 0.12); },
  };

  /* ---------- YouTube (iframe postMessage — 외부 스크립트 없이 제어) ---------- */
  const YT = {
    iframe: null, time: 0, stamp: 0, state: -1, ready: false,
    mount(box, id) {
      this.unmount();
      const f = document.createElement('iframe');
      f.src = `https://www.youtube.com/embed/${id}?enablejsapi=1&playsinline=1&rel=0&origin=${encodeURIComponent(location.origin)}`;
      f.allow = 'autoplay; encrypted-media';
      f.title = 'YouTube 음원';
      f.addEventListener('load', () => {
        this.ready = true;
        this.post({ event: 'listening', id: 1, channel: 'widget' });
      });
      box.innerHTML = ''; box.appendChild(f); this.iframe = f;
    },
    unmount() { if (this.iframe) this.iframe.remove(); this.iframe = null; this.ready = false; this.time = 0; this.state = -1; },
    post(o) { try { this.iframe && this.iframe.contentWindow.postMessage(JSON.stringify(o), '*'); } catch { /* 무시 */ } },
    cmd(func, args = []) { this.post({ event: 'command', func, args, id: 1, channel: 'widget' }); },
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

  /* ---------- 플레이어 엔진 ---------- */
  const COUNT_IN = 4;
  const P = {
    song: null, source: 'beat', voiceMode: 'tts', playing: false, loop: false,
    t0: 0, pauseBeat: 0, timer: null, raf: 0, nextBeat: 0, voiceEvents: [], vIdx: 0, lastBeat: -99,
    get spb() { return 60 / this.song.bpm; },
    load(song) {
      this.stop(true);
      this.song = song; this.pauseBeat = 0; this.lastBeat = -99;
      this.source = store.get('source', 'beat');
      if (this.source === 'yt' && !this.ytId()) this.source = 'beat';
      this.voiceMode = store.get('voiceMode', 'clone');
    },
    ytId() { return ytId(store.get('yt.' + this.song.id, '') || this.song.youtubeId); },
    ytOffset() { const v = store.get('ytOff.' + this.song.id, null); return v == null ? (this.song.ytOffset || 0) : v; },
    beatNow() {
      if (!this.playing) return this.pauseBeat;
      if (this.source === 'yt') return (YT.now() - this.ytOffset()) / this.spb;
      return (Beat.ctx.currentTime - this.t0) / this.spb - COUNT_IN;
    },
    buildVoice() {
      const ev = [];
      for (const it of this.song.timeline) {
        if (it.type !== 'call') continue;
        const m = mixById(it.mixId); if (!m) continue;
        if (this.voiceMode === 'clone' && Voice.clips[m.id]) ev.push({ beat: it.beat, clip: m.id });
        else for (const w of m.words) ev.push({ beat: it.beat + w.b, ja: w.ja });
      }
      this.voiceEvents = ev.sort((a, b) => a.beat - b.beat);
    },
    seekVoice(b) { this.vIdx = this.voiceEvents.findIndex((e) => e.beat >= b - 0.01); if (this.vIdx < 0) this.vIdx = this.voiceEvents.length; },
    play() {
      if (this.playing) return;
      this.buildVoice();
      const from = this.pauseBeat >= this.song.lengthBeats ? 0 : this.pauseBeat;
      this.seekVoice(from);
      if (this.source === 'yt') {
        YT.cmd('seekTo', [Math.max(0, this.ytOffset() + from * this.spb), true]);
        YT.cmd('playVideo');
        YT.time = Math.max(0, this.ytOffset() + from * this.spb); YT.stamp = performance.now(); YT.state = 1;
      } else {
        const c = Beat.ensure();
        this.t0 = c.currentTime + 0.08 - from * this.spb;
        this.nextBeat = Math.ceil(from - COUNT_IN - 0.001);
        this.timer = setInterval(() => this.schedule(), 25);
        this.schedule();
      }
      this.playing = true; this.lastBeat = from - COUNT_IN;
      this.raf = requestAnimationFrame(() => this.tick());
      UI.syncTransport();
    },
    schedule() {
      const c = Beat.ctx, ahead = c.currentTime + 0.12;
      for (;;) {
        const t = this.t0 + (this.nextBeat + COUNT_IN) * this.spb;
        if (t > ahead) break;
        if (this.nextBeat >= this.song.lengthBeats) break;
        Beat.hit(this.nextBeat, t);
        if (this.nextBeat >= 0) Beat.offHat(t + this.spb / 2);
        this.nextBeat++;
      }
    },
    pause() {
      if (!this.playing) return;
      this.pauseBeat = Math.max(0, this.beatNow());
      this.halt();
      if (this.source === 'yt') { YT.cmd('pauseVideo'); YT.state = 2; }
      UI.syncTransport();
    },
    halt() { this.playing = false; clearInterval(this.timer); cancelAnimationFrame(this.raf); Voice.stop(); },
    stop(silent) {
      this.halt(); this.pauseBeat = 0;
      if (this.source === 'yt') YT.cmd('pauseVideo');
      if (!silent) { UI.syncTransport(); UI.frame(0); }
    },
    seek(b) {
      const was = this.playing;
      if (was) this.halt();
      this.pauseBeat = Math.max(0, Math.min(b, this.song.lengthBeats - 1));
      if (was) this.play(); else UI.frame(this.pauseBeat);
    },
    tick() {
      if (!this.playing) return;
      const b = this.beatNow();
      if (b - this.lastBeat > 2 || b < this.lastBeat - 0.5) this.seekVoice(b); // 점프 시 몰아서 재생하지 않음
      while (this.vIdx < this.voiceEvents.length && this.voiceEvents[this.vIdx].beat <= b) {
        const e = this.voiceEvents[this.vIdx++];
        if (this.voiceMode === 'off') continue;
        if (e.clip) Voice.playClip(e.clip); else Voice.speak(e.ja, this.song.bpm);
      }
      this.lastBeat = b;
      UI.frame(b);
      if (this.source === 'beat' && b >= this.song.lengthBeats) {
        if (this.loop) { this.halt(); this.pauseBeat = 0; this.play(); return; }
        this.stop(); return;
      }
      this.raf = requestAnimationFrame(() => this.tick());
    },
  };

  /* ---------- 플레이어 UI ---------- */
  const UI = {
    rows: [], activeIdx: -1, nowKey: '',
    syncTransport() {
      const btn = $('#playBtn'); if (btn) { btn.textContent = P.playing ? '❚❚' : '▶'; btn.setAttribute('aria-label', P.playing ? '일시정지' : '재생'); }
    },
    frame(b) {
      const song = P.song; if (!song || !$('#nowBox')) return;
      const secs = b * P.spb + (P.source === 'yt' ? P.ytOffset() : 0);
      const total = song.lengthBeats * P.spb + (P.source === 'yt' ? P.ytOffset() : 0);
      $('#timeTxt').textContent = b < 0 ? `카운트 ${Math.ceil(-b)}` : `${fmt(secs)} / ${fmt(total)} · ${Math.floor(b / 4) + 1}소절 ${Math.floor(((b % 4) + 4) % 4) + 1}박`;
      $('#progBar').style.width = `${Math.max(0, Math.min(100, (b / song.lengthBeats) * 100))}%`;
      const beatInBar = ((Math.floor(b) % 4) + 4) % 4;
      $$('#metro span').forEach((s, i) => s.classList.toggle('on', P.playing && i === beatInBar));

      // 활성 행
      let idx = -1;
      for (let i = 0; i < this.rows.length; i++) if (this.rows[i].beat <= b + 0.01) idx = i;
      if (idx !== this.activeIdx) {
        this.activeIdx = idx;
        $$('#scroller .row').forEach((r, i) => { r.classList.toggle('active', i === idx); r.classList.toggle('past', i < idx); });
        const el = $$('#scroller .row')[idx];
        if (el && P.playing) {
          const sc = $('#scroller');
          sc.scrollTop = el.offsetTop - sc.offsetTop - sc.clientHeight / 3;
        }
      }

      // NOW 카드: 현재 콜
      const calls = song.timeline.filter((t) => t.type === 'call');
      let cur = null;
      for (const c of calls) { const m = mixById(c.mixId); if (m && b >= c.beat - 0.01 && b < c.beat + mixLen(m)) cur = { c, m }; }
      const lyric = [...song.timeline].filter((t) => t.type === 'lyric' && t.beat <= b + 0.01).pop();
      let wIdx = -1;
      if (cur) cur.m.words.forEach((w, i) => { if (b >= cur.c.beat + w.b - 0.01) wIdx = i; });
      const key = `${cur ? cur.c.beat : 'x'}|${wIdx}|${lyric ? lyric.beat : 'x'}`;
      if (key === this.nowKey) return;
      this.nowKey = key;
      const box = $('#nowBox');
      if (cur) {
        box.innerHTML = `<div class="label">NOW · ${esc(D.KINDS[cur.m.kind])}</div>
          <div class="title ja">${esc(cur.m.name)} <span style="font-weight:500;opacity:.7">${esc(cur.m.ko)}</span></div>
          <div class="now-words">${cur.m.words.map((w, i) => `<span class="w ja ${i === wIdx ? 'on' : i < wIdx ? 'done' : ''}">${esc(showText(w.ja))}<span class="k">${esc(showText(w.ko))}</span></span>`).join('')}</div>
          <div class="now-lyric">${lyric ? esc(lyric.text) : ''}</div>`;
      } else {
        const next = calls.find((c) => c.beat > b);
        const nm = next && mixById(next.mixId);
        box.innerHTML = `<div class="label">${b < 0 ? 'COUNT IN' : 'NEXT CALL'}</div>
          <div class="title">${nm ? `<span class="ja">${esc(nm.name)}</span> · ${Math.max(0, Math.ceil((next.beat - b) / 4))}소절 뒤` : '남은 콜 없음'}</div>
          <div class="now-lyric">${lyric ? esc(lyric.text) : '<span style="opacity:.5">♪</span>'}</div>`;
      }
    },
  };

  /* ---------- 렌더러 ---------- */
  const mixCard = (m) => `
    <a class="card" href="#/mix/${m.id}">
      ${confBadge(m.confidence)}
      <h3>${esc(m.ko)}</h3>
      <div class="ja-name ja">${esc(m.name)}${m.alias ? ' · ' + esc(m.alias) : ''}</div>
      <p style="margin-top:10px">${esc(m.desc)}</p>
      <div class="tags">
        <span class="tag">${esc(D.KINDS[m.kind])}</span>
        <span class="tag">속도 ${esc(D.SPEEDS[m.speed].label)}</span>
        <span class="tag">${esc(m.lang)}</span>
        ${m.traits.map((t) => `<span class="tag">${D.TRAITS[t].emoji} ${esc(D.TRAITS[t].label)}</span>`).join('')}
      </div>
    </a>`;

  const songCard = (s) => {
    const mixes = [...new Set(s.timeline.filter((t) => t.type === 'call').map((t) => mixById(t.mixId)).filter(Boolean))];
    return `<a class="card" href="#/play/${encodeURIComponent(s.id)}">
      ${s.user ? '<span class="badge b-single">내가 추가</span>' : ''}
      <h3>${esc(s.title)}</h3>
      <div class="ja-name">${esc(s.artist)} · BPM ${s.bpm}${s.level ? ' · ' + esc(s.level) : ''}</div>
      <p style="margin-top:10px">콜 ${s.timeline.filter((t) => t.type === 'call').length}개 · ${Math.round(s.lengthBeats / 4)}소절${ytId(store.get('yt.' + s.id, '') || s.youtubeId) ? ' · ▶ YouTube 연결됨' : ' · 데모 비트'}</p>
      <div class="tags">${mixes.map((m) => `<span class="tag">${esc(m.ko)}</span>`).join('')}</div>
    </a>`;
  };

  const pageHero = (eyebrow, title, lead) => `
    <section class="hero page-hero"><div class="hero-inner">
      <span class="eyebrow">${eyebrow}</span><h1>${title}</h1><p class="lead">${lead}</p>
    </div></section>`;

  const views = {
    home() {
      const counts = { cross: 0, single: 0, unverified: 0 };
      D.MIXES.forEach((m) => counts[m.confidence]++);
      return `
      <section class="hero"><div class="hero-inner">
        <span class="eyebrow">지하아이돌 믹스콜 학습소</span>
        <h1>현장에서 바로 외치는<br><em>믹스콜</em>, 박자부터 배우자</h1>
        <p class="lead">곡별 · 믹스콜별 · 성격별로 찾아보고, AI 음성이 박에 맞춰 먼저 외쳐 주는 플레이어로 연습하세요. 일본어 구호에는 한국어 발음을 함께 적었습니다.</p>
        <div class="hero-cta">
          <a class="btn-grad" href="#/play/demo-starter">▶ 첫 연습곡 바로 시작</a>
          <a class="btn-ghost" href="#/mixes">믹스콜 사전 보기</a>
        </div>
        <div class="hero-stats">
          <div><b>${D.MIXES.length}</b>콜 유형</div>
          <div><b>${allSongs().length}</b>연습곡</div>
          <div><b>${Object.keys(D.TRAITS).length}</b>성격 분류</div>
          <div><b>${counts.cross}</b>교차 확인 항목</div>
        </div>
      </div></section>

      <div class="wrap lift"><div class="grid g3">
        <a class="card entry-card" href="#/songs"><span class="arrow">→</span><div class="entry-icon">🎵</div><h3>곡별로 배우기</h3><p>곡의 흐름 위에서 어디에 어떤 콜이 들어가는지, 가사와 함께 따라갑니다.</p></a>
        <a class="card entry-card" href="#/mixes"><span class="arrow">→</span><div class="entry-icon">📣</div><h3>믹스콜별로 배우기</h3><p>구호 한 단어씩, 박 배치·발음·속도를 익히고 단독으로 반복 연습합니다.</p></a>
        <a class="card entry-card" href="#/traits"><span class="arrow">→</span><div class="entry-icon">🧭</div><h3>성격별로 배우기</h3><p>열광형·고백형·고속형·클랩형 등 분위기로 골라 비슷한 콜을 묶어 봅니다.</p></a>
      </div></div>

      <section class="section"><div class="wrap">
        <h2>믹스콜 분류 기준</h2>
        <p class="sub">모든 콜은 아래 네 축으로 태그되어 있어 검색·필터에 그대로 쓰입니다.</p>
        <div class="grid g2">
          <div class="card"><h3>⏱ 속도</h3><p>${Object.values(D.SPEEDS).map((s) => `<b>${s.label}</b> ${s.desc}`).join('<br>')}</p></div>
          <div class="card"><h3>🧩 종류</h3><p>${Object.values(D.KINDS).join('<br>')}</p></div>
          <div class="card"><h3>🗣 방식 (언어계)</h3><p><b>영어계</b> 스탠다드 MIX<br><b>일본어계</b> 재패니즈 MIX · 구상<br><b>아이누어계</b> 아이누 MIX<br><b>파생·의성어</b> 원장·가변·PPPH 등</p></div>
          <div class="card"><h3>📍 위치</h3><p>${Object.values(D.POSITIONS).join(' · ')}<br><span class="hint">기본 순서: 인트로 영어 → 1절 끝 일본어 → 2절 끝 아이누어</span></p></div>
        </div>
      </div></section>

      <section class="section" style="padding-top:0"><div class="wrap">
        <h2>입문 루트</h2>
        <p class="sub">이 순서대로 외우면 대부분의 현장에서 첫 간주를 따라갈 수 있어요.</p>
        <div class="grid g3">${['standard', 'japanese', 'ppph'].map((id) => mixCard(mixById(id))).join('')}</div>
      </div></section>`;
    },

    songs(q = '') {
      return pageHero('곡별 학습', '곡 흐름 위에서 콜 익히기', '곡을 고르면 음원(데모 비트 또는 YouTube)과 함께 가사·콜 타임라인이 흘러가고, AI 음성이 박에 맞춰 콜을 먼저 외칩니다.') + `
      <div class="wrap lift">
        <div class="search"><span aria-hidden="true">🔍</span><input id="q" placeholder="곡 제목, 아티스트, 콜 이름으로 검색" value="${esc(q)}" aria-label="곡 검색"><span class="count" id="cnt"></span></div>
        <div class="grid g3" id="list"></div>
        <p class="hint" style="margin-top:20px">실제 곡은 <a href="#/editor">곡 추가</a>에서 YouTube 링크와 콜 타이밍을 입력해 직접 만들 수 있습니다. (저작권 문제로 실제 가사·음원은 사이트에 포함하지 않음)</p>
      </div>`;
    },

    mixes() {
      const group = (label, key, map) => `<div class="chips" data-group="${key}"><span class="chip-label">${label}</span>
        <button class="chip" data-v="" aria-pressed="true">전체</button>
        ${Object.entries(map).map(([k, v]) => `<button class="chip" data-v="${esc(k)}" aria-pressed="false">${esc(typeof v === 'string' ? v : v.label)}</button>`).join('')}</div>`;
      const langs = Object.fromEntries([...new Set(D.MIXES.map((m) => m.lang))].map((l) => [l, l]));
      return pageHero('믹스콜별 학습', '구호 하나씩, 박 단위로', '종류·속도·방식(언어계)·위치로 걸러 보고, 카드를 눌러 단어별 박 배치와 발음을 확인하세요.') + `
      <div class="wrap lift">
        <div class="search"><span aria-hidden="true">🔍</span><input id="q" placeholder="이름, 구호 단어(예: 타이가, 虎)로 검색" aria-label="믹스콜 검색"><span class="count" id="cnt"></span></div>
        <div class="card" style="margin-bottom:24px">
          ${group('종류', 'kind', D.KINDS)}
          ${group('속도', 'speed', D.SPEEDS)}
          ${group('방식 (언어계)', 'lang', langs)}
          ${group('위치', 'pos', D.POSITIONS)}
          ${group('자료 신뢰도', 'conf', { cross: '교차 확인', single: '단일 출처', unverified: '확인 필요' })}
        </div>
        <div class="grid g3" id="list"></div>
      </div>`;
    },

    traits(sel) {
      return pageHero('성격별 학습', '분위기로 고르는 콜', '같은 성격의 콜을 묶어서 보고, 그 콜이 들어간 연습곡으로 바로 넘어갈 수 있습니다.') + `
      <div class="wrap lift">
        <div class="grid g3">${Object.entries(D.TRAITS).map(([k, t]) => `
          <a class="card trait-tile" href="#/traits/${k}" aria-pressed="${k === sel}"><span class="emoji">${t.emoji}</span><h3>${esc(t.label)}</h3><p>${esc(t.desc)}</p>
          <div class="tags"><span class="tag">${D.MIXES.filter((m) => m.traits.includes(k)).length}개 콜</span></div></a>`).join('')}
        </div>
        ${sel && D.TRAITS[sel] ? (() => {
          const ms = D.MIXES.filter((m) => m.traits.includes(sel));
          const ss = allSongs().filter((s) => s.timeline.some((t) => t.type === 'call' && ms.some((m) => m.id === t.mixId)));
          return `<section class="section"><h2>${D.TRAITS[sel].emoji} ${esc(D.TRAITS[sel].label)} 콜</h2><p class="sub">${esc(D.TRAITS[sel].desc)}</p>
            <div class="grid g3">${ms.map(mixCard).join('')}</div>
            <h2 style="margin-top:40px">이 성격의 콜이 들어간 곡</h2><p class="sub">${ss.length}곡</p>
            <div class="grid g3">${ss.map(songCard).join('') || '<p class="empty">아직 없음</p>'}</div></section>`;
        })() : '<p class="empty">위에서 성격을 고르세요.</p>'}
      </div>`;
    },

    mix(id) {
      const m = mixById(id);
      if (!m) return views.notFound();
      const L = mixLen(m);
      const usedIn = allSongs().filter((s) => s.timeline.some((t) => t.mixId === id));
      return `
      <section class="hero page-hero"><div class="hero-inner">
        <a class="back" href="#/mixes">← 믹스콜 목록</a>
        <h1 class="ja">${esc(m.name)}</h1>
        <p class="lead">${esc(m.ko)}${m.alias ? ' · ' + esc(m.alias) : ''} — ${esc(m.desc)}</p>
        <div class="hero-cta"><a class="btn-grad" href="#/play/mix:${m.id}">▶ 이 콜만 반복 연습</a></div>
      </div></section>
      <div class="wrap lift">
        <div class="grid g2">
          <div class="card">
            <h3>구호 · 박 배치 ${confBadge(m.confidence).replace('class="badge', 'style="position:static;margin-left:6px" class="badge')}</h3>
            <p class="hint" style="margin-bottom:12px">총 ${L}박 (${(L / 4).toFixed(L % 4 ? 1 : 0)}소절) · 4/4박 기준 일반 예시</p>
            <div class="table-scroll"><table class="word-table">
              <thead><tr><th>박</th><th>구호</th><th>한국어 발음</th><th>길이</th></tr></thead>
              <tbody>${m.words.map((w) => `<tr class="${w.pre ? 'pre' : ''}"><td>${w.b}</td><td class="ja">${esc(showText(w.ja))}</td><td>${esc(showText(w.ko))}</td>
                <td><div class="beatbar" title="${w.len}박">${Array.from({ length: Math.min(16, Math.ceil(w.len)) }, () => '<span class="on"></span>').join('')}</div></td></tr>`).join('')}</tbody>
            </table></div>
          </div>
          <div>
            <div class="card" style="margin-bottom:22px">
              <h3>분류</h3>
              <div class="tags">
                <span class="tag">${esc(D.KINDS[m.kind])}</span>
                <span class="tag">속도 ${esc(D.SPEEDS[m.speed].label)} — ${esc(D.SPEEDS[m.speed].desc)}</span>
                <span class="tag">방식 ${esc(m.lang)}</span>
                ${m.positions.map((p) => `<span class="tag">📍 ${esc(D.POSITIONS[p])}</span>`).join('')}
                ${m.traits.map((t) => `<a class="tag" href="#/traits/${t}" style="text-decoration:none">${D.TRAITS[t].emoji} ${esc(D.TRAITS[t].label)}</a>`).join('')}
              </div>
            </div>
            <div class="card" style="margin-bottom:22px"><h3>연습 팁</h3><p>${esc(m.tip)}</p></div>
            <div class="card"><h3>참고 자료</h3>
              ${m.sources.length ? `<ul class="src-list">${m.sources.map((s) => `<li><a href="${esc(s)}" target="_blank" rel="noopener">${esc(s)}</a></li>`).join('')}</ul>` : '<p>이번 조사에서 확인한 출처 없음 (일반 지식 기반 · 확인 필요)</p>'}
            </div>
          </div>
        </div>
        <section class="section"><h2>이 콜이 들어간 곡</h2><p class="sub">${usedIn.length}곡</p>
          <div class="grid g3">${usedIn.map(songCard).join('') || '<p class="empty">아직 없음 — 곡 추가에서 만들어 보세요.</p>'}</div></section>
      </div>`;
    },

    play(song) {
      const rows = [...song.timeline].sort((a, b) => a.beat - b.beat || (a.type === 'section' ? -1 : 1));
      UI.rows = rows; UI.activeIdx = -1; UI.nowKey = '';
      const yid = P.ytId();
      const clipCount = song.timeline.filter((t) => t.type === 'call' && Voice.clips[t.mixId]).length;
      const callCount = song.timeline.filter((t) => t.type === 'call').length;
      return `
      <section class="hero page-hero" style="padding-bottom:60px"><div class="hero-inner">
        <a class="back" href="${song.practice ? `#/mix/${song.practice}` : '#/songs'}">← ${song.practice ? '믹스콜 상세' : '곡 목록'}</a>
        <h1>${esc(song.title)}</h1>
        <p class="lead">${esc(song.artist)} · BPM ${song.bpm} · ${Math.round(song.lengthBeats / 4)}소절</p>
      </div></section>
      <div class="wrap lift">
        <div class="player">
          <div class="card stage">
            <div class="now" id="nowBox"></div>
            <div class="transport">
              <button class="btn primary round" id="playBtn" aria-label="재생">▶</button>
              <button class="btn" id="stopBtn">■ 처음으로</button>
              ${song.practice ? `<button class="btn" id="loopBtn" aria-pressed="${P.loop}">↻ 반복</button>` : ''}
              <div class="metro" id="metro" aria-hidden="true"><span></span><span></span><span></span><span></span></div>
              <span class="timebox" id="timeTxt"></span>
            </div>
            <div class="progress" id="prog" title="클릭해서 이동"><i id="progBar"></i></div>
            <h4 style="margin:22px 0 10px">가사 · 콜 타임라인 <span class="hint">(행을 누르면 그 위치로 이동)</span></h4>
            <div class="scroller well" id="scroller">
              ${rows.map((r) => {
                const t = `${Math.floor(r.beat / 4) + 1}소절`;
                if (r.type === 'section') return `<div class="row section"><span class="t">${t}</span><span class="body">${esc(r.text)}</span></div>`;
                if (r.type === 'lyric') return `<div class="row lyric" data-b="${r.beat}"><span class="t">${t}</span><span class="body">${esc(r.text)}</span></div>`;
                const m = mixById(r.mixId);
                return `<div class="row call" data-b="${r.beat}"><span class="t">${t}</span><span class="body"><b class="ja">📣 ${esc(m ? m.name : r.mixId)}</b>${m ? ` <span class="mini">${esc(m.ko)}</span><div class="mini ja">${m.words.map((w) => esc(showText(w.ko))).join(' · ')}</div>` : ' <span class="mini">(알 수 없는 콜 ID)</span>'}</span></div>`;
              }).join('')}
            </div>
          </div>
          <aside class="side">
            <div class="card">
              <h4>🎧 음원</h4>
              <div class="seg" role="group" aria-label="음원 선택">
                <button data-src="beat" aria-pressed="${P.source === 'beat'}">데모 비트</button>
                <button data-src="yt" aria-pressed="${P.source === 'yt'}">YouTube 임베드</button>
              </div>
              <div id="ytArea" ${P.source === 'yt' ? '' : 'hidden'}>
                <div class="field" style="margin-top:14px"><label for="ytUrl">YouTube 링크 또는 영상 ID</label><input id="ytUrl" value="${esc(store.get('yt.' + song.id, '') || song.youtubeId || '')}" placeholder="https://youtu.be/…"></div>
                <div class="field"><label for="ytOff">싱크 오프셋 (초) — 곡의 1소절 1박이 시작되는 영상 시간</label><input id="ytOff" type="number" step="0.05" value="${P.ytOffset()}"></div>
                <div class="yt-box" id="ytBox"${yid ? '' : ' hidden'}></div>
                <p class="notice">공식 MV·라이브 영상을 연결하세요. 영상마다 시작 지점이 달라 오프셋을 맞춰야 콜이 박에 붙습니다.</p>
              </div>
              <p class="notice" id="beatNote" ${P.source === 'beat' ? '' : 'hidden'}>BPM ${song.bpm} 데모 비트(킥·클랩·하이햇)가 4박 카운트 후 시작됩니다.</p>
            </div>
            <div class="card">
              <h4>🗣 나의 AI 음성</h4>
              <div class="seg" role="group" aria-label="음성 선택">
                <button data-voice="clone" aria-pressed="${P.voiceMode === 'clone'}">AI 클론</button>
                <button data-voice="tts" aria-pressed="${P.voiceMode === 'tts'}">기본 TTS</button>
                <button data-voice="off" aria-pressed="${P.voiceMode === 'off'}">끄기</button>
              </div>
              <p class="notice">클론 음성 등록 ${clipCount}/${callCount}개 콜. 등록되지 않은 콜은 브라우저 일본어 TTS로 대신 외칩니다. <a href="#/voice">음성 등록하기 →</a></p>
            </div>
          </aside>
        </div>
      </div>`;
    },

    voice() {
      return pageHero('AI 음성', '내 목소리로 콜 외치기', '내 목소리로 만든 AI 클론 음성 클립을 콜마다 등록하면, 플레이어가 콜 시작 박에 맞춰 재생합니다. 등록 안 된 콜은 일본어 TTS가 대신합니다.') + `
      <div class="wrap lift">
        <div class="grid g2">
          <div class="card">
            <h3>콜별 음성 클립</h3>
            <p class="hint">mp3·wav·m4a. 클립은 이 브라우저에만 저장됩니다(IndexedDB). 콜의 첫 박에서 바로 소리가 나도록 앞 무음을 잘라 주세요.</p>
            <div id="voiceList">${D.MIXES.filter((m) => m.kind !== 'action').map((m) => `
              <div class="voice-row" data-id="${m.id}">
                <div><b class="ja">${esc(m.name)}</b> <span class="hint">${esc(m.ko)}</span><div class="st" id="st-${m.id}">${Voice.clips[m.id] ? '✅ 클론 음성 등록됨' : '— TTS 사용 중'}</div></div>
                <div class="acts">
                  <button class="btn" data-test="${m.id}">▶ 듣기</button>
                  <label class="btn file-btn">⬆ 업로드<input type="file" accept="audio/*" data-up="${m.id}"></label>
                  <button class="btn" data-del="${m.id}" ${Voice.clips[m.id] ? '' : 'hidden'}>삭제</button>
                </div>
              </div>`).join('')}</div>
          </div>
          <div>
            <div class="card" style="margin-bottom:22px">
              <h3>최애 이름</h3>
              <p class="hint">구상·이름 콜의 (최애) 자리에 들어갑니다. TTS는 이 이름을 읽어요.</p>
              <div class="field" style="margin-top:10px"><input id="oshiName" value="${esc(oshi())}" placeholder="예: みゆ"></div>
            </div>
            <div class="card" style="margin-bottom:22px">
              <h3>AI 클론 음성 만드는 법</h3>
              <ol style="margin:8px 0 0;padding-left:20px;color:var(--ink-2);font-size:14px">
                <li>조용한 곳에서 내 목소리 샘플을 1분 이상 녹음 (wav 권장)</li>
                <li>보이스 클로닝 서비스로 내 목소리 모델 생성</li>
                <li>각 콜의 구호 텍스트(왼쪽 일본어)를 해당 BPM 속도로 생성</li>
                <li>클립 앞뒤 무음을 자르고 여기서 콜별로 업로드</li>
              </ol>
              <p class="notice">저장소 배포용으로는 <code>mixcall-web/voices/&lt;콜 ID&gt;.mp3</code>(예: <code>voices/standard.mp3</code>)에 두고 <code>voices/voices.json</code> 목록에 ID를 적으면 모든 방문자에게 자동 적용됩니다.</p>
            </div>
            <div class="card"><h3>TTS 상태</h3><p id="ttsState"></p></div>
          </div>
        </div>
      </div>`;
    },

    editor() {
      const list = userSongs();
      return pageHero('곡 추가', '실제 곡에 콜 타이밍 붙이기', 'YouTube 링크와 BPM, 박 단위 타임라인을 입력하면 곡별 학습 목록에 추가됩니다. 이 브라우저에 저장됩니다.') + `
      <div class="wrap lift">
        <div class="grid g2">
          <form class="card" id="songForm">
            <input type="hidden" id="fId">
            <div class="field"><label for="fTitle">곡 제목</label><input id="fTitle" required></div>
            <div class="field"><label for="fArtist">아티스트</label><input id="fArtist"></div>
            <div class="grid" style="grid-template-columns:1fr 1fr;gap:12px">
              <div class="field"><label for="fBpm">BPM</label><input id="fBpm" type="number" min="40" max="300" value="160" required></div>
              <div class="field"><label for="fBars">길이 (소절)</label><input id="fBars" type="number" min="4" max="400" value="48" required></div>
            </div>
            <div class="grid" style="grid-template-columns:2fr 1fr;gap:12px">
              <div class="field"><label for="fYt">YouTube 링크 (선택)</label><input id="fYt" placeholder="https://youtu.be/…"></div>
              <div class="field"><label for="fOff">오프셋(초)</label><input id="fOff" type="number" step="0.05" value="0"></div>
            </div>
            <div class="field"><label for="fTl">타임라인 — 한 줄에 <code>소절.박 | 종류 | 내용</code></label>
              <textarea id="fTl" spellcheck="false">1.1 | section | 인트로
1.1 | call | standard
9.1 | section | 1절
9.1 | lyric | (가사 메모 — 실제 가사 전문 대신 요약을 권장)
17.1 | call | japanese</textarea>
              <span class="hint">종류: section(구간 이름) · lyric(가사) · call(콜 ID). 소절.박은 1부터 시작 (예: 9.3 = 9소절 3박).<br>콜 ID: ${D.MIXES.map((m) => `<code>${m.id}</code>`).join(' ')}</span>
            </div>
            <p class="hint" id="fErr" style="color:var(--bad)"></p>
            <div style="display:flex;gap:10px;flex-wrap:wrap"><button class="btn primary" type="submit">저장</button><button class="btn" type="button" id="fReset">새로 쓰기</button></div>
          </form>
          <div>
            <div class="card" style="margin-bottom:22px" id="myList"><h3>내가 추가한 곡</h3>
              ${list.length ? list.map((s) => `<div class="voice-row"><div><b>${esc(s.title)}</b><div class="hint">${esc(s.artist)} · BPM ${s.bpm}</div></div>
                <div class="acts"><a class="btn" href="#/play/${encodeURIComponent(s.id)}">▶</a><button class="btn" data-edit="${esc(s.id)}">수정</button><button class="btn" data-rm="${esc(s.id)}">삭제</button></div></div>`).join('') : '<p>아직 없음</p>'}
            </div>
            <div class="card"><h3>백업 · 공유</h3><p class="hint">내 곡 목록을 JSON으로 내보내거나 붙여넣어 가져옵니다.</p>
              <div class="field" style="margin-top:10px"><textarea id="ioBox" style="min-height:120px" placeholder="여기에 JSON 붙여넣기"></textarea></div>
              <div style="display:flex;gap:10px;flex-wrap:wrap"><button class="btn" id="exportBtn" type="button">내보내기</button><button class="btn" id="importBtn" type="button">가져오기</button></div>
            </div>
          </div>
        </div>
      </div>`;
    },

    notFound() { return pageHero('404', '찾을 수 없어요', '<a href="#/" style="color:#fff">홈으로</a>'); },
  };

  /* ---------- 페이지별 동작 ---------- */
  const binders = {
    songs() {
      const run = () => {
        const q = $('#q').value.trim().toLowerCase();
        const res = allSongs().filter((s) => {
          if (!q) return true;
          const names = s.timeline.filter((t) => t.type === 'call').map((t) => { const m = mixById(t.mixId); return m ? `${m.name} ${m.ko}` : ''; }).join(' ');
          return `${s.title} ${s.artist} ${(s.tags || []).join(' ')} ${names}`.toLowerCase().includes(q);
        });
        $('#list').innerHTML = res.map(songCard).join('') || '<p class="empty">검색 결과 없음</p>';
        $('#cnt').textContent = `${res.length}곡`;
      };
      $('#q').addEventListener('input', run); run();
    },
    mixes() {
      const f = { kind: '', speed: '', lang: '', pos: '', conf: '' };
      const run = () => {
        const q = $('#q').value.trim().toLowerCase();
        const res = D.MIXES.filter((m) =>
          (!f.kind || m.kind === f.kind) && (!f.speed || m.speed === f.speed) && (!f.lang || m.lang === f.lang)
          && (!f.pos || m.positions.includes(f.pos)) && (!f.conf || m.confidence === f.conf)
          && (!q || `${m.name} ${m.ko} ${m.alias} ${m.desc} ${m.words.map((w) => w.ja + ' ' + w.ko).join(' ')}`.toLowerCase().includes(q)));
        $('#list').innerHTML = res.map(mixCard).join('') || '<p class="empty">조건에 맞는 콜 없음</p>';
        $('#cnt').textContent = `${res.length}개`;
      };
      $$('.chips').forEach((g) => g.addEventListener('click', (e) => {
        const b = e.target.closest('.chip'); if (!b) return;
        $$('.chip', g).forEach((c) => c.setAttribute('aria-pressed', String(c === b)));
        f[g.dataset.group] = b.dataset.v; run();
      }));
      $('#q').addEventListener('input', run); run();
    },
    play() {
      UI.frame(0); UI.syncTransport();
      $('#playBtn').addEventListener('click', () => {
        if (P.source === 'yt' && !P.ytId()) { alert('YouTube 링크를 먼저 입력하세요.'); return; }
        P.playing ? P.pause() : P.play();
      });
      $('#stopBtn').addEventListener('click', () => P.stop());
      const loopBtn = $('#loopBtn');
      if (loopBtn) loopBtn.addEventListener('click', () => { P.loop = !P.loop; loopBtn.setAttribute('aria-pressed', String(P.loop)); });
      $('#prog').addEventListener('click', (e) => {
        const r = e.currentTarget.getBoundingClientRect();
        P.seek(Math.floor(((e.clientX - r.left) / r.width) * P.song.lengthBeats));
      });
      $('#scroller').addEventListener('click', (e) => { const r = e.target.closest('.row[data-b]'); if (r) P.seek(+r.dataset.b); });
      const mountYt = () => { const id = P.ytId(); const box = $('#ytBox'); box.hidden = !id; if (id) YT.mount(box, id); else YT.unmount(); };
      $$('[data-src]').forEach((b) => b.addEventListener('click', () => {
        P.stop(true); P.source = b.dataset.src; store.set('source', P.source);
        $$('[data-src]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
        $('#ytArea').hidden = P.source !== 'yt'; $('#beatNote').hidden = P.source !== 'beat';
        if (P.source === 'yt') mountYt(); else YT.unmount();
        UI.syncTransport(); UI.frame(0);
      }));
      $$('[data-voice]').forEach((b) => b.addEventListener('click', () => {
        P.voiceMode = b.dataset.voice; store.set('voiceMode', P.voiceMode);
        $$('[data-voice]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
        if (P.playing) { P.buildVoice(); P.seekVoice(P.beatNow()); }
      }));
      $('#ytUrl').addEventListener('change', (e) => { store.set('yt.' + P.song.id, e.target.value.trim()); P.stop(true); mountYt(); UI.syncTransport(); });
      $('#ytOff').addEventListener('change', (e) => { store.set('ytOff.' + P.song.id, parseFloat(e.target.value) || 0); });
      if (P.source === 'yt') mountYt();
    },
    traits() {},
    mix() {},
    home() {},
    voice() {
      const tts = $('#ttsState');
      const upd = () => {
        if (!('speechSynthesis' in window)) { tts.textContent = '이 브라우저는 음성 합성(TTS)을 지원하지 않습니다. 클론 클립만 재생됩니다.'; return; }
        const v = Voice.pickVoice();
        tts.textContent = v ? `일본어 음성: ${v.name}` : '일본어 음성을 찾지 못했습니다. 기기 기본 음성으로 읽습니다(발음이 어색할 수 있음).';
      };
      upd(); setTimeout(upd, 600);
      $('#oshiName').addEventListener('change', (e) => store.set('oshi', e.target.value.trim()));
      const list = $('#voiceList');
      list.addEventListener('click', async (e) => {
        const t = e.target.closest('[data-test]'), d = e.target.closest('[data-del]');
        if (t) {
          const m = mixById(t.dataset.test);
          if (!Voice.playClip(m.id)) {
            let i = 0; const seq = m.words.filter((w) => !w.pre);
            const next = () => { if (i < seq.length) { Voice.speak(seq[i++].ja, 150); setTimeout(next, 650); } };
            next();
          }
        }
        if (d) {
          const id = d.dataset.del;
          try { await idb.del(id); } catch { /* 무시 */ }
          if (Voice.clips[id] && Voice.clips[id].startsWith('blob:')) URL.revokeObjectURL(Voice.clips[id]);
          delete Voice.clips[id];
          $('#st-' + id).textContent = '— TTS 사용 중'; d.hidden = true;
        }
      });
      list.addEventListener('change', async (e) => {
        const inp = e.target.closest('[data-up]'); if (!inp || !inp.files[0]) return;
        const id = inp.dataset.up, file = inp.files[0];
        try { await idb.put(id, { blob: file, name: file.name }); } catch { /* 저장 실패해도 이번 세션엔 사용 */ }
        Voice.clips[id] = URL.createObjectURL(file);
        $('#st-' + id).textContent = `✅ 클론 음성 등록됨 (${file.name})`;
        $(`[data-del="${id}"]`).hidden = false;
      });
    },
    editor() {
      const err = $('#fErr');
      const toBeat = (s) => { const [bar, bt = '1'] = String(s).split('.'); return (parseInt(bar, 10) - 1) * 4 + (parseFloat(bt) - 1); };
      const fromBeat = (b) => `${Math.floor(b / 4) + 1}.${(b % 4) + 1}`;
      const fill = (s) => {
        $('#fId').value = s ? s.id : '';
        $('#fTitle').value = s ? s.title : ''; $('#fArtist').value = s ? s.artist : '';
        $('#fBpm').value = s ? s.bpm : 160; $('#fBars').value = s ? Math.round(s.lengthBeats / 4) : 48;
        $('#fYt').value = s ? s.youtubeId : ''; $('#fOff').value = s ? s.ytOffset : 0;
        if (s) $('#fTl').value = s.timeline.map((t) => `${fromBeat(t.beat)} | ${t.type} | ${t.type === 'call' ? t.mixId : t.text}`).join('\n');
        err.textContent = '';
      };
      $('#fReset').addEventListener('click', () => fill(null));
      $('#songForm').addEventListener('submit', (e) => {
        e.preventDefault(); err.textContent = '';
        const tl = []; const lines = $('#fTl').value.split('\n').map((l) => l.trim()).filter(Boolean);
        for (const [i, l] of lines.entries()) {
          const [pos, type, ...rest] = l.split('|').map((x) => x.trim());
          const text = rest.join('|').trim(); const beat = toBeat(pos);
          if (!Number.isFinite(beat) || beat < 0) { err.textContent = `${i + 1}번째 줄: 소절.박 형식 오류 (${pos})`; return; }
          if (type === 'call') {
            if (!mixById(text)) { err.textContent = `${i + 1}번째 줄: 없는 콜 ID "${text}"`; return; }
            tl.push({ beat, type, mixId: text });
          } else if (type === 'lyric' || type === 'section') tl.push({ beat, type, text });
          else { err.textContent = `${i + 1}번째 줄: 종류는 section / lyric / call 중 하나`; return; }
        }
        const bpm = +$('#fBpm').value, bars = +$('#fBars').value;
        if (!(bpm >= 40 && bpm <= 300)) { err.textContent = 'BPM은 40~300'; return; }
        const song = {
          id: $('#fId').value || 'user-' + Date.now().toString(36), user: true,
          title: $('#fTitle').value.trim(), artist: $('#fArtist').value.trim() || '아티스트 미입력', bpm,
          youtubeId: ytId($('#fYt').value), ytOffset: parseFloat($('#fOff').value) || 0,
          lengthBeats: Math.max(bars * 4, ...tl.map((t) => t.beat + 4)), level: '사용자', tags: [], timeline: tl,
        };
        const list = userSongs().filter((s) => s.id !== song.id); list.push(song); store.set('userSongs', list);
        location.hash = '#/play/' + encodeURIComponent(song.id);
      });
      $('#myList').addEventListener('click', (e) => {
        const ed = e.target.closest('[data-edit]'), rm = e.target.closest('[data-rm]');
        if (ed) { fill(userSongs().find((s) => s.id === ed.dataset.edit)); window.scrollTo({ top: 0 }); }
        if (rm && confirm('이 곡을 삭제할까요?')) { store.set('userSongs', userSongs().filter((s) => s.id !== rm.dataset.rm)); route(); }
      });
      $('#exportBtn').addEventListener('click', () => { $('#ioBox').value = JSON.stringify(userSongs(), null, 2); $('#ioBox').select(); });
      $('#importBtn').addEventListener('click', () => {
        try {
          const arr = JSON.parse($('#ioBox').value);
          if (!Array.isArray(arr)) throw new Error();
          const ok = arr.filter((s) => s && s.title && s.bpm && Array.isArray(s.timeline)).map((s) => ({ ...s, user: true }));
          const map = new Map(userSongs().map((s) => [s.id, s])); ok.forEach((s) => map.set(s.id, s));
          store.set('userSongs', [...map.values()]); route();
        } catch { alert('JSON 형식이 올바르지 않습니다.'); }
      });
    },
  };

  /* ---------- 라우터 ---------- */
  function route() {
    P.stop(true); YT.unmount();
    const parts = (location.hash.replace(/^#\/?/, '') || '').split('/').map(decodeURIComponent);
    let name = parts[0] || 'home', html;
    if (name === 'play') {
      let song;
      if (parts[1] && parts[1].startsWith('mix:')) {
        const m = mixById(parts[1].slice(4));
        if (m) {
          const L = mixLen(m);
          song = { id: 'practice-' + m.id, practice: m.id, title: `${m.ko} 반복 연습`, artist: m.name, bpm: store.get('practiceBpm', 150), youtubeId: '', ytOffset: 0,
            lengthBeats: Math.ceil((L + 4) / 4) * 4, timeline: [{ beat: 0, type: 'section', text: '연습 구간' }, { beat: 0, type: 'call', mixId: m.id }] };
        }
      } else song = songById(parts[1]);
      if (!song) { html = views.notFound(); name = 'notFound'; } else { P.load(song); html = views.play(song); }
    } else if (name === 'mix') html = views.mix(parts[1]);
    else if (name === 'traits') html = views.traits(parts[1]);
    else if (views[name]) html = views[name]();
    else { html = views.notFound(); name = 'notFound'; }
    app.innerHTML = html;
    $$('[data-nav]').forEach((a) => a.classList.toggle('active', a.dataset.nav === (name === 'mix' ? 'mixes' : name === 'play' ? 'songs' : name)));
    if (binders[name]) binders[name]();
    if (name === 'play' && P.song.practice) addPracticeBpm();
    window.scrollTo({ top: 0 });
  }

  function addPracticeBpm() {
    const side = $('.side');
    const c = document.createElement('div'); c.className = 'card';
    c.innerHTML = `<h4>⏱ 연습 BPM <span id="bpmVal">${P.song.bpm}</span></h4><input type="range" id="bpmRange" min="80" max="220" step="2" value="${P.song.bpm}" aria-label="연습 BPM">
      <p class="hint">느리게 시작해 익숙해지면 올리세요. 대부분의 지하아이돌 곡은 140~190 사이.</p>`;
    side.prepend(c);
    $('#bpmRange').addEventListener('input', (e) => {
      const v = +e.target.value; $('#bpmVal').textContent = v; store.set('practiceBpm', v);
      const was = P.playing; P.stop(true); P.song.bpm = v; if (was) P.play(); else { UI.syncTransport(); UI.frame(0); }
    });
  }

  window.addEventListener('hashchange', route);
  route();
})();
