package com.restaurant.repo;

import com.restaurant.domain.DiningTable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableRepository extends JpaRepository<DiningTable, Long> {
    boolean existsByLabel(String label);
}
