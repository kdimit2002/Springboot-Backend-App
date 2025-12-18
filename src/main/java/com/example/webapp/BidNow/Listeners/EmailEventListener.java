package com.example.webapp.BidNow.Listeners;

import com.example.webapp.BidNow.Dtos.EmailEvent;
import com.example.webapp.BidNow.Services.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailEventListener {

    private final EmailService emailService;

    public EmailEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailEvent(EmailEvent event) {
        // εδώ είμαστε 100% σίγουροι ότι έγινε commit
        emailService.sendSimpleEmailAsync(event.to(), event.subject(), event.body());
    }
}
