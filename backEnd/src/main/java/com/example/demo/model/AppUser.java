package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Entity
@Getter
@Setter
@Table(name = "usuarios")
public class AppUser implements UserDetails {

    @Id
    @SequenceGenerator(name = "user_sequence", sequenceName = "user_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String username;
    private String password;
    @Enumerated(EnumType.STRING)
    private AppUserRole appUserRole;
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;



    public AppUser( String username, String password, AppUserRole appUserRole,String phoneNumber) {
        this.username = username;
        this.password = password;
        this.appUserRole = appUserRole;
        this.phoneNumber = phoneNumber;
    }


    public AppUser() {

    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        SimpleGrantedAuthority grantedAuthority = new SimpleGrantedAuthority(appUserRole.name());

        return Collections.singletonList(grantedAuthority);
    }


    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }


    @Override
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public AppUserRole getAppUserRole() {
        return appUserRole;
    }

    public void setAppUserRole(AppUserRole appUserRole) {
        this.appUserRole = appUserRole;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return appUserRole == AppUserRole.SUPER_ADMIN
                || (tenant != null && tenant.isActive());
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "AppUser{" +
                "id=" + id +
                ", userName='" + username + '\'' +
                ", appUserRole=" + appUserRole +
                '}';
    }


}

