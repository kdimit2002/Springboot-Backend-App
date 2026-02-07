package com.example.webapp.BidNow;
import com.example.webapp.BidNow.Dtos.ChatMessageRequest;
import com.example.webapp.BidNow.Dtos.ChatMessageResponse;
import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.AuctionMessage;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Repositories.AuctionMessageRepository;
import com.example.webapp.BidNow.Repositories.AuctionRepository;
import com.example.webapp.BidNow.Repositories.BidRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.example.webapp.BidNow.Services.AuctionChatService;
import com.example.webapp.BidNow.Services.UserActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionChatServiceTest {

    @Mock private UserEntityRepository userEntityRepository;
    @Mock private AuctionRepository auctionRepository;
    @Mock private BidRepository bidRepository;
    @Mock private AuctionMessageRepository auctionMessageRepository;
    @Mock private UserActivityService userActivityService;

    // αυτά υπάρχουν στο service σου, δεν τα ελέγχουμε εδώ
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Spy
    @InjectMocks
    private AuctionChatService auctionChatService; // <-- βάλε το πραγματικό όνομα service

    // Helper: exception assert
    private static void assertStatus(ResponseStatusException ex, HttpStatus status) {
        assertEquals(status, ex.getStatusCode());
    }

    /**
     * When user sends message request
     * with blank content -> response with error 400
     *
     */
    @Test
    void sendMessage_whenContentBlank_shouldThrowBadRequest() {

        ChatMessageRequest req = new ChatMessageRequest();
        req.setContent("   ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auctionChatService.sendMessage(1L, req));

        assertStatus(ex, HttpStatus.BAD_REQUEST);

        // Check that service method send a reason and that contains empty word
        assertNotNull(ex.getReason());
        assertTrue(ex.getReason().toLowerCase().contains("empty"));

        verifyNoMoreInteractions();
    }


    /**
     * When user sends message request
     * but the auction is expired -> response with error 400
     *
     */
    @Test
    void sendMessage_whenAuctionExpired_shouldThrowBadRequest() {
        ChatMessageRequest req = new ChatMessageRequest();
        req.setContent("hello");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // Mock sender user entity
        UserEntity sender = mock(UserEntity.class);
        // Only user id and firebase id is needed here
        when(sender.getId()).thenReturn(10L);
        when(userEntityRepository.findByFirebaseId("fb_sender")).thenReturn(Optional.of(sender));

        // Mock an expired auction to ensure that the service method throws exception
        Auction auction = mock(Auction.class);
        // The end date of the auction must be in the past
        when(auction.getEndDate()).thenReturn(LocalDateTime.now().minusSeconds(1));
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auctionChatService.sendMessage(1L, req));

        assertStatus(ex, HttpStatus.BAD_REQUEST);
        verifyNoMoreInteractions();
        SecurityContextHolder.clearContext();

    }

    /**
     * When user sends message request,
     * but he is not eligible for chatting -> response with error 403
     *
     */
    @Test
    void sendMessage_whenUserNotEligible_shouldThrowForbidden() {
        ChatMessageRequest req = new ChatMessageRequest();
        req.setContent("hello");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

// run test...



        // Mock sender user entity
        UserEntity sender = mock(UserEntity.class);

        // Only user id and firebase id is needed here
        when(sender.getId()).thenReturn(10L);
        when(sender.getFirebaseId()).thenReturn("fb_sender");
        when(userEntityRepository.findByFirebaseId("fb_sender")).thenReturn(Optional.of(sender));

        // Auction must be active now
        Auction auction = mock(Auction.class);
        when(auction.getEndDate()).thenReturn(LocalDateTime.now().plusDays(1));
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));

        // User didn't bid on the current auction
        when(bidRepository.existsByAuctionIdAndBidderId(1L, 10L)).thenReturn(false);
        // User has never created an auction
        when(auctionRepository.existsByOwner_Id(10L)).thenReturn(false);
        // User has never won an auction
        when(auctionRepository.existsByWinner_Id(10L)).thenReturn(false);

        // Then the service method must throw an exception
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> auctionChatService.sendMessage(1L, req));

        // With status forbidden (403)
        assertStatus(ex, HttpStatus.FORBIDDEN);
        verifyNoInteractions(auctionMessageRepository);
        SecurityContextHolder.clearContext();

    }

    /**
     * Happy path:
     * When user passes the methods requirements the message is trimmed stored in the database and
     * service method returns a dto and his remaining messages
     *
     */
    @Test
    void sendMessage_whenValid_shouldSaveMessage_andReturnDtoWithRemaining() {
        ChatMessageRequest req = new ChatMessageRequest();
        req.setContent("  hi there  ");

        // Mock a firebaseId for the user
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // real-ish sender fields for toResponse() method
        UserEntity sender = mock(UserEntity.class);
        when(sender.getId()).thenReturn(10L);
        when(sender.getFirebaseId()).thenReturn("fb_sender");
        when(sender.getUsername()).thenReturn("senderName");
        when(userEntityRepository.findByFirebaseId("fb_sender")).thenReturn(Optional.of(sender));

        Auction auction = mock(Auction.class);
        when(auction.getEndDate()).thenReturn(LocalDateTime.now().plusDays(1));
        when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));

        // Even with one of the three requirements as true the message should be sent
        // todo: this must be changed d in future because admin might disable user's eligibility for chat

        when(bidRepository.existsByAuctionIdAndBidderId(1L, 10L)).thenReturn(true);
        when(auctionRepository.existsByOwner_Id(10L)).thenReturn(false);
        when(auctionRepository.existsByWinner_Id(10L)).thenReturn(false);

        // rate limit counter (must be lower than 25)
        when(auctionMessageRepository.countByAuctionIdAndSenderId(1L, 10L)).thenReturn(3L);

        // For capturing argument returned when saving auction message
        ArgumentCaptor<AuctionMessage> msgCaptor = ArgumentCaptor.forClass(AuctionMessage.class);


        AuctionMessage saved = mock(AuctionMessage.class);
        when(saved.getId()).thenReturn(55L);
        when(saved.getContent()).thenReturn("hi there");
        when(saved.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(saved.getSender()).thenReturn(sender);

        when(auctionMessageRepository.save(msgCaptor.capture())).thenReturn(saved);

        ChatMessageResponse out = auctionChatService.sendMessage(1L, req);

        // assert save content trimmed and other parameters are correct
        AuctionMessage toSave = msgCaptor.getValue();
        assertEquals("hi there", toSave.getContent());
        assertEquals(sender, toSave.getSender());
        assertEquals(auction, toSave.getAuction());

        // assert response dto
        assertNotNull(out);
        assertEquals(55L, out.getId());
        assertEquals("hi there", out.getContent());
        assertEquals("fb_sender", out.getSenderFirebaseId());
        assertEquals("senderName", out.getSenderDisplayName());
        // remaining messages counter is incremented
        assertEquals(25 - (int)(3L + 1), out.getRemainingMessages()); // 21

        // Logging method is called
        verify(userActivityService).saveUserActivityAsync(Endpoint.SEND_MESSAGE, contains("sent message"));
        SecurityContextHolder.clearContext();

    }
}
