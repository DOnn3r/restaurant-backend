package com.restaurant.web;

import com.restaurant.domain.DiningTable;
import com.restaurant.repo.OrderRepository;
import com.restaurant.repo.TableRepository;
import com.restaurant.service.OrderService;
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
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableRepository tables;
    private final OrderRepository orders;
    private final OrderService service;

    @GetMapping
    public List<TableView> list() {
        return service.tableViews();
    }

    @GetMapping("/{id}")
    public TableView one(@PathVariable Long id) {
        return service.tableView(id);
    }

    /** 204 quand la table est libre. */
    @GetMapping("/{id}/order")
    public ResponseEntity<OrderView> openOrder(@PathVariable Long id) {
        return service.openOrderOfTable(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Crée la commande de la table, ou ajoute des plats à la commande déjà ouverte. */
    @PostMapping("/{id}/order")
    @PreAuthorize("hasAnyRole('SERVEUR','ADMIN')")
    public OrderView order(@PathVariable Long id, @Valid @RequestBody OrderRequest req) {
        return service.createOrAppend(id, req);
    }

    // ---- Administration des tables

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public TableView create(@Valid @RequestBody TableRequest req) {
        String label = req.label().trim();
        if (tables.existsByLabel(label)) throw Http.conflict("Une table porte déjà ce nom");
        DiningTable t = new DiningTable();
        t.setLabel(label);
        t.setSeats(req.seats());
        return service.tableView(tables.save(t).getId());
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<TableView> bulk(@Valid @RequestBody TableBulkRequest req) {
        int n = (int) tables.count() + 1;
        List<Long> ids = new ArrayList<>();
        for (int created = 0; created < req.count(); n++) {
            String label = "Table " + n;
            if (tables.existsByLabel(label)) continue;
            DiningTable t = new DiningTable();
            t.setLabel(label);
            t.setSeats(req.seats());
            ids.add(tables.save(t).getId());
            created++;
        }
        return ids.stream().map(service::tableView).toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TableView update(@PathVariable Long id, @Valid @RequestBody TableRequest req) {
        DiningTable t = tables.findById(id).orElseThrow(() -> Http.notFound("Table introuvable"));
        String label = req.label().trim();
        if (!label.equals(t.getLabel()) && tables.existsByLabel(label)) throw Http.conflict("Une table porte déjà ce nom");
        t.setLabel(label);
        t.setSeats(req.seats());
        tables.save(t);
        return service.tableView(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!tables.existsById(id)) throw Http.notFound("Table introuvable");
        if (orders.existsByDiningTableId(id)) {
            throw Http.conflict("Cette table a un historique de commandes et ne peut pas être supprimée");
        }
        tables.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
