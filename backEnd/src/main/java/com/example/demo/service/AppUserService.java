package com.example.demo.service;

import com.example.demo.dto.UserSummaryDto;
import com.example.demo.exceptions.UserAlreadyExistsException;
import com.example.demo.model.AppUser;
import com.example.demo.model.AppUserRole;
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

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.*;


@Service
public class AppUserService {
    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    Authentication auth;
    private static final long EXPIRATION_TIME = 86400000;
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
        try {
            AppUser foundUser = appUserRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            if (password == null || !passwordEncoder.matches(password, foundUser.getPassword())) {
                log.warn(
                        "Login password mismatch for username='{}', storedHashPrefix='{}'",
                        username,
                        passwordPrefix(foundUser.getPassword())
                );
                throw new BadCredentialsException("Invalid username or password");
            }

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

    private String passwordPrefix(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.length() < 4) {
            return "<none>";
        }
        return encodedPassword.substring(0, 4);
    }




    public AppUser registerUser(AppUser registration) {
        if (appUserRepository.existsByUsername(registration.getUsername())) {
            throw new UserAlreadyExistsException("Usuario ya existe");
        }

        AppUser user = new AppUser();
        user.setUsername(registration.getUsername());
        user.setPassword(passwordEncoder.encode(registration.getPassword()));
        user.setAppUserRole(registration.getAppUserRole() != null ? registration.getAppUserRole() : AppUserRole.GESTOR);
        AppUser creator = getCurrentUser();
        if (creator != null && creator.getTenant() != null) {
            user.setTenant(creator.getTenant());
        }

        return appUserRepository.save(user);
    }

    public AppUser getUserById(Long id) {
        return appUserRepository.findById(id).orElse(null);
    }

    public Optional<AppUser> findByUsername(String username){ return  appUserRepository.findByUsername(username); }


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
        return appUserRepository.findAll().stream()
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getAppUserRole().name()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<UserSummaryDto> getUsersForTenant(Long tenantId) {
        return appUserRepository.findAll().stream()
                .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(tenantId))
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getAppUserRole().name()))
                .collect(java.util.stream.Collectors.toList());
    }
}
