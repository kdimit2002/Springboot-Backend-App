package com.example.webapp.BidNow.Exceptions;

/**
 * @Author Kendeas
 */
public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(String errorMessage) {
        super(errorMessage);
    }
}
