package com.example.demo.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ApiFallbackController implements ErrorController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "app", "FinanzasFlow API",
                "status", "running",
                "login", "/api/users/login"
        ));
    }

    @RequestMapping("/error")
    public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusCode instanceof Integer value ? value : 500;
        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        return ResponseEntity.status(httpStatus).body(Map.of(
                "status", status,
                "error", httpStatus.getReasonPhrase(),
                "path", path != null ? path : "",
                "message", message != null ? message : ""
        ));
    }
}
