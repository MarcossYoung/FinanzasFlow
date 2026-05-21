package com.example.demo.controller;

import com.example.demo.dto.ChatRequest;
import com.example.demo.service.AiService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    // Integration 1: Finance Insight
    @PostMapping("/finance-insight")
    public ResponseEntity<Map<String, String>> financeInsight(@RequestBody Map<String, String> body) {
        String insight = aiService.generateFinanceInsight(body.get("from"), body.get("to"));
        return ResponseEntity.ok(Map.of("insight", insight));
    }

    // Integration 2: Parse invoice/cobranza description
    @PostMapping("/parse-order")
    public ResponseEntity<Map<String, Object>> parseOrder(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(aiService.parseOrderDescription(body.get("description")));
    }

    // Integration 3: Weekly Digest
    @GetMapping("/weekly-digest")
    public ResponseEntity<Map<String, Object>> weeklyDigest() {
        return ResponseEntity.ok(aiService.generateWeeklyDigest());
    }

    // Integration 4: Streaming Chat
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest req, Authentication auth) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String username = auth != null ? auth.getName() : "usuario";
        aiService.streamChat(req, username, emitter);
        return emitter;
    }

    // Integration 5: Parse Search Query
    @PostMapping("/parse-search")
    public ResponseEntity<Map<String, Object>> parseSearch(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(aiService.parseSearchQuery(body.get("query")));
    }
}
