package com.restaurant.web;

import com.restaurant.domain.Dish;
import com.restaurant.repo.DishRepository;
import com.restaurant.repo.OrderItemRepository;
import com.restaurant.web.Dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/dishes")
@RequiredArgsConstructor
public class DishController {

    private final DishRepository dishes;
    private final OrderItemRepository items;

    @GetMapping
    @Transactional(readOnly = true)
    public List<DishView> list() {
        return dishes.findAllByOrderByCategoryAscNameAsc().stream().map(DishView::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DishView create(@Valid @RequestBody DishRequest req) {
        Dish d = new Dish();
        apply(d, req);
        return DishView.of(dishes.save(d));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public DishView update(@PathVariable Long id, @Valid @RequestBody DishRequest req) {
        Dish d = dishes.findById(id).orElseThrow(() -> Http.notFound("Plat introuvable"));
        apply(d, req);
        return DishView.of(dishes.save(d));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!dishes.existsById(id)) throw Http.notFound("Plat introuvable");
        if (items.existsByDishId(id)) {
            throw Http.conflict("Ce plat a déjà été commandé : masquez-le de la carte plutôt que de le supprimer");
        }
        dishes.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void apply(Dish d, DishRequest r) {
        d.setName(r.name().trim());
        d.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        d.setPrice(r.price());
        d.setCategory(r.category().trim());
        List<String> ing = new ArrayList<>();
        if (r.ingredients() != null) {
            r.ingredients().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().forEach(ing::add);
        }
        d.getIngredients().clear();
        d.getIngredients().addAll(ing);
        if (r.available() != null) d.setAvailable(r.available());
    }
}
