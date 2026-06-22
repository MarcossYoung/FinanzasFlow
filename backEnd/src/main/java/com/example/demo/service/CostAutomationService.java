package com.example.demo.service;

import com.example.demo.model.Costs;
import com.example.demo.model.PaymentFrequency;
import com.example.demo.repository.CostRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CostAutomationService {
    @Autowired
    private CostRepo costRepo;

    // Se ejecuta todos los días a las 01:00 AM
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateRecurringCosts() {
        LocalDate today = LocalDate.now();

        // 1. Traer todos los costos recurrentes activos (Excluye ONE_TIME)
        // Nota: Idealmente deberías tener un metodo en el repo: findByFrequencyNot("ONE_TIME")
        List<Costs> recurringCosts = costRepo.findByFrequencyNot(PaymentFrequency.ONE_TIME).stream()
                .filter(c -> !PaymentFrequency.ONE_TIME.equals(c.getFrequency()))
                .toList();

        for (Costs original : recurringCosts) {
            if (shouldGenerateCost(original, today)) {
                createCopy(original, today);
            }
        }
    }

    private boolean shouldGenerateCost(Costs original, LocalDate today) {
        LocalDate start = original.getDate();

        // Evitar duplicados: Si ya existe un gasto con el mismo asunto HOY, no lo creamos
        Long tenantId = original.getTenant() != null ? original.getTenant().getId() : null;
        boolean alreadyExists = tenantId != null
                ? costRepo.existsByDateAndReasonAndAmountAndTenant_Id(
                        today, original.getReason(), original.getAmount(), tenantId)
                : costRepo.existsByDateAndReasonAndAmount(
                        today, original.getReason(), original.getAmount());

        if (alreadyExists) return false;

        if (PaymentFrequency.YEARLY.equals(original.getFrequency())) {
            return start.getDayOfYear() == today.getDayOfYear() && !start.isEqual(today);
        }

        if (PaymentFrequency.MONTHLY.equals(original.getFrequency())) {
            return start.getDayOfMonth() == today.getDayOfMonth() && !start.isEqual(today);
        }

        if (PaymentFrequency.WEEKLY.equals(original.getFrequency())) {
            return start.getDayOfWeek() == today.getDayOfWeek() && !start.isEqual(today);
        }

        return false;
    }

    private void createCopy(Costs original, LocalDate today) {
        Costs newCost = new Costs();
        newCost.setTenant(original.getTenant());
        newCost.setDate(today);
        newCost.setAmount(original.getAmount());
        newCost.setReason(original.getReason() + " (Auto)"); // Para identificarlo
        newCost.setCostType(original.getCostType());
        newCost.setFrequency(original.getFrequency()); // Sigue siendo recurrente para el mes que viene

        costRepo.save(newCost);
        System.out.println("Gasto recurrente generado: " + newCost.getReason());

    }
}
