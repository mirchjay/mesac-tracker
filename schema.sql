-- MESAC Tracker SQLite Schema (Updated to match server.js API endpoints)

CREATE TABLE IF NOT EXISTS users (
    user_id TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('ADMIN', 'COACH', 'STUDENT')),
    token_balance INTEGER DEFAULT 10
);

CREATE TABLE IF NOT EXISTS sports (
    sport_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL CHECK(category IN ('JV Boys', 'JV Girls', 'Varsity Boys', 'Varsity Girls'))
);

CREATE TABLE IF NOT EXISTS matches (
    match_id TEXT PRIMARY KEY,
    sport_id TEXT,
    home_team TEXT NOT NULL,
    away_team TEXT NOT NULL,
    team_a_score INTEGER DEFAULT 0,
    team_b_score INTEGER DEFAULT 0,
    status TEXT NOT NULL CHECK(status IN ('UPCOMING', 'LIVE', 'DONE')),
    FOREIGN KEY (sport_id) REFERENCES sports(sport_id)
);

CREATE TABLE IF NOT EXISTS match_events (
    event_id TEXT PRIMARY KEY,
    match_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    player_name TEXT DEFAULT 'N/A',
    details TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES matches(match_id)
);

CREATE TABLE IF NOT EXISTS prediction_votes (
    vote_id TEXT PRIMARY KEY,
    user_id TEXT,
    match_id TEXT NOT NULL,
    vote_type TEXT,
    selection TEXT NOT NULL,
    tokens_staked INTEGER DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (match_id) REFERENCES matches(match_id)
);

CREATE TABLE IF NOT EXISTS records (
    record_id TEXT PRIMARY KEY,
    record_holder TEXT NOT NULL,
    record_value TEXT,
    sport_id TEXT,
    year_set INTEGER NOT NULL,
    FOREIGN KEY (sport_id) REFERENCES sports(sport_id)
);