package com.restaurant.web;

import com.restaurant.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Dto {
    private Dto() {}

    // ---- Auth & équipe
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record UserView(Long id, String username, String fullName, Role role, boolean active) {
        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive());
        }
    }
    public record LoginResponse(String token, UserView user) {}
    public record UserCreate(@NotBlank @Size(max = 50) String username,
                             @NotBlank @Size(min = 6, max = 100) String password,
                             @NotBlank @Size(max = 100) String fullName,
                             @NotNull Role role) {}
    public record UserUpdate(@Size(max = 100) String fullName, Role role, Boolean active,
                             @Size(min = 6, max = 100) String password) {}

    // ---- Carte
    public record DishRequest(@NotBlank @Size(max = 120) String name,
                              @Size(max = 500) String description,
                              @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
                              @NotBlank @Size(max = 60) String category,
                              List<@NotBlank String> ingredients,
                              Boolean available) {}
    public record DishView(Long id, String name, String description, BigDecimal price, String category,
                           List<String> ingredients, boolean available) {
        public static DishView of(Dish d) {
            return new DishView(d.getId(), d.getName(), d.getDescription(), d.getPrice(), d.getCategory(),
                    List.copyOf(d.getIngredients()), d.isAvailable());
        }
    }

    // ---- Tables
    public record TableRequest(@NotBlank @Size(max = 40) String label, @Min(1) @Max(100) int seats) {}
    public record TableBulkRequest(@Min(1) @Max(100) int count, @Min(1) @Max(100) int seats) {}
    public record TableView(Long id, String label, int seats, boolean occupied, Long orderId,
                            BigDecimal total, long readyCount) {}

    // ---- Commandes
    public record OrderItemRequest(@NotNull Long dishId, @Min(1) @Max(99) int quantity,
                                   @Size(max = 300) String note) {}
    public record OrderRequest(@NotEmpty @Valid List<OrderItemRequest> items) {}
    public record OrderItemView(Long id, Long dishId, String dishName, BigDecimal unitPrice, int quantity,
                                String note, OrderItem.Status status, Instant createdAt, BigDecimal lineTotal) {}
    public record OrderView(Long id, Long tableId, String tableLabel, String serverName,
                            CustomerOrder.Status status, Instant createdAt, Instant paidAt,
                            CustomerOrder.Payment paymentMethod, List<OrderItemView> items, BigDecimal total) {}
    public record BillLine(String name, BigDecimal unitPrice, int quantity, BigDecimal total) {}
    public record BillView(Long orderId, String tableLabel, String serverName, Instant createdAt,
                           List<BillLine> lines, BigDecimal total, long unservedCount) {}
    public record PayRequest(@NotNull CustomerOrder.Payment method) {}
    public record StatusRequest(@NotNull OrderItem.Status status) {}

    // ---- Cuisine
    public record Ticket(Long orderId, String tableLabel, String serverName, Instant createdAt,
                         List<OrderItemView> items) {}

    // ---- Dépenses & statistiques
    public record ExpenseRequest(@NotBlank @Size(max = 120) String label,
                                 @NotBlank @Size(max = 60) String category,
                                 @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                                 @NotNull LocalDate date) {}
    public record ExpenseView(Long id, String label, String category, BigDecimal amount, LocalDate date) {
        public static ExpenseView of(Expense e) {
            return new ExpenseView(e.getId(), e.getLabel(), e.getCategory(), e.getAmount(), e.getDate());
        }
    }
    public record DayStat(LocalDate date, BigDecimal revenue, BigDecimal expenses) {}
    public record DishStat(String name, long quantity, BigDecimal revenue) {}
    public record PaymentStat(CustomerOrder.Payment method, BigDecimal amount) {}
    public record CategoryStat(String category, BigDecimal amount) {}
    public record Stats(BigDecimal revenue, BigDecimal expenses, BigDecimal profit, long orders,
                        BigDecimal averageTicket, List<DayStat> days, List<DishStat> topDishes,
                        List<PaymentStat> payments, List<CategoryStat> expensesByCategory) {}
}
