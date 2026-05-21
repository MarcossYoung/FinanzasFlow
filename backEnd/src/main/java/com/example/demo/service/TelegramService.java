package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TelegramService {
    private final RestTemplate restTemplate;

    @Value("${telegram.bot.token}")
    private String token;

    public TelegramService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean sendMessage(String chatId, String text) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) return false;
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        try {
            restTemplate.postForObject(url, Map.of("chat_id", chatId, "text", text), String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
