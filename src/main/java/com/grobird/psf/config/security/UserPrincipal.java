package com.grobird.psf.config.security;

import java.util.Collection;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {

    @Getter
    private final Long userId;

    @Getter
    private final Long tenantId;

    private final String email;

    @Getter
    private final String role;

    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(
            Long userId,
            Long tenantId,
            String email,
            String role,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.email = email;
        this.role = role;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // JWT-based auth
    }

    @Override
    public String getUsername() {
        return email;
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
        return true;
    }
}
