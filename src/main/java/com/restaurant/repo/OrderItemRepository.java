package com.restaurant.repo;

import com.restaurant.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByDishId(Long dishId);
}
