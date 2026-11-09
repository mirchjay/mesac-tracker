package com.mesac.repository;

import com.mesac.model.Staff;
import com.mesac.model.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Staff> findAllByOrderByRoleAscNameAsc();

    long countByRoleAndActiveTrue(StaffRole role);

    List<Staff> findByTeamId(Long teamId);
}
