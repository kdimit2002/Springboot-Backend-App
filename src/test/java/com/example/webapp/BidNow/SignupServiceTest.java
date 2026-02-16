package com.example.webapp.BidNow;


import com.example.webapp.BidNow.Dtos.AuthUserDto;
import com.example.webapp.BidNow.Dtos.LocationDto;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Enums.Region;
import com.example.webapp.BidNow.Exceptions.FirebaseUserDeleteException;
import com.example.webapp.BidNow.Repositories.ReferralCodeRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.Services.EmailService;
import com.example.webapp.BidNow.Services.SignupService;
import com.example.webapp.BidNow.Services.UserActivityService;
import com.example.webapp.BidNow.Services.UserEntityService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testing signup service method
 *
 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock private UserEntityService userEntityService;
    @Mock private UserEntityRepository userEntityRepository;
    @Mock private FirebaseRetryService firebaseRetryService;
    @Mock private ReferralCodeRepository referralCodeRepository;
    @Mock private UserActivityService userActivityService;


    @Spy
    @InjectMocks
    private SignupService authService;


    /**
     * Testing the case where a user sends an invalid role
     *
     * @throws Exception
     */
    @Test
    void saveUser_whenRoleInvalid_shouldThrowIllegalArgumentException() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");

        // Mock security context holder
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // Mock the location parameter
        LocationDto location = mock(LocationDto.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.saveUser(Avatar.DEFAULT, "Admin", location));

        assertTrue(ex.getMessage().toLowerCase().contains("role"));

        // Must not go to fetchUser function
        verify(authService, never()).fetchUser(anyString());
        verifyNoInteractions(userEntityRepository, userEntityService, firebaseRetryService);
        SecurityContextHolder.clearContext();

    }

    /**
     * Testing the case where a user sends an already existing username
     *
     * @throws Exception
     */
    @Test
    void saveUser_whenUsernameAlreadyUsed_shouldThrowIllegalArgumentException() throws Exception {

        // Mock a firebaseId for the user
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // Fetching user record from firebase (Mocked)
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getUid()).thenReturn("fb_sender");
        when(userRecord.getDisplayName()).thenReturn("john");
        when(userRecord.getEmail()).thenReturn("john@mail.com");
        when(userRecord.getPhoneNumber()).thenReturn("123");

        doReturn(userRecord).when(authService).fetchUser("fb_sender");

        when(userEntityRepository.existsByFirebaseId("fb_sender")).thenReturn(false);
        when(userEntityRepository.existsByUsername("john")).thenReturn(true);

        LocationDto location = mock(LocationDto.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.saveUser(Avatar.DEFAULT, "Bidder", location));

        assertTrue(ex.getMessage().toLowerCase().contains("username"));
        verify(userEntityRepository).existsByUsername("john");
        // flow never goes to assignRoles method
        verify(userEntityService, never()).assignRoles(anyString());
        SecurityContextHolder.clearContext();

    }

    /**
     * Testing validateAndSaveUser method when a user sends an empty display name - username
     *
     * @throws Exception
     */
    @Test
    void validateAndSaveUser_whenMissingDisplayName_shouldDeleteFirebaseUserAndThrow() throws FirebaseAuthException {

        // Mock a firebaseId for the user
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // Fetching user record from firebase but with null display name
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getUid()).thenReturn("fb_sender");
        when(userRecord.getPhoneNumber()).thenReturn("123");
        when(userRecord.getDisplayName()).thenReturn(null); // missing

        when(userEntityRepository.existsByFirebaseId("fb_sender")).thenReturn(false);

        // Mock a user entity (user entity)
        UserEntity user = mock(UserEntity.class);
        LocationDto location = mock(LocationDto.class);

        FirebaseUserDeleteException ex = assertThrows(FirebaseUserDeleteException.class,
                () -> authService.validateAndSaveUser(userRecord, user, location));

        assertTrue(ex.getMessage().toLowerCase().contains("display name"));
        // User must be deleted from firebase
        verify(firebaseRetryService).deleteUserFromFirebase("fb_sender");
        verify(userEntityRepository,never()).save(any()); // We must not save the user in the DB
        SecurityContextHolder.clearContext();

    }

    /**
     * Testing validateAndSaveUser method when a user created his account in firebase but not filled phone number
     *
     * @throws Exception
     */
    @Test
    void validateAndSaveUser_whenMissingPhone_shouldDeleteFirebaseUserAndThrow() throws FirebaseAuthException {

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");

        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // Fetching user record from firebase but with null phone number
        UserRecord userRecord = mock(UserRecord.class);
        when(userRecord.getUid()).thenReturn("fb_sender");
        when(userRecord.getPhoneNumber()).thenReturn(null); // missing
        when(userRecord.getDisplayName()).thenReturn("john");

        when(userEntityRepository.existsByFirebaseId("fb_sender")).thenReturn(false);

        UserEntity user = mock(UserEntity.class);
        LocationDto location = mock(LocationDto.class);

        FirebaseUserDeleteException ex = assertThrows(FirebaseUserDeleteException.class,
                () -> authService.validateAndSaveUser(userRecord, user, location));

        assertTrue(ex.getMessage().toLowerCase().contains("phone"));
        verify(firebaseRetryService).deleteUserFromFirebase("fb_sender");
        verify(userEntityRepository,never()).save(any()); // We must not save the user in the DB

        SecurityContextHolder.clearContext();

    }


    @Test
    void saveUserAfterSuccessfulValidations_shouldSetLocation_saveUser_andReturnAuthUserDto() {

        // Create "Fake" security context holder to call
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("fb_sender");
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        try {
            // given
            Role bidderRole = mock(Role.class);
            when(bidderRole.getName()).thenReturn("Bidder");

            UserEntity user = new UserEntity();
            user.setUsername("john");
            user.setFirebaseId("fb_sender");
            user.setRoles(new HashSet<>(Set.of(bidderRole)));

            LocationDto locationDto = new LocationDto(
                    "Cyprus", Region.NICOSIA, "Strovolos", "Street 1", "2000"
            );

            when(referralCodeRepository.existsByOwner_FirebaseId("fb_sender")).thenReturn(true);

            // when
            AuthUserDto out = authService.saveUserAfterSuccessfulValidations(user, locationDto);

            // then: location was set
            assertNotNull(user.getLocation());
            assertEquals("Cyprus", user.getLocation().getCountry());
            assertEquals(Region.NICOSIA, user.getLocation().getRegion());
            assertEquals("Strovolos", user.getLocation().getCity());
            assertEquals("Street 1", user.getLocation().getAddressLine());
            assertEquals("2000", user.getLocation().getPostalCode());

            // Check that user is saved in DB
            verify(userEntityRepository).save(user);

            // Check that returned dto is correct
            assertNotNull(out);
            assertEquals("john", out.username());
            assertEquals("Bidder", out.roleName());              // dominant role from roles
            assertTrue(out.isReferralCodeOwner());

            // We must check that a new Signup is logged
            verify(userActivityService).saveUserActivityAsync(eq(Endpoint.USER_SIGNUP), contains("has signed up"));

        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

