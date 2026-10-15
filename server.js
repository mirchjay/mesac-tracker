const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');
const sqlite3 = require('sqlite3').verbose();
const path = require('path');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: "*" } });

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Local In-Memory SQLite Setup (Simulating H2 behavior)
const db = new sqlite3.Database(':memory:');

// Initialize Local Schema
const fs = require('fs');
const schemaSql = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');

db.serialize(() => {
    db.exec(schemaSql, (err) => {
        if (err) console.error("Error initializing database schema:", err);
        else console.log("Database initialized successfully.");
    });
});

// Real-Time Socket Connection
io.on('connection', (socket) => {
    console.log(`Client connected: ${socket.id}`);

    // Join match update room
    socket.on('join_match', (matchId) => {
        socket.join(`match_${matchId}`);
        console.log(`Socket ${socket.id} joined room: match_${matchId}`);
    });

    socket.on('disconnect', () => {
        console.log(`Client disconnected: ${socket.id}`);
    });
});

// REST API ENDPOINTS

// 1. Get Active / Live Matches
app.get('/api/matches/live', (req, res) => {
    db.all(`SELECT * FROM matches WHERE status = 'LIVE'`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ matches: rows });
    });
});

// 2. Coach Portal: Update Live Score / Add Event
app.post('/api/matches/:id/score', (req, res) => {
    const matchId = req.params.id;
    const { team_a_score, team_b_score, event_type, player_name, details } = req.body;

    db.run(
        `UPDATE matches SET team_a_score = ?, team_b_score = ? WHERE match_id = ?`,
        [team_a_score, team_b_score, matchId],
        function (err) {
            if (err) return res.status(500).json({ error: err.message });

            // Log event (penalty, score, sub)
            const eventId = uuidv4();
            db.run(
                `INSERT INTO match_events (event_id, match_id, event_type, player_name, details) VALUES (?, ?, ?, ?, ?)`,
                [eventId, matchId, event_type || 'SCORE', player_name || 'N/A', details || 'Score updated'],
                (eventErr) => {
                    if (eventErr) console.error("Event log error:", eventErr);

                    const updateData = { matchId, team_a_score, team_b_score, player_name, event_type, details };
                    
                    // Broadcast real-time update to all spectators
                    io.to(`match_${matchId}`).emit('score_updated', updateData);
                    io.emit('global_score_broadcast', updateData);

                    res.json({ message: "Score updated successfully", data: updateData });
                }
            );
        }
    );
});

// 3. Viper Token Prediction / Poll Vote
app.post('/api/predictions/vote', (req, res) => {
    const { userId, matchId, voteType, selection, tokens } = req.body;
    
    if (!selection || !tokens || tokens <= 0) {
        return res.status(400).json({ error: "Invalid vote parameters" });
    }

    const voteId = uuidv4();
    db.run(
        `INSERT INTO prediction_votes (vote_id, user_id, match_id, vote_type, selection, tokens_staked) VALUES (?, ?, ?, ?, ?, ?)`,
        [voteId, userId || 'anon-user', matchId, voteType, selection, tokens],
        function (err) {
            if (err) return res.status(500).json({ error: err.message });

            // Recalculate totals for display
            db.all(
                `SELECT selection, SUM(tokens_staked) as total_tokens FROM prediction_votes WHERE match_id = ? GROUP BY selection`,
                [matchId],
                (pollErr, rows) => {
                    if (pollErr) return res.status(500).json({ error: pollErr.message });

                    io.to(`match_${matchId}`).emit('poll_updated', { matchId, pollResults: rows });
                    res.json({ message: "Vote cast successfully!", pollResults: rows });
                }
            );
        }
    );
});

// 4. Get Historic Records Page
app.get('/api/records', (req, res) => {
    db.all(`SELECT * FROM records ORDER BY year_set DESC`, [], (err, rows) => {
        if (err) return res.status(500).json({ error: err.message });
        res.json({ records: rows });
    });
});

// 5. Gate Scan Pass Verification
app.post('/api/gate/verify', (req, res) => {
    const { passCode } = req.body;
    if (passCode && passCode.startsWith("MESAC-2026")) {
        return res.json({ status: "VALID", user: "Spectator Guest", gateAccess: "GRANTED" });
    }
    res.status(400).json({ status: "INVALID", gateAccess: "DENIED" });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`MESAC Results Tracker running on http://localhost:${PORT}`);
});