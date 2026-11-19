package com.restaurant.web;

import com.restaurant.service.OrderService;
import com.restaurant.web.Dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final OrderService service;

    @GetMapping("/tickets")
    public List<Ticket> tickets() {
        return service.tickets();
    }

    @PatchMapping("/items/{id}/status")
    public OrderItemView status(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        return service.setItemStatus(id, req.status());
    }
}
