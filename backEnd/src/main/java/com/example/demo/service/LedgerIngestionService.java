package com.example.demo.service;

import com.example.demo.dto.*;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LedgerIngestionService {
    public record ClaimResult(Long ingestionId, boolean shouldProcess, TelegramIngestionStatus status) {}
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_CUIT_LENGTH = 30;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_PHONE_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_LINE_DESCRIPTION_LENGTH = 255;

    private final TelegramLedgerIngestionRepo ingestionRepo;
    private final TenantRepo tenantRepo;
    private final CustomerRepo customerRepo;
    private final InvoiceService invoiceService;
    private final CostService costService;
    private final ActivityLogService activityLogService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Value("${telegram.ingestion.stale-minutes:10}")
    private long staleMinutes;

    public LedgerIngestionService(TelegramLedgerIngestionRepo ingestionRepo,
                                  TenantRepo tenantRepo,
                                  CustomerRepo customerRepo,
                                  InvoiceService invoiceService,
                                  CostService costService,
                                  PlatformTransactionManager transactionManager,
                                  ActivityLogService activityLogService) {
        this.ingestionRepo = ingestionRepo;
        this.tenantRepo = tenantRepo;
        this.customerRepo = customerRepo;
        this.invoiceService = invoiceService;
        this.costService = costService;
        this.activityLogService = activityLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ClaimResult claim(String chatId, Long messageId, Long tenantId) {
        if (chatId == null || chatId.isBlank() || messageId == null || tenantId == null) {
            throw new IllegalArgumentException("Telegram source and tenant are required");
        }
        try {
            ClaimResult created = transactionTemplate.execute(status -> {
                Tenant tenant = tenantRepo.findById(tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
                LocalDateTime now = LocalDateTime.now();
                TelegramLedgerIngestion ingestion = new TelegramLedgerIngestion();
                ingestion.setChatId(chatId);
                ingestion.setMessageId(messageId);
                ingestion.setTenant(tenant);
                ingestion.setStatus(TelegramIngestionStatus.PROCESSING);
                ingestion.setCreatedAt(now);
                ingestion.setUpdatedAt(now);
                TelegramLedgerIngestion saved = ingestionRepo.saveAndFlush(ingestion);
                return new ClaimResult(saved.getId(), true, saved.getStatus());
            });
            if (created != null) return created;
        } catch (DataIntegrityViolationException ignored) {
            // Another delivery already owns this source message.
        }

        TelegramLedgerIngestion existing = ingestionRepo.findByChatIdAndMessageId(chatId, messageId)
                .orElseThrow(() -> new IllegalStateException("Duplicate ingestion claim was not found"));
        if (existing.getStatus() == TelegramIngestionStatus.PROCESSING) {
            Boolean reclaimed = transactionTemplate.execute(status -> ingestionRepo.reclaimIfStale(
                    existing.getId(),
                    TelegramIngestionStatus.PROCESSING,
                    LocalDateTime.now().minusMinutes(staleMinutes),
                    LocalDateTime.now()
            ) == 1);
            if (Boolean.TRUE.equals(reclaimed)) {
                return new ClaimResult(existing.getId(), true, TelegramIngestionStatus.PROCESSING);
            }
        }
        return new ClaimResult(existing.getId(), false, existing.getStatus());
    }

    @Transactional
    public void markPending(Long ingestionId, LedgerExtraction extraction) {
        LedgerExtraction normalized = normalizeExtraction(extraction);
        TelegramLedgerIngestion ingestion = ingestionRepo.findLockedById(ingestionId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion not found"));
        if (ingestion.getStatus() != TelegramIngestionStatus.PROCESSING) return;
        ingestion.setExtractionJson(objectMapper.convertValue(normalized, Map.class));
        ingestion.setStatus(TelegramIngestionStatus.PENDING_DIRECTION);
        ingestion.setFailureReason(null);
        ingestion.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public void recordCallbackMessage(Long ingestionId, long callbackMessageId) {
        ingestionRepo.findLockedById(ingestionId).ifPresent(ingestion -> {
            if (ingestion.getStatus() == TelegramIngestionStatus.PENDING_DIRECTION) {
                ingestion.setCallbackMessageId(callbackMessageId);
                ingestion.setUpdatedAt(LocalDateTime.now());
            }
        });
    }

    @Transactional
    public void markFailed(Long ingestionId, String reason) {
        ingestionRepo.findLockedById(ingestionId).ifPresent(ingestion -> {
            if (ingestion.getStatus() != TelegramIngestionStatus.COMPLETED) {
                ingestion.setStatus(TelegramIngestionStatus.FAILED);
                ingestion.setFailureReason(sanitizeFailure(reason));
                ingestion.setUpdatedAt(LocalDateTime.now());
            }
        });
    }

    @Transactional
    public LedgerIngestionResult finalizeDirection(Long ingestionId, String chatId, Long tenantId,
                                                    Long callbackMessageId,
                                                    Long ownerId, LedgerDirection direction) {
        TelegramLedgerIngestion ingestion = ingestionRepo.findLockedById(ingestionId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion not found"));
        if (!ingestion.getChatId().equals(chatId) || !ingestion.getTenant().getId().equals(tenantId)) {
            throw new SecurityException("Ingestion does not belong to this chat and tenant");
        }
        if (callbackMessageId == null || ingestion.getCallbackMessageId() == null
                || !ingestion.getCallbackMessageId().equals(callbackMessageId)) {
            throw new SecurityException("Callback does not belong to this pending ingestion");
        }
        LedgerExtraction extraction = normalizeExtraction(readExtraction(ingestion));
        if (ingestion.getStatus() == TelegramIngestionStatus.COMPLETED) {
            return resultFrom(ingestion, extraction, true);
        }
        if (ingestion.getStatus() != TelegramIngestionStatus.PENDING_DIRECTION) {
            throw new IllegalStateException("Ingestion is not pending direction");
        }
        LedgerRecordType recordType;
        Long recordId;
        if (direction == LedgerDirection.COBRO) {
            Customer customer = resolveOrCreateCustomer(extraction, tenantId);
            InvoiceResponse invoice = invoiceService.createForTenant(toInvoiceRequest(extraction, customer), tenantId, ownerId);
            recordType = LedgerRecordType.INVOICE;
            recordId = invoice.id();
        } else {
            Costs cost = costService.createForTenant(toCostRequest(extraction), tenantId, ownerId);
            recordType = LedgerRecordType.COST;
            recordId = cost.getId();
        }

        ingestion.setDirection(direction);
        ingestion.setRecordType(recordType);
        ingestion.setRecordId(recordId);
        ingestion.setStatus(TelegramIngestionStatus.COMPLETED);
        ingestion.setUpdatedAt(LocalDateTime.now());
        activityLogService.record(tenantId, ActivityLogService.TELEGRAM_INGESTION, ownerId);
        return resultFrom(ingestion, extraction, false);
    }

    @Transactional(readOnly = true)
    public LedgerIngestionResult getCompletedResult(Long ingestionId, String chatId, Long tenantId) {
        TelegramLedgerIngestion ingestion = ingestionRepo.findById(ingestionId)
                .orElseThrow(() -> new IllegalArgumentException("Ingestion not found"));
        if (!ingestion.getChatId().equals(chatId) || !ingestion.getTenant().getId().equals(tenantId)
                || ingestion.getStatus() != TelegramIngestionStatus.COMPLETED) {
            throw new IllegalStateException("Ingestion is not completed for this chat");
        }
        return resultFrom(ingestion, readExtraction(ingestion), true);
    }

    public void validateExtraction(LedgerExtraction extraction) {
        normalizeExtraction(extraction);
    }

    public LedgerExtraction normalizeExtraction(LedgerExtraction extraction) {
        if (extraction == null || extraction.amount() == null || extraction.amount().signum() <= 0) {
            throw new IllegalArgumentException("El total debe ser positivo");
        }
        BigDecimal amount = normalizePositiveMoney(extraction.amount(), "El total");
        String title = trimToLength(extraction.titulo(), MAX_TITLE_LENGTH);
        String counterpartyName = trimToLength(extraction.counterpartyName(), MAX_NAME_LENGTH);
        String cuitDni = trimToLength(extraction.cuitDni(), MAX_CUIT_LENGTH);
        String email = trimToLength(extraction.email(), MAX_EMAIL_LENGTH);
        String phone = trimToLength(extraction.phone(), MAX_PHONE_LENGTH);
        String description = trimToLength(extraction.description(), MAX_DESCRIPTION_LENGTH);
        if (isBlank(counterpartyName) && isBlank(cuitDni) && isBlank(email)) {
            throw new IllegalArgumentException("Falta identificar al cliente o proveedor");
        }
        List<LedgerLineItemExtraction> rows = normalizeLineItems(extraction.lineItems());
        BigDecimal sum = BigDecimal.ZERO;
        for (LedgerLineItemExtraction row : rows) {
            sum = sum.add(row.quantity().multiply(row.unitPrice()));
        }
        if (!rows.isEmpty() && sum.setScale(2, RoundingMode.HALF_UP).compareTo(amount) != 0) {
            throw new IllegalArgumentException("El total no coincide con los renglones extraidos");
        }
        return new LedgerExtraction(
                title, counterpartyName, cuitDni, email, phone, amount,
                extraction.issueDate(), extraction.dueDate(), description, rows);
    }

    private List<LedgerLineItemExtraction> normalizeLineItems(List<LedgerLineItemExtraction> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream()
                .filter(row -> row != null && !isBlank(row.description()))
                .map(row -> new LedgerLineItemExtraction(
                        trimToLength(row.description(), MAX_LINE_DESCRIPTION_LENGTH),
                        normalizePositiveQuantity(row.quantity()),
                        normalizePositiveMoney(row.unitPrice(), "El precio unitario")
                ))
                .toList();
    }

    private BigDecimal normalizePositiveMoney(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " debe ser positivo");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException(label + " excede el maximo permitido");
        }
        return normalized;
    }

    private BigDecimal normalizePositiveQuantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser positiva");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private Customer resolveOrCreateCustomer(LedgerExtraction extraction, Long tenantId) {
        Optional<Customer> match = Optional.empty();
        if (!isBlank(extraction.cuitDni())) {
            match = customerRepo.findFirstByTenant_IdAndCuitDniIgnoreCase(tenantId, extraction.cuitDni().trim());
        }
        if (match.isEmpty() && !isBlank(extraction.email())) {
            match = customerRepo.findFirstByTenant_IdAndEmailIgnoreCase(tenantId, extraction.email().trim());
        }
        if (match.isEmpty() && !isBlank(extraction.counterpartyName())) {
            match = customerRepo.findFirstByTenant_IdAndNameIgnoreCase(tenantId, extraction.counterpartyName().trim());
        }
        if (match.isPresent()) return match.get();

        Tenant tenant = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        Customer customer = new Customer();
        customer.setTenant(tenant);
        customer.setName(firstMeaningful(extraction.counterpartyName(), extraction.cuitDni(), extraction.email()));
        customer.setCuitDni(trimToNull(extraction.cuitDni()));
        customer.setEmail(trimToNull(extraction.email()));
        customer.setPhone(trimToNull(extraction.phone()));
        customer.setPaymentScore(100);
        customer.setCreatedAt(LocalDateTime.now());
        return customerRepo.save(customer);
    }

    private InvoiceCreateRequest toInvoiceRequest(LedgerExtraction extraction, Customer customer) {
        List<InvoiceLineItemRequest> lineItems = extraction.lineItems().stream()
                .filter(row -> row != null && !isBlank(row.description()))
                .map(row -> new InvoiceLineItemRequest(null, row.description().trim(), row.quantity(), row.unitPrice()))
                .toList();
        String title = !isBlank(extraction.titulo())
                ? extraction.titulo().trim()
                : "Factura - " + customer.getName();
        return new InvoiceCreateRequest(
                null, title, (long) lineItems.size(), extraction.issueDate(), extraction.dueDate(),
                extraction.dueDate(), null, extraction.description(), extraction.amount(), null,
                null, extraction.phone(), customer.getId(), lineItems
        );
    }

    private CostCreateRequest toCostRequest(LedgerExtraction extraction) {
        String supplier = firstMeaningful(extraction.counterpartyName(), extraction.cuitDni(), extraction.email());
        String reason = isBlank(extraction.description()) ? supplier : supplier + " - " + extraction.description().trim();
        if (reason.length() > 255) reason = reason.substring(0, 255);
        return new CostCreateRequest(
                extraction.issueDate() != null ? extraction.issueDate() : LocalDate.now(),
                extraction.amount(), reason, CostType.MATERIAL, PaymentFrequency.ONE_TIME
        );
    }

    private LedgerExtraction readExtraction(TelegramLedgerIngestion ingestion) {
        if (ingestion.getExtractionJson() == null) {
            throw new IllegalStateException("Ingestion has no extraction");
        }
        return objectMapper.convertValue(ingestion.getExtractionJson(), LedgerExtraction.class);
    }

    private LedgerIngestionResult resultFrom(TelegramLedgerIngestion ingestion, LedgerExtraction extraction,
                                             boolean alreadyCompleted) {
        LocalDate date = ingestion.getDirection() == LedgerDirection.GASTO
                ? (extraction.issueDate() != null ? extraction.issueDate() : LocalDate.now())
                : extraction.dueDate();
        return new LedgerIngestionResult(
                ingestion.getId(), ingestion.getDirection(), ingestion.getRecordType(), ingestion.getRecordId(),
                extraction.amount(), firstMeaningful(extraction.counterpartyName(), extraction.cuitDni(), extraction.email()),
                date, alreadyCompleted
        );
    }

    private String sanitizeFailure(String reason) {
        String safe = reason == null || reason.isBlank() ? "Error de procesamiento" : reason.replaceAll("[\\r\\n]+", " ");
        return safe.length() > 500 ? safe.substring(0, 500) : safe;
    }

    private String firstMeaningful(String... values) {
        for (String value : values) if (!isBlank(value)) return value.trim();
        return "Sin contraparte";
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String trimToLength(String value, int maxLength) {
        if (isBlank(value)) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
