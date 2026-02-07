package com.example.webapp.BidNow;


import com.example.webapp.BidNow.Dtos.AuthUserDto;
import com.example.webapp.BidNow.Dtos.LocationDto;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Exceptions.FirebaseUserDeleteException;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.Services.EmailService;
import com.example.webapp.BidNow.Services.SignupService;
import com.example.webapp.BidNow.Services.UserEntityService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
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
    @Mock private EmailService emailService;


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
    }

    /**
     * Testing validateAndSaveUser method when a user sends an empty display name - username
     *
     * @throws Exception
     */
    @Test
    void validateAndSaveUser_whenMissingDisplayName_shouldDeleteFirebaseUserAndThrow() throws FirebaseAuthException {

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
    }

    /**
     * Testing validateAndSaveUser method when a user created his account in firebase but not filled phone number
     *
     * @throws Exception
     */
    @Test
    void validateAndSaveUser_whenMissingPhone_shouldDeleteFirebaseUserAndThrow() throws FirebaseAuthException {

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

    }



    //todo check again
    /**
     * Happy path:
     * returns response dto, updates claims in firebase, saves user to DB
     * @throws FirebaseAuthException
     * @throws IOException
     */
    @Test
    void saveUser_whenValid_shouldReturnAuthDto_andAfterCommitSetsClaims() throws FirebaseAuthException, IOException {
        // fake transaction sync
        TransactionSynchronizationManager.initSynchronization();
        try {
            // Fetching user record from firebase but with correct parameters (Mocked)
            UserRecord userRecord = mock(UserRecord.class);
            when(userRecord.getUid()).thenReturn("fb_sender");
            when(userRecord.getDisplayName()).thenReturn("john");
            when(userRecord.getEmail()).thenReturn("john@mail.com");
            when(userRecord.getPhoneNumber()).thenReturn("123");

            // Mocking fetch User method
            doReturn(userRecord).when(authService).fetchUser("fb_sender");

            // no duplicates
            when(userEntityRepository.existsByFirebaseId("fb_sender")).thenReturn(false);
            when(userEntityRepository.existsByUsername("john")).thenReturn(false);
            when(userEntityRepository.existsByEmail("john@mail.com")).thenReturn(false);
            when(userEntityRepository.existsByPhoneNumber("123")).thenReturn(false);

            // roles
            Role r1 = mock(Role.class); when(r1.getName()).thenReturn("Auctioneer");
            Role r2 = mock(Role.class); when(r2.getName()).thenReturn("Bidder");
            Set<Role> roles = new HashSet<>(Set.of(r1, r2));
            when(userEntityService.assignRoles("Auctioneer")).thenReturn(roles);

            LocationDto location = mock(LocationDto.class);

            // Δεν θέλουμε να μπούμε μέσα στο saveUserAfterSuccessfulValidations κλπ.
            AuthUserDto expected = mock(AuthUserDto.class);
            doReturn(expected).when(authService).validateAndSaveUser(any(UserRecord.class), any(UserEntity.class), eq(location));

            AuthUserDto out = authService.saveUser(Avatar.DEFAULT, "Auctioneer", location);
            assertSame(expected, out);

            // Πρέπει να έχει registered ένα synchronization
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertFalse(syncs.isEmpty());

            // “τρέχουμε” afterCommit χειροκίνητα για να δούμε ότι πάει να βάλει claims
            syncs.forEach(TransactionSynchronization::afterCommit);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);

            verify(firebaseRetryService).setFirebaseClaims(eq("fb_sender"), mapCaptor.capture());

            Object rolesObj = mapCaptor.getValue().get("roles");
            assertNotNull(rolesObj);
            assertTrue(rolesObj instanceof List<?>);

            @SuppressWarnings("unchecked")
            List<String> roleList = (List<String>) rolesObj;

            // Μην βασιστείς σε σειρά (HashSet) — έλεγξε περιεχόμενο
            assertTrue(roleList.contains("Auctioneer"));
            assertTrue(roleList.contains("Bidder"));

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}

