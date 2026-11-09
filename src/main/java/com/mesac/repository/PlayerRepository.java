package com.mesac.repository;

import com.mesac.model.Division;
import com.mesac.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findAllByOrderByNameAsc();

    /** One roster: a school's players for one sport and division. */
    List<Player> findByTeamIdAndSportIdAndDivisionOrderByNumberAscNameAsc(Long teamId, Long sportId, Division division);

    boolean existsByTeamIdAndSportIdAndDivisionAndNumber(Long teamId, Long sportId, Division division, Integer number);

    void deleteByTeamId(Long teamId);

    void deleteBySportId(Long sportId);
}
