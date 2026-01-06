package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.NotificationEvent;
import com.example.webapp.BidNow.Entities.Notification;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Repositories.NotificationRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationAsyncService
 *
 * Creates Notification records asynchronously, so the main request flow is not delayed.
 *
 */
@Service
public class NotificationAsyncService {

    private final NotificationRepository notificationRepository;
    private final UserEntityRepository userEntityRepository;

    public NotificationAsyncService(NotificationRepository notificationRepository,
                                    UserEntityRepository userEntityRepository) {
        this.notificationRepository = notificationRepository;
        this.userEntityRepository = userEntityRepository;
    }

    @Async("notificationExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(NotificationEvent e) {

        // Optional dedupe: avoid inserting the same notification twice (same user/type/metadata).
        if (e.metadataJson() != null
                && notificationRepository.existsByUser_IdAndTypeAndMetadataJson(e.userId(), e.type(), e.metadataJson())) {
            return;
        }

        UserEntity userRef = userEntityRepository.getReferenceById(e.userId());

        Notification n = new Notification();
        n.setUser(userRef);
        n.setType(e.type());
        n.setTitle(e.title());
        n.setBody(e.body());
        n.setMetadataJson(e.metadataJson());
        n.setRead(false);

        notificationRepository.save(n);
    }
}
