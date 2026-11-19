package com.restaurant.repo;

import com.restaurant.domain.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByStatusOrderByCreatedAtAsc(CustomerOrder.Status status);

    Optional<CustomerOrder> findFirstByDiningTableIdAndStatus(Long tableId, CustomerOrder.Status status);

    boolean existsByDiningTableId(Long tableId);

    List<CustomerOrder> findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
            CustomerOrder.Status status, Instant from, Instant to);
}
