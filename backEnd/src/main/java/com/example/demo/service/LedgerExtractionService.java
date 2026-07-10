package com.example.demo.service;

import com.example.demo.config.TenantContext;
import com.example.demo.dto.LedgerExtraction;
import com.example.demo.dto.LedgerExtractionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Service
public class LedgerExtractionService {
    // Keep in sync with TelegramIngestionWorker.ALLOWED_MEDIA by convention; do not share code.
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp"
    );

    private final AiService aiService;
    private final TenantAiSpendService tenantAiSpendService;

    @Value("${ledger.extract.max-file-bytes:10485760}")
    private long maxFileBytes;

    public LedgerExtractionService(AiService aiService, TenantAiSpendService tenantAiSpendService) {
        this.aiService = aiService;
        this.tenantAiSpendService = tenantAiSpendService;
    }

    public LedgerExtraction extract(MultipartFile file, String caption) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es requerido.");
        }
        if (file.getSize() > maxFileBytes) {
            throw new IllegalArgumentException("El archivo es demasiado grande (maximo 10MB).");
        }
        String mediaType = file.getContentType();
        if (mediaType == null || !ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("Formato no soportado. Usa PDF, JPG, PNG o WEBP.");
        }

        Long tenantId = TenantContext.get();
        tenantAiSpendService.assertUnderLimit(tenantId);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo.", e);
        }

        LedgerExtractionResult result = aiService.parseLedgerMediaFromBytesWithUsage(bytes, mediaType, caption);
        tenantAiSpendService.recordSpend(tenantId, tenantAiSpendService.costCentsFor(result.usage()));
        return result.extraction();
    }
}
