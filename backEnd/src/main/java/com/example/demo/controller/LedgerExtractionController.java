package com.example.demo.controller;

import com.example.demo.dto.LedgerExtraction;
import com.example.demo.service.LedgerExtractionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ledger")
public class LedgerExtractionController {
    private final LedgerExtractionService ledgerExtractionService;

    public LedgerExtractionController(LedgerExtractionService ledgerExtractionService) {
        this.ledgerExtractionService = ledgerExtractionService;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LedgerExtraction> extract(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption
    ) {
        return ResponseEntity.ok(ledgerExtractionService.extract(file, caption));
    }
}
