const sqlite3 = require('sqlite3').verbose();
const fs = require('fs');
const path = require('path');

// Change ':memory:' to 'mesac_tracker.db' if you want disk persistence
const db = new sqlite3.Database(path.join(__dirname, 'mesac_tracker.db'));

const schemaSql = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');

db.serialize(() => {
    // 1. Run Schema Definition
    db.exec(schemaSql, (err) => {
        if (err) {
            console.error("Error creating tables:", err.message);
            return;
        }
        console.log("Database tables initialized.");
    });

    // 2. Insert Seed Data
    const seedSports = `
        INSERT OR IGNORE INTO sports (sport_id, name, category) VALUES 
        ('sp-1', 'Basketball', 'Varsity Boys'),
        ('sp-2', 'Soccer', 'Varsity Girls');
    `;

    const seedMatches = `
        INSERT OR IGNORE INTO matches (match_id, sport_id, home_team, away_team, team_a_score, team_b_score, status) VALUES 
        ('m-101', 'sp-1', 'Vipers', 'Falcons', 42, 38, 'LIVE'),
        ('m-102', 'sp-2', 'Dragons', 'Vipers', 0, 0, 'UPCOMING');
    `;

    const seedRecords = `
        INSERT OR IGNORE INTO records (record_id, record_holder, record_value, year_set) VALUES 
        ('r-1', 'Alex Johnson', '100m Dash - 10.4s', 2024),
        ('r-2', 'Sam Smith', 'Most Points in Single Game - 45 pts', 2023);
    `;

    db.exec(seedSports + seedMatches + seedRecords, (err) => {
        if (err) console.error("Error seeding data:", err.message);
        else console.log("Seed data inserted successfully.");
    });
});

db.close();