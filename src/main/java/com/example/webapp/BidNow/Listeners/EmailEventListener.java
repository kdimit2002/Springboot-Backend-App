package com.example.webapp.BidNow.Listeners;

import com.example.webapp.BidNow.Dtos.EmailEvent;
import com.example.webapp.BidNow.Services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event-driven class
 * for asynchronously sending email to the appropriate person
 */
@Component
public class EmailEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private final EmailService emailService;

    public EmailEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Event will be handled even if no transaction is running.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailEvent(EmailEvent event) {
        try {
            emailService.sendSimpleEmailAsync(event.to(), event.subject(), event.body());
        } catch (Exception ex) {
            log.error("Failed to schedule email to={}, with subject={}", event.to(),event.subject(), ex);
        }
    }
}
