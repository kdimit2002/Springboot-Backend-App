package com.example.webapp.BidNow.Controllers;


import com.example.webapp.BidNow.Dtos.NotificationDto;
import com.example.webapp.BidNow.Dtos.PageResponse;
import com.example.webapp.BidNow.Services.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * Controller for notification endpoints.
 *
 * Base path: /api/notifications
 * Provides: paginated notifications for the current user.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }


    /**
     * Get current user's notifications (paginated).
     *
     *
     * This API sends notifications to remind or inform user
     * about some of the app's actions (like auction ended, someone outbidded you etc.)
     *
     * GET /api/notifications/notifications?page=0&size=20
     *
     * @param page page number (default 0)
     * @param size page size (default 20)
     * @return Page of NotificationDto
     *
     */
    @GetMapping("/getNotifications")
    public ResponseEntity<Page<NotificationDto>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(notificationService.getMyNotifications(page, size));
    }



}
