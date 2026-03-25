package pl.piomin.services.employee.model;

public enum ErrorCode {
    EMPLOYEE_NOT_FOUND("EMP-001"),
    INVALID_INPUT("EMP-002"),
    INTERNAL_ERROR("EMP-500");

    private final String code;

    ErrorCode(String code) { this.code = code; }

    public String getCode() { return code; }
}
