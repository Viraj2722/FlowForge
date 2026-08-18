package com.flowforge.service.exception;

/**
 * Thrown by the service layer when a requested entity does not exist. The global
 * exception handler maps this to HTTP 404. Keeping it as a dedicated exception (rather
 * than returning null/Optional to controllers) centralises the 404 decision.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " with id " + id + " not found");
    }
}
