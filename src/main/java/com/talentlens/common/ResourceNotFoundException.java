package com.talentlens.common;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id " + id);
    }
}
