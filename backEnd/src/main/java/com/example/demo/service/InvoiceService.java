package com.example.demo.service;

import com.example.demo.dto.InvoiceCreateRequest;
import com.example.demo.dto.InvoiceLineItemRequest;
import com.example.demo.dto.InvoiceResponse;
import com.example.demo.dto.InvoiceUpdateDto;
import com.example.demo.config.TenantContext;
import com.example.demo.exceptions.ResourceNotFoundException;
import com.example.demo.model.*;
import com.example.demo.repository.CustomerRepo;
import com.example.demo.repository.PaymentRepo;
import com.example.demo.repository.InvoiceRepo;
import com.example.demo.repository.TenantRepo;
import com.example.demo.repository.UserRepo;
import com.example.demo.repository.WorkOrderRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private final InvoiceRepo InvoiceRepo;
    private final WorkOrderRepo workOrderRepo;
    private final AppUserService userService;
    private final PaymentRepo orderPaymentsRepo;
    private final CustomerRepo customerRepo;
    private final TenantRepo tenantRepo;
    private final UserRepo userRepo;
    private final RestTemplate restTemplate;

    @Value("${n8n.webhook.product-created:}")
    private String n8nWebhookUrl;

    public InvoiceService(InvoiceRepo InvoiceRepo,
                          WorkOrderRepo workOrderRepo,
                          AppUserService userService,
                          PaymentRepo orderPaymentsRepo,
                          CustomerRepo customerRepo,
                          TenantRepo tenantRepo,
                          UserRepo userRepo,
                          RestTemplate restTemplate) {
        this.InvoiceRepo = InvoiceRepo;
        this.workOrderRepo = workOrderRepo;
        this.userService = userService;
        this.orderPaymentsRepo = orderPaymentsRepo;
        this.customerRepo = customerRepo;
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.restTemplate = restTemplate;
    }

    // ---------------- CREATE ----------------

    @Transactional
    public InvoiceResponse createProduct(InvoiceCreateRequest req) {
        AppUser owner = userService.getCurrentUser();
        if (owner == null || owner.getTenant() == null) {
            throw new IllegalStateException("Authenticated tenant owner is required");
        }
        return createInternal(req, owner.getTenant(), owner);
    }

    @Transactional
    public InvoiceResponse createForTenant(InvoiceCreateRequest req, Long tenantId, Long ownerId) {
        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        AppUser owner = userRepo.findByIdAndTenant_Id(ownerId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Owner does not belong to tenant"));
        return createInternal(req, tenant, owner);
    }

    private InvoiceResponse createInternal(InvoiceCreateRequest req, Tenant tenant, AppUser owner) {
        Invoice p = new Invoice();

        p.setTitulo(req.titulo());
        p.setCantidad(req.cantidad() != null ? req.cantidad() : 0L);
        p.setStartDate(req.startDate() != null ? req.startDate() : LocalDate.now());
        p.setFechaEntrega(req.fechaEntrega());
        p.setFechaEstimada(req.fechaEstimada() != null
                ? req.fechaEstimada()
                : (req.fechaEntrega() != null ? req.fechaEntrega() : p.getStartDate().plusDays(35)));
        p.setFoto(req.foto());
        p.setNotas(req.notas());
        p.setOwner(owner);
        p.setTenant(tenant);
        if (req.customerId() != null) {
            Customer customer = customerRepo.findByIdAndTenant_Id(req.customerId(), tenant.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer does not belong to tenant"));
            p.setCustomer(customer);
        }
        p.setClientPhone(req.clientPhone());
        replaceLineItems(p, req.lineItems());
        p.setPrecio(resolveInvoiceTotal(req.precio(), p));

        Invoice saved = InvoiceRepo.save(p);

        //WorkOrder Creation
        WorkOrder wo = new WorkOrder();
        wo.setInvoice(saved);
        wo.setStatus(Status.EN_GESTION);
        wo.setUpdateAt(LocalDateTime.now());

        workOrderRepo.save(wo);

        saved.setWorkOrder(wo);

        // ----- CREATE PAYMENT: DEPOSIT -----
        if (req.amount() != null) {
            OrderPayments deposit = new OrderPayments();
            deposit.setInvoice(saved);
            deposit.setTenant(saved.getTenant());
            deposit.setPaymentType("DEPOSIT");
            deposit.setAmount(req.amount());

            LocalDate depositDate =
                    (req.startDate() != null) ? req.startDate() : LocalDate.now();
            deposit.setPaymentDate(depositDate);

            orderPaymentsRepo.save(deposit);
        }

        scheduleN8nWebhookAfterCommit(saved);

        return InvoiceResponse.from(saved);
    }

    private void scheduleN8nWebhookAfterCommit(Invoice p) {
        if (n8nWebhookUrl == null || n8nWebhookUrl.isBlank()) return;
        Map<String, Object> payload = Map.of(
                "invoiceId", p.getId(),
                "titulo", p.getTitulo() != null ? p.getTitulo() : "",
                "clientPhone", p.getClientPhone() != null ? p.getClientPhone() : "",
                "precio", p.getPrecio(),
                "startDate", p.getStartDate().toString(),
                "fechaEstimada", p.getFechaEstimada() != null ? p.getFechaEstimada().toString() : ""
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fireN8nWebhook(payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fireN8nWebhook(payload);
            }
        });
    }

    private void fireN8nWebhook(Map<String, Object> payload) {
        try {
            restTemplate.postForObject(n8nWebhookUrl, payload, String.class);
        } catch (Exception e) {
            // log but don't fail Invoice creation
        }
    }

    // ---------------- READ (unchanged) ----------------

    public InvoiceResponse getById(long id) throws ResourceNotFoundException {
        Invoice p = findOwnedInvoice(id);
        return InvoiceResponse.from(p);
    }

    public Page<InvoiceResponse> getAll(Pageable pageable) {
        Long tenantId = currentTenantId();
        return InvoiceRepo.findByTenant_Id(tenantId, pageable).map(InvoiceResponse::from);
    }


    // ---------------- UPDATE ----------------

    public InvoiceResponse update(Long id, InvoiceUpdateDto dto) throws ResourceNotFoundException {
        Invoice Invoice = findOwnedInvoice(id);

        applyProductUpdates(Invoice, dto);

        Invoice saved = InvoiceRepo.save(Invoice);

        // ----- CREATE PAYMENT: RESTO -----
        if (dto.getAmount() != null) {
            OrderPayments pago = new OrderPayments();
            pago.setInvoice(saved);
            pago.setTenant(saved.getTenant());
            pago.setPaymentType(dto.getPaymentType());
            pago.setAmount(dto.getAmount());
            pago.setPaymentDate(LocalDate.now());

            orderPaymentsRepo.save(pago);
        }

        return InvoiceResponse.from(saved);
    }

    private void applyProductUpdates(Invoice Invoice, InvoiceUpdateDto dto) {
        if (dto.getTitulo() != null) Invoice.setTitulo(dto.getTitulo());
        if (dto.getNotas() != null) Invoice.setNotas(dto.getNotas());
        if (dto.getFoto() != null) Invoice.setFoto(dto.getFoto());
        if (dto.getCantidad() != null) Invoice.setCantidad(dto.getCantidad());
        if (dto.getClientPhone() != null) Invoice.setClientPhone(dto.getClientPhone());
        if (dto.getCustomerId() != null) {
            customerRepo.findByIdAndTenant_Id(dto.getCustomerId(), currentTenantId()).ifPresent(Invoice::setCustomer);
        }
        if (dto.getLineItems() != null) {
            replaceLineItems(Invoice, dto.getLineItems());
            Invoice.setPrecio(resolveInvoiceTotal(dto.getPrecio(), Invoice));
        } else if (dto.getPrecio() != null) {
            Invoice.setPrecio(dto.getPrecio());
        }

        if (dto.getFechaEstimada() != null && !dto.getFechaEstimada().isBlank()) {
            Invoice.setFechaEstimada(LocalDate.parse(dto.getFechaEstimada()));
        }
        if (dto.getFechaEntrega() != null && !dto.getFechaEntrega().isBlank()) {
            Invoice.setFechaEntrega(LocalDate.parse(dto.getFechaEntrega()));
        }

        if (dto.getAssignedUserId() != null) {
            AppUser newOwner = userService.getUserById(dto.getAssignedUserId());
            if (newOwner != null) Invoice.setOwner(newOwner);
        }

        WorkOrder wo = Invoice.getWorkOrder();
        if (wo != null) {
            if (dto.getWorkOrderStatus() != null) wo.setStatus(dto.getWorkOrderStatus());
            wo.setUpdateAt(LocalDateTime.now());
        }
    }

    private void replaceLineItems(Invoice invoice, List<InvoiceLineItemRequest> rows) {
        invoice.getLineItems().clear();
        if (rows == null) return;

        for (InvoiceLineItemRequest row : rows) {
            if (row == null || row.description() == null || row.description().isBlank()) {
                continue;
            }

            BigDecimal quantity = normalizeQuantity(row.quantity());
            BigDecimal unitPrice = normalizeMoney(row.unitPrice());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0 && unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            InvoiceLineItem item = new InvoiceLineItem();
            item.setInvoice(invoice);
            item.setDescription(row.description().trim());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setSubtotal(quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP));
            invoice.getLineItems().add(item);
        }
    }

    private BigDecimal resolveInvoiceTotal(BigDecimal fallback, Invoice invoice) {
        if (invoice.getLineItems() == null || invoice.getLineItems().isEmpty()) {
            return normalizeMoney(fallback);
        }
        return invoice.getLineItems().stream()
                .map(InvoiceLineItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(3, RoundingMode.HALF_UP);
    }

    // ---------------- DELETE ----------------

    public boolean delete(Long id) throws ResourceNotFoundException {
        Invoice Invoice = findOwnedInvoice(id);

        workOrderRepo.findByInvoice_Id(id).ifPresent(workOrderRepo::delete);

        // delete ALL payments for this Invoice (OneToMany)
        List<OrderPayments> payments = orderPaymentsRepo.findAllByInvoice_IdAndTenant_Id(id, currentTenantId());
        if (!payments.isEmpty()) {
            orderPaymentsRepo.deleteAll(payments);
        }

        InvoiceRepo.delete(Invoice);
        return true;
    }

    public void guardar(Invoice p) {
        InvoiceRepo.save(p);
    }
    public List<InvoiceResponse> getProductsDueThisWeek() {
        Long tenantId = currentTenantId();
        LocalDate today = LocalDate.now().with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate endOfWeek = today.plusDays(7);

        List<Invoice> products = InvoiceRepo.findByFechaEstimadaBetweenAndTenant_Id(today, endOfWeek, tenantId);

        return products.stream()
                .map(InvoiceResponse::from)
                .collect(Collectors.toList());
    }

    public long countOrders() {
        Long tenantId = currentTenantId();
        return tenantId == null ? InvoiceRepo.count() : InvoiceRepo.countByTenant_Id(tenantId);
    }



    public List<Object[]> findTopProducts() {
        Long tenantId = currentTenantId();
        return tenantId == null ? InvoiceRepo.findTopOrders() : InvoiceRepo.findTopOrdersByTenant(tenantId);
    }
    public Invoice findByTitle(String title) {
        Long tenantId = currentTenantId();
        return InvoiceRepo.findByTituloAndTenant_Id(title, tenantId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
    }

    public Page<InvoiceResponse> searchByTitle(String query, Pageable pageable) {
        return InvoiceRepo.searchByTituloAndTenant(query, currentTenantId(), pageable).map(InvoiceResponse::from);
    }

    public List<InvoiceResponse> getProductsPastDue() {
        Long tenantId = currentTenantId();
        List<Invoice> products = InvoiceRepo.findByWorkOrderStatusAndTenant_Id(Status.EN_DISPUTA, tenantId);

        return products.stream()
                .map(InvoiceResponse::from)
                .collect(Collectors.toList());
    }

    public List<InvoiceResponse> getProductsNotPickedUp(){
        Long tenantId = currentTenantId();
        List<Invoice> products = InvoiceRepo.findByWorkOrderStatusAndTenant_Id(Status.PROMETIO_PAGO, tenantId);

        return products.stream()
                .map(InvoiceResponse::from)
                .collect(Collectors.toList());
    }

    public Page<InvoiceResponse> searchWithFilters(
            String titulo, String workOrderStatusStr,
            String from, String to, Pageable pageable) {

        Status workOrderStatus = null;
        if (workOrderStatusStr != null && !workOrderStatusStr.isBlank()) {
            try { workOrderStatus = Status.valueOf(workOrderStatusStr.toUpperCase()); } catch (Exception ignored) {}
        }

        LocalDate fromDate = (from != null && !from.isBlank()) ? LocalDate.parse(from) : null;
        LocalDate toDate = (to != null && !to.isBlank()) ? LocalDate.parse(to) : null;

        String tituloParam = (titulo != null && !titulo.isBlank()) ? titulo : null;

        Long tenantId = currentTenantId();
        return InvoiceRepo.filterProductsByTenant(
                tenantId, tituloParam, workOrderStatus, fromDate, toDate, pageable
        ).map(InvoiceResponse::from);
    }

    private Long currentTenantId() {
        return TenantContext.get();
    }

    private Invoice findOwnedInvoice(Long id) throws ResourceNotFoundException {
        Long tenantId = currentTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("Invoice not found with ID: " + id);
        }
        return InvoiceRepo.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + id));
    }

    private Tenant currentTenant() {
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            return tenantRepo.findById(tenantId).orElse(null);
        }
        return tenantRepo.findAll().stream().findFirst().orElse(null);
    }
}
