package com.carelink.identity.domain.exception;

public class TenantAlreadyExistsException extends RuntimeException {
    public TenantAlreadyExistsException(String message) { super(message); }
}
