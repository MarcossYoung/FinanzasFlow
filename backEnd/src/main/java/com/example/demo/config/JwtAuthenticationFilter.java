package com.example.demo.config;

import com.example.demo.service.CustomUserDetailsService;
import com.example.demo.model.AppUser;
import com.example.demo.model.AppUserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Key;
import java.util.Base64;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService customUserDetailsService;

    @Value("${jwt.secret}")
    private String secretKey;

    @Autowired
    public JwtAuthenticationFilter(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            chain.doFilter(request, response);
            return;
        }
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            Claims claims = validateToken(jwt);
            String username = claims.getSubject();
            TenantContext.set(claims.get("tenantId", Long.class));

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                if (userDetails instanceof AppUser appUser
                        && appUser.getAppUserRole() != AppUserRole.SUPER_ADMIN
                        && (appUser.getTenant() == null || !appUser.getTenant().isActive())) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tenant inactive");
                    return;
                }

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (ExpiredJwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
            return;
        } catch (SignatureException | MalformedJwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token signature");
            return;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or malformed token");
            return;
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Claims validateToken(String token) {
        Key key = getSigningKey();
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        byte[] keyBytes = decodeSecret(secretKey);
        if (keyBytes.length < 32) {
            try {
                keyBytes = MessageDigest.getInstance("SHA-256")
                        .digest(secretKey.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to prepare JWT signing key", e);
            }
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
