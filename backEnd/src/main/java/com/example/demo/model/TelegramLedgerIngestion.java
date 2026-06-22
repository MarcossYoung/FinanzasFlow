package com.example.demo.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "telegram_ledger_ingestions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_telegram_ingestion_source", columnNames = {"chat_id", "message_id"})
})
public class TelegramLedgerIngestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private String chatId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "callback_message_id")
    private Long callbackMessageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TelegramIngestionStatus status;

    @Enumerated(EnumType.STRING)
    private LedgerDirection direction;

    @Type(JsonBinaryType.class)
    @Column(name = "extraction_json", columnDefinition = "jsonb")
    private Map<String, Object> extractionJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type")
    private LedgerRecordType recordType;

    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
