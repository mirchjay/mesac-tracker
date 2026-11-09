/* =====================================================================
   MESAC big screen - read-only scoreboard for a TV or projector.
   Open /display on the venue screen; it updates itself live.
   ===================================================================== */
(() => {
    'use strict';

    let matches = [];
    const flashing = new Set();
    const $ = (s) => document.querySelector(s);
    const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const toDate = (s) => (s ? new Date(s) : null);
    const fmtTime = (s) => (s ? toDate(s).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : '');
    /** School logo in a white tile (dark screen); colour square if there's no logo or it fails to load. */
    const logo = (t, size) => `<span class="dlogo ${t.logoUrl ? '' : 'nologo'}" style="--s:${size}px;--team:${esc(t.color)}" aria-hidden="true">${t.logoUrl ? `<img src="${esc(t.logoUrl)}" alt="" onerror="this.parentNode.classList.add('nologo');this.remove()">` : ''}</span>`;
    const bySchedule = (a, b) => (toDate(a.scheduledAt) || 0) - (toDate(b.scheduledAt) || 0);

    function period(m) {
        const s = m.sport, p = m.currentPeriod;
        return p > s.periodCount ? `OT${p - s.periodCount}` : `${s.periodName.charAt(0)}${p}`;
    }

    function board(m) {
        const row = (side) => {
            const t = side === 'HOME' ? m.homeTeam : m.awayTeam;
            const mine = side === 'HOME' ? m.homeScore : m.awayScore;
            const theirs = side === 'HOME' ? m.awayScore : m.homeScore;
            return `
                <div class="board-row ${mine < theirs ? 'trailing' : ''}" style="--team:${esc(t.color)}">
                    <span class="jersey"></span>
                    <div class="team-code">${logo(t, 44)}<span>${esc(t.shortName)}<span class="team-name">${esc(t.name)}</span></span></div>
                    <div class="led ${flashing.has(`${m.id}-${side}`) ? 'flash' : ''}">${mine}</div><div></div>
                </div>`;
        };
        return `
            <article class="board" style="--sport:${esc(m.sport.color)}">
                <div class="board-top">
                    <span class="sport">${esc(m.sport.icon)} ${esc(m.sport.name)}</span>
                    <span>${esc(m.divisionLabel)}</span>
                    <span class="live-pill">${esc(period(m))}</span>
                </div>
                ${row('HOME')}${row('AWAY')}
                <div class="board-foot"><span>${esc(m.venue || '')}</span><span>${esc(m.code)}</span></div>
            </article>`;
    }

    function render() {
        const live = matches.filter((m) => m.status === 'LIVE').sort(bySchedule);
        const next = matches.filter((m) => m.status === 'UPCOMING').sort(bySchedule).slice(0, 5);
        const recent = matches.filter((m) => m.status === 'DONE')
            .sort((a, b) => (toDate(b.endedAt) || 0) - (toDate(a.endedAt) || 0)).slice(0, 8);

        $('#displaySub').textContent = live.length
            ? `${live.length} ${live.length === 1 ? 'match' : 'matches'} in play`
            : (next[0] ? `Next up at ${fmtTime(next[0].scheduledAt)}` : 'No matches in play');

        $('#displayBoards').innerHTML = live.length
            ? `<div class="boards">${live.map(board).join('')}</div>`
            : '<div class="empty" style="font-size:1.4rem;padding:48px">Matches will appear here as soon as they start.</div>';

        $('#displayNext').innerHTML = next.length ? `
            <h2>Coming up</h2>
            <ul class="fixtures">${next.map((m) => `
                <li class="fixture">
                    <div class="when">${esc(fmtTime(m.scheduledAt))}</div>
                    <div class="ico">${esc(m.sport.icon)}</div>
                    <div class="who"><div class="vs">
                        ${logo(m.homeTeam, 26)}${esc(m.homeTeam.shortName)}
                        <span style="color:var(--mist);font-weight:400">vs</span>
                        ${logo(m.awayTeam, 26)}${esc(m.awayTeam.shortName)}
                    </div><div class="meta">${esc(m.sport.name)}, ${esc(m.divisionLabel)}${m.venue ? `, ${esc(m.venue)}` : ''}</div></div>
                    <div></div>
                </li>`).join('')}</ul>` : '';

        // Ribbon shows latest final scores.
        const items = recent.map((m) => `
            <span class="ribbon-item">${esc(m.sport.icon)}
                <span class="sw" style="background:${esc(m.homeTeam.color)}"></span>${esc(m.homeTeam.shortName)}
                <span class="rs">${m.homeScore}–${m.awayScore}</span>
                ${esc(m.awayTeam.shortName)}<span class="sw" style="background:${esc(m.awayTeam.color)}"></span>
            </span>`);
        let run = items.join('');
        while (items.length && run.length < 4000) run += items.join('');
        $('#ribbon').innerHTML = `<div class="ribbon-flag quiet">Final scores</div><div class="ribbon-track"><div class="ribbon-run">${run}${run}</div></div>`;
    }

    function upsert(m) {
        const i = matches.findIndex((x) => x.id === m.id);
        if (i >= 0) {
            ['HOME', 'AWAY'].forEach((side) => {
                const k = side === 'HOME' ? 'homeScore' : 'awayScore';
                if (matches[i][k] !== m[k]) {
                    flashing.add(`${m.id}-${side}`);
                    setTimeout(() => flashing.delete(`${m.id}-${side}`), 650);
                }
            });
            matches[i] = m;
        } else matches.push(m);
    }

    async function load() {
        try {
            const res = await fetch('/api/matches');
            matches = await res.json();
            render();
        } catch (_) {
            $('#displaySub').textContent = 'Waiting for the server…';
        }
    }

    function connect() {
        const es = new EventSource('/api/stream');
        es.addEventListener('open', load);
        es.addEventListener('match', (e) => { upsert(JSON.parse(e.data)); render(); });
        es.addEventListener('match-deleted', (e) => { const { id } = JSON.parse(e.data); matches = matches.filter((m) => m.id !== id); render(); });
        es.addEventListener('teams', load);
        es.addEventListener('sports', load);
    }

    function tick() {
        $('#displayClock').textContent = new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    }

    $('#fsBtn').addEventListener('click', () => {
        if (document.fullscreenElement) document.exitFullscreen();
        else document.documentElement.requestFullscreen().catch(() => {});
    });

    tick();
    setInterval(tick, 1000);
    load().then(connect);
})();
