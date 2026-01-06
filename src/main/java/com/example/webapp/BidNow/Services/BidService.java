package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.BidEventDto;
import com.example.webapp.BidNow.Dtos.NotificationEvent;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Bid;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Enums.NotificationType;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.Repositories.BidRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * Service for managing bids.
 *
 * Handles bid validation rules, optional auction end-date extension,
 * persistence, notifications (via events), and real-time updates (WebSocket).
 */
@Service
public class BidService {

    private final UserActivityService userActivityService;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserEntityRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket STOMP publisher
    private final ApplicationEventPublisher eventPublisher;

    public BidService(
            UserActivityService userActivityService,
            BidRepository bidRepository,
            AuctionRepository auctionRepository,
            UserEntityRepository userRepository,
            SimpMessagingTemplate messagingTemplate,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userActivityService = userActivityService;
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
    }

    //ToDo: Bids must be enabled to retrieve them!!!

    /**
     * Places a bid for a given auction.
     *
     * Notes:
     * - Evicts auction cache to force fresh data on next read.
     * - Uses multiple business rules (status, ownership, min increment, anti-last-second bids).
     * - Publishes notifications as events
     * - Sends a WebSocket update AFTER_COMMIT so clients see only committed data.
     */
    @CacheEvict(cacheNames = "auctionById", key = "#auctionId")
    @Transactional
    public BidEventDto placeBid(Long auctionId, String firebaseId, BigDecimal amount) {

        // Load auction and bidder
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new RuntimeException("Auction not found"));

        UserEntity bidder = userRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = auction.getEndDate();
        long secondsUntilEnd = Duration.between(now, end).getSeconds();

        // Prevent owner from bidding on their own auction.
        if (auction.getOwner().getFirebaseId().equals(bidder.getFirebaseId())) {
            throw new IllegalArgumentException("You cannot bid on your Auction");
        }

        // Reject bids on expired/finished auctions.
        if (auction.getStatus() == AuctionStatus.EXPIRED || auction.getEndDate().isBefore(now)) {
            throw new IllegalArgumentException("The auction has finished");
        }

        // Only ACTIVE auctions accept bids.
        if (!auction.getStatus().equals(AuctionStatus.ACTIVE)) {
            throw new IllegalArgumentException("This auction is not active");
        }

        // -------------------------------
        // Min increment rule:
        // first bid => startingAmount + minInc
        // next bid  => highestBid + minInc
        // -------------------------------
        BigDecimal minInc = auction.getMinBidIncrement();

        Bid prevHighest = null;
        BigDecimal minimumAllowed;

        if (auction.getBids().isEmpty()) {
            minimumAllowed = auction.getStartingAmount().add(minInc);
        } else {
            // Use repository to get the true highest bid (safer than relying on in-memory ordering).
            prevHighest = bidRepository
                    .findTopByAuction_IdAndIsEnabledTrueOrderByAmountDescCreatedAtDesc(auctionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bid not found"));

            minimumAllowed = prevHighest.getAmount().add(minInc);
        }

        if (amount.compareTo(minimumAllowed) < 0) {
            throw new IllegalArgumentException(
                    "Bid must be at least " + minimumAllowed +
                            " (minimum increment " + minInc + ")"
            );
        }

        // Extra safety check on time window.
        if (secondsUntilEnd <= 0) {
            throw new IllegalArgumentException("The auction has finished");
        }

        // Anti-sniping rule: disallow bids in the last 2 seconds.
        if (secondsUntilEnd <= 2) {
            throw new IllegalArgumentException("You cannot place a bid in the last 2 seconds of the auction");
        }

        // If bid arrives in the last minute, extend so there is always at least 60 seconds remaining.
        if (secondsUntilEnd < 60) {
            LocalDateTime newEndDate = now.plusSeconds(60);
            auction.setEndDate(newEndDate);
        }

        // Persist bid.
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setAmount(amount);

        Bid saved = bidRepository.save(bid);

        // logging
        userActivityService.saveUserActivityAsync(
                Endpoint.PLACE_BID,
                "User: " + getUserFirebaseId() + " placed " +
                        bid.getAmount() + " on auction: " + auctionId
        );

        // OUTBID notification:
        // notify previous highest bidder if there was one and it’s not the same user.
        if (prevHighest == null || !prevHighest.getBidder().getId().equals(bidder.getId())) {

            if (prevHighest != null) {
                Long outbidUserId = prevHighest.getBidder().getId();

                String metadata = "{\"auctionId\":" + auctionId
                        + ",\"type\":\"OUTBID\""
                        + ",\"newAmount\":\"" + saved.getAmount() + "\""
                        + ",\"oldAmount\":\"" + prevHighest.getAmount() + "\""
                        + "}";

                eventPublisher.publishEvent(new NotificationEvent(
                        outbidUserId,
                        NotificationType.OUTBID,
                        "You were outbid",
                        "User \"" + bidder.getUsername() + "\" outbid you on \"" + auction.getTitle() + "\". New bid: €" + saved.getAmount(),
                        metadata
                ));
            }

            // Notify auction owner about the new bid.
            Long ownerId = auction.getOwner().getId();

            String ownerMetadata = "{"
                    + "\"auctionId\":" + auctionId
                    + ",\"bidId\":" + saved.getId()
                    + ",\"amount\":\"" + saved.getAmount().toPlainString() + "\""
                    + ",\"bidderUsername\":\"" + bidder.getUsername() + "\""
                    + ",\"endDate\":\"" + auction.getEndDate() + "\""
                    + "}";

            eventPublisher.publishEvent(new NotificationEvent(
                    ownerId,
                    NotificationType.NEW_BID_ON_MY_AUCTION,
                    "New bid on your auction",
                    "User \"" + bidder.getUsername() + "\" placed a new bid (€" + saved.getAmount().toPlainString()
                            + ") on your auction \"" + auction.getTitle() + "\".",
                    ownerMetadata
            ));
        }

        // Include (possibly updated) endDate in the event payload.
        LocalDateTime newEndDate = auction.getEndDate();

        BidEventDto dto = new BidEventDto(
                saved.getId(),
                saved.getAmount(),
                saved.getBidder().getUsername(),
                LocalDateTime.now(),
                saved.getAuction().getId(),
                newEndDate
        );

        // WebSocket publish only after DB commit to all active users
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagingTemplate.convertAndSend(
                        "/topic/auctions/" + auctionId,
                        dto
                );
            }
        });

        return dto;
    }
}
