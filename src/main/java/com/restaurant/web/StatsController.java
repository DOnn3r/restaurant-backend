package com.restaurant.web;

import com.restaurant.service.StatsService;
import com.restaurant.web.Dto.Stats;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stats")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService stats;

    @GetMapping
    public Stats get(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return stats.compute(from, to);
    }
}
