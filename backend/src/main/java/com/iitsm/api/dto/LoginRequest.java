package com.iitsm.api.dto;

public record LoginRequest(String id, String password) {

    @Override
    public String toString() {
        return "LoginRequest[id=" + id + "]";
    }
}
