package com.example.demo.dto;

import java.util.List;
import java.util.Map;

public record ChatRequest(
        String message,
        String page,
        List<Map<String, String>> history
) {}
