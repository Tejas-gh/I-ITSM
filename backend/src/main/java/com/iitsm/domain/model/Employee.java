package com.iitsm.domain.model;

public final class Employee extends User {

    private final String department;

    public Employee(String id, String name, String email, String department) {
        super(id, name, email, Role.EMPLOYEE);
        this.department = requireNonBlank(department, "department");
    }

    public String getDepartment() {
        return department;
    }
}
