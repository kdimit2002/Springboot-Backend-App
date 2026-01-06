package com.example.webapp.BidNow.Exceptions;


public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(String errorMessage) {
        super(errorMessage);
    }
}
