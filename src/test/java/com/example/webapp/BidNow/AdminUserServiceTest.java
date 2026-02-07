package com.example.webapp.BidNow;

import com.example.webapp.BidNow.Dtos.*;
import com.example.webapp.BidNow.Entities.Auction;
import com.example.webapp.BidNow.Entities.Bid;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Repositories.*;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.Services.AdminUserEntityService;
import com.example.webapp.BidNow.Services.AuctionChatService;
import com.example.webapp.BidNow.Services.UserActivityService;
import com.example.webapp.BidNow.Services.UserEntityService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testing:
 *  1) That the user is deleted, anonymized correctly.
 *  2) When deleted or disabled, user's actions are being handled correctly
 *
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    // Mocking the repositories to avoid real database calls

    // Used when anonymizing user
    @Mock
    private UserEntityRepository userEntityRepository;
    @Mock
    private FirebaseRetryService firebaseRetryService;


    // Used when calling disableUserAuctions method
    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionChatService auctionChatService;
    @Mock
    private ApplicationEventPublisher eventPublisher;


    // Inject our admin service for managing users to be tested
    @Spy
    @InjectMocks
    private AdminUserEntityService adminUserService; // <-- άλλαξε το όνομα στο δικό σου service

    // ---------- helpers ----------
    private static Role role(String name) {
        return new Role(name);
    }

    private static UserEntity user(long id, String firebaseId) {
        UserEntity u = new UserEntity();
        u.setId(id);
        u.setFirebaseId(firebaseId);
        u.setUsername("oldUser");
        u.setEmail("old@mail.com");
        u.setPhoneNumber("99999999");
        u.setBanned(false);
        u.setAnonymized(false);
        u.setEligibleForChat(true);
        u.setAllTimeRewardPoints(100L);
        u.setRoles(new HashSet<>(Set.of(role("Bidder"))));
        return u;
    }


    /**
     * Testing when admin deletes a user's account (anonymize)
     * <p>
     * User must be anonymized in DB and get deleted in Firebase
     *
     * @throws FirebaseAuthException
     */
    @Test
    void updateUser_whenAnonymized_true_shouldOverrideFields_andDeleteFromFirebase() throws FirebaseAuthException {
        // Current user's firebase id
        String firebaseId = "fb_123";
        // Creating current user
        UserEntity entity = user(42L, firebaseId);

        // location is not needed here so we mock it
        LocationDto location = mock(LocationDto.class);

        // mock dto that could be sent by admin
        UserEntityUpdateAdmin dto = new UserEntityUpdateAdmin(
                "newName",
                "new@mail.com",
                10L,
                Avatar.DEFAULT,
                "Bidder",
                false,
                true,   // isAnonymized = true (μπαίνει στο anonymize branch)
                false,
                location
        );

        // Replace finding user with the user entity we created
        when(userEntityRepository.findByFirebaseId(firebaseId)).thenReturn(Optional.of(entity));

        // Skip disableUserActions method
        doNothing().when(adminUserService).disableUserActions(any(UserEntity.class));

        // Call the method is going ti be tested
        AdminUserEntityDto result = adminUserService.updateUser(firebaseId, dto);

        // verify that the user is saved (as anonymized) and deleted from firebase
        verify(userEntityRepository).save(entity);
        verify(firebaseRetryService).deleteUserFromFirebase(firebaseId);

        // Check tha the user was anonymized correctly
        assertEquals("deleted_user_" + entity.getId(), entity.getUsername());
        assertEquals("anonymized_" + entity.getId() + "@example.com", entity.getEmail());
        assertTrue(entity.getAnonymized());
        assertEquals("deleted_firebase_" + entity.getId(), entity.getFirebaseId());
        assertEquals("XXXXXX", entity.getPhoneNumber());
        assertEquals(Avatar.DEFAULT, entity.getAvatar());
        assertFalse(entity.isEligibleForChat());

        // Check tha we are returning a response dto
        assertNotNull(result);
        // verify tha no other db call or service call was made
        verifyNoMoreInteractions();
    }


    // TESTING disableUserActionsMethod

    /**
     * Testing that when a user is disabled or anonymized
     * his bids are being disabled as well
     *
     */
    @Test
    void disableUserActions_shouldDisableAllUserBids() {
        // Mock a disabled user entity with id 1L (only user's id is needed for this test)
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(1L);

        Bid bid1 = mock(Bid.class);
        Bid bid2 = mock(Bid.class);
        when(bidRepository.findByBidderId(1L)).thenReturn(List.of(bid1, bid2));

        // Put empty data so that we only check for the disabling of the current user;s bids
        when(auctionRepository.findDistinctByBids_Bidder_Id(1L)).thenReturn(List.of());
        when(auctionRepository.findByOwnerId(1L)).thenReturn(List.of());

        // Calling the method that disables user's bods
        adminUserService.disableUserActions(user);

        // Check if the bids has been disabled
        verify(bid1).setEnabled(false);
        verify(bid2).setEnabled(false);
    }

    @Test
    void disableUserActions_shouldCancelActiveOwnedAuction_whenEndDateInFuture_andNotExpired() {
        // Mock the disabled user entity with id 1L (only user's id is needed for this test)
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(7L);

        // Assign with empty list - Not needed when checking if user's auctions where canceled
        when(bidRepository.findByBidderId(7L)).thenReturn(List.of());
        when(auctionRepository.findDistinctByBids_Bidder_Id(7L)).thenReturn(List.of());

        // Mock an active Auction (that will be assigned to the disabled user)
        Auction activeAuction = mock(Auction.class);
        when(activeAuction.getId()).thenReturn(200L);
        when(activeAuction.getEndDate()).thenReturn(LocalDateTime.now().plusDays(1));
        when(activeAuction.getStatus()).thenReturn(AuctionStatus.ACTIVE);

        when(auctionRepository.findByOwnerId(7L)).thenReturn(List.of(activeAuction));

        // Assign with empty list - Not needed when checking if user's auctions where canceled
        when(bidRepository.findDistinctBiddersByAuctionId(200L)).thenReturn(List.of());

        // Call method tha should disable user's auctions
        adminUserService.disableUserActions(user);

        // After calling the method user's auctions must be cancelled
        verify(activeAuction).setStatus(AuctionStatus.CANCELLED);
    }
}