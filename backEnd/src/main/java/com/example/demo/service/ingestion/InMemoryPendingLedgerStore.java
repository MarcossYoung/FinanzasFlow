package com.example.demo.service.ingestion;

import com.example.demo.dto.LedgerExtraction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(name = "telegram.staging.backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryPendingLedgerStore implements PendingLedgerStore {
    private static final long RESERVED = Long.MIN_VALUE;

    private final ConcurrentMap<Long, PendingLedger> byToken = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> bySource = new ConcurrentHashMap<>();
    private final Clock clock;
    private final long staleMinutes;

    @Autowired
    public InMemoryPendingLedgerStore(@Value("${telegram.ingestion.stale-minutes:10}") long staleMinutes) {
        this(staleMinutes, Clock.systemUTC());
    }

    InMemoryPendingLedgerStore(long staleMinutes, Clock clock) {
        this.staleMinutes = Math.max(1, staleMinutes);
        this.clock = clock;
    }

    @Override
    public Optional<Long> claim(String chatId, long messageId, Long tenantId) {
        if (chatId == null || chatId.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("Telegram source and tenant are required");
        }
        purgeExpired();

        String sourceKey = sourceKey(chatId, messageId);
        if (bySource.putIfAbsent(sourceKey, RESERVED) != null) {
            return Optional.empty();
        }

        try {
            while (true) {
                long token = nextToken();
                PendingLedger pending = new PendingLedger(token, chatId, messageId, tenantId, null, clock.instant());
                if (byToken.putIfAbsent(token, pending) == null) {
                    bySource.put(sourceKey, token);
                    return Optional.of(token);
                }
            }
        } catch (RuntimeException e) {
            bySource.remove(sourceKey, RESERVED);
            throw e;
        }
    }

    @Override
    public void attachExtraction(long token, LedgerExtraction extraction) {
        byToken.computeIfPresent(token, (ignored, pending) -> isExpired(pending) ? null : pending.withExtraction(extraction));
    }

    @Override
    public Optional<PendingLedger> get(long token) {
        PendingLedger pending = byToken.get(token);
        if (pending == null) return Optional.empty();
        if (isExpired(pending)) {
            remove(token);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    @Override
    public void remove(long token) {
        PendingLedger removed = byToken.remove(token);
        if (removed != null) {
            bySource.remove(sourceKey(removed.chatId(), removed.messageId()), token);
        }
    }

    @Scheduled(fixedDelayString = "${telegram.ingestion.sweep-ms:120000}")
    public void purgeExpired() {
        byToken.forEach((token, pending) -> {
            if (isExpired(pending)) {
                remove(token);
            }
        });
    }

    private long nextToken() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private boolean isExpired(PendingLedger pending) {
        return pending.createdAt().plus(Duration.ofMinutes(staleMinutes)).isBefore(clock.instant());
    }

    private String sourceKey(String chatId, long messageId) {
        return chatId + ":" + messageId;
    }
}
