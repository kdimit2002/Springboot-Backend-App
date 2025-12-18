package com.example.webapp.BidNow.Controllers;

import com.example.webapp.BidNow.Dtos.BidDto;
import com.example.webapp.BidNow.Dtos.BidEventDto;
import com.example.webapp.BidNow.Services.BidService;
import com.example.webapp.BidNow.helpers.UserEntityHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;


/**
 * Controller for bid actions on auctions.
 *
 * Base path: /api/bids
 * Provides: placing a bid on a specific auction.
 */
@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }


    /**
     * Place a bid on an auction.
     *
     * This Api is doing the necessary checks (Bid amount is
     * bigger than last bid amount or auction's starting amount, auction is active etc.)
     *
     * And then broadcasts bids across all active users.
     *
     * POST /api/auctions/{auctionId}/bids
     *
     * @param auctionId auction id
     * @param amount    bid amount, Big Decimal
     * @return BidEventDto describing the bid event, used by websocket updates
     */
    @PostMapping("/{auctionId}/placeBid")
    public ResponseEntity<BidEventDto> placeBid(
            @PathVariable Long auctionId,
            @RequestBody BigDecimal amount
            ) {

        String firebaseId = getUserFirebaseId();
        BidEventDto event = bidService.placeBid(auctionId,firebaseId,amount);
        return ResponseEntity.ok(event);

    }
}
