package com.example.webapp.BidNow.RetryServices;

import com.example.webapp.BidNow.Exceptions.FirebaseConnectionException;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Services.EmailService;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Optional;

/**
 * Retry service for firebase authentication external api/
 * Retry service is used for consistency between the application's database
 * on connectivity and transient errors
 */
@Service
public class FirebaseRetryService {

    private final FirebaseAuth firebaseAuth;
    private static final Logger log = LoggerFactory.getLogger(FirebaseRetryService.class);

    private final EmailService emailService;

    public FirebaseRetryService(FirebaseAuth firebaseAuth, EmailService emailService) {
        this.firebaseAuth = firebaseAuth;
        this.emailService = emailService;
    }



    // ToDo: async retry??

    /**
     *
     * Assigns custom claims to a Firebase user.
     * After 5 failed tries, the flow goes to the recover method: notifyClaimsNotLoaded().
     *
     */
    @Retryable(recover = "notifyClaimsNotLoaded",
            retryFor = {FirebaseAuthException.class,
            ResourceAccessException.class, ConnectException.class, SocketTimeoutException.class},          // ποιες εξαιρέσεις retry
            maxAttempts = 5,  // retry 5 times
            backoff = @Backoff(delay = 500, multiplier = 2) // 1s, 2s, 4s, 8s...
    )
    public void setFirebaseClaims(String firebaseId, Map<String,Object> claims) throws FirebaseAuthException {
        log.warn("Attempting to store Firebase user claims {}", firebaseId);
        try{
            firebaseAuth.setCustomUserClaims(firebaseId,claims);
        }  catch (Exception e) {
            emailService.sendSimpleEmailAsync("bidnowApp@gmail.com",
                    "User's claims weren't assigned in firebase",
                    "User with firebase id: " + firebaseId+ " could not have his claims assigned");
            log.error("Unexpected error while setting claims for Firebase user {}", firebaseId, e);
            throw new RuntimeException("Unexpected error setting user's Firebase claims", e);
        }

        log.info("Successfully stored Firebase user claims {}", firebaseId);


    }


    /**
     * Recovery method.
     * Triggered after 5 failed attempts to assign Firebase claims.
     */
    @Recover
    public void notifyClaimsNotLoaded(FirebaseAuthException e,String firebaseId, Map<String,Object> claims) {
        log.error("User {} couldn't have his roles assigned to firebase, or maybe assigned but firebase couldn't verify. THERE IS A USER NOW IN FIREBASE DATABASE WITHOUT CLAIMS: {}", firebaseId, e.getMessage(),e);
        //ToDo: send email
        emailService.sendSimpleEmailAsync("bidnowApp@gmail.com",
                "User could not had his claims assigned in firebase",
                "User with firebase id: " + firebaseId+ " was attempted to has his claims assigned but couldn't assign them");
        throw new FirebaseConnectionException("Failed to assign roles to Firebase user " + firebaseId, e);
    }




    // ToDo: @Async if needed but need to use another bean @Service because retryable cannot be used concurrently with async in the same bean

    /**
     * Delete user from Firebase and revoke his refresh token.
     * After 5 failed tries, the flow goes to the recover method: notifyUserNotDeleted().
     *
     */
    @Retryable(recover = "notifyUserNotDeleted",
            retryFor = {FirebaseAuthException.class,
                    ResourceAccessException.class, ConnectException.class, SocketTimeoutException.class},
            maxAttempts = 5,
            backoff = @Backoff(delay = 500, multiplier = 2) // 1s, 2s, 4s, 8s...
    )
    public boolean  deleteUserFromFirebase(String firebaseId) throws FirebaseAuthException{
        firebaseAuth.revokeRefreshTokens(firebaseId);
        firebaseAuth.deleteUser(firebaseId);
        return true;
    }

    /**
     * Recovery method.
     * Triggered after 5 failed attempts to delete user from firebase.
     */
    @Recover
    public boolean  notifyUserNotDeleted(FirebaseAuthException e,String firebaseId){
        log.error("Failed to delete Firebase user {} after retries: {}", firebaseId, e.getMessage(), e);

        emailService.sendSimpleEmailAsync(
                "bidnowapp@gmail.com",
                "User couldn't be deleted from Firebase",
                "FirebaseId: " + firebaseId + "\nCause: " + e.getClass().getSimpleName() + " - " + e.getMessage()
        );

        return false;

    }




    /**
     * Fetch user from Firebase.
     * After 5 failed tries, the flow goes to the recover method: notifyUserNotRetrieved().
     *
     */
    @Retryable(recover = "notifyUserNotRetrieved",
            retryFor = {FirebaseAuthException.class,
                    ResourceAccessException.class, ConnectException.class, SocketTimeoutException.class},            // ποιες εξαιρέσεις retry
            maxAttempts = 3,                  // πόσες φορές
            backoff = @Backoff(delay = 500, multiplier = 2) // 1s, 2s, 4s, 8s...
    )
    public UserRecord getUserFromFirebase(String firebaseId) throws FirebaseAuthException {
        log.warn("Attempting to get Firebase user {}", firebaseId);
        UserRecord userRecord;
        try {
            userRecord = firebaseAuth.getUser(firebaseId);
            log.info("Successfully got Firebase user {}", firebaseId);
        }
         catch (Exception e) {
            log.error("Unexpected error while fetching user from Firebase with userId: {}", firebaseId, e);
             emailService.sendSimpleEmailAsync("bidnowApp@gmail.com",
                     "User could not fetch user from firebase",
                     "User with firebase id: " + firebaseId+ " was attempted to be fetched but couldn't be fetched");

            throw new RuntimeException("Unexpected error setting Firebase claims", e);
        }

        return userRecord;
    }

    /**
     * Recovery method.
     * Triggered after 5 failed attempts to fetch user from firebase.
     */
    @Recover
    public UserRecord notifyUserNotRetrieved(FirebaseAuthException e, String firebaseId){
        log.error("User {} couldn't be retrieved from firebase: {}", firebaseId, e.getMessage(),e);

        //ToDo: send email
        emailService.sendSimpleEmailAsync("bidnowApp@gmail.com",
                "User could not fetch user from firebase",
                "User with firebase id: " + firebaseId+ " was attempted to be fetched but couldn't be fetched");

        throw new FirebaseConnectionException("User couldn't be retrieved from firebase",e);
    }



}
