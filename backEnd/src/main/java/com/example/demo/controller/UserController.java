package com.example.demo.controller;

import com.example.demo.dto.ChangePasswordRequest;
import com.example.demo.dto.UserSummaryDto;
import com.example.demo.model.AppUser;
import com.example.demo.service.AppUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private AppUserService appUserService;


    @GetMapping
    public ResponseEntity<List<UserSummaryDto>> getAllUsers() {
        return ResponseEntity.ok(appUserService.getAllUsers());
    }

    @PostMapping("/registro")
  public ResponseEntity<?> registro(@RequestBody AppUser user) {
      try {
          AppUser regUser = appUserService.registerUser(user);
          return ResponseEntity.status(HttpStatus.CREATED).body(regUser);
      } catch (IllegalArgumentException e) {
          return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                  .body("Error: " + e.getMessage());
      } catch (Exception e) {
          return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                  .body("Error: " + e.getMessage());
      }
  }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AppUser user) {
        String username = user.getUsername() == null ? "" : user.getUsername().trim().toLowerCase(Locale.ROOT);
        log.info("Login attempt for username='{}'", username);
        try {
            Map<String, Object> response = appUserService.loginUser(username, user.getPassword());
            log.info("Login success for username='{}'", username);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.error("Login error for username='{}'", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login service temporarily unavailable"));
        } catch (RuntimeException e) {
            log.warn("Login failed for username='{}': {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password"));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            appUserService.changeOwnPassword(request.currentPassword(), request.newPassword());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @GetMapping("/{id}")
    public ResponseEntity<UserSummaryDto> getUser(@PathVariable Long id) {
        AppUser u = appUserService.getVisibleUserById(id);
        if (u == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new UserSummaryDto(u.getId(), u.getUsername(), u.getAppUserRole().name()));
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Controller is working!");
    }
    }
