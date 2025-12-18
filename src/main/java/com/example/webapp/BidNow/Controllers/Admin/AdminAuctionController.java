package com.example.webapp.BidNow.Controllers.Admin;

import com.example.webapp.BidNow.Dtos.AuctionAdminUpdateRequest;
import com.example.webapp.BidNow.Dtos.AuctionListItemDto;
import com.example.webapp.BidNow.Dtos.AuctionResponseDto;
import com.example.webapp.BidNow.Dtos.PageResponse;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Services.AuctionService;
import com.example.webapp.BidNow.Services.UserActivityService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *
 * Admin-only REST controller for managing auctions.
 *
 * Responsibilities:
 * - Approve auction publication requests
 * - Cancel auction publication requests
 * - Retrieve pending auctions for review
 * - Perform admin edits on existing auctions
 *
 */
@RestController
@RequestMapping("/api/admin/auctions")
public class AdminAuctionController {

    private final UserActivityService userActivityService;
    private final AuctionService auctionService;

    public AdminAuctionController(UserActivityService userActivityService, AuctionService auctionService) {
        this.userActivityService = userActivityService;
        this.auctionService = auctionService;
    }

    /**
     * Auction publication, approval is handled only by admin
     * @param id the auction identifier to approve auction
     * @return 204 No Content when the action is completed successfully
     */
    @PatchMapping("/{id}/approve")
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveAuction(@PathVariable Long id) {
        userActivityService.saveUserActivityAsync(Endpoint.APPROVE,"Admin approved auction with id: "+ id);
        auctionService.approveAuction(id);
        return ResponseEntity.noContent().build();
    }
    /**
     * Auction publication, cancel is handled only by admin
     * @param id the auction identifier to cancel auction
     * @return 204 No Content when the action is completed successfully
     * */
    @PatchMapping("/{id}/cancel")
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelAuction(@PathVariable Long id) {
        userActivityService.saveUserActivityAsync(Endpoint.CANCEL_AUCTION,"Admin didn't approve auction with id: "+ id);
        auctionService.cancelAuction(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint for admin to inspect an auction creation request.
     *
     * @return 200 OK with a list of pending auctions as {@link AuctionResponseDto}
     * */
    @GetMapping("/pending")
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuctionResponseDto>> getPendingAuctions() {
        return ResponseEntity.ok(auctionService.getPendingAuctions());
    }

    /**
     * Endpoint for admin to inspect Expired and Cancelled Auctions
     *
     * todo: Cancelled / Expired auctions images must be deleted for R2 after a while
     *
     * GET /auctions/my
     * Allowed roles: ADMIN
     *
     * statusGroup expected values:
     * - PENDING_APPROVAL
     * - EXPIRED
     * - CANCELLED
     *
     * @param page page number (default 0)
     * @param size page size (default 30)
     * @param sortBy optional sort field
     * @param direction optional sort direction
     * @param statusGroup group for the user's auctions (default ACTIVE)
     * @return PageResponse of user's own auctions
     */
    @GetMapping("/adminGetNonActiveAuctions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<AuctionListItemDto>> getMyAuctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false, defaultValue = "EXPIRED") String statusGroup
    ) {

        Page<AuctionListItemDto> pageResult =
                auctionService.adminGetNonActiveAuctions(page, size, sortBy, direction, statusGroup);

        PageResponse<AuctionListItemDto> response = toPageResponse(pageResult);
        return ResponseEntity.ok(response);
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /**
     * Admin can edit auctions
     *
     * @param id      the auction identifier to update
     * @param request admin update payload (fields allowed to be edited by admin)
     * @return 200 OK with the updated auction representation
     *
     */
    @PatchMapping("/{id}")
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuctionResponseDto> editAuction(
            @PathVariable Long id,
            @RequestBody AuctionAdminUpdateRequest request
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("AUTH in editAuction: " + auth);
        AuctionResponseDto updated = auctionService.adminEditAuction(id, request);
        return ResponseEntity.ok(updated);
    }

}
