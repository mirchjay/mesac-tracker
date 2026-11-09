/* =====================================================================
   MESAC Day line
   Plain JavaScript, no libraries. Talks to the Spring Boot API in /api
   and listens to /api/stream so every open screen stays in sync.

   Everyone starts in the User view: the timeline, schedule, standings,
   teams and records, read-only, plus "Who will win?" picks on upcoming
   matches. Coaches and admins log in (top right) to get every tool.
   Admin accounts also get the Staff page: coach logins and the activity
   log. The server checks every change, so hidden buttons are not the
   only lock.
   ===================================================================== */
(() => {
    'use strict';

    // ------------------------------------------------------------------ state
    const S = {
        sports: [], teams: [], matches: [], events: [], records: [], divisions: [],
        view: 'day',
        me: null,                 // signed-in account { id, name, username, role } or null
        staff: null,              // Staff page: accounts
        activity: null,           // Staff page: activity log
        activityFor: 'all',
        day: startOfDay(new Date()),
        sel: null,                // selected match id (Day line)
        dockEvents: [],
        dockPoll: null,
        picks: new Map(),         // matchId -> { homeVotes, awayVotes, mine } (User view)
        dayFilters: { sport: 'all', division: 'all' },
        filters: { sport: 'all', status: 'all', division: 'all', q: '' },
        standings: { sportId: null, division: 'VARSITY_BOYS', rows: null },
        flash: new Set(),
        drawerSubmit: null,
        connected: false,
        everConnected: false,
    };

    const VIEWS = ['day', 'schedule', 'standings', 'teams', 'records', 'staff'];
    const SWATCHES = ['#C8102E', '#E07A12', '#F5C542', '#2FBF71', '#0F8A6B', '#0E7FA8', '#1F5FBF', '#6C3FC5', '#B5179E', '#5B6B7D'];
    const MIN = 60000, HOUR = 3600000;

    const canEdit = () => !!S.me;                                   // coach or admin
    const isAdminAccount = () => !!S.me && S.me.role === 'ADMIN';   // can manage accounts

    // One random id per browser, so each user gets one pick per match.
    const voter = (() => {
        const make = () => (window.crypto && crypto.randomUUID ? crypto.randomUUID() : 'fan-' + Math.random().toString(36).slice(2) + Date.now().toString(36));
        try {
            let id = localStorage.getItem('mesac-fan-id');
            if (!id) { id = make(); localStorage.setItem('mesac-fan-id', id); }
            return id;
        } catch (_) {
            return make();
        }
    })();

    // ---------------------------------------------------------------- helpers
    const $ = (sel, root = document) => root.querySelector(sel);
    const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
    const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const byId = (list, id) => list.find((x) => String(x.id) === String(id));
    const plural = (n, one, many) => `${n} ${n === 1 ? one : (many || one + 's')}`;
    const ICON = {
        prev: '<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M10 3L5 8l5 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
        next: '<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M6 3l5 5-5 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
        close: '<svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true"><path d="M3.5 3.5l9 9M12.5 3.5l-9 9" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
        swap: '<svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true"><path d="M3 6h11l-3-3M15 12H4l3 3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    };

    async function api(method, url, body) {
        const headers = {};
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const res = await fetch(url, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined, credentials: 'same-origin' });
        let data = null;
        try { data = await res.json(); } catch (_) { /* empty body */ }
        if (res.status === 401 && S.me && !url.startsWith('/api/auth/')) {
            // Signed out on the server (session timed out or account turned off).
            S.me = null;
            closeDrawer();
            if (S.view === 'staff') location.hash = '#/day';
            queueRender();
        }
        if (!res.ok) throw new Error((data && data.error) || `Something went wrong (${res.status}).`);
        return data;
    }

    /** Runs an API call; shows the error as a toast if it fails. Returns null on failure. */
    async function attempt(fn, okMessage) {
        try {
            const result = await fn();
            if (okMessage) toast(okMessage);
            return result;
        } catch (err) {
            toast(err.message, { error: true });
            return null;
        }
    }

    // ---------------------------------------------------------------- dates
    // The server sends local times like "2026-11-05T14:30:00".
    function startOfDay(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
    const toDate = (s) => (s ? new Date(s) : null);
    const sameDay = (a, b) => a && b && startOfDay(a).getTime() === startOfDay(b).getTime();
    const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
    const dayDiff = (d) => Math.round((startOfDay(d) - startOfDay(new Date())) / 86400000);
    const fmtTime = (d) => (d ? new Date(d).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : '');
    const fmtLong = (d) => new Date(d).toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' });
    function fmtRel(d) {
        const diff = dayDiff(new Date(d));
        if (diff === 0) return 'Today';
        if (diff === 1) return 'Tomorrow';
        if (diff === -1) return 'Yesterday';
        return fmtLong(d);
    }
    function ago(s) {
        const sec = Math.max(0, Math.round((Date.now() - toDate(s)) / 1000));
        if (sec < 45) return 'just now';
        const min = Math.round(sec / 60);
        if (min < 60) return `${min} min ago`;
        const h = Math.round(min / 60);
        return h < 24 ? `${h} h ago` : fmtRel(toDate(s));
    }
    function elapsed(s) {
        if (!s) return '';
        const sec = Math.max(0, Math.floor((Date.now() - toDate(s)) / 1000));
        const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), r = sec % 60;
        const mm = String(m).padStart(2, '0'), ss = String(r).padStart(2, '0');
        return h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
    }
    function localInputValue(d) {
        const p = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
    }
    function defaultStart() {
        // Next quarter hour today, or 9:00 on another day.
        const now = new Date();
        if (!sameDay(S.day, now)) return new Date(S.day.getFullYear(), S.day.getMonth(), S.day.getDate(), 9, 0);
        const d = new Date(now);
        d.setSeconds(0, 0);
        d.setMinutes(Math.ceil((d.getMinutes() + 1) / 15) * 15);
        return d;
    }

    // ---------------------------------------------------------- match helpers
    const increments = (sport) => (sport.scoreIncrements || '1').split(',').map(Number).filter((n) => n > 0);
    function periodLabel(m) {
        if (m.status === 'UPCOMING') return 'Not started';
        if (m.status === 'DONE') return 'Final';
        const s = m.sport, p = m.currentPeriod;
        return p > s.periodCount ? `Overtime ${p - s.periodCount}` : `${s.periodName} ${p}`;
    }
    function shortPeriod(m) {
        const s = m.sport, p = m.currentPeriod;
        return p > s.periodCount ? `OT${p - s.periodCount}` : `${s.periodName.charAt(0)}${p}`;
    }
    const matchStart = (m) => toDate((m.status !== 'UPCOMING' && m.startedAt) || m.scheduledAt);
    function matchSpan(m) {
        const start = matchStart(m) || new Date();
        const len = (m.sport.matchMinutes || 60) * MIN;
        let end;
        if (m.status === 'DONE') end = m.endedAt ? toDate(m.endedAt) : new Date(+start + len);
        else if (m.status === 'LIVE') end = new Date(Math.max(Date.now(), +start + len));
        else end = new Date(+start + len);
        if (end - start < 20 * MIN) end = new Date(+start + 20 * MIN);
        return { start, end };
    }
    const bySchedule = (a, b) => (matchStart(a) || 0) - (matchStart(b) || 0);
    const vs = (m) => `${m.homeTeam.shortName} v ${m.awayTeam.shortName}`;
    const venues = () => [...new Set(S.matches.map((m) => m.venue).filter(Boolean))].sort();

    function upsertMatch(m) {
        const i = S.matches.findIndex((x) => x.id === m.id);
        if (i >= 0) {
            const old = S.matches[i];
            if (old.homeScore !== m.homeScore) flash(m.id, 'HOME');
            if (old.awayScore !== m.awayScore) flash(m.id, 'AWAY');
            S.matches[i] = m;
        } else {
            S.matches.push(m);
        }
    }
    function flash(id, side) {
        const key = `${id}-${side}`;
        S.flash.add(key);
        setTimeout(() => S.flash.delete(key), 650);
    }
    function upsertEvent(ev) {
        const i = S.events.findIndex((x) => x.id === ev.id);
        if (i >= 0) S.events[i] = ev; else S.events = [ev, ...S.events].slice(0, 60);
        if (S.sel === ev.matchId) {
            const j = S.dockEvents.findIndex((x) => x.id === ev.id);
            if (j >= 0) S.dockEvents[j] = ev; else S.dockEvents.unshift(ev);
        }
    }

    // ------------------------------------------------------------ rendering
    let renderQueued = false;
    function queueRender() {
        if (renderQueued) return;
        renderQueued = true;
        requestAnimationFrame(() => { renderQueued = false; render(); });
    }
    function render() {
        renderTop();
        renderView();
        renderDock();
    }

    function renderTop() {
        $$('#nav a').forEach((a) => a.classList.toggle('on', a.dataset.view === S.view));
        const staffLink = $('#staffLink');
        if (staffLink) staffLink.hidden = !isAdminAccount();
        const acct = $('#account');
        const html = S.me
            ? `<button class="who-btn" data-action="account" title="Account and log out"><span class="who-name">${esc(S.me.name)}</span><span class="tag ${S.me.role === 'ADMIN' ? 'live' : ''}">${S.me.role === 'ADMIN' ? 'Admin' : 'Coach'}</span></button>`
            : '<button class="btn sm dark" data-action="login">Log in</button>';
        if (acct && acct._last !== html) { acct.innerHTML = html; acct._last = html; }
        const conn = $('#conn');
        conn.classList.toggle('on', S.connected);
        conn.classList.toggle('off', !S.connected && S.everConnected);
        const label = S.connected ? 'Live updates on' : (S.everConnected ? 'Reconnecting' : 'Connecting');
        conn.title = label;
        conn.setAttribute('aria-label', label);
    }

    function renderView() {
        const view = $('#view');
        // Keep focus and cursor in a text box while live updates redraw the page.
        const active = document.activeElement;
        const keep = active && view.contains(active) && active.id
            ? { id: active.id, start: active.selectionStart, end: active.selectionEnd } : null;
        view.innerHTML = ({ day: viewDay, schedule: viewSchedule, standings: viewStandings, teams: viewTeams, records: viewRecords, staff: viewStaff }[S.view] || viewDay)();
        if (keep) {
            const el = document.getElementById(keep.id);
            if (el) {
                el.focus();
                try { if (keep.start != null) el.setSelectionRange(keep.start, keep.end); } catch (_) { /* not a text box */ }
            }
        }
    }

    function tag(m) {
        if (m.status === 'LIVE') return `<span class="tag live">Live ${esc(shortPeriod(m))}</span>`;
        if (m.status === 'DONE') return '<span class="tag done">Final</span>';
        return '<span class="tag">Upcoming</span>';
    }

    const sportChips = (current, action) => `
        <button class="chip ${current === 'all' ? 'on' : ''}" data-action="${action}" data-v="all">All sports</button>
        ${S.sports.map((s) => `<button class="chip ${current === String(s.id) ? 'on' : ''}" data-action="${action}" data-v="${s.id}"><span class="dot" style="--c:${esc(s.color)}"></span>${esc(s.name)}</button>`).join('')}`;

    // ------------------------------------------------------------- Day line
    function dayMatches() {
        const f = S.dayFilters;
        return S.matches.filter((m) => sameDay(matchStart(m), S.day)
            && (f.sport === 'all' || String(m.sport.id) === f.sport)
            && (f.division === 'all' || m.division === f.division));
    }

    function viewDay() {
        const list = dayMatches().sort(bySchedule);
        const isToday = sameDay(S.day, new Date());
        const live = list.filter((m) => m.status === 'LIVE').length;
        const toPlay = list.filter((m) => m.status === 'UPCOMING').length;
        const done = list.filter((m) => m.status === 'DONE').length;
        const f = S.dayFilters;

        const tally = list.length
            ? `${esc(fmtLong(S.day))}. ${live ? `<b class="live">${live} live</b>, ` : ''}<b>${toPlay}</b> to play, <b>${done}</b> final.`
            : esc(fmtLong(S.day));

        return `
            <div class="head">
                <div>
                    <div class="daybar">
                        <button class="btn sm icon quiet" data-action="day" data-v="-1" aria-label="Previous day">${ICON.prev}</button>
                        <h1>${esc(fmtRel(S.day))}</h1>
                        <button class="btn sm icon quiet" data-action="day" data-v="1" aria-label="Next day">${ICON.next}</button>
                        ${isToday ? '' : '<button class="btn sm" data-action="day" data-v="0">Back to today</button>'}
                    </div>
                    <p class="tally">${tally}</p>
                </div>
                <div class="head-actions">
                    <span class="muted" style="font-size:var(--t-sm)">${canEdit() ? 'Click a match to score it' : 'Click a match for details'}</span>
                    ${canEdit() ? '<button class="btn dark" data-action="new-match">New match <kbd>N</kbd></button>' : ''}
                </div>
            </div>
            <div class="filters">
                ${sportChips(f.sport, 'day-sport')}
                <select id="dayDivision" aria-label="Division" data-change="day-division">
                    <option value="all">All divisions</option>
                    ${S.divisions.map((d) => `<option value="${d.value}" ${f.division === d.value ? 'selected' : ''}>${esc(d.label)}</option>`).join('')}
                </select>
            </div>
            ${list.length ? timeline(list, isToday) : `
                <div class="empty">
                    <div><strong>No matches on this day${f.sport !== 'all' || f.division !== 'all' ? ' for these filters' : ''}.</strong>Use the arrows to check another day${canEdit() ? ', or add a match' : ''}.</div>
                    ${canEdit() ? '<button class="btn" data-action="new-match">New match</button>' : ''}
                </div>`}`;
    }

    function timeline(list, isToday) {
        const day0 = S.day.getTime();
        const spans = new Map(list.map((m) => [m.id, matchSpan(m)]));

        // Hours shown: at least 8:00 to 18:00, wider if matches run outside that.
        let lo = 8, hi = 18;
        spans.forEach(({ start, end }) => {
            lo = Math.min(lo, Math.floor((start - day0) / HOUR));
            hi = Math.max(hi, Math.ceil((end - day0) / HOUR));
        });
        lo = Math.max(0, lo); hi = Math.min(24, Math.max(hi, lo + 1));
        const pct = (t) => Math.max(0, Math.min(100, ((t - day0) / HOUR - lo) / (hi - lo) * 100));
        const step = hi - lo > 12 ? 2 : 1;

        // One lane per venue; overlapping matches in a venue stack into extra rows.
        const lanes = new Map();
        list.forEach((m) => {
            const key = m.venue || 'No venue';
            if (!lanes.has(key)) lanes.set(key, []);
            lanes.get(key).push(m);
        });
        const laneNames = [...lanes.keys()].sort((a, b) => (a === 'No venue') - (b === 'No venue') || a.localeCompare(b));

        const ticks = [];
        for (let h = lo; h <= hi; h += step) {
            const label = new Date(day0 + h * HOUR).toLocaleTimeString([], { hour: 'numeric' });
            ticks.push(`<span style="left:${((h - lo) / (hi - lo)) * 100}%">${esc(label)}</span>`);
        }

        const lanesHtml = laneNames.map((name) => {
            const items = lanes.get(name).sort(bySchedule);
            const rowEnds = [];
            const placed = items.map((m) => {
                const { start, end } = spans.get(m.id);
                let row = rowEnds.findIndex((e) => e <= +start);
                if (row < 0) { row = rowEnds.length; rowEnds.push(0); }
                rowEnds[row] = +end;
                return { m, start, end, row };
            });
            const rows = Math.max(1, rowEnds.length);
            const live = items.filter((m) => m.status === 'LIVE').length;
            return `
                <div class="tl-lane">
                    <div class="tl-name">${esc(name)}<small>${live ? `${live} live` : plural(items.length, 'match', 'matches')}</small></div>
                    <div class="tl-track" style="height:calc(var(--lane-row) * ${rows});--hour:${(100 / (hi - lo)) * step}%">
                        ${placed.map((p) => block(p, pct)).join('')}
                    </div>
                </div>`;
        }).join('');

        let nowLine = '';
        if (isToday) {
            const now = Date.now();
            const h = (now - day0) / HOUR;
            if (h >= lo && h <= hi) {
                const frac = (h - lo) / (hi - lo);
                nowLine = `<div class="tl-now" style="left:calc(132px + (100% - 132px) * ${frac.toFixed(4)})"><span>Now ${esc(fmtTime(now))}</span></div>`;
            }
        }

        return `
            <div class="tl-scroll">
                <div class="tl">
                    ${nowLine}
                    <div class="tl-axis"><span></span><div class="tl-ticks">${ticks.join('')}</div></div>
                    ${lanesHtml}
                </div>
            </div>`;
    }

    function block({ m, start, end, row }, pct) {
        const left = pct(start), width = Math.max(pct(end) - left, 3);
        const cls = m.status === 'LIVE' ? 'live' : m.status === 'DONE' ? 'done' : '';
        const marks = `<span class="marks"><span class="sq" style="--c:${esc(m.homeTeam.color)}"></span><span class="sq" style="--c:${esc(m.awayTeam.color)}"></span></span>`;
        let bottom, label;
        if (m.status === 'UPCOMING') {
            bottom = `<span class="when">${esc(fmtTime(m.scheduledAt))}</span>${marks}`;
            label = `${vs(m)}, ${m.sport.name}, starts ${fmtTime(m.scheduledAt)}`;
        } else {
            const fl = S.flash.has(`${m.id}-HOME`) || S.flash.has(`${m.id}-AWAY`) ? 'flash' : '';
            bottom = `<span class="sc ${fl}">${m.homeScore}–${m.awayScore}</span><span class="when">${m.status === 'LIVE' ? esc(shortPeriod(m)) : 'Final'}</span>`;
            label = `${m.homeTeam.shortName} ${m.homeScore}, ${m.awayTeam.shortName} ${m.awayScore}, ${m.status === 'LIVE' ? 'live, ' + periodLabel(m) : 'final'}`;
        }
        return `
            <button class="blk ${cls} ${S.sel === m.id ? 'sel' : ''}" style="left:${left}%;width:calc(${width}% - 4px);top:calc(var(--lane-row) * ${row})"
                data-action="select" data-id="${m.id}" aria-label="${esc(label)}" aria-pressed="${S.sel === m.id}">
                <span class="t"><span class="dot" style="--c:${esc(m.sport.color)}"></span>${esc(vs(m))}</span>
                <span class="b">${bottom}</span>
            </button>`;
    }

    // ---------------------------------------------------------- scoring dock
    async function selectMatch(id) {
        const m = byId(S.matches, id);
        if (!m) return;
        S.sel = m.id;
        S.dockEvents = [];
        S.dockPoll = null;
        render();
        const detail = await attempt(() => api('GET', `/api/matches/${m.id}`));
        if (detail && S.sel === m.id) {
            upsertMatch(detail.match);
            S.dockEvents = detail.events;
            S.dockPoll = detail.poll;
            render();
        }
    }
    function deselect() {
        S.sel = null;
        render();
    }

    function renderDock() {
        const dock = $('#dock');
        const m = S.sel != null && S.view === 'day' ? byId(S.matches, S.sel) : null;
        if (!m) {
            dock.hidden = true;
            dock.innerHTML = '';
            document.body.classList.remove('has-dock');
            return;
        }
        // Keep what's typed in the note box while the panel redraws.
        const noteEl = $('#noteInput');
        const noteVal = noteEl ? noteEl.value : '';
        const noteFocused = noteEl && document.activeElement === noteEl;

        const isLive = m.status === 'LIVE', done = m.status === 'DONE', upcoming = m.status === 'UPCOMING';
        const incs = increments(m.sport);
        const side = (s) => {
            const team = s === 'HOME' ? m.homeTeam : m.awayTeam;
            const mine = s === 'HOME' ? m.homeScore : m.awayScore;
            const theirs = s === 'HOME' ? m.awayScore : m.homeScore;
            const keys = s === 'HOME' ? ['Q', 'W', 'E'] : ['I', 'O', 'P'];
            return `
                <div class="side ${s === 'AWAY' ? 'away' : ''} ${!upcoming && mine < theirs ? 'trail' : ''}">
                    <div class="who"><span class="sq" style="--c:${esc(team.color)}"></span>${esc(team.shortName)} <small>${esc(team.name)}</small></div>
                    <div class="big ${S.flash.has(`${m.id}-${s}`) ? 'flash' : ''}" aria-live="polite" aria-label="${esc(team.shortName)} score">${mine}</div>
                    ${canEdit() ? `<div class="pad">
                        ${incs.map((n, i) => `<button data-action="score" data-id="${m.id}" data-side="${s}" data-delta="${n}" ${isLive ? '' : 'disabled'} aria-label="${esc(team.shortName)} plus ${n}">+${n}${keys[i] ? `<kbd>${keys[i]}</kbd>` : ''}</button>`).join('')}
                        <button class="minus" data-action="score" data-id="${m.id}" data-side="${s}" data-delta="-1" ${isLive && mine > 0 ? '' : 'disabled'} aria-label="${esc(team.shortName)} minus 1">−1<kbd>${s === 'HOME' ? 'A' : 'L'}</kbd></button>
                    </div>` : ''}
                </div>`;
        };

        let mid;
        if (!canEdit()) {
            if (upcoming) mid = `<div class="period">Starts ${esc(fmtTime(m.scheduledAt))}<small>${esc(fmtRel(toDate(m.scheduledAt)))}</small></div>`;
            else if (done) mid = `<div class="final">Final</div><div class="clock">Ended ${esc(fmtTime(m.endedAt))}</div>`;
            else mid = `<div class="period">${esc(periodLabel(m))}<small>${m.currentPeriod > m.sport.periodCount ? 'Extra time' : `${m.currentPeriod} of ${m.sport.periodCount}`}</small></div><div class="clock" id="dockClock" title="Time since start">${esc(elapsed(m.startedAt))}</div>`;
        } else if (upcoming) {
            mid = `
                <div class="period">Starts ${esc(fmtTime(m.scheduledAt))}<small>${esc(fmtRel(toDate(m.scheduledAt)))}</small></div>
                <button class="btn go" data-action="start" data-id="${m.id}">Start match <kbd>S</kbd></button>`;
        } else if (done) {
            mid = `
                <div class="final">Final</div>
                <div class="clock">Ended ${esc(fmtTime(m.endedAt))}</div>
                <button class="btn" data-action="set-score" data-id="${m.id}">Correct score</button>
                ${canEdit() ? `<button class="btn quiet" data-action="reopen" data-id="${m.id}">Reopen match</button>` : ''}`;
        } else {
            mid = `
                <div class="period">${esc(periodLabel(m))}<small>${m.currentPeriod > m.sport.periodCount ? 'Extra time' : `${m.currentPeriod} of ${m.sport.periodCount}`}</small></div>
                <div class="row">
                    <button class="btn sm icon" data-action="period" data-id="${m.id}" data-dir="-1" aria-label="Previous ${esc(m.sport.periodName)}">${ICON.prev}</button>
                    <button class="btn sm" data-action="period" data-id="${m.id}" data-dir="1">Next ${esc(m.sport.periodName.toLowerCase())} <kbd>]</kbd></button>
                </div>
                <div class="clock" id="dockClock" title="Time since start">${esc(elapsed(m.startedAt))}</div>
                <button class="btn" data-action="undo" data-id="${m.id}">Undo <kbd>Z</kbd></button>
                <button class="btn quiet" data-action="set-score" data-id="${m.id}">Set score</button>
                <button class="btn dark" data-action="finish" data-id="${m.id}">End match</button>`;
        }

        const p = S.dockPoll;
        let pulse = '';
        if (p && p.homeVotes + p.awayVotes > 0 && (canEdit() || !upcoming)) {
            const total = p.homeVotes + p.awayVotes;
            const hp = Math.round((p.homeVotes / total) * 100);
            pulse = `
                <div class="pulse" aria-label="Fan picks: ${esc(m.homeTeam.shortName)} ${hp} percent, ${esc(m.awayTeam.shortName)} ${100 - hp} percent">
                    <span style="flex-grow:${Math.max(p.homeVotes, 0.001)};background:${esc(m.homeTeam.color)}">${esc(m.homeTeam.shortName)} ${hp}%</span>
                    <span style="flex-grow:${Math.max(p.awayVotes, 0.001)};background:${esc(m.awayTeam.color)};justify-content:flex-end">${100 - hp}% ${esc(m.awayTeam.shortName)}</span>
                </div>
                <div class="pulse-legend"><span>Who fans think will win, from ${plural(total, 'pick')}</span>${canEdit() ? `<button class="btn sm quiet danger" data-action="reset-poll" data-id="${m.id}">Clear picks</button>` : ''}</div>`;
        }

        dock.innerHTML = `
            <div class="dock-in">
                <div class="dock-head">
                    <span class="what"><span class="dot" style="--c:${esc(m.sport.color)}"></span>${esc(m.sport.name)}, ${esc(m.divisionLabel)}${m.venue ? `, ${esc(m.venue)}` : ''}, ${esc(m.code)}</span>
                    ${tag(m)}
                    <span class="sp"></span>
                    ${canEdit() ? `<button class="btn sm quiet" data-action="edit-match" data-id="${m.id}">Edit</button><button class="btn sm quiet danger" data-action="delete-match" data-id="${m.id}">Delete</button>` : ''}
                    <button class="btn sm icon quiet" data-action="deselect" aria-label="Close scoring panel">${ICON.close}</button>
                </div>
                <div class="dock-body">
                    <div class="score">${side('HOME')}<div class="mid">${mid}</div>${side('AWAY')}</div>
                    <div class="log">
                        ${!canEdit() && upcoming ? pickBlock(m) : ''}
                        ${canEdit() ? `<form class="note" data-form="note">
                            <input id="noteInput" type="text" name="message" maxlength="240" placeholder="Add a note, e.g. Timeout ACS" aria-label="Note">
                            <button class="btn" type="submit">Add</button>
                        </form>` : ''}
                        ${S.dockEvents.length ? `<ul class="events">${S.dockEvents.map(eventRow).join('')}</ul>` : '<p class="muted" style="margin:0;font-size:var(--t-sm)">Nothing logged yet.</p>'}
                        ${pulse}
                    </div>
                </div>
            </div>`;
        dock.hidden = false;
        document.body.classList.add('has-dock');

        const newNote = $('#noteInput');
        if (newNote) {
            newNote.value = noteVal;
            if (noteFocused) newNote.focus();
        }
    }

    /** User view: "Who will win?" on an upcoming match. Results show after picking. */
    function pickBlock(m) {
        const p = S.picks.get(m.id) || { homeVotes: 0, awayVotes: 0, mine: null };
        const total = p.homeVotes + p.awayVotes;
        const btn = (side) => {
            const t = side === 'HOME' ? m.homeTeam : m.awayTeam;
            return `<button class="pickbtn" data-action="pick" data-id="${m.id}" data-side="${side}" aria-pressed="${p.mine === side}"><span class="sq" style="--c:${esc(t.color)}"></span>${esc(t.shortName)}</button>`;
        };
        let result = '<div class="split-note">Pick a team to see what everyone else thinks.</div>';
        if (p.mine) {
            const hp = total ? Math.round((p.homeVotes / total) * 100) : 50;
            const mineTeam = p.mine === 'HOME' ? m.homeTeam : m.awayTeam;
            result = `
                <div class="pulse" aria-label="${esc(m.homeTeam.shortName)} ${hp} percent, ${esc(m.awayTeam.shortName)} ${100 - hp} percent">
                    <span style="flex-grow:${Math.max(p.homeVotes, 0.001)};background:${esc(m.homeTeam.color)}">${esc(m.homeTeam.shortName)} ${hp}%</span>
                    <span style="flex-grow:${Math.max(p.awayVotes, 0.001)};background:${esc(m.awayTeam.color)};justify-content:flex-end">${100 - hp}% ${esc(m.awayTeam.shortName)}</span>
                </div>
                <div class="split-note">You picked ${esc(mineTeam.shortName)}. ${plural(total, 'pick')} so far. You can change it until the match starts.</div>`;
        }
        return `<div class="pick-q">Who will win?</div><div class="picks">${btn('HOME')}${btn('AWAY')}</div>${result}`;
    }

    async function pick(id, side) {
        const summary = await attempt(() => api('POST', `/api/matches/${id}/poll/vote`, { side, tokens: 1, voter }));
        if (!summary) return;
        S.picks.set(id, { homeVotes: summary.homeVotes, awayVotes: summary.awayVotes, mine: side });
        S.dockPoll = summary;
        renderDock();
    }

    function eventRow(ev) {
        const showScore = ['SCORE', 'CORRECTION', 'UNDO'].includes(ev.type);
        return `
            <li class="${ev.undone ? 'undone' : ''}">
                <span class="sq" style="--c:${esc(ev.teamColor || 'var(--outline)')}"></span>
                <div><div class="msg">${esc(ev.message)}</div>${ev.actor ? `<div class="who">by ${esc(ev.actor)}</div>` : ''}</div>
                <div class="at">${showScore ? `<b>${ev.homeAfter}–${ev.awayAfter}</b>` : ''}${esc(ago(ev.createdAt))}</div>
            </li>`;
    }

    // Tick the match clock every second; move the "now" line every 30 seconds.
    setInterval(() => {
        const el = $('#dockClock');
        const m = S.sel != null ? byId(S.matches, S.sel) : null;
        if (el && m) el.textContent = elapsed(m.startedAt);
    }, 1000);
    setInterval(() => { if (S.view === 'day' && !isTyping(document.activeElement)) renderView(); }, 30000);

    // -------------------------------------------------------------- schedule
    function viewSchedule() {
        const f = S.filters;
        const q = f.q.trim().toLowerCase();
        const list = S.matches.filter((m) => {
            if (f.sport !== 'all' && String(m.sport.id) !== f.sport) return false;
            if (f.status !== 'all' && m.status !== f.status) return false;
            if (f.division !== 'all' && m.division !== f.division) return false;
            if (q) {
                const hay = `${m.homeTeam.name} ${m.homeTeam.shortName} ${m.awayTeam.name} ${m.awayTeam.shortName} ${m.venue || ''} ${m.code} ${m.sport.name}`.toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        }).sort(bySchedule);

        const groups = [];
        list.forEach((m) => {
            const key = startOfDay(matchStart(m)).getTime();
            let g = groups.find((x) => x.key === key);
            if (!g) { g = { key, items: [] }; groups.push(g); }
            g.items.push(m);
        });
        const st = (v, label) => `<button class="chip ${f.status === v ? 'on' : ''}" data-action="filter-status" data-v="${v}">${label}</button>`;
        const any = f.sport !== 'all' || f.status !== 'all' || f.division !== 'all' || f.q;

        return `
            <div class="head">
                <div><h1>Schedule</h1><p>${plural(list.length, 'match', 'matches')}${any ? ' match these filters' : ' in the tournament'}.${canEdit() ? '' : ' Coaches and admins can log in to add or change matches.'}</p></div>
                ${canEdit() ? '<div class="head-actions"><button class="btn dark" data-action="new-match">New match <kbd>N</kbd></button></div>' : ''}
            </div>
            <div class="filters">${sportChips(f.sport, 'filter-sport')}</div>
            <div class="filters">
                ${st('all', 'Any status')}${st('LIVE', 'Live')}${st('UPCOMING', 'Upcoming')}${st('DONE', 'Final')}
                <select id="schedDivision" aria-label="Division" data-change="division">
                    <option value="all">All divisions</option>
                    ${S.divisions.map((d) => `<option value="${d.value}" ${f.division === d.value ? 'selected' : ''}>${esc(d.label)}</option>`).join('')}
                </select>
                <input id="schedSearch" type="search" placeholder="Search team, venue or M-012" value="${esc(f.q)}" data-input="search" aria-label="Search matches">
                ${any ? '<button class="btn sm quiet" data-action="clear-filters">Clear filters</button>' : ''}
            </div>
            ${groups.length ? groups.map((g) => `
                <h3 class="day-h">${esc(fmtRel(new Date(g.key)))}${dayDiff(new Date(g.key)) > 1 || dayDiff(new Date(g.key)) < -1 ? '' : `, ${esc(new Date(g.key).toLocaleDateString([], { day: 'numeric', month: 'long' }))}`}</h3>
                <ul class="list">${g.items.map(scheduleRow).join('')}</ul>`).join('') : `
                <div class="empty"><div><strong>No matches found.</strong>Try another sport or status, or clear the filters.</div>
                ${any ? '<button class="btn" data-action="clear-filters">Clear filters</button>' : ''}</div>`}`;
    }

    function scheduleRow(m) {
        return `
            <li>
                <div class="time">${esc(fmtTime(matchStart(m)))}<small>${esc(m.code)}</small></div>
                <button class="open" data-action="goto" data-id="${m.id}" aria-label="Open ${esc(vs(m))} on the Day line">
                    <div class="vs"><span class="sq" style="--c:${esc(m.homeTeam.color)}"></span>${esc(m.homeTeam.shortName)} <span class="muted" style="font-weight:400">v</span> <span class="sq" style="--c:${esc(m.awayTeam.color)}"></span>${esc(m.awayTeam.shortName)} ${tag(m)}</div>
                    <div class="meta">${esc(m.sport.name)}, ${esc(m.divisionLabel)}${m.venue ? `, ${esc(m.venue)}` : ''}</div>
                </button>
                <div class="res">${m.status === 'UPCOMING' ? '' : `${m.homeScore}–${m.awayScore}`}</div>
                <div class="acts">
                    <button class="btn sm" data-action="goto" data-id="${m.id}">${m.status === 'LIVE' ? 'Score' : 'Open'}</button>
                    ${canEdit() ? `<button class="btn sm quiet" data-action="edit-match" data-id="${m.id}">Edit</button><button class="btn sm quiet danger" data-action="delete-match" data-id="${m.id}" aria-label="Delete ${esc(m.code)}">Delete</button>` : ''}
                </div>
            </li>`;
    }

    // ------------------------------------------------------------- standings
    let standingsTimer = null;
    function loadStandings(delay = 0) {
        clearTimeout(standingsTimer);
        standingsTimer = setTimeout(async () => {
            const st = S.standings;
            if (!st.sportId && S.sports[0]) st.sportId = S.sports[0].id;
            if (!st.sportId) return;
            const rows = await attempt(() => api('GET', `/api/standings?sportId=${st.sportId}&division=${st.division}`));
            if (rows) { st.rows = rows; if (S.view === 'standings') queueRender(); }
        }, delay);
    }

    function viewStandings() {
        const st = S.standings;
        const sport = byId(S.sports, st.sportId) || S.sports[0];
        if (!sport) return '<div class="empty">Add a sport first on the Teams page.</div>';
        const draws = sport.allowsDraw;
        const rows = st.rows;
        let table;
        if (!rows) table = '<p class="muted">Working out the table…</p>';
        else if (!rows.length) table = `<div class="empty"><div><strong>No ${esc(sport.name.toLowerCase())} matches in this division yet.</strong>The table fills in as results come in.</div></div>`;
        else table = `
            <div class="table-wrap"><table class="grid">
                <thead><tr><th></th><th>Team</th><th class="num">Played</th><th class="num">Won</th>${draws ? '<th class="num">Drawn</th>' : ''}<th class="num">Lost</th>
                <th class="num">For</th><th class="num">Against</th><th class="num">+/−</th><th class="num">Points</th><th>Last 5</th></tr></thead>
                <tbody>${rows.map((r, i) => `
                    <tr>
                        <td class="rank">${i + 1}</td>
                        <td><span style="display:inline-flex;align-items:center;gap:8px;font-weight:600"><span class="sq" style="--c:${esc(r.team.color)}"></span>${esc(r.team.shortName)}</span> <span class="muted">${esc(r.team.name)}</span></td>
                        <td class="num">${r.played}</td><td class="num">${r.wins}</td>${draws ? `<td class="num">${r.draws}</td>` : ''}<td class="num">${r.losses}</td>
                        <td class="num">${r.pointsFor}</td><td class="num">${r.pointsAgainst}</td>
                        <td class="num">${r.difference > 0 ? '+' : ''}${r.difference}</td>
                        <td class="num pts">${r.standingPoints}</td>
                        <td><span class="form">${r.form.map((x) => `<i class="${x}" title="${{ W: 'Win', L: 'Loss', D: 'Draw' }[x]}">${x}</i>`).join('') || '<span class="muted">No games</span>'}</span></td>
                    </tr>`).join('')}</tbody>
            </table></div>`;

        return `
            <div class="head"><div><h1>Standings</h1><p>From final scores. 3 points for a win${draws ? ', 1 for a draw' : ''}. Ties split by score difference, then points scored.</p></div></div>
            <div class="filters">${S.sports.map((s) => `<button class="chip ${s.id === sport.id ? 'on' : ''}" data-action="st-sport" data-v="${s.id}"><span class="dot" style="--c:${esc(s.color)}"></span>${esc(s.name)}</button>`).join('')}</div>
            <div class="filters">${S.divisions.map((d) => `<button class="chip ${d.value === st.division ? 'on' : ''}" data-action="st-division" data-v="${d.value}">${esc(d.label)}</button>`).join('')}</div>
            ${table}`;
    }

    // ---------------------------------------------------------- teams/sports
    function teamRecord(teamId) {
        let w = 0, l = 0, d = 0;
        S.matches.forEach((m) => {
            if (m.status !== 'DONE') return;
            const home = m.homeTeam.id === teamId, away = m.awayTeam.id === teamId;
            if (!home && !away) return;
            const mine = home ? m.homeScore : m.awayScore, theirs = home ? m.awayScore : m.homeScore;
            if (mine > theirs) w++; else if (mine < theirs) l++; else d++;
        });
        return d ? `${w}–${l}–${d}` : `${w}–${l}`;
    }

    function viewTeams() {
        const admin = canEdit();
        const teamTile = (t) => {
            const inner = `
                <span class="wl" title="Wins and losses, all sports">${teamRecord(t.id)}</span>
                <span class="code"><span class="sq" style="--c:${esc(t.color)};width:14px;height:14px"></span>${esc(t.shortName)}</span>
                <span class="nm">${esc(t.name)}</span>
                <span class="ct">${esc(t.city || '')}</span>`;
            return admin
                ? `<button class="team" data-action="edit-team" data-id="${t.id}" aria-label="Edit ${esc(t.name)}">${inner}</button>`
                : `<div class="team">${inner}</div>`;
        };
        return `
            <div class="head">
                <div><h1>Teams</h1><p>${admin ? 'Click a team to edit it.' : 'Log in to add or edit teams and sports.'}</p></div>
                ${admin ? '<div class="head-actions"><button class="btn" data-action="new-sport">Add sport</button><button class="btn dark" data-action="new-team">Add team</button></div>' : ''}
            </div>
            <div class="teams">
                ${S.teams.map(teamTile).join('')}
                ${admin ? '<button class="add" data-action="new-team">Add team</button>' : ''}
            </div>
            <h2 class="sub">Sports</h2>
            <ul class="sports">
                ${S.sports.map((s) => `
                    <li>
                        <span class="dot" style="--c:${esc(s.color)};width:14px;height:14px"></span>
                        <div><b>${esc(s.name)}</b><div class="meta">Buttons ${increments(s).map((n) => '+' + n).join(' ')}, ${s.periodCount} × ${esc(s.periodName.toLowerCase())}, about ${s.matchMinutes} min, ${s.allowsDraw ? 'draws allowed' : 'no draws'}</div></div>
                        <span class="meta">${plural(S.matches.filter((m) => m.sport.id === s.id).length, 'match', 'matches')}</span>
                        ${admin ? `<button class="btn sm" data-action="edit-sport" data-id="${s.id}">Edit</button>` : '<span></span>'}
                    </li>`).join('')}
            </ul>`;
    }

    // --------------------------------------------------------------- records
    function viewRecords() {
        const admin = canEdit();
        const groups = [];
        S.records.forEach((r) => {
            const key = r.sport ? r.sport.id : 0;
            let g = groups.find((x) => x.key === key);
            if (!g) { g = { key, sport: r.sport, items: [] }; groups.push(g); }
            g.items.push(r);
        });
        return `
            <div class="head">
                <div><h1>Records</h1><p>Conference bests.${admin ? '' : ' Log in to add or edit records.'}</p></div>
                ${admin ? '<div class="head-actions"><button class="btn dark" data-action="new-record">Add record</button></div>' : ''}
            </div>
            ${groups.length ? groups.map((g) => `
                <section class="rec-group">
                    <h3>${g.sport ? `<span class="dot" style="--c:${esc(g.sport.color)}"></span>${esc(g.sport.name)}` : 'All sports'}</h3>
                    ${g.items.map((r) => `
                        <div class="rec">
                            <div><div class="title">${esc(r.title)}</div><div class="by">${esc(r.holder)}${r.school ? `, ${esc(r.school)}` : ''}${r.divisionLabel ? `, ${esc(r.divisionLabel)}` : ''}</div></div>
                            <div class="val">${esc(r.recordValue || '—')}<small>Set in ${r.yearSet}</small></div>
                            <div class="acts">${admin ? `<button class="btn sm" data-action="edit-record" data-id="${r.id}">Edit</button>` : ''}</div>
                        </div>`).join('')}
                </section>`).join('') : `
                <div class="empty"><div><strong>No records yet.</strong>${admin ? 'Add the first conference best.' : 'An admin can add them.'}</div>${admin ? '<button class="btn" data-action="new-record">Add record</button>' : ''}</div>`}`;
    }

    // ------------------------------------------------------- match actions
    async function doScore(id, side, delta) {
        const m = await attempt(() => api('POST', `/api/matches/${id}/score`, { side, delta }));
        if (!m) return;
        upsertMatch(m);
        queueRender();
        const team = side === 'HOME' ? m.homeTeam : m.awayTeam;
        toast(`${team.shortName} ${delta > 0 ? '+' : ''}${delta}, now ${m.homeScore}–${m.awayScore}`, { key: 'score', action: { label: 'Undo', fn: () => doUndo(id) } });
    }
    async function doUndo(id) {
        const m = await attempt(() => api('POST', `/api/matches/${id}/undo`));
        if (m) { upsertMatch(m); queueRender(); toast(`Undone. Score is ${m.homeScore}–${m.awayScore}.`, { key: 'score' }); }
    }
    async function matchAction(id, path, body, okMsg) {
        const m = await attempt(() => api('POST', `/api/matches/${id}/${path}`, body));
        if (m) { upsertMatch(m); queueRender(); if (okMsg) toast(typeof okMsg === 'function' ? okMsg(m) : okMsg); }
        return m;
    }
    async function finishMatch(id) {
        const m = byId(S.matches, id);
        const ok = await confirmBox({
            title: 'End this match?',
            body: `Final score ${m.homeTeam.shortName} ${m.homeScore}–${m.awayScore} ${m.awayTeam.shortName}. It will count in the standings.`,
            ok: 'End match',
        });
        if (ok) matchAction(id, 'finish', undefined, 'Match ended. Standings updated.');
    }
    function goto(id) {
        const m = byId(S.matches, id);
        if (!m) return;
        S.day = startOfDay(matchStart(m));
        S.dayFilters = { sport: 'all', division: 'all' };
        if (location.hash !== '#/day') location.hash = '#/day';
        selectMatch(m.id);
    }
    function requireStaff() {
        if (canEdit()) return true;
        toast('Log in as a coach or admin to do that.', { error: true });
        return false;
    }

    function setScoreDialog(id) {
        const m = byId(S.matches, id);
        openModal(`
            <form class="modal" data-form="set-score" data-id="${m.id}">
                <h3>Set the score</h3>
                <p>Use this to fix a mistake. It is logged in the timeline and can be undone.</p>
                <div class="setscore">
                    <label>${esc(m.homeTeam.shortName)}<input id="ssHome" type="number" name="home" min="0" value="${m.homeScore}" required></label>
                    <span class="muted" style="font-size:2rem;padding-bottom:14px">–</span>
                    <label>${esc(m.awayTeam.shortName)}<input type="number" name="away" min="0" value="${m.awayScore}" required></label>
                </div>
                <div class="acts"><button type="button" class="btn quiet" data-action="close-modal">Cancel</button><button class="btn dark" type="submit">Save score</button></div>
            </form>`);
        setTimeout(() => { const el = $('#ssHome'); if (el) { el.focus(); el.select(); } }, 30);
    }

    // --------------------------------------------------------------- drawers
    function openDrawer(title, fieldsHtml, { submit = 'Save', onSubmit, danger = null } = {}) {
        const d = $('#drawer');
        d.innerHTML = `
            <header><h2>${esc(title)}</h2><button class="btn icon quiet" data-action="close-drawer" aria-label="Close">${ICON.close}</button></header>
            <form data-form="drawer" novalidate>
                <div class="fields">${fieldsHtml}</div>
                <footer>
                    ${danger ? `<button type="button" class="btn danger" data-action="${danger.action}" data-id="${danger.id}" style="margin-right:auto">${esc(danger.label)}</button>` : ''}
                    <button type="button" class="btn quiet" data-action="close-drawer">Cancel</button>
                    <button type="submit" class="btn dark">${esc(submit)}</button>
                </footer>
            </form>`;
        S.drawerSubmit = onSubmit;
        d.hidden = false;
        $('#drawerBackdrop').hidden = false;
        setTimeout(() => { const first = $('input:not([type=radio]):not([type=hidden]), select', d); if (first) first.focus(); }, 40);
    }
    function closeDrawer() {
        $('#drawer').hidden = true;
        $('#drawerBackdrop').hidden = true;
        $('#drawer').innerHTML = '';
        S.drawerSubmit = null;
    }

    const teamOptions = (selectedId) => S.teams.map((t) => `<option value="${t.id}" ${String(t.id) === String(selectedId) ? 'selected' : ''}>${esc(t.shortName)}, ${esc(t.name)}</option>`).join('');

    function matchForm(m) {
        if (!requireStaff()) return;
        const editing = !!m;
        const sportId = m ? m.sport.id : (S.dayFilters.sport !== 'all' ? Number(S.dayFilters.sport) : S.sports[0] && S.sports[0].id);
        const division = m ? m.division : (S.dayFilters.division !== 'all' ? S.dayFilters.division : 'VARSITY_BOYS');
        const when = m && m.scheduledAt ? localInputValue(toDate(m.scheduledAt)) : localInputValue(defaultStart());
        const home = m ? m.homeTeam.id : S.teams[0] && S.teams[0].id;
        const away = m ? m.awayTeam.id : S.teams[1] && S.teams[1].id;

        const fields = `
            <div class="field"><span>Sport</span>
                <div class="pick">${S.sports.map((s) => `
                    <label><input type="radio" name="sportId" value="${s.id}" ${s.id === sportId ? 'checked' : ''}><span><span class="dot" style="--c:${esc(s.color)}"></span>${esc(s.name)}</span></label>`).join('')}
                </div>
            </div>
            <label class="field"><span>Division</span>
                <select name="division">${S.divisions.map((d) => `<option value="${d.value}" ${d.value === division ? 'selected' : ''}>${esc(d.label)}</option>`).join('')}</select>
            </label>
            <div class="versus">
                <label class="field"><span>Home</span><select name="homeTeamId" id="homePick">${teamOptions(home)}</select></label>
                <button type="button" class="btn icon quiet" data-action="swap-teams" title="Swap home and away" aria-label="Swap home and away">${ICON.swap}</button>
                <label class="field"><span>Away</span><select name="awayTeamId" id="awayPick">${teamOptions(away)}</select></label>
            </div>
            <div class="row2">
                <label class="field"><span>Date and time</span><input type="datetime-local" name="scheduledAt" value="${when}" required></label>
                <label class="field"><span>Venue</span><input type="text" name="venue" list="venueList" value="${esc(m ? m.venue || '' : '')}" placeholder="Main Gym"><small>Each venue is a row on the Day line.</small></label>
            </div>
            <datalist id="venueList">${venues().map((v) => `<option value="${esc(v)}">`).join('')}</datalist>
            ${editing ? '' : `
            <div class="field"><span>When does it start?</span>
                <label class="check"><input type="radio" name="status" value="UPCOMING" checked> At the scheduled time</label>
                <label class="check"><input type="radio" name="status" value="LIVE"> Now (opens it for scoring)</label>
            </div>`}`;

        openDrawer(editing ? `Edit ${m.code}` : 'New match', fields, {
            submit: editing ? 'Save changes' : 'Create match',
            danger: editing ? { action: 'delete-match', id: m.id, label: 'Delete' } : null,
            onSubmit: async (fd) => {
                const body = {
                    sportId: Number(fd.get('sportId')),
                    division: fd.get('division'),
                    homeTeamId: Number(fd.get('homeTeamId')),
                    awayTeamId: Number(fd.get('awayTeamId')),
                    scheduledAt: fd.get('scheduledAt') || null,
                    venue: fd.get('venue'),
                    status: fd.get('status') || undefined,
                };
                if (!body.sportId) { toast('Pick a sport.', { error: true }); return false; }
                if (body.homeTeamId === body.awayTeamId) { toast('Pick two different teams.', { error: true }); return false; }
                const saved = await attempt(() => (editing ? api('PUT', `/api/matches/${m.id}`, body) : api('POST', '/api/matches', body)));
                if (!saved) return false;
                upsertMatch(saved);
                toast(editing ? 'Changes saved.' : `Match ${saved.code} created.`);
                if (!editing) goto(saved.id); else queueRender();
                return true;
            },
        });
    }

    function teamForm(t) {
        if (!requireStaff()) return;
        const editing = !!t;
        const color = t ? t.color : SWATCHES[Math.floor(Math.random() * SWATCHES.length)];
        const fields = `
            <label class="field"><span>School name</span><input type="text" name="name" value="${esc(t ? t.name : '')}" placeholder="American Community School" required></label>
            <div class="row2">
                <label class="field"><span>Short code</span><input type="text" name="shortName" value="${esc(t ? t.shortName : '')}" maxlength="6" placeholder="ACS" required style="text-transform:uppercase"><small>Up to 6 letters.</small></label>
                <label class="field"><span>City</span><input type="text" name="city" value="${esc(t ? t.city || '' : '')}" placeholder="Abu Dhabi"></label>
            </div>
            <div class="field"><span>Team colour</span>
                <div class="swatches">
                    <input type="color" name="color" id="teamColor" value="${esc(color)}" aria-label="Pick a colour">
                    ${SWATCHES.map((c) => `<button type="button" style="--c:${c}" data-action="swatch" data-c="${c}" aria-label="Use ${c}"></button>`).join('')}
                </div>
            </div>`;
        openDrawer(editing ? `Edit ${t.shortName}` : 'Add team', fields, {
            submit: editing ? 'Save changes' : 'Add team',
            danger: editing ? { action: 'delete-team', id: t.id, label: 'Delete' } : null,
            onSubmit: async (fd) => {
                const body = Object.fromEntries(fd.entries());
                const saved = await attempt(() => (editing ? api('PUT', `/api/teams/${t.id}`, body) : api('POST', '/api/teams', body)));
                if (!saved) return false;
                await refreshCatalog();
                toast(editing ? 'Team saved.' : `${saved.shortName} added.`);
                return true;
            },
        });
    }

    function sportForm(s) {
        if (!requireStaff()) return;
        const editing = !!s;
        const fields = `
            <label class="field"><span>Name</span><input type="text" name="name" value="${esc(s ? s.name : '')}" placeholder="Futsal" required></label>
            <label class="field"><span>Score buttons</span><input type="text" name="scoreIncrements" value="${esc(s ? s.scoreIncrements : '1')}" placeholder="1,2,3"><small>Comma-separated. Basketball uses 1,2,3. Soccer uses 1.</small></label>
            <div class="row2">
                <label class="field"><span>A period is called</span><input type="text" name="periodName" value="${esc(s ? s.periodName : 'Half')}" placeholder="Quarter"></label>
                <label class="field"><span>Number of periods</span><input type="number" name="periodCount" min="1" max="9" value="${s ? s.periodCount : 2}"></label>
            </div>
            <label class="field"><span>Typical match length (minutes)</span><input type="number" name="matchMinutes" min="10" max="300" value="${s ? s.matchMinutes : 60}"><small>Sets how wide matches are on the Day line.</small></label>
            <label class="check"><input type="checkbox" name="allowsDraw" ${s && s.allowsDraw ? 'checked' : ''}> A match can end in a draw</label>
            <div class="field"><span>Colour</span><div class="swatches"><input type="color" name="color" value="${esc(s ? s.color : '#5B8DEF')}" aria-label="Pick a colour"></div></div>`;
        openDrawer(editing ? `Edit ${s.name}` : 'Add sport', fields, {
            submit: editing ? 'Save changes' : 'Add sport',
            danger: editing ? { action: 'delete-sport', id: s.id, label: 'Delete' } : null,
            onSubmit: async (fd) => {
                const body = {
                    name: fd.get('name'), icon: s ? s.icon : null, color: fd.get('color'),
                    scoreIncrements: fd.get('scoreIncrements'), periodName: fd.get('periodName'),
                    periodCount: Number(fd.get('periodCount')), matchMinutes: Number(fd.get('matchMinutes')),
                    allowsDraw: fd.get('allowsDraw') === 'on',
                };
                const saved = await attempt(() => (editing ? api('PUT', `/api/sports/${s.id}`, body) : api('POST', '/api/sports', body)));
                if (!saved) return false;
                await refreshCatalog();
                toast(editing ? 'Sport saved.' : `${saved.name} added.`);
                return true;
            },
        });
    }

    function recordForm(r) {
        if (!requireStaff()) return;
        const editing = !!r;
        const year = new Date().getFullYear();
        const fields = `
            <div class="row2">
                <label class="field"><span>Sport</span><select name="sportId"><option value="">All sports</option>${S.sports.map((s) => `<option value="${s.id}" ${r && r.sport && r.sport.id === s.id ? 'selected' : ''}>${esc(s.name)}</option>`).join('')}</select></label>
                <label class="field"><span>Division</span><select name="division"><option value="">Any division</option>${S.divisions.map((d) => `<option value="${d.value}" ${r && r.division === d.value ? 'selected' : ''}>${esc(d.label)}</option>`).join('')}</select></label>
            </div>
            <label class="field"><span>Record</span><input type="text" name="title" value="${esc(r ? r.title : '')}" placeholder="Most points in a single game" required></label>
            <div class="row2">
                <label class="field"><span>Held by</span><input type="text" name="holder" value="${esc(r ? r.holder : '')}" placeholder="Player or team" required></label>
                <label class="field"><span>School</span><input type="text" name="school" list="schoolList" value="${esc(r ? r.school || '' : '')}" placeholder="ACS"></label>
            </div>
            <datalist id="schoolList">${S.teams.map((t) => `<option value="${esc(t.shortName)}">${esc(t.name)}</option>`).join('')}</datalist>
            <div class="row2">
                <label class="field"><span>Value</span><input type="text" name="recordValue" value="${esc(r ? r.recordValue || '' : '')}" placeholder="45 pts"></label>
                <label class="field"><span>Year set</span><input type="number" name="yearSet" min="1950" max="${year}" value="${r ? r.yearSet : year}"></label>
            </div>`;
        openDrawer(editing ? 'Edit record' : 'Add record', fields, {
            submit: editing ? 'Save changes' : 'Add record',
            danger: editing ? { action: 'delete-record', id: r.id, label: 'Delete' } : null,
            onSubmit: async (fd) => {
                const body = {
                    sportId: fd.get('sportId') ? Number(fd.get('sportId')) : null,
                    division: fd.get('division') || null,
                    title: fd.get('title'), holder: fd.get('holder'), school: fd.get('school'),
                    recordValue: fd.get('recordValue'), yearSet: Number(fd.get('yearSet')),
                };
                const saved = await attempt(() => (editing ? api('PUT', `/api/records/${r.id}`, body) : api('POST', '/api/records', body)));
                if (!saved) return false;
                await refreshRecords();
                toast(editing ? 'Record saved.' : 'Record added.');
                return true;
            },
        });
    }

    // ------------------------------------------------------- signing in
    function loginDialog() {
        openModal(`
            <form class="modal" data-form="login" autocomplete="on">
                <h3>Log in</h3>
                <p>For coaches and admins. Everyone else can look around without logging in.</p>
                <div class="stack">
                    <label class="field"><span>Username</span><input id="loginUser" type="text" name="username" autocomplete="username" autocapitalize="none" spellcheck="false" required></label>
                    <label class="field"><span>Password</span><input type="password" name="password" autocomplete="current-password" required></label>
                </div>
                <p id="loginError" class="form-error" role="alert"></p>
                <div class="acts"><button type="button" class="btn quiet" data-action="close-modal">Cancel</button><button class="btn dark" type="submit">Log in</button></div>
            </form>`);
        setTimeout(() => { const el = $('#loginUser'); if (el) el.focus(); }, 30);
    }

    function accountDialog() {
        if (!S.me) { loginDialog(); return; }
        openModal(`
            <form class="modal" data-form="password">
                <h3>${esc(S.me.name)}</h3>
                <p>Signed in as <b>${esc(S.me.username)}</b> (${S.me.role === 'ADMIN' ? 'admin' : 'coach'}). Everything you change is logged under your name.</p>
                <div class="stack">
                    <label class="field"><span>Current password</span><input type="password" name="current" autocomplete="current-password" required></label>
                    <label class="field"><span>New password</span><input type="password" name="next" minlength="8" autocomplete="new-password" required><small>At least 8 characters.</small></label>
                    <label class="field"><span>New password again</span><input type="password" name="again" minlength="8" autocomplete="new-password" required></label>
                </div>
                <div class="acts">
                    <button type="button" class="btn danger" data-action="logout" style="margin-right:auto">Log out</button>
                    <button type="button" class="btn quiet" data-action="close-modal">Close</button>
                    <button class="btn dark" type="submit">Change password</button>
                </div>
            </form>`);
    }

    async function logout() {
        await api('POST', '/api/auth/logout').catch(() => null);
        S.me = null;
        S.staff = null;
        S.activity = null;
        closeModal();
        closeDrawer();
        if (S.view === 'staff') location.hash = '#/day';
        render();
        toast('Logged out. You are in the User view.');
    }

    function makePassword() {
        const chars = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        const rnd = new Uint32Array(12);
        window.crypto.getRandomValues(rnd);
        return Array.from(rnd, (n) => chars[n % chars.length]).join('');
    }

    // ------------------------------------------------------------ Staff page
    async function loadStaff() {
        const list = await attempt(() => api('GET', '/api/staff'));
        if (list) { S.staff = list; if (S.view === 'staff') renderView(); }
    }
    async function loadActivity() {
        const q = S.activityFor === 'all' ? '' : `?staffId=${encodeURIComponent(S.activityFor)}`;
        const list = await attempt(() => api('GET', `/api/activity${q}`));
        if (list) { S.activity = list; if (S.view === 'staff') renderView(); }
    }
    // Keep the Staff page fresh while it is open.
    setInterval(() => {
        if (S.view === 'staff' && isAdminAccount() && !isTyping(document.activeElement) && $('#drawer').hidden) { loadStaff(); loadActivity(); }
    }, 20000);

    function fmtWhen(s) {
        if (!s) return 'Never';
        const d = toDate(s);
        return `${fmtRel(d)}, ${fmtTime(d)}`;
    }

    function viewStaff() {
        if (!isAdminAccount()) return '<div class="empty"><div><strong>Admins only.</strong>Log in with an admin account to manage staff.</div></div>';
        const staff = S.staff;
        const act = S.activity;
        const who = S.activityFor === 'all' ? null : byId(staff || [], S.activityFor);
        const accounts = staff === null ? '<p class="muted">Loading accounts…</p>' : `
            <div class="table-wrap"><table class="grid staff-table">
                <thead><tr><th>Name</th><th>Username</th><th>Role</th><th>Status</th><th>Last login</th><th class="num">Actions</th><th></th></tr></thead>
                <tbody>${staff.map((a) => `
                    <tr class="${a.active ? '' : 'off'}">
                        <td><b>${esc(a.name)}</b>${S.me.id === a.id ? ' <span class="muted">(you)</span>' : ''}</td>
                        <td>${esc(a.username)}</td>
                        <td><span class="tag ${a.role === 'ADMIN' ? 'live' : ''}">${a.role === 'ADMIN' ? 'Admin' : 'Coach'}</span></td>
                        <td>${a.active ? 'Active' : '<span class="bad-text">Turned off</span>'}</td>
                        <td>${esc(fmtWhen(a.lastLoginAt))}</td>
                        <td class="num">${a.actions}</td>
                        <td class="row-acts">
                            <button class="btn sm ${S.activityFor === String(a.id) ? 'dark' : ''}" data-action="staff-filter" data-v="${a.id}">Activity</button>
                            <button class="btn sm quiet" data-action="edit-staff" data-id="${a.id}">Edit</button>
                            <button class="btn sm quiet" data-action="reset-staff" data-id="${a.id}">Reset password</button>
                        </td>
                    </tr>`).join('')}</tbody>
            </table></div>`;
        const feed = act === null ? '<p class="muted">Loading activity…</p>' : act.length ? `
            <ul class="list activity">${act.map((e) => `
                <li>
                    <div class="time">${esc(fmtTime(e.createdAt))}<small>${esc(fmtRel(toDate(e.createdAt)))}</small></div>
                    <div class="what"><b>${esc(e.staffName || 'Unknown')}</b> ${esc(e.action)}</div>
                    <div class="acts">${e.matchId && byId(S.matches, e.matchId) ? `<button class="btn sm quiet" data-action="goto" data-id="${e.matchId}">Open match</button>` : ''}</div>
                </li>`).join('')}</ul>`
            : `<div class="empty"><div><strong>Nothing yet.</strong>${who ? `${esc(who.name)} hasn't done anything yet.` : 'Actions show up here as soon as someone logs in and makes a change.'}</div></div>`;
        return `
            <div class="head">
                <div><h1>Staff</h1><p>Coach and admin logins, and a log of everything each person has done.</p></div>
                <div class="head-actions">
                    <button class="btn" data-action="refresh-staff">Refresh</button>
                    <button class="btn dark" data-action="new-staff">Add coach</button>
                </div>
            </div>
            ${accounts}
            <div class="sub-row">
                <h2 class="sub">Activity: ${who ? esc(who.name) : 'everyone'}</h2>
                ${who ? '<button class="btn sm quiet" data-action="staff-filter" data-v="all">Show everyone</button>' : ''}
            </div>
            ${feed}`;
    }

    function staffForm(a) {
        if (!isAdminAccount()) return;
        const editing = !!a;
        const fields = `
            <label class="field"><span>Name</span><input type="text" name="name" value="${esc(a ? a.name : '')}" placeholder="Coach Rivera" required><small>Shown in the activity log and on match timelines.</small></label>
            ${editing ? `<div class="field"><span>Username</span><div><b>${esc(a.username)}</b></div></div>` : `
            <label class="field"><span>Username</span><input type="text" name="username" placeholder="c.rivera" autocapitalize="none" autocomplete="off" spellcheck="false" required><small>3 to 30 characters: letters, numbers, dot, dash or underscore.</small></label>
            <div class="field"><span>Password</span>
                <div class="inline"><input id="newStaffPassword" type="text" name="password" value="${makePassword()}" autocomplete="off" required><button type="button" class="btn" data-action="gen-password" data-for="newStaffPassword">New</button></div>
                <small>Give this to them. They can change it after they log in.</small>
            </div>`}
            <label class="field"><span>Role</span>
                <select name="role">
                    <option value="COACH" ${!a || a.role === 'COACH' ? 'selected' : ''}>Coach: every tool except Staff</option>
                    <option value="ADMIN" ${a && a.role === 'ADMIN' ? 'selected' : ''}>Admin: every tool, including Staff</option>
                </select>
            </label>
            ${editing ? `<label class="check"><input type="checkbox" name="active" ${a.active ? 'checked' : ''}> Account is on. Turn it off to block logins and keep the history.</label>` : ''}`;
        openDrawer(editing ? `Edit ${a.name}` : 'Add coach', fields, {
            submit: editing ? 'Save changes' : 'Create account',
            onSubmit: async (fd) => {
                const body = editing
                    ? { name: fd.get('name'), role: fd.get('role'), active: fd.get('active') === 'on' }
                    : { name: fd.get('name'), username: String(fd.get('username') || '').trim().toLowerCase(), password: fd.get('password'), role: fd.get('role') };
                const saved = await attempt(() => (editing ? api('PUT', `/api/staff/${a.id}`, body) : api('POST', '/api/staff', body)));
                if (!saved) return false;
                toast(editing ? 'Account saved.' : `Account created. Username ${saved.username}, password ${body.password}`);
                loadStaff();
                loadActivity();
                return true;
            },
        });
    }

    function resetPasswordDialog(a) {
        if (!a) return;
        openModal(`
            <form class="modal" data-form="reset-password" data-id="${a.id}">
                <h3>Reset password for ${esc(a.name)}</h3>
                <p>Their old password stops working straight away. Give them the new one.</p>
                <div class="field"><span>New password</span>
                    <div class="inline"><input id="resetPw" type="text" name="password" value="${makePassword()}" minlength="8" autocomplete="off" required><button type="button" class="btn" data-action="gen-password" data-for="resetPw">New</button></div>
                </div>
                <div class="acts"><button type="button" class="btn quiet" data-action="close-modal">Cancel</button><button class="btn dark" type="submit">Reset password</button></div>
            </form>`);
    }

    // ----------------------------------------------------------- dialogs
    function openModal(html) {
        const m = $('#modal');
        m.innerHTML = html;
        m.hidden = false;
    }
    function closeModal() {
        const m = $('#modal');
        m.hidden = true;
        m.innerHTML = '';
        if (m._resolve) { m._resolve(false); m._resolve = null; }
    }
    function confirmBox({ title, body, ok = 'Confirm', danger = false }) {
        return new Promise((resolve) => {
            openModal(`
                <div class="modal" role="alertdialog" aria-label="${esc(title)}">
                    <h3>${esc(title)}</h3><p>${esc(body)}</p>
                    <div class="acts">
                        <button class="btn quiet" data-action="close-modal">Cancel</button>
                        <button class="btn ${danger ? 'danger' : 'dark'}" id="confirmOk">${esc(ok)}</button>
                    </div>
                </div>`);
            const m = $('#modal');
            m._resolve = resolve;
            const okBtn = $('#confirmOk');
            okBtn.focus();
            okBtn.addEventListener('click', () => { m._resolve = null; closeModal(); resolve(true); });
        });
    }
    function shortcutsDialog() {
        openModal(`
            <div class="modal wide">
                <h3>Keyboard shortcuts</h3>
                <dl class="keys">
                    <dt><kbd>1</kbd> to <kbd>5</kbd></dt><dd>Switch page (<kbd>6</kbd> is Staff, for admins)</dd>
                    <dt><kbd>←</kbd> <kbd>→</kbd></dt><dd>Day line: previous / next day (with no match open)</dd>
                    <dt><kbd>T</kbd></dt><dd>Day line: back to today</dd>
                    <dt><kbd>N</kbd></dt><dd>New match (when logged in)</dd>
                    <dt><kbd>Q</kbd> <kbd>W</kbd> <kbd>E</kbd></dt><dd>Home +1 / +2 / +3</dd>
                    <dt><kbd>I</kbd> <kbd>O</kbd> <kbd>P</kbd></dt><dd>Away +1 / +2 / +3</dd>
                    <dt><kbd>A</kbd> / <kbd>L</kbd></dt><dd>Take a point from home / away</dd>
                    <dt><kbd>Z</kbd></dt><dd>Undo last score change</dd>
                    <dt><kbd>[</kbd> <kbd>]</kbd></dt><dd>Previous / next period</dd>
                    <dt><kbd>S</kbd></dt><dd>Start the open match</dd>
                    <dt><kbd>Esc</kbd></dt><dd>Close whatever is open</dd>
                </dl>
                <div class="acts"><button class="btn dark" data-action="close-modal">Done</button></div>
            </div>`);
    }

    const toastSlots = {};
    function toast(message, { error = false, action = null, key = null } = {}) {
        const box = $('#toasts');
        if (key && toastSlots[key]) { toastSlots[key].remove(); clearTimeout(toastSlots[key]._t); }
        const el = document.createElement('div');
        el.className = `toast ${error ? 'error' : ''}`;
        el.setAttribute('role', error ? 'alert' : 'status');
        el.innerHTML = `<span>${esc(message)}</span>`;
        if (action) {
            const b = document.createElement('button');
            b.textContent = action.label;
            b.addEventListener('click', () => { el.remove(); action.fn(); });
            el.appendChild(b);
        }
        box.appendChild(el);
        if (key) toastSlots[key] = el;
        el._t = setTimeout(() => { el.remove(); if (key && toastSlots[key] === el) delete toastSlots[key]; }, error ? 6000 : 4000);
    }

    // ------------------------------------------------------------ data
    async function bootstrap() {
        const data = await attempt(() => api('GET', '/api/bootstrap'));
        if (!data) {
            $('#view').innerHTML = '<div class="empty"><div><strong>Can\'t reach the server.</strong>Check that the Spring Boot app is running, then reload this page.</div></div>';
            return false;
        }
        Object.assign(S, { sports: data.sports, teams: data.teams, matches: data.matches, events: data.events, records: data.records, divisions: data.divisions });
        const who = await api('GET', '/api/auth/me').catch(() => null);
        S.me = who && who.signedIn ? who.me : null;
        await loadPicks();
        if (!S.standings.sportId && S.sports[0]) S.standings.sportId = S.sports[0].id;
        return true;
    }
    async function loadPicks() {
        const list = await attempt(() => api('GET', `/api/matches/picks?voter=${encodeURIComponent(voter)}`));
        if (list) S.picks = new Map(list.map((p) => [p.matchId, { homeVotes: p.homeVotes, awayVotes: p.awayVotes, mine: p.mine }]));
    }

    async function refreshCatalog() {
        const [teams, sports, matches] = await Promise.all([api('GET', '/api/teams'), api('GET', '/api/sports'), api('GET', '/api/matches')]);
        S.teams = teams; S.sports = sports; S.matches = matches;
        queueRender();
    }
    async function refreshRecords() {
        S.records = await api('GET', '/api/records');
        queueRender();
    }

    function connectLive() {
        const es = new EventSource('/api/stream');
        es.addEventListener('open', async () => {
            const wasLost = S.everConnected && !S.connected;
            S.connected = true;
            S.everConnected = true;
            if (wasLost) { await bootstrap(); toast('Reconnected. Everything is up to date.'); }
            queueRender();
        });
        es.addEventListener('error', () => { S.connected = false; queueRender(); });
        const on = (name, fn) => es.addEventListener(name, (e) => { try { fn(JSON.parse(e.data)); } catch (err) { console.error(name, err); } });
        on('match', (m) => {
            upsertMatch(m);
            if (S.view === 'standings' && m.status === 'DONE') loadStandings(300);
            queueRender();
        });
        on('match-deleted', ({ id }) => {
            S.matches = S.matches.filter((m) => m.id !== id);
            if (S.sel === id) S.sel = null;
            queueRender();
        });
        on('event', (ev) => { upsertEvent(ev); queueRender(); });
        on('poll', ({ matchId, poll }) => {
            const old = S.picks.get(matchId);
            S.picks.set(matchId, { homeVotes: poll.homeVotes, awayVotes: poll.awayVotes, mine: old ? old.mine : null });
            if (S.sel === matchId) { S.dockPoll = poll; renderDock(); }
        });
        on('teams', () => refreshCatalog().catch(() => {}));
        on('sports', () => refreshCatalog().catch(() => {}));
        on('records', () => refreshRecords().catch(() => {}));
    }

    function route() {
        const v = (location.hash.replace(/^#\/?/, '') || 'day').split('?')[0];
        S.view = VIEWS.includes(v) ? v : 'day';
        if (S.view === 'staff' && !isAdminAccount()) { S.view = 'day'; history.replaceState(null, '', '#/day'); }
        if (S.view === 'standings') { S.standings.rows = null; loadStandings(); }
        if (S.view === 'staff') { loadStaff(); loadActivity(); }
        render();
        window.scrollTo(0, 0);
    }

    // ------------------------------------------------------ click handling
    const actions = {
        'login': () => loginDialog(),
        'account': () => accountDialog(),
        'logout': () => logout(),
        'gen-password': (el) => { const input = document.getElementById(el.dataset.for); if (input) { input.value = makePassword(); input.select(); } },
        'staff-filter': (el) => { S.activityFor = el.dataset.v; S.activity = null; renderView(); loadActivity(); },
        'new-staff': () => staffForm(),
        'edit-staff': (el) => staffForm(byId(S.staff || [], el.dataset.id)),
        'reset-staff': (el) => resetPasswordDialog(byId(S.staff || [], el.dataset.id)),
        'refresh-staff': () => { loadStaff(); loadActivity(); },
        'pick': (el) => pick(Number(el.dataset.id), el.dataset.side),
        'day': (el) => {
            const n = Number(el.dataset.v);
            S.day = n === 0 ? startOfDay(new Date()) : addDays(S.day, n);
            S.sel = null;
            render();
        },
        'day-sport': (el) => { S.dayFilters.sport = el.dataset.v; render(); },
        'select': (el) => {
            const id = Number(el.dataset.id);
            if (S.sel === id) deselect(); else selectMatch(id);
        },
        'deselect': () => deselect(),
        'goto': (el) => goto(Number(el.dataset.id)),
        'score': (el) => doScore(Number(el.dataset.id), el.dataset.side, Number(el.dataset.delta)),
        'undo': (el) => doUndo(Number(el.dataset.id)),
        'start': (el) => matchAction(Number(el.dataset.id), 'start', undefined, (m) => `${vs(m)} is live.`),
        'finish': (el) => finishMatch(Number(el.dataset.id)),
        'reopen': (el) => { if (requireStaff()) matchAction(Number(el.dataset.id), 'reopen', undefined, 'Match reopened. It no longer counts in the standings.'); },
        'period': (el) => matchAction(Number(el.dataset.id), 'period', { direction: Number(el.dataset.dir) }),
        'set-score': (el) => setScoreDialog(Number(el.dataset.id)),
        'reset-poll': async (el) => {
            if (!requireStaff()) return;
            const ok = await confirmBox({ title: 'Clear all fan picks?', body: 'Every Viper Token pick for this match will be removed.', ok: 'Clear picks', danger: true });
            if (!ok) return;
            const poll = await attempt(() => api('DELETE', `/api/matches/${el.dataset.id}/poll`), 'Fan picks cleared.');
            if (poll) { S.dockPoll = poll; renderDock(); }
        },
        'new-match': () => matchForm(),
        'edit-match': (el) => matchForm(byId(S.matches, el.dataset.id)),
        'delete-match': async (el) => {
            if (!requireStaff()) return;
            const m = byId(S.matches, el.dataset.id);
            if (!m) return;
            const ok = await confirmBox({ title: `Delete ${m.code}?`, body: `${vs(m)} and its timeline will be removed. This can't be undone.`, ok: 'Delete match', danger: true });
            if (!ok) return;
            if (await attempt(() => api('DELETE', `/api/matches/${m.id}`), `${m.code} deleted.`)) {
                S.matches = S.matches.filter((x) => x.id !== m.id);
                if (S.sel === m.id) S.sel = null;
                closeDrawer();
                queueRender();
            }
        },
        'swap-teams': () => { const h = $('#homePick'), a = $('#awayPick'); const v = h.value; h.value = a.value; a.value = v; },
        'filter-sport': (el) => { S.filters.sport = el.dataset.v; render(); },
        'filter-status': (el) => { S.filters.status = el.dataset.v; render(); },
        'clear-filters': () => { S.filters = { sport: 'all', status: 'all', division: 'all', q: '' }; render(); },
        'st-sport': (el) => { S.standings.sportId = Number(el.dataset.v); S.standings.rows = null; render(); loadStandings(); },
        'st-division': (el) => { S.standings.division = el.dataset.v; S.standings.rows = null; render(); loadStandings(); },
        'new-team': () => teamForm(),
        'edit-team': (el) => teamForm(byId(S.teams, el.dataset.id)),
        'delete-team': async (el) => {
            const t = byId(S.teams, el.dataset.id);
            const ok = await confirmBox({ title: `Delete ${t.shortName}?`, body: 'Teams that still have matches can\'t be deleted.', ok: 'Delete team', danger: true });
            if (ok && await attempt(() => api('DELETE', `/api/teams/${t.id}`), `${t.shortName} deleted.`)) { closeDrawer(); refreshCatalog(); }
        },
        'swatch': (el) => { const c = $('#teamColor'); if (c) c.value = el.dataset.c; },
        'new-sport': () => sportForm(),
        'edit-sport': (el) => sportForm(byId(S.sports, el.dataset.id)),
        'delete-sport': async (el) => {
            const s = byId(S.sports, el.dataset.id);
            const ok = await confirmBox({ title: `Delete ${s.name}?`, body: 'Sports that still have matches or records can\'t be deleted.', ok: 'Delete sport', danger: true });
            if (ok && await attempt(() => api('DELETE', `/api/sports/${s.id}`), `${s.name} deleted.`)) { closeDrawer(); refreshCatalog(); }
        },
        'new-record': () => recordForm(),
        'edit-record': (el) => recordForm(byId(S.records, el.dataset.id)),
        'delete-record': async (el) => {
            const ok = await confirmBox({ title: 'Delete this record?', body: 'It will be removed from the records list.', ok: 'Delete record', danger: true });
            if (ok && await attempt(() => api('DELETE', `/api/records/${el.dataset.id}`), 'Record deleted.')) { closeDrawer(); refreshRecords(); }
        },
        'close-drawer': () => closeDrawer(),
        'close-modal': () => closeModal(),
    };

    document.addEventListener('click', (e) => {
        const el = e.target.closest('[data-action]');
        if (!el || el.disabled) return;
        const fn = actions[el.dataset.action];
        if (fn) { e.preventDefault(); fn(el, e); }
    });
    $('#drawerBackdrop').addEventListener('click', closeDrawer);
    $('#modal').addEventListener('click', (e) => { if (e.target.id === 'modal') closeModal(); });

    document.addEventListener('submit', async (e) => {
        const form = e.target.closest('[data-form]');
        if (!form) return;
        e.preventDefault();
        const kind = form.dataset.form;
        const fd = new FormData(form);
        if (kind === 'drawer' && S.drawerSubmit) {
            const btn = $('button[type=submit]', form);
            btn.disabled = true;
            const ok = await S.drawerSubmit(fd);
            btn.disabled = false;
            if (ok) closeDrawer();
        } else if (kind === 'set-score') {
            const m = await matchAction(Number(form.dataset.id), 'set-score', { home: Number(fd.get('home')), away: Number(fd.get('away')) }, (x) => `Score set to ${x.homeScore}–${x.awayScore}.`);
            if (m) closeModal();
        } else if (kind === 'login') {
            const btn = $('button[type=submit]', form);
            const err = $('#loginError');
            btn.disabled = true;
            const me = await api('POST', '/api/auth/login', { username: String(fd.get('username') || '').trim(), password: fd.get('password') })
                .catch((x) => { if (err) err.textContent = x.message; return null; });
            btn.disabled = false;
            if (me) {
                S.me = me;
                closeModal();
                render();
                toast(`Signed in as ${me.name}. Every tool is on${me.role === 'ADMIN' ? ', and Staff is in the menu' : ''}.`);
            }
        } else if (kind === 'password') {
            if (fd.get('next') !== fd.get('again')) { toast('The two new passwords don\'t match.', { error: true }); return; }
            const ok = await attempt(async () => { await api('POST', '/api/auth/password', { current: fd.get('current'), next: fd.get('next') }); return true; }, 'Password changed.');
            if (ok) closeModal();
        } else if (kind === 'reset-password') {
            const ok = await attempt(async () => { await api('POST', `/api/staff/${form.dataset.id}/password`, { password: fd.get('password') }); return true; }, 'Password reset. Give the new password to them.');
            if (ok) { closeModal(); loadStaff(); loadActivity(); }
        } else if (kind === 'note') {
            const message = String(fd.get('message') || '').trim();
            if (!message || S.sel == null) return;
            const ev = await attempt(() => api('POST', `/api/matches/${S.sel}/note`, { message }));
            if (ev) { upsertEvent(ev); renderDock(); const box = $('#noteInput'); if (box) box.value = ''; }
        }
    });

    document.addEventListener('input', (e) => {
        if (e.target.dataset && e.target.dataset.input === 'search') { S.filters.q = e.target.value; renderView(); }
    });
    document.addEventListener('change', (e) => {
        const kind = e.target.dataset && e.target.dataset.change;
        if (kind === 'day-division') { S.dayFilters.division = e.target.value; render(); }
        if (kind === 'division') { S.filters.division = e.target.value; render(); }
    });

    // ---------------------------------------------------------- keyboard
    function isTyping(el) {
        return el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable);
    }
    document.addEventListener('keydown', (e) => {
        const modalOpen = !$('#modal').hidden;
        const drawerOpen = !$('#drawer').hidden;
        if (e.key === 'Escape') {
            if (modalOpen) closeModal();
            else if (drawerOpen) closeDrawer();
            else if (S.sel != null) deselect();
            return;
        }
        if (modalOpen || drawerOpen || isTyping(document.activeElement) || e.ctrlKey || e.metaKey || e.altKey) return;
        const k = e.key.toLowerCase();

        if (S.view === 'day' && S.sel != null && canEdit()) {
            const m = byId(S.matches, S.sel);
            if (m && m.status === 'LIVE') {
                const incs = increments(m.sport);
                const hi = ['q', 'w', 'e'].indexOf(k), ai = ['i', 'o', 'p'].indexOf(k);
                if (hi >= 0 && incs[hi]) { e.preventDefault(); doScore(m.id, 'HOME', incs[hi]); return; }
                if (ai >= 0 && incs[ai]) { e.preventDefault(); doScore(m.id, 'AWAY', incs[ai]); return; }
                if (k === 'a' && m.homeScore > 0) { doScore(m.id, 'HOME', -1); return; }
                if (k === 'l' && m.awayScore > 0) { doScore(m.id, 'AWAY', -1); return; }
                if (k === 'z') { doUndo(m.id); return; }
                if (k === ']') { matchAction(m.id, 'period', { direction: 1 }); return; }
                if (k === '[') { matchAction(m.id, 'period', { direction: -1 }); return; }
            }
            if (m && m.status === 'UPCOMING' && k === 's') { matchAction(m.id, 'start', undefined, (x) => `${vs(x)} is live.`); return; }
        }
        if (S.view === 'day' && S.sel == null) {
            if (e.key === 'ArrowLeft') { S.day = addDays(S.day, -1); render(); return; }
            if (e.key === 'ArrowRight') { S.day = addDays(S.day, 1); render(); return; }
            if (k === 't') { S.day = startOfDay(new Date()); render(); return; }
        }
        if (k === 'n' && canEdit()) { e.preventDefault(); matchForm(); return; }
        if (k === '?') { shortcutsDialog(); return; }
        const n = Number(k);
        const max = isAdminAccount() ? VIEWS.length : VIEWS.length - 1;
        if (n >= 1 && n <= max) location.hash = `#/${VIEWS[n - 1]}`;
    });

    // ------------------------------------------------------------- start
    window.addEventListener('hashchange', route);
    (async () => {
        renderTop();
        if (await bootstrap()) {
            route();
            connectLive();
        }
    })();
})();
