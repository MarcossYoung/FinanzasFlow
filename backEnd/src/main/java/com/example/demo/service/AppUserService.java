package com.example.demo.service;

import com.example.demo.dto.UserSummaryDto;
import com.example.demo.exceptions.UserAlreadyExistsException;
import com.example.demo.model.AppUser;
import com.example.demo.model.AppUserRole;
import com.example.demo.model.Tenant;
import com.example.demo.repository.UserRepo;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class AppUserService {
    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    Authentication auth;
    private static final long EXPIRATION_TIME = 86400000;
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();

    @Value("${auth.login.max-failures:5}")
    private int maxLoginFailures;

    @Value("${auth.login.lock-minutes:15}")
    private long loginLockMinutes;

    @Value("${jwt.secret}")
    private String secretKey;
    @Autowired
    private final UserRepo appUserRepository;
    @Autowired
    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;

    @Autowired
    public AppUserService(AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          UserRepo appUserRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> loginUser(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        try {
            assertLoginAllowed(normalizedUsername);
            Optional<AppUser> userLookup = appUserRepository.findByUsername(normalizedUsername);
            if (userLookup.isEmpty()) {
                recordFailedLogin(normalizedUsername);
                throw new UsernameNotFoundException("User not found: " + normalizedUsername);
            }
            AppUser foundUser = userLookup.get();
            if (!isAllowedByTenantState(foundUser)) {
                throw new BadCredentialsException("Tenant is inactive");
            }

            if (password == null || !passwordEncoder.matches(password, foundUser.getPassword())) {
                recordFailedLogin(normalizedUsername);
                log.warn("Login password mismatch for username='{}'", normalizedUsername);
                throw new BadCredentialsException("Invalid username or password");
            }
            clearFailedLogin(normalizedUsername);

            // Set security context
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    foundUser,
                    null,
                    foundUser.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Generate token
            Long tenantId = foundUser.getTenant() != null ? foundUser.getTenant().getId() : null;
            String token = jwtTokenUtil.generateToken(foundUser.getUsername(), tenantId);

            // Build and return response
            Map<String, Object> response = new HashMap<>();
            response.put("username", foundUser.getUsername());
            response.put("id", foundUser.getId());
            response.put("role", foundUser.getAppUserRole());
            response.put("token", token);
            response.put("tenantId", tenantId);

            return response;

        } catch (BadCredentialsException | UsernameNotFoundException e) {
            throw new RuntimeException("Invalid username or password", e);
        } catch (Exception e) {
            throw new IllegalStateException("Login failed due to an internal authentication error", e);
        }
    }

    public AppUser registerUser(AppUser registration) {
        String normalizedUsername = normalizeUsername(registration.getUsername());
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new UserAlreadyExistsException("Usuario ya existe");
        }

        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(registration.getPassword()));
        AppUser creator = getCurrentUser();
        user.setAppUserRole(resolveCreatableRole(creator, registration.getAppUserRole()));
        if (creator != null && creator.getTenant() != null) {
            user.setTenant(creator.getTenant());
        }

        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser createTenantUser(Tenant tenant, String username, String rawPassword, AppUserRole role) {
        if (tenant == null || tenant.getId() == null) {
            throw new IllegalArgumentException("Tenant is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (role == AppUserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Tenant users cannot be SUPER_ADMIN");
        }
        String normalizedUsername = normalizeUsername(username);
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new UserAlreadyExistsException("Usuario ya existe");
        }
        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setAppUserRole(role != null ? role : AppUserRole.ADMIN);
        user.setTenant(tenant);
        return appUserRepository.save(user);
    }

    private AppUserRole resolveCreatableRole(AppUser creator, AppUserRole requestedRole) {
        AppUserRole role = requestedRole != null ? requestedRole : AppUserRole.GESTOR;
        if (role == AppUserRole.SUPER_ADMIN
                && (creator == null || creator.getAppUserRole() != AppUserRole.SUPER_ADMIN)) {
            throw new IllegalArgumentException("Only SUPER_ADMIN can create SUPER_ADMIN users");
        }
        return role;
    }

    public AppUser getUserById(Long id) {
        return appUserRepository.findById(id).orElse(null);
    }

    public AppUser getVisibleUserById(Long id) {
        AppUser currentUser = getCurrentUser();
        AppUser target = appUserRepository.findById(id).orElse(null);
        if (target == null || currentUser == null) return null;
        if (currentUser.getAppUserRole() == AppUserRole.SUPER_ADMIN) return target;
        if (currentUser.getTenant() == null || target.getTenant() == null) return null;
        return currentUser.getTenant().getId().equals(target.getTenant().getId()) ? target : null;
    }

    public Optional<AppUser> findByUsername(String username){ return  appUserRepository.findByUsername(normalizeUsername(username)); }


    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String username = authentication.getName();
        return appUserRepository.findByUsername(username).orElse(null);
    }

    public AppUser getFirstUser() {
        return appUserRepository.findAll().stream().findFirst().orElse(null);
    }

    public boolean delete(Long id) {
        Optional<AppUser> category = appUserRepository.findById(id);
        if (category.isPresent()) {
            appUserRepository.delete(category.get());
            return true;
        }
        throw new RuntimeException("Invoice with ID " + id + " not found");
    }

    public long countUsers() {
        return appUserRepository.count();
    }

    public List<UserSummaryDto> getAllUsers() {
        AppUser currentUser = getCurrentUser();
        if (currentUser != null && currentUser.getAppUserRole() != AppUserRole.SUPER_ADMIN
                && currentUser.getTenant() != null) {
            return getUsersForTenant(currentUser.getTenant().getId());
        }
        return appUserRepository.findAll().stream()
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getAppUserRole().name()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<UserSummaryDto> getUsersForTenant(Long tenantId) {
        return appUserRepository.findAll().stream()
                .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(tenantId))
                .filter(u -> u.getAppUserRole() != AppUserRole.SUPER_ADMIN)
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getAppUserRole().name()))
                .collect(java.util.stream.Collectors.toList());
    }

    private boolean isAllowedByTenantState(AppUser user) {
        if (user.getAppUserRole() == AppUserRole.SUPER_ADMIN) return true;
        return user.getTenant() != null && user.getTenant().isActive();
    }

    private void assertLoginAllowed(String username) {
        LoginAttempt attempt = loginAttempts.get(loginKey(username));
        if (attempt == null || attempt.lockedUntil() == null) return;
        if (Instant.now().isBefore(attempt.lockedUntil())) {
            throw new BadCredentialsException("Too many login attempts");
        }
        loginAttempts.remove(loginKey(username));
    }

    private void recordFailedLogin(String username) {
        String key = loginKey(username);
        LoginAttempt updated = loginAttempts.compute(key, (ignored, existing) -> {
            int failures = existing == null ? 1 : existing.failures() + 1;
            Instant lockedUntil = failures >= maxLoginFailures
                    ? Instant.now().plusSeconds(loginLockMinutes * 60)
                    : null;
            return new LoginAttempt(failures, lockedUntil);
        });
        if (updated != null && updated.lockedUntil() != null) {
            log.warn("Temporarily locked login for username='{}' after repeated failures", username);
        }
    }

    private void clearFailedLogin(String username) {
        loginAttempts.remove(loginKey(username));
    }

    private String loginKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeUsername(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private record LoginAttempt(int failures, Instant lockedUntil) {}
}
