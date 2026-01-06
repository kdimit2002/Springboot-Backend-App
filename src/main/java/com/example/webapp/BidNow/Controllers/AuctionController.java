package com.example.webapp.BidNow.Controllers;

import com.example.webapp.BidNow.Dtos.AuctionCreateRequest;
import com.example.webapp.BidNow.Dtos.AuctionListItemDto;
import com.example.webapp.BidNow.Dtos.AuctionResponseDto;
import com.example.webapp.BidNow.Dtos.PageResponse;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Services.AuctionService;
import com.example.webapp.BidNow.Services.UserActivityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * Controller for auction endpoints.
 *
 * Base path: /auctions
 * Provides:
 * - List auctions
 * - Get auction details
 * - Create auction
 * - User-specific auction views.
 */
@RestController
@RequestMapping("/auctions")
public class AuctionController {

    // TODO: ADMIN EDIT AUCTIONS

    private final UserActivityService userActivityService;
    private final AuctionService auctionService;

    public AuctionController(UserActivityService userActivityService, AuctionService auctionService) {
        this.userActivityService = userActivityService;
        this.auctionService = auctionService;
    }

    /**
     * Get active by default, or expired last days auctions.
     *
     * GET /auctions
     * Query params (optional): sortBy, direction, categoryId, page, size, expiredLast7Days, search, region, country
     *
     * Notes:
     * - Logs the request parameters for analytics purposes.
     * - Uses a fixed page size of 30.
     *
     * @return PageResponse of AuctionListItemDto
     */
    @GetMapping
    public ResponseEntity<PageResponse<AuctionListItemDto>> getAuctions(
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false, defaultValue = "false") boolean expiredLast7Days,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String country
    ) {
        if (size != 30) {
            throw new RuntimeException("size should be 30");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if(auth != null && auth.isAuthenticated() &&    // If user is signed in
                !(auth instanceof AnonymousAuthenticationToken))
            userActivityService.saveUserActivityAsync(
                    Endpoint.GET_AUCTIONS,
                    "user=" + getUserFirebaseId() +
                            ", search=" + search +
                            ", categoryId=" + categoryId +
                            ", region=" + region +
                            ", country=" + country +
                            ", sortBy=" + sortBy +
                            ", direction=" + direction +
                            ", page=" + page +
                            ", size=" + size +
                            ", expiredLast7Days=" + expiredLast7Days
            );
        else
            userActivityService.saveUserActivityAsync( // Anonymous user
                    Endpoint.GET_AUCTIONS,
                    "Anonymous user"+
                            ", search=" + search +
                            ", categoryId=" + categoryId +
                            ", region=" + region +
                            ", country=" + country +
                            ", sortBy=" + sortBy +
                            ", direction=" + direction +
                            ", page=" + page +
                            ", size=" + size +
                            ", expiredLast7Days=" + expiredLast7Days);


        Page<AuctionListItemDto> pageResult;
        if (expiredLast7Days) {
            pageResult = auctionService.getExpiredAuctionsLastDays(
                    7, sortBy, direction, categoryId, page, size, region, country
            );
        } else {
            pageResult = auctionService.getActiveAuctions(
                    sortBy, direction, categoryId, page, size, search, region, country
            );
        }

        PageResponse<AuctionListItemDto> response = toPageResponse(pageResult);
        return ResponseEntity.ok(response);
    }


    /**
     * Get full auction details by id.
     *
     * GET /auctions/{id}
     *
     * @param id auction id
     * @return AuctionResponseDto with full auction information
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponseDto> getAuctionById(@PathVariable Long id) {
        userActivityService.saveUserActivityAsync(
                Endpoint.GET_AUCTION,
                "User: " + getUserFirebaseId() + ", AuctionId: " + id
        );
        AuctionResponseDto dto = auctionService.getAuctionById(id);
        return ResponseEntity.ok(dto);
    }

    /**
     * Create a new auction.
     *
     * POST /auctions
     * Allowed roles: AUCTIONEER, ADMIN
     *
     * @param request auction creation payload
     * @return created auction as AuctionResponseDto
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('AUCTIONEER','ADMIN')")
    public ResponseEntity<AuctionResponseDto> createAuction(
            @Valid @RequestBody AuctionCreateRequest request) {
        AuctionResponseDto created = auctionService.createAuction(request);
        return ResponseEntity.ok(created);
    }

    /**
     * Get auctions where the current user has placed bids (active bids view).
     *
     * GET /auctions/my-bids
     * Allowed roles: BIDDER, AUCTIONEER, ADMIN
     *
     * @param page page number (default 0)
     * @param size page size (default 30)
     * @param sortBy optional sort field
     * @param direction optional sort direction
     * @return PageResponse of auctions the user has bid on
     */
    @GetMapping("/my-bids")
    @PreAuthorize("hasAnyRole('BIDDER','AUCTIONEER','ADMIN')")
    public ResponseEntity<PageResponse<AuctionListItemDto>> getMyActiveBidAuctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction
    ) {
        Page<AuctionListItemDto> pageResult =
                auctionService.getMyActiveBidAuctions(page, size, sortBy, direction);

        PageResponse<AuctionListItemDto> response = toPageResponse(pageResult);
        return ResponseEntity.ok(response);
    }

    /**
     * Get auctions that the current user has won.
     *
     * GET /auctions/my-wins
     * Allowed roles: BIDDER, AUCTIONEER, ADMIN
     *
     * @param page page number (default 0)
     * @param size page size (default 30)
     * @param sortBy optional sort field
     * @param direction optional sort direction
     * @return PageResponse of auctions won by the user
     */
    @GetMapping("/my-wins")
    @PreAuthorize("hasAnyRole('BIDDER','AUCTIONEER','ADMIN')")
    public ResponseEntity<PageResponse<AuctionListItemDto>> getMyWonAuctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction
    ) {
        Page<AuctionListItemDto> pageResult =
                auctionService.getMyWonAuctions(page, size, sortBy, direction);

        PageResponse<AuctionListItemDto> response = toPageResponse(pageResult);
        return ResponseEntity.ok(response);
    }

    /**
     * Get auctions created by the current user that are still pending approval.
     *
     * GET /auctions/my-pending
     * Allowed roles: AUCTIONEER, ADMIN
     *
     * @param page page number (default 0)
     * @param size page size (default 30)
     * @param sortBy optional sort field
     * @param direction optional sort direction
     * @return PageResponse of pending auctions for the user
     */
    //ToDo: Join this with myAuctions endpoint
    @GetMapping("/my-pending")
    @PreAuthorize("hasAnyRole('AUCTIONEER','ADMIN')")
    public ResponseEntity<PageResponse<AuctionListItemDto>> getMyPendingAuctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction
    ) {
        Page<AuctionListItemDto> pageResult =
                auctionService.getMyPendingAuctions(page, size, sortBy, direction);

        PageResponse<AuctionListItemDto> response = toPageResponse(pageResult);
        return ResponseEntity.ok(response);
    }


    // ---------------------------------------------------------------------
    // Helper: μετατροπή Page<T> → PageResponse<T>
    // ---------------------------------------------------------------------
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
     * Get auctions created by the current user.
     * User can see his past or current auctions
     * todo: Cancelled / Expired auctions images must be deleted for R2 after a while
     *
     * GET /auctions/my
     * Allowed roles: AUCTIONEER, ADMIN
     *
     * statusGroup examples:
     * - ACTIVE
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
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('AUCTIONEER','ADMIN')")
    public ResponseEntity<PageResponse<AuctionListItemDto>> getMyAuctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false, defaultValue = "ACTIVE") String statusGroup
    ) {

        Page<AuctionListItemDto> pageResult =
                auctionService.getMyAuctions(page, size, sortBy, direction, statusGroup);

        PageResponse<AuctionListItemDto> response = toPageResponse(pageResult);
        return ResponseEntity.ok(response);
    }

}