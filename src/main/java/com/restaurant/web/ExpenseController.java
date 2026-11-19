package com.restaurant.web;

import com.restaurant.domain.Expense;
import com.restaurant.repo.ExpenseRepository;
import com.restaurant.web.Dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseRepository expenses;

    @GetMapping
    public List<ExpenseView> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenses.findByDateBetweenOrderByDateDescIdDesc(from, to).stream().map(ExpenseView::of).toList();
    }

    @PostMapping
    public ExpenseView create(@Valid @RequestBody ExpenseRequest req) {
        return ExpenseView.of(expenses.save(apply(new Expense(), req)));
    }

    @PutMapping("/{id}")
    public ExpenseView update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest req) {
        Expense e = expenses.findById(id).orElseThrow(() -> Http.notFound("Dépense introuvable"));
        return ExpenseView.of(expenses.save(apply(e, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!expenses.existsById(id)) throw Http.notFound("Dépense introuvable");
        expenses.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Expense apply(Expense e, ExpenseRequest r) {
        e.setLabel(r.label().trim());
        e.setCategory(r.category().trim());
        e.setAmount(r.amount());
        e.setDate(r.date());
        return e;
    }
}
