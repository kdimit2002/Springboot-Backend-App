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


@Service
public class BidService {

    private final UserActivityService userActivityService;
    private final BidRepository bidRepository;
    private final AuctionRepository auctionRepository;
    private final UserEntityRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // για WebSocket STOMP
    private final ApplicationEventPublisher eventPublisher;



    public BidService(UserActivityService userActivityService, BidRepository bidRepository,
                      AuctionRepository auctionRepository,
                      UserEntityRepository userRepository,
                      SimpMessagingTemplate messagingTemplate, ApplicationEventPublisher eventPublisher) {
        this.userActivityService = userActivityService;
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
    }


    //ToDo: Bids must be enabled is true to retrieve them!!!


    @CacheEvict(cacheNames = "auctionById", key = "#auctionId")
    @Transactional
    public BidEventDto placeBid(Long auctionId, String firebaseId, BigDecimal amount) {

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new RuntimeException("Auction not found"));

        UserEntity bidder = userRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = auction.getEndDate();

        long secondsUntilEnd = Duration.between(now, end).getSeconds();

        if (auction.getOwner().getFirebaseId().equals(bidder.getFirebaseId()))
            throw new IllegalArgumentException("You cannot bid on your Auction");

        if (auction.getStatus() == AuctionStatus.EXPIRED || auction.getEndDate().isBefore(now))
            throw new IllegalArgumentException("The auction has finished");

        if (!auction.getStatus().equals(AuctionStatus.ACTIVE))
            throw new IllegalArgumentException("This auction is not active");

        // -------------------------------
        // Κανόνας minBidIncrement
        // -------------------------------
        BigDecimal minInc = auction.getMinBidIncrement();

        Bid prevHighest = null;
        BigDecimal minimumAllowed;
        if (auction.getBids().isEmpty()) {
            // Πρώτο bid → τουλάχιστον startingAmount + minInc
            minimumAllowed = auction.getStartingAmount().add(minInc);
        } else {
            // Επόμενα bids → τουλάχιστον highestBid + minInc
            prevHighest = bidRepository.findTopByAuction_IdAndIsEnabledTrueOrderByAmountDescCreatedAtDesc(auctionId).orElseThrow(()->new ResourceNotFoundException("Bid not found"));
            minimumAllowed = prevHighest.getAmount().add(minInc);
        }

        if (amount.compareTo(minimumAllowed) < 0) {
            throw new IllegalArgumentException(
                    "Bid must be at least " + minimumAllowed +
                            " (minimum increment " + minInc + ")"
            );
        }
        // -------------------------------

        if (secondsUntilEnd <= 0) {
            throw new IllegalArgumentException("The auction has finished");
        }

        if (secondsUntilEnd <= 2) {
            throw new IllegalArgumentException("You cannot place a bid in the last 2 seconds of the auction");
        }

        // guarantee at least 60s αν είμαστε στο τελευταίο λεπτό
        if (secondsUntilEnd < 60) {
            LocalDateTime newEndDate = now.plusSeconds(60);
            auction.setEndDate(newEndDate);
        }

        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setAmount(amount);

        Bid saved = bidRepository.save(bid);
        userActivityService.saveUserActivityAsync(
                Endpoint.PLACE_BID,
                "User: " + getUserFirebaseId() + " placed " +
                        bid.getAmount() + " on auction: " + auctionId
        );

        // OUTBID notify: αν υπήρχε προηγούμενος highest και ΔΕΝ είναι ο ίδιος user
        if (prevHighest == null || !prevHighest.getBidder().getId().equals(bidder.getId())) {

            if(prevHighest != null ) {
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
            // ✅ NEW_BID_ON_MY_AUCTION: ενημερώνουμε τον owner
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

        LocalDateTime newEndDate = auction.getEndDate();

        BidEventDto dto = new BidEventDto(
                saved.getId(),
                saved.getAmount(),
                saved.getBidder().getUsername(),
                LocalDateTime.now(),
                saved.getAuction().getId(),
                newEndDate
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        messagingTemplate.convertAndSend(
                                "/topic/auctions/" + auctionId,
                                dto
                        );
                    }
                }
        );

        return dto;
    }


}