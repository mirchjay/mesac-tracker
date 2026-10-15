-- Users Table
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL DEFAULT 'SPECTATOR',
    viper_tokens INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sports Table
CREATE TABLE IF NOT EXISTS sports (
    sport_id VARCHAR(36) PRIMARY KEY,
    sport_name VARCHAR(50) NOT NULL,
    category VARCHAR(20) NOT NULL -- 'TIMED_EVENT' or 'TEAM_MATCH'
);

-- Matches Table
CREATE TABLE IF NOT EXISTS matches (
    match_id VARCHAR(36) PRIMARY KEY,
    sport_id VARCHAR(36) REFERENCES sports(sport_id),
    team_a_name VARCHAR(50) NOT NULL,
    team_b_name VARCHAR(50) NOT NULL,
    team_a_score INT DEFAULT 0,
    team_b_score INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING', -- 'UPCOMING', 'LIVE', 'COMPLETED'
    livestream_url VARCHAR(255),
    start_time TIMESTAMP NOT NULL
);

-- Match Events (Penalties, Substitutions, Scores)
CREATE TABLE IF NOT EXISTS match_events (
    event_id VARCHAR(36) PRIMARY KEY,
    match_id VARCHAR(36) REFERENCES matches(match_id),
    event_type VARCHAR(30) NOT NULL, -- 'SCORE', 'PENALTY', 'SUBSTITUTION'
    player_name VARCHAR(100),
    details TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Predictions & Viper Token Votes
CREATE TABLE IF NOT EXISTS prediction_votes (
    vote_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) REFERENCES users(user_id),
    match_id VARCHAR(36) REFERENCES matches(match_id),
    vote_type VARCHAR(30) NOT NULL, -- 'MATCH_WINNER', 'CROWD_MVP'
    selection VARCHAR(100) NOT NULL,
    tokens_staked INT NOT NULL
);

-- Historic Records Table
CREATE TABLE IF NOT EXISTS records (
    record_id VARCHAR(36) PRIMARY KEY,
    sport_name VARCHAR(50) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    holder_name VARCHAR(100) NOT NULL,
    record_value VARCHAR(50) NOT NULL,
    year_set INT NOT NULL
);

-- Seed Initial Demo Data
INSERT INTO sports (sport_id, sport_name, category) VALUES 
('s1', 'Varsity Basketball', 'TEAM_MATCH'),
('s2', 'Swimming 100m Freestyle', 'TIMED_EVENT');

INSERT INTO matches (match_id, sport_id, team_a_name, team_b_name, team_a_score, team_b_score, status, livestream_url, start_time) VALUES 
('m1', 's1', 'Dubai Falcons', 'Abu Dhabi Vipers', 42, 38, 'LIVE', 'https://www.youtube.com/embed/live_stream?channel=MESAC', CURRENT_TIMESTAMP);

INSERT INTO records (record_id, sport_name, event_name, holder_name, record_value, year_set) VALUES 
('r1', 'Swimming', '100m Freestyle', 'Alex Rivera (ASD)', '51.82s', 2025),
('r2', 'Varsity Basketball', 'Season Champions', 'Dubai American Academy', 'Undefeated (12-0)', 2025);