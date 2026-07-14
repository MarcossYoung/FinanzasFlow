package com.example.demo.controller;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.service.LedgerExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerExtractionControllerTest {
    private final LedgerExtractionService service = mock(LedgerExtractionService.class);
    private final LedgerExtractionController controller = new LedgerExtractionController(service);

    @Test
    void returnsExtractionOnSuccess() {
        MultipartFile file = mock(MultipartFile.class);
        LedgerExtraction extraction = sampleExtraction();
        when(service.extract(file, "factura")).thenReturn(extraction);

        ResponseEntity<LedgerExtraction> response = controller.extract(file, "factura");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(extraction, response.getBody());
    }

    @Test
    void propagatesServiceExceptions() {
        MultipartFile file = mock(MultipartFile.class);
        IllegalArgumentException error = new IllegalArgumentException("bad");
        when(service.extract(file, null)).thenThrow(error);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> controller.extract(file, null));

        assertSame(error, thrown);
    }

    private LedgerExtraction sampleExtraction() {
        return new LedgerExtraction("Factura", "ACME", null, null, null,
                new BigDecimal("14500"), LocalDate.of(2026, 7, 10), null, "desc", List.of(),
                null, null, null, null);
    }
}
