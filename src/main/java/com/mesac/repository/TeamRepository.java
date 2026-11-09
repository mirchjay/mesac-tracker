package com.mesac.repository;

import com.mesac.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findAllByOrderByNameAsc();

    boolean existsByShortNameIgnoreCase(String shortName);
}
