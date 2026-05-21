package com.example.demo.dto;

import com.example.demo.model.Invoice;
import com.example.demo.model.Status;
import com.example.demo.model.WorkOrder;
import com.example.demo.model.OrderPayments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String titulo,
        Long cantidad,
        LocalDate startDate,
        LocalDate fechaEntrega,
        LocalDate fechaEstimada,
        String foto,
        String notas,
        BigDecimal precio,
        Long ownerId,
        Long workOrderId,
        Status workOrderStatus,
        BigDecimal totalPaid,
        BigDecimal depositPaid,
        BigDecimal daysLate,
        String clientPhone,
        Long customerId,
        String customerName,
        String customerPhone,
        List<InvoiceLineItemResponse> lineItems
) {

    public static InvoiceResponse from(Invoice p) {
        WorkOrder wo = p.getWorkOrder();

        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal depositPaid = BigDecimal.ZERO;

        if (p.getOrderPayments() != null) {
            for (OrderPayments pay : p.getOrderPayments()) {
                BigDecimal amount = pay.getAmount();
                if (amount != null) {
                    totalPaid = totalPaid.add(amount);
                    if ("DEPOSIT".equals(pay.getPaymentType())) {
                        depositPaid = depositPaid.add(amount);
                    }
                }
            }
        }

        BigDecimal daysLate = null;
        if (wo != null && wo.getUpdateAt() != null && wo.getStatus() == Status.EN_DISPUTA) {
            daysLate = BigDecimal.valueOf(ChronoUnit.DAYS.between(
                    wo.getUpdateAt(),
                    java.time.LocalDateTime.now()
            ));
        }

        List<InvoiceLineItemResponse> lineItems = p.getLineItems() == null
                ? List.of()
                : p.getLineItems().stream()
                        .map(InvoiceLineItemResponse::from)
                        .toList();

        return new InvoiceResponse(
                p.getId(),
                p.getTitulo(),
                p.getCantidad(),
                p.getStartDate(),
                p.getFechaEntrega(),
                p.getFechaEstimada(),
                p.getFoto(),
                p.getNotas(),
                p.getPrecio(),
                p.getOwner() != null ? p.getOwner().getId() : null,
                wo != null ? wo.getId() : null,
                wo != null ? wo.getStatus() : null,
                totalPaid,
                depositPaid,
                daysLate,
                p.getClientPhone(),
                p.getCustomer() != null ? p.getCustomer().getId() : null,
                p.getCustomer() != null ? p.getCustomer().getName() : null,
                p.getCustomer() != null ? p.getCustomer().getPhone() : null,
                lineItems
        );
    }
}
