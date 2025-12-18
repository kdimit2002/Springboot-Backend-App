package com.example.webapp.BidNow.Controllers.Admin;

import com.example.webapp.BidNow.Dtos.AdminBroadcastNotificationRequest;
import com.example.webapp.BidNow.Services.AdminAnnouncementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * Admin controller for sending notifications/announcements.
 *
 * Base path: /api/admin/notifications
 */
@RestController
@RequestMapping("/api/admin/notifications")
//@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final AdminAnnouncementService adminAnnouncementService;

    public AdminNotificationController(AdminAnnouncementService adminAnnouncementService) {
        this.adminAnnouncementService = adminAnnouncementService;
    }

    /**
     * Broadcast a general announcement to all users.
     * This announcement is retrieved in users get notifications api.
     * And is a notification of GENERAL type
     *
     * POST /api/admin/notifications/broadcast
     *
     * @param request announcement payload
     * @return JSON with the created announcement id
     */
    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(@RequestBody AdminBroadcastNotificationRequest request) {
        Long id = adminAnnouncementService.broadcastGeneral(request);
        return ResponseEntity.ok(Map.of("announcementId", id));
    }
}
