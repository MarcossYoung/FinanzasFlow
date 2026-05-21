package com.example.demo.dto;

import com.example.demo.model.Status;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
public class InvoiceUpdateDto {

    // Invoice fields
    private String titulo;
    private Long cantidad;
    private BigDecimal precio;
    private String notas;
    private String foto;
    private String fechaEstimada; // "2025-01-15"
    private String fechaEntrega;

    // WORK ORDER fields
    private Status workOrderStatus;
    private String workOrderNotes;

    //Payment fields
    private BigDecimal amount;
    private String paymentType;

    // Feature additions
    private String clientPhone;
    private Long assignedUserId;
    private Long customerId;
    private List<InvoiceLineItemRequest> lineItems;
}
