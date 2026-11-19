package com.restaurant.web;

import com.restaurant.domain.CustomerOrder;
import com.restaurant.service.OrderService;
import com.restaurant.web.Dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    /** Historique des commandes encaissées (admin). */
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public List<OrderView> history(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(from, to);
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('SERVEUR','ADMIN')")
    public OrderView addItems(@PathVariable Long id, @Valid @RequestBody OrderRequest req) {
        return service.addItems(id, req);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('SERVEUR','ADMIN')")
    public OrderView removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        return service.removeItem(id, itemId);
    }

    @GetMapping("/{id}/bill")
    @PreAuthorize("hasAnyRole('SERVEUR','ADMIN')")
    public BillView bill(@PathVariable Long id) {
        return service.bill(id);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('SERVEUR','ADMIN')")
    public OrderView pay(@PathVariable Long id, @Valid @RequestBody PayRequest req) {
        return service.pay(id, req.method());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SERVEUR','ADMIN')")
    public OrderView cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
