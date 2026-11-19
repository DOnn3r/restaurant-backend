package com.restaurant.repo;

import com.restaurant.domain.Dish;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findAllByOrderByCategoryAscNameAsc();
}
