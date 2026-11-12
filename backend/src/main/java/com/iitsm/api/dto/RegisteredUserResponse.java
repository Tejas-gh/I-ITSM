package com.iitsm.api.dto;

import com.iitsm.domain.model.Role;

public record RegisteredUserResponse(String id, String name, String email, Role role) {
}
