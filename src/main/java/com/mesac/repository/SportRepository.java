package com.mesac.repository;

import com.mesac.model.Sport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SportRepository extends JpaRepository<Sport, UUID> {
    
    // Custom query methods
    //hello
    List<Sport> findByCategory(String category);
}