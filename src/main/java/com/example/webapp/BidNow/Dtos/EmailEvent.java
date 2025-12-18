package com.example.webapp.BidNow.Dtos;

/**
 * Email Request event
 *
 * This is used via transaction event listener
 * to manage an email event asynchronously via a
 * thread pool to send email after commit of DB requests
 */
public record EmailEvent(String to, String subject, String body) {}
