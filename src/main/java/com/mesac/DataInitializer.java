package com.mesac;

import com.mesac.model.Match;
import com.mesac.repository.MatchRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final MatchRepository matchRepository;

    public DataInitializer(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Only seed data if the database table is currently empty
        if (matchRepository.count() == 0) {
            matchRepository.save(new Match(null, "ASD (American School of Dubai)", "DAA (Dubai American Academy)", 58, 52, "LIVE"));
            matchRepository.save(new Match(null, "ACS (Abu Dhabi)", "ABA (Oman)", 3, 1, "DONE"));
            matchRepository.save(new Match(null, "TAISM (Muscat)", "AES (New Delhi)", 0, 0, "UPCOMING"));
            System.out.println(">>> Sample MESAC matches seeded automatically into the database!");
        }
    }
}