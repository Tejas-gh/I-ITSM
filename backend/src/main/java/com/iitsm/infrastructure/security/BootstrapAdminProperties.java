package com.iitsm.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First-administrator settings ({@code iitsm.bootstrap.admin.*}), normally supplied through
 * {@code BOOTSTRAP_ADMIN_ID}, {@code _NAME}, {@code _EMAIL} and {@code _PASSWORD}. There is no default password.
 */
@ConfigurationProperties(prefix = "iitsm.bootstrap.admin")
public record BootstrapAdminProperties(String id, String name, String email, String password) {

    @Override
    public String toString() {
        return "BootstrapAdminProperties[id=" + id + ", name=" + name + ", email=" + email + "]";
    }
}
