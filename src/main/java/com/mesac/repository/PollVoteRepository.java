package com.mesac.repository;

import com.mesac.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    List<PollVote> findByMatch_Id(Long matchId);

    /** This fan's pick for one match (each browser gets its own voter id). */
    Optional<PollVote> findByMatch_IdAndVoter(Long matchId, String voter);

    void deleteByMatch_Id(Long matchId);

    /** True once the sample "demo-" picks have been added. */
    boolean existsByVoterStartingWith(String prefix);
}
