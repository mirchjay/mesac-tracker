package com.mesac;

import com.mesac.model.Match;
import com.mesac.repository.MatchRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
            matchRepository.save(new Match(
                "m-" + UUID.randomUUID().toString().substring(0, 5),
                "Volleyball",
                "Vipers",
                "Falcons",
                25,
                21,
                "DONE"
            ));

            matchRepository.save(new Match(
                "m-" + UUID.randomUUID().toString().substring(0, 5),
                "Basketball",
                "Eagles",
                "Tigers",
                48,
                52,
                "LIVE"
            ));

            matchRepository.save(new Match(
                "m-" + UUID.randomUUID().toString().substring(0, 5),
                "Soccer",
                "Panthers",
                "Lions",
                0,
                0,
                "UPCOMING"
            ));
        }
    }
}