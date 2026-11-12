package com.iitsm.application.security;

import com.iitsm.domain.model.User;

/** Creates the access token for an authenticated user; implemented in infrastructure (JWT). */
public interface TokenIssuer {

    AccessToken issue(User user);
}
