package com.example.webapp.BidNow.Exceptions;

public class FirebaseConnectionException extends RuntimeException {
    public FirebaseConnectionException(String message, Exception ex) {
        super(message,ex);
    }
}
