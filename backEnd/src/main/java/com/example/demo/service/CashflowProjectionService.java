package com.example.demo.service;

import com.example.demo.dto.DailyCashflowPoint;
import com.example.demo.model.Costs;
import com.example.demo.model.PaymentFrequency;
import com.example.demo.model.PaymentSchedule;
import com.example.demo.model.ScheduleStatus;
import com.example.demo.repository.CostRepo;
import com.example.demo.repository.PaymentScheduleRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class CashflowProjectionService {
    private final PaymentScheduleRepo scheduleRepo;
    private final CostRepo costRepo;

    public CashflowProjectionService(PaymentScheduleRepo scheduleRepo, CostRepo costRepo) {
        this.scheduleRepo = scheduleRepo;
        this.costRepo = costRepo;
    }

    @Transactional(readOnly = true)
    public List<DailyCashflowPoint> project(LocalDate from, LocalDate to) {
        List<PaymentSchedule> schedules = scheduleRepo.findByStatusAndExpectedDateBetween(
                ScheduleStatus.PENDIENTE, from, to);
        List<Costs> costs = costRepo.findByDateBetween(from, to);
        List<DailyCashflowPoint> points = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            LocalDate current = day;
            BigDecimal inflow = schedules.stream()
                    .filter(s -> current.equals(s.getExpectedDate()))
                    .map(this::weightedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outflow = costs.stream()
                    .filter(c -> current.equals(c.getDate()))
                    .filter(c -> !PaymentFrequency.MONTHLY.equals(c.getFrequency()))
                    .map(Costs::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(monthlyDailyCost(costs));
            BigDecimal net = inflow.subtract(outflow);
            cumulative = cumulative.add(net);
            points.add(new DailyCashflowPoint(current, inflow, outflow, net, cumulative));
        }

        return points;
    }

    private BigDecimal weightedAmount(PaymentSchedule schedule) {
        BigDecimal score = BigDecimal.valueOf(100);
        if (schedule.getInvoice() != null
                && schedule.getInvoice().getCustomer() != null
                && schedule.getInvoice().getCustomer().getPaymentScore() != null) {
            score = BigDecimal.valueOf(schedule.getInvoice().getCustomer().getPaymentScore());
        }
        return schedule.getAmount().multiply(score).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal monthlyDailyCost(List<Costs> costs) {
        BigDecimal monthly = costs.stream()
                .filter(c -> PaymentFrequency.MONTHLY.equals(c.getFrequency()))
                .map(Costs::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return monthly.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    }
}
