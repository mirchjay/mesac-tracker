package com.mesac.repository;

import com.mesac.model.ConferenceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConferenceRecordRepository extends JpaRepository<ConferenceRecord, Long> {

    List<ConferenceRecord> findAllByOrderByYearSetDesc();

    boolean existsBySportId(Long sportId);
}
