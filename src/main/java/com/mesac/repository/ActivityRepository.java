package com.mesac.repository;

import com.mesac.model.ActivityEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<ActivityEntry, Long> {

    List<ActivityEntry> findTop300ByOrderByIdDesc();

    List<ActivityEntry> findTop300ByStaffIdOrderByIdDesc(Long staffId);

    long countByStaffId(Long staffId);

    Optional<ActivityEntry> findFirstByStaffIdOrderByIdDesc(Long staffId);
}
