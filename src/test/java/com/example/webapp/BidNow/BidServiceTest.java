package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Bid;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.Repositories.BidRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock private UserActivityService userActivityService;
    @Mock private BidRepository bidRepository;
    @Mock private AuctionRepository auctionRepository;
    @Mock private UserEntityRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BidService bidService;

    /**
     *
     */
    @Test
    void placeBid_shouldReject_whenAmountBelowMinimumForFirstBid() {
        // Initialization must
        Long auctionId = 1L;
        String bidderFirebaseId = "bidder-fb";

        UserEntity owner = new UserEntity();
        owner.setId(10L);
        owner.setFirebaseId("owner-fb");

        UserEntity bidder = new UserEntity();
        bidder.setId(20L);
        bidder.setFirebaseId(bidderFirebaseId);
        bidder.setUsername("alice");

        Auction auction = new Auction();
        auction.setId(auctionId);
        auction.setOwner(owner);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndDate(LocalDateTime.now().plusMinutes(5));
        auction.setStartingAmount(new BigDecimal("100"));
        auction.setMinBidIncrement(new BigDecimal("10"));
        auction.setBids(new ArrayList<>()); // first bid case

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(userRepository.findByFirebaseId(bidderFirebaseId)).thenReturn(Optional.of(bidder));

        BigDecimal tooSmall = new BigDecimal("109"); // minimum should be 110

        // Act
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> bidService.placeBid(auctionId, bidderFirebaseId, tooSmall)
        );

        // Assert
        assertTrue(ex.getMessage().contains("Bid must be at least"));
        verify(bidRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(eventPublisher);
    }


    @Test
    void placeBid_shouldReject_whenWithinLast1Second() {
        Long auctionId = 1L;

        UserEntity owner = new UserEntity();
        owner.setId(10L);
        owner.setFirebaseId("owner-fb");

        UserEntity bidder = new UserEntity();
        bidder.setId(20L);
        bidder.setFirebaseId("bidder-fb");
        bidder.setUsername("alice");

        Auction auction = new Auction();
        auction.setId(auctionId);
        auction.setOwner(owner);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndDate(LocalDateTime.now().plusSeconds(1)); // anti-sniping boundary
        auction.setStartingAmount(new BigDecimal("100"));
        auction.setMinBidIncrement(new BigDecimal("10"));
        auction.setBids(new ArrayList<>());

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(userRepository.findByFirebaseId(bidder.getFirebaseId())).thenReturn(Optional.of(bidder));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> bidService.placeBid(auctionId, bidder.getFirebaseId(), new BigDecimal("110"))
        );

        assertTrue(ex.getMessage().contains("last 1 second"));
        verify(bidRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(messagingTemplate);
    }


    @Test
    void placeBid_shouldReject_whenAmountBelowMinimumForNextBid() {
        Long auctionId = 1L;

        UserEntity owner = new UserEntity();
        owner.setId(10L);
        owner.setFirebaseId("owner-fb");

        UserEntity bidder = new UserEntity();
        bidder.setId(20L);
        bidder.setFirebaseId("bidder-fb");
        bidder.setUsername("alice");

        Auction auction = new Auction();
        auction.setId(auctionId);
        auction.setOwner(owner);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndDate(LocalDateTime.now().plusMinutes(5));
        auction.setStartingAmount(new BigDecimal("100"));
        auction.setMinBidIncrement(new BigDecimal("10"));

        // Make it "not empty" to go to the else branch
        auction.setBids(new ArrayList<>());
        auction.getBids().add(new Bid());

        Bid prevHighest = new Bid();
        prevHighest.setId(999L);
        prevHighest.setAuction(auction);
        prevHighest.setBidder(owner); // doesn't matter here
        prevHighest.setAmount(new BigDecimal("200"));

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(userRepository.findByFirebaseId(bidder.getFirebaseId())).thenReturn(Optional.of(bidder));
        when(bidRepository.findTopByAuction_IdAndIsEnabledTrueOrderByAmountDescCreatedAtDesc(auctionId))
                .thenReturn(Optional.of(prevHighest));

        BigDecimal tooSmall = new BigDecimal("209"); // min should be 210

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> bidService.placeBid(auctionId, bidder.getFirebaseId(), tooSmall)
        );

        assertTrue(ex.getMessage().contains("Bid must be at least"));
        verify(bidRepository).findTopByAuction_IdAndIsEnabledTrueOrderByAmountDescCreatedAtDesc(auctionId);
        verify(bidRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verifyNoInteractions(messagingTemplate);
    }


}
