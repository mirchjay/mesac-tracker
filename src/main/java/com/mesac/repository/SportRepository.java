package com.mesac.repository;

import com.mesac.model.Sport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SportRepository extends JpaRepository<Sport, Long> {

    Optional<Sport> findByNameIgnoreCase(String name);
}