package com.example.demo.service;

import com.example.demo.exceptions.TelegramApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class TelegramService {
    public record TelegramFile(String path, Long size) {}
    public record InlineButton(String text, String callbackData) {}

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telegram.bot.token:}")
    private String token;

    @Value("${telegram.ingestion.max-download-bytes:10485760}")
    private long maxDownloadBytes;

    public TelegramService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean sendMessage(String chatId, String text) {
        try {
            sendMessageInternal(Map.of("chat_id", chatId, "text", text));
            return true;
        } catch (TelegramApiException e) {
            return false;
        }
    }

    public long sendMessageWithButtons(String chatId, String text, List<InlineButton> buttons) {
        List<Map<String, String>> row = buttons.stream()
                .map(button -> {
                    if (button.callbackData() == null || button.callbackData().getBytes(StandardCharsets.UTF_8).length > 64) {
                        throw new IllegalArgumentException("Telegram callback data exceeds 64 bytes");
                    }
                    return Map.of("text", button.text(), "callback_data", button.callbackData());
                })
                .toList();
        Map<String, Object> response = sendMessageInternal(Map.of(
                "chat_id", chatId,
                "text", text,
                "reply_markup", Map.of("inline_keyboard", List.of(row))
        ));
        Map<?, ?> result = asMap(response.get("result"));
        if (result == null || !(result.get("message_id") instanceof Number number)) {
            throw new TelegramApiException(TelegramApiException.Reason.API, "Telegram response omitted message_id");
        }
        return number.longValue();
    }

    public void answerCallbackQuery(String callbackId) {
        requireConfigured();
        try {
            Map<String, Object> response = restTemplate.postForObject(
                    apiUrl("answerCallbackQuery"),
                    Map.of("callback_query_id", callbackId),
                    Map.class
            );
            requireOk(response, TelegramApiException.Reason.API);
        } catch (TelegramApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramApiException(TelegramApiException.Reason.API, "Telegram callback acknowledgement failed", e);
        }
    }

    public TelegramFile getFile(String fileId) {
        requireConfigured();
        if (fileId == null || fileId.isBlank()) {
            throw new TelegramApiException(TelegramApiException.Reason.METADATA, "Telegram file_id is missing");
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(apiUrl("getFile"))
                    .queryParam("file_id", fileId)
                    .build().encode().toUriString();
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            requireOk(response, TelegramApiException.Reason.METADATA);
            Map<?, ?> result = asMap(response.get("result"));
            String path = result == null ? null : stringValue(result.get("file_path"));
            if (!isSafeTelegramPath(path)) {
                throw new TelegramApiException(TelegramApiException.Reason.METADATA, "Telegram returned an invalid file path");
            }
            Long size = result.get("file_size") instanceof Number number ? number.longValue() : null;
            if (size != null && size > maxDownloadBytes) {
                throw new TelegramApiException(TelegramApiException.Reason.TOO_LARGE, "Telegram file exceeds configured size limit");
            }
            return new TelegramFile(path, size);
        } catch (TelegramApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramApiException(TelegramApiException.Reason.METADATA, "Telegram file metadata failed", e);
        }
    }

    public String getFilePath(String fileId) {
        return getFile(fileId).path();
    }

    public byte[] downloadFile(String filePath) {
        requireConfigured();
        if (!isSafeTelegramPath(filePath)) {
            throw new TelegramApiException(TelegramApiException.Reason.DOWNLOAD, "Unsafe Telegram file path");
        }
        String url = "https://api.telegram.org/file/bot" + token + "/" + filePath;
        try {
            return restTemplate.execute(url, HttpMethod.GET, null, response -> {
                long declaredLength = response.getHeaders().getContentLength();
                if (declaredLength > maxDownloadBytes) {
                    throw new TelegramApiException(TelegramApiException.Reason.TOO_LARGE, "Telegram file exceeds configured size limit");
                }
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int read;
                    while ((read = response.getBody().read(buffer)) != -1) {
                        total += read;
                        if (total > maxDownloadBytes) {
                            throw new TelegramApiException(TelegramApiException.Reason.TOO_LARGE, "Telegram file exceeds configured size limit");
                        }
                        output.write(buffer, 0, read);
                    }
                    return output.toByteArray();
                }
            });
        } catch (TelegramApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramApiException(TelegramApiException.Reason.DOWNLOAD, "Telegram file download failed", e);
        }
    }

    private Map<String, Object> sendMessageInternal(Map<String, Object> payload) {
        requireConfigured();
        if (payload.get("chat_id") == null || stringValue(payload.get("chat_id")).isBlank()) {
            throw new TelegramApiException(TelegramApiException.Reason.API, "Telegram chat_id is missing");
        }
        try {
            Map<String, Object> response = restTemplate.postForObject(apiUrl("sendMessage"), payload, Map.class);
            requireOk(response, TelegramApiException.Reason.API);
            return response;
        } catch (TelegramApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TelegramApiException(TelegramApiException.Reason.API, "Telegram sendMessage failed", e);
        }
    }

    private void requireOk(Map<String, Object> response, TelegramApiException.Reason reason) {
        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            throw new TelegramApiException(reason, "Telegram API returned an unsuccessful response");
        }
    }

    private void requireConfigured() {
        if (token == null || token.isBlank()) {
            throw new TelegramApiException(TelegramApiException.Reason.NOT_CONFIGURED, "Telegram bot is not configured");
        }
    }

    private String apiUrl(String method) {
        return "https://api.telegram.org/bot" + token + "/" + method;
    }

    private boolean isSafeTelegramPath(String path) {
        return path != null && !path.isBlank() && !path.contains("..")
                && !path.contains(":") && !path.startsWith("/") && !path.startsWith("\\");
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
