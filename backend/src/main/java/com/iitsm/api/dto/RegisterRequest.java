package com.iitsm.api.dto;

/** Self-registration body. It has no role field: every registration creates an EMPLOYEE. */
public record RegisterRequest(String id, String name, String email, String department, String password) {

    @Override
    public String toString() {
        return "RegisterRequest[id=" + id + ", name=" + name + ", email=" + email + ", department=" + department + "]";
    }
}
