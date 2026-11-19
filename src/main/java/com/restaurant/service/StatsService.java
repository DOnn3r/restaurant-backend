package com.restaurant.service;

import com.restaurant.domain.CustomerOrder;
import com.restaurant.domain.Expense;
import com.restaurant.repo.ExpenseRepository;
import com.restaurant.repo.OrderRepository;
import com.restaurant.web.Dto.*;
import com.restaurant.web.Http;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final OrderRepository orders;
    private final ExpenseRepository expenses;

    @Value("${app.timezone}")
    private String timezone;

    /** Chiffre d'affaires = commandes encaissées ; bénéfice = chiffre d'affaires - dépenses saisies. */
    public Stats compute(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw Http.bad("La date de fin précède la date de début");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw Http.bad("Période limitée à 366 jours");

        ZoneId zone = ZoneId.of(timezone);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<CustomerOrder> paid = orders.findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
                CustomerOrder.Status.PAID, start, end);
        List<Expense> exps = expenses.findByDateBetweenOrderByDateDescIdDesc(from, to);

        Map<LocalDate, BigDecimal> revByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> expByDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            revByDay.put(d, BigDecimal.ZERO);
            expByDay.put(d, BigDecimal.ZERO);
        }

        BigDecimal revenue = BigDecimal.ZERO;
        Map<String, Long> dishQty = new HashMap<>();
        Map<String, BigDecimal> dishRev = new HashMap<>();
        Map<CustomerOrder.Payment, BigDecimal> byPayment = new EnumMap<>(CustomerOrder.Payment.class);

        for (CustomerOrder o : paid) {
            BigDecimal total = o.total();
            revenue = revenue.add(total);
            LocalDate day = o.getPaidAt().atZone(zone).toLocalDate();
            revByDay.merge(day, total, BigDecimal::add);
            if (o.getPaymentMethod() != null) byPayment.merge(o.getPaymentMethod(), total, BigDecimal::add);
            o.getItems().forEach(i -> {
                dishQty.merge(i.getDishName(), (long) i.getQuantity(), Long::sum);
                dishRev.merge(i.getDishName(), i.lineTotal(), BigDecimal::add);
            });
        }

        BigDecimal totalExpenses = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (Expense e : exps) {
            totalExpenses = totalExpenses.add(e.getAmount());
            expByDay.merge(e.getDate(), e.getAmount(), BigDecimal::add);
            byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }

        List<DayStat> days = revByDay.entrySet().stream()
                .map(e -> new DayStat(e.getKey(), e.getValue(), expByDay.getOrDefault(e.getKey(), BigDecimal.ZERO)))
                .toList();
        List<DishStat> top = dishQty.entrySet().stream()
                .map(e -> new DishStat(e.getKey(), e.getValue(), dishRev.get(e.getKey())))
                .sorted(Comparator.comparingLong(DishStat::quantity).reversed())
                .limit(8).toList();
        List<PaymentStat> payments = byPayment.entrySet().stream()
                .map(e -> new PaymentStat(e.getKey(), e.getValue())).toList();
        List<CategoryStat> cats = byCategory.entrySet().stream()
                .map(e -> new CategoryStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(CategoryStat::amount).reversed()).toList();

        BigDecimal avg = paid.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_UP);

        return new Stats(revenue, totalExpenses, revenue.subtract(totalExpenses), paid.size(), avg,
                days, top, payments, cats);
    }
}
