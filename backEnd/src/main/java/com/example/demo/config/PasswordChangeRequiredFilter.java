package com.example.demo.config;

import com.example.demo.model.AppUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/api/users/change-password";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AppUser appUser
                && appUser.isMustChangePassword()) {

            String requestPath = request.getServletPath();
            if (requestPath == null || requestPath.isBlank()) {
                requestPath = request.getRequestURI();
            }
            boolean isChangePasswordRequest =
                    "POST".equalsIgnoreCase(request.getMethod())
                            && CHANGE_PASSWORD_PATH.equals(requestPath);

            if (!isChangePasswordRequest) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                        "error", "PASSWORD_CHANGE_REQUIRED",
                        "message", "Debes cambiar tu contrasena antes de continuar."
                )));
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
