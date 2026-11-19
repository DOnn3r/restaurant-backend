package com.restaurant.service;

import com.restaurant.domain.*;
import com.restaurant.repo.*;
import com.restaurant.security.CurrentUser;
import com.restaurant.web.Dto.*;
import com.restaurant.web.Http;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final TableRepository tables;
    private final DishRepository dishes;
    private final OrderItemRepository items;
    private final CurrentUser current;

    @org.springframework.beans.factory.annotation.Value("${app.timezone}")
    private String timezone;

    // ---------- Mapping

    public static OrderItemView toItemView(OrderItem i) {
        return new OrderItemView(i.getId(), i.getDish() == null ? null : i.getDish().getId(), i.getDishName(),
                i.getUnitPrice(), i.getQuantity(), i.getNote(), i.getStatus(), i.getCreatedAt(), i.lineTotal());
    }

    public static OrderView toView(CustomerOrder o) {
        return new OrderView(o.getId(), o.getDiningTable().getId(), o.getDiningTable().getLabel(),
                o.getServer().getFullName(), o.getStatus(), o.getCreatedAt(), o.getPaidAt(), o.getPaymentMethod(),
                o.getItems().stream().map(OrderService::toItemView).toList(), o.total());
    }

    private CustomerOrder load(Long id) {
        return orders.findById(id).orElseThrow(() -> Http.notFound("Commande introuvable"));
    }

    private CustomerOrder loadOpen(Long id) {
        CustomerOrder o = load(id);
        if (o.getStatus() != CustomerOrder.Status.OPEN) throw Http.conflict("Cette commande est déjà clôturée");
        return o;
    }

    // ---------- Tables (vue du service)

    @Transactional(readOnly = true)
    public List<TableView> tableViews() {
        Map<Long, CustomerOrder> open = orders.findByStatusOrderByCreatedAtAsc(CustomerOrder.Status.OPEN).stream()
                .collect(Collectors.toMap(o -> o.getDiningTable().getId(), Function.identity(), (a, b) -> a));
        return tables.findAll(Sort.by("id")).stream().map(t -> toTableView(t, open.get(t.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public TableView tableView(Long tableId) {
        DiningTable t = tables.findById(tableId).orElseThrow(() -> Http.notFound("Table introuvable"));
        return toTableView(t, orders.findFirstByDiningTableIdAndStatus(tableId, CustomerOrder.Status.OPEN).orElse(null));
    }

    private TableView toTableView(DiningTable t, CustomerOrder o) {
        if (o == null) return new TableView(t.getId(), t.getLabel(), t.getSeats(), false, null, BigDecimal.ZERO, 0);
        long ready = o.getItems().stream().filter(i -> i.getStatus() == OrderItem.Status.READY).count();
        return new TableView(t.getId(), t.getLabel(), t.getSeats(), true, o.getId(), o.total(), ready);
    }

    @Transactional(readOnly = true)
    public Optional<OrderView> openOrderOfTable(Long tableId) {
        return orders.findFirstByDiningTableIdAndStatus(tableId, CustomerOrder.Status.OPEN).map(OrderService::toView);
    }

    // ---------- Prise de commande

    /** Crée la commande de la table, ou ajoute les plats à la commande déjà ouverte. */
    public OrderView createOrAppend(Long tableId, OrderRequest req) {
        DiningTable table = tables.findById(tableId).orElseThrow(() -> Http.notFound("Table introuvable"));
        CustomerOrder order = orders.findFirstByDiningTableIdAndStatus(tableId, CustomerOrder.Status.OPEN)
                .orElseGet(() -> {
                    CustomerOrder o = new CustomerOrder();
                    o.setDiningTable(table);
                    o.setServer(current.get());
                    return o;
                });
        append(order, req.items());
        return toView(orders.saveAndFlush(order));
    }

    public OrderView addItems(Long orderId, OrderRequest req) {
        CustomerOrder order = loadOpen(orderId);
        append(order, req.items());
        return toView(orders.saveAndFlush(order));
    }

    private void append(CustomerOrder order, List<OrderItemRequest> requests) {
        for (OrderItemRequest r : requests) {
            Dish d = dishes.findById(r.dishId()).orElseThrow(() -> Http.notFound("Plat introuvable"));
            if (!d.isAvailable()) throw Http.conflict("« " + d.getName() + " » n'est plus disponible");
            OrderItem it = new OrderItem();
            it.setOrder(order);
            it.setDish(d);
            it.setDishName(d.getName());
            it.setUnitPrice(d.getPrice());
            it.setQuantity(r.quantity());
            it.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
            order.getItems().add(it);
        }
    }

    /** Un plat ne peut être retiré que tant que la cuisine ne l'a pas lancé (sauf admin). */
    public OrderView removeItem(Long orderId, Long itemId) {
        CustomerOrder order = loadOpen(orderId);
        OrderItem item = order.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> Http.notFound("Ligne introuvable"));
        boolean admin = current.get().getRole() == Role.ADMIN;
        if (!admin && item.getStatus() != OrderItem.Status.PENDING) {
            throw Http.conflict("Ce plat est déjà en préparation : demandez à un admin de l'annuler");
        }
        order.getItems().remove(item);
        if (order.getItems().isEmpty()) order.setStatus(CustomerOrder.Status.CANCELLED);
        return toView(orders.saveAndFlush(order));
    }

    public OrderView cancel(Long orderId) {
        CustomerOrder order = loadOpen(orderId);
        boolean admin = current.get().getRole() == Role.ADMIN;
        boolean started = order.getItems().stream().anyMatch(i -> i.getStatus() != OrderItem.Status.PENDING);
        if (started && !admin) throw Http.conflict("La cuisine a déjà commencé : seul un admin peut annuler");
        order.setStatus(CustomerOrder.Status.CANCELLED);
        return toView(orders.saveAndFlush(order));
    }

    @Transactional(readOnly = true)
    public OrderView get(Long orderId) {
        return toView(load(orderId));
    }

    // ---------- Addition & encaissement

    @Transactional(readOnly = true)
    public BillView bill(Long orderId) {
        CustomerOrder o = load(orderId);
        record Key(String name, BigDecimal price) {}
        Map<Key, Integer> qty = new LinkedHashMap<>();
        for (OrderItem i : o.getItems()) qty.merge(new Key(i.getDishName(), i.getUnitPrice()), i.getQuantity(), Integer::sum);
        List<BillLine> lines = qty.entrySet().stream()
                .map(e -> new BillLine(e.getKey().name(), e.getKey().price(), e.getValue(),
                        e.getKey().price().multiply(BigDecimal.valueOf(e.getValue()))))
                .toList();
        long unserved = o.getItems().stream().filter(i -> i.getStatus() != OrderItem.Status.SERVED).count();
        return new BillView(o.getId(), o.getDiningTable().getLabel(), o.getServer().getFullName(),
                o.getCreatedAt(), lines, o.total(), unserved);
    }

    public OrderView pay(Long orderId, CustomerOrder.Payment method) {
        CustomerOrder o = loadOpen(orderId);
        if (o.getItems().isEmpty()) throw Http.conflict("Commande vide");
        o.setStatus(CustomerOrder.Status.PAID);
        o.setPaidAt(Instant.now());
        o.setPaymentMethod(method);
        return toView(orders.saveAndFlush(o));
    }

    @Transactional(readOnly = true)
    public List<OrderView> history(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(timezone);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        return orders.findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                CustomerOrder.Status.PAID, start, end).stream().map(OrderService::toView).toList();
    }

    // ---------- Cuisine

    @Transactional(readOnly = true)
    public List<Ticket> tickets() {
        List<Ticket> result = new ArrayList<>();
        for (CustomerOrder o : orders.findByStatusOrderByCreatedAtAsc(CustomerOrder.Status.OPEN)) {
            List<OrderItemView> active = o.getItems().stream()
                    .filter(i -> i.getStatus() != OrderItem.Status.SERVED)
                    .map(OrderService::toItemView).toList();
            if (!active.isEmpty()) {
                result.add(new Ticket(o.getId(), o.getDiningTable().getLabel(), o.getServer().getFullName(),
                        o.getCreatedAt(), active));
            }
        }
        return result;
    }

    /** Cuisinier : PENDING / PREPARING / READY. Serveur : SERVED. Admin : tout. */
    public OrderItemView setItemStatus(Long itemId, OrderItem.Status status) {
        OrderItem item = items.findById(itemId).orElseThrow(() -> Http.notFound("Ligne introuvable"));
        if (item.getOrder().getStatus() != CustomerOrder.Status.OPEN) throw Http.conflict("Commande clôturée");
        Role role = current.get().getRole();
        boolean serving = status == OrderItem.Status.SERVED;
        boolean allowed = role == Role.ADMIN || (serving ? role == Role.SERVEUR : role == Role.CUISINIER);
        if (!allowed) throw Http.forbidden("Action non autorisée pour votre rôle");
        item.setStatus(status);
        return toItemView(item);
    }
}
