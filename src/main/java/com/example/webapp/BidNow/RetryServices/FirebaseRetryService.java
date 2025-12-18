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
 * @Author Kendeas
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
    @Retryable(recover = "notifyClaimsNotLoaded",
            retryFor = {FirebaseAuthException.class,
            ResourceAccessException.class, ConnectException.class, SocketTimeoutException.class},          // ποιες εξαιρέσεις retry
            maxAttempts = 5,                  // πόσες φορές
            backoff = @Backoff(delay = 500, multiplier = 2) // 1s, 2s, 4s, 8s...
    )
    public void setFirebaseClaims(String firebaseId, Map<String,Object> claims) throws FirebaseAuthException {
        log.warn("Attempting to store Firebase user claims {}", firebaseId);

        try{
            firebaseAuth.setCustomUserClaims(firebaseId,claims);
        }  catch (Exception e) {
            emailService.sendSimpleEmailAsync("bidnowApp@gmail.com",
                    "User could not had his claims assigned in firebase",
                    "User with firebase id: " + firebaseId+ " was attempted to has his claims assigned but couldn't assign them");
            log.error("Unexpected error while setting claims for Firebase user {}", firebaseId, e);
            throw new RuntimeException("Unexpected error setting Firebase claims", e);
        }

        log.info("Successfully stored Firebase user claims {}", firebaseId);


    }


    @Recover
    public void notifyClaimsNotLoaded(FirebaseAuthException e,String firebaseId, Map<String,Object> claims) {
        log.error("User {} couldn't have his roles assigned to firebase, or maybe assigned but firebase couldn't verify. THERE IS A USER NOW IN FIREBASE DATABASE WITHOUT CLAIMS: {}", firebaseId, e.getMessage(),e);
        //ToDo: send email
        emailService.sendSimpleEmailAsync("bidnowApp@gmail.com",
                "User could not had his claims assigned in firebase",
                "User with firebase id: " + firebaseId+ " was attempted to has his claims assigned but couldn't assign them");
        throw new FirebaseConnectionException("Failed to assign roles to Firebase user " + firebaseId, e);
    }





    // ToDo: Check if exception firebaseauthexception.class gets all exceptions possible
    // ToDo: @Async if needed but need to use another bean @Service because retryable cannot be used concurently with async in same proxy bean
    @Retryable(recover = "notifyUserNotDeleted",
            retryFor = {FirebaseAuthException.class,
                    ResourceAccessException.class, ConnectException.class, SocketTimeoutException.class},           // ποιες εξαιρέσεις retry
            maxAttempts = 5,                  // πόσες φορές
            backoff = @Backoff(delay = 500, multiplier = 2) // 1s, 2s, 4s, 8s...
    )
    public void deleteUserFromFirebase(String firebaseId) throws FirebaseAuthException{
        log.warn("Attempting to delete Firebase user {}", firebaseId);

        try {
            log.warn("Attempting to delete Firebase user {}", firebaseId);

            firebaseAuth.revokeRefreshTokens(firebaseId);
            firebaseAuth.deleteUser(firebaseId);

            log.info("Successfully deleted Firebase user {}", firebaseId);
        } catch (Exception e) {
            emailService.sendSimpleEmailAsync("bidnowApp@gmail.com",
                    "User was couldn't be deleted from firebase",
                    "User with firebase id: " + firebaseId+ " was attempted to be deleted but couldn't be");
            log.error("Unexpected exception while deleting Firebase user {}", firebaseId, e);
            throw new RuntimeException("Unexpected error deleting Firebase user", e);
        }

        log.info("Successfully deleted Firebase user {}", firebaseId);
    }


    @Recover
    public void notifyUserNotDeleted(FirebaseAuthException e,String firebaseId){
        try {
            firebaseAuth.getUser(firebaseId);
        } catch (FirebaseAuthException ex) {
                // Ο χρήστης είναι ήδη διαγραμμένος
                throw new ResourceNotFoundException("User wasn't found "+ ex.getMessage());
        }
        log.error("User {} couldn't be deleted from firebase, or maybe deleted but firebase couldn't verify. THERE IS A USER NOW IN FIREBASE DATABASE NOT AUTHORIZED: {}", firebaseId, e.getMessage(),e);
        emailService.sendSimpleEmailAsync(
                "bidnowapp@gmail.com",
                "User " + firebaseId + " couldn't be deleted from firebase",
                "User with Firebase ID: " + firebaseId +
                        " attempted to be deleted but firebase couldn't delete him or deleted him but couldn't notify us.\n" +
                        "Action: User is trying to be deleted from Firebase."
        );
        throw new FirebaseConnectionException("User couldn't be deleted " + e.getMessage(),e);

    }






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
