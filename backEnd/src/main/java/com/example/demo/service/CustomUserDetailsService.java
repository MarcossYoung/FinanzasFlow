package com.example.demo.service;

import com.example.demo.model.AppUser;
import com.example.demo.repository.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? username : username.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> optionalAppUser = userRepository.findByUsername(normalized);
        if (optionalAppUser.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + normalized);
        }

        return optionalAppUser.get();
    }
}
