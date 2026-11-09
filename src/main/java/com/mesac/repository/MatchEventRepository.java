package com.mesac.repository;

import com.mesac.model.EventType;
import com.mesac.model.MatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchEventRepository extends JpaRepository<MatchEvent, Long> {

    /** Timeline for one match, newest first. */
    List<MatchEvent> findByMatch_IdOrderByIdDesc(Long matchId);

    /** Activity feed across all matches, newest first. */
    List<MatchEvent> findTop40ByOrderByIdDesc();

    /** The latest score change that hasn't been undone yet. */
    Optional<MatchEvent> findFirstByMatch_IdAndUndoneFalseAndTypeInOrderByIdDesc(Long matchId, Collection<EventType> types);

    /** Score changes with a tagged scorer that still count (for top scorers). */
    List<MatchEvent> findByTypeAndUndoneFalseAndPlayerIdIsNotNull(EventType type);

    void deleteByMatch_Id(Long matchId);
}
