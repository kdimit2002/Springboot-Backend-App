package com.example.webapp.BidNow.Listeners;

import com.example.webapp.BidNow.Dtos.NotificationEvent;
import com.example.webapp.BidNow.Services.NotificationAsyncService;
import com.example.webapp.BidNow.Services.UserEntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event-driven class
 * for asynchronously sending notifications to the appropriate user
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private final NotificationAsyncService notificationAsyncService;

    public NotificationEventListener(NotificationAsyncService notificationAsyncService) {
        this.notificationAsyncService = notificationAsyncService;
    }

    /**
     * Event will be handled even if no transaction is running.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationEvent(NotificationEvent event) {
        log.info("NotificationEvent received AFTER_COMMIT for userId={}, type={}",
                event.userId(), event.type());
        try {
            notificationAsyncService.createNotification(event);
        } catch (Exception ex) {
            log.error("Failed to schedule notification for userId={}, type={}", event.userId(), event.type(), ex);
        }
    }
}
