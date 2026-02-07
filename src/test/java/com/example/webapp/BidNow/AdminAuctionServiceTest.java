package com.example.webapp.BidNow;

import com.example.webapp.BidNow.Dtos.NotificationEvent;
import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Category;
import com.example.webapp.BidNow.Entities.ReferralCode;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Enums.NotificationType;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.*;
import com.example.webapp.BidNow.Services.*;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class AdminAuctionServiceTest {
    @Mock private AuctionRepository auctionRepository;
    @Mock private UserActivityService userActivityService;
    @Mock private AuctionMessageRepository auctionMessageRepository;
    @Mock private BidRepository bidRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserEntityRepository userEntityRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private EmailService emailService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuctionService auctionService;

    /**
     * Testing endpoint called admin approves an auction
     * Happy path:
     *  - Auction status must be PENDING_APPROVAL
     *  - One DB call for finding the auction
     *  - After endpoint is finished auction must have its status approved
     *  - Auction must be saved ones
     *  - Notification must be created for it
     */
    @Test
    void approveRequest_HappyPath() {
        // arrange
        UserEntity owner = new UserEntity();
        owner.setId(10L);
        Auction auction = new Auction();
        auction.setId(1L);
        auction.setTitle("Test Auction");
        auction.setStatus(AuctionStatus.PENDING_APPROVAL);
        auction.setOwner(owner);


        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
        auctionService.approveAuction(1L);
        verify(auctionRepository, times(1)).findById(1L);
        ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
        verify(auctionRepository, times(1)).save(auctionCaptor.capture());
        assertEquals(AuctionStatus.ACTIVE, auctionCaptor.getValue().getStatus());

        // assert event published
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        NotificationEvent event = eventCaptor.getValue();
        assertEquals(10L, event.userId());
        assertEquals(NotificationType.AUCTION_APPROVED, event.type());
        assertTrue(event.metadataJson().contains("\"auctionId\":1"));
        assertTrue(event.metadataJson().contains("\"newStatus\":\"ACTIVE\""));
    }


    /**
     * Testing that approveAuction endpoint is throwing a ResourceNotFoundException
     * when a user sends an auctionId that doesn't exist.
     */
    @Test
    void approveAuction_AuctionNotFound_ThrowsResourceNotFound() {

        when(auctionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> auctionService.approveAuction(1L));

        verify(auctionRepository, times(1)).findById(1L);
        verify(auctionRepository, never()).save(any(Auction.class)); // must not save
        verify(eventPublisher, never()).publishEvent(any()); // must not create a notification
        verifyNoMoreInteractions(auctionRepository, eventPublisher);
    }

    /**
     * Testing that an exception will occur if the AuctionStatus is not PENDING_APPROVAL
     */
    @Test
    void approveAuction_NotPendingApproval_ThrowsRuntimeException() {

        Auction auction = new Auction();
        auction.setId(1L);
        auction.setStatus(AuctionStatus.ACTIVE); // or EXPIRED / CANCELLED

        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> auctionService.approveAuction(1L));
        assertTrue(ex.getMessage().toLowerCase().contains("pending approval"));

        verify(auctionRepository, times(1)).findById(1L);
        verify(auctionRepository, never()).save(any(Auction.class)); // must not save
        verify(eventPublisher, never()).publishEvent(any()); // must not create a notification
        verifyNoMoreInteractions(auctionRepository, eventPublisher);
    }

}
