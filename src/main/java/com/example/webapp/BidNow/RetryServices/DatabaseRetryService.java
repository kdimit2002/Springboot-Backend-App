package com.example.webapp.BidNow.RetryServices;

import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Exceptions.FirebaseConnectionException;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.example.webapp.BidNow.Services.EmailService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.database.DatabaseException;
import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseRetryService {

    public static final Logger log = LoggerFactory.getLogger(DatabaseRetryService.class);


    private final EmailService emailService;

    public final UserEntityRepository userEntityRepository;

    public DatabaseRetryService(EmailService emailService, UserEntityRepository userEntityRepository) {
        this.emailService = emailService;
        this.userEntityRepository = userEntityRepository;
    }






    @Retryable(recover = "notifyUserNotDeleted",
            retryFor = {
                    TransientDataAccessException.class, // Spring translated
                    JDBCConnectionException.class,       // Hibernate raw
                    CannotGetJdbcConnectionException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteUserFromDatabase(String firebaseId)  throws  TransientDataAccessException,JDBCConnectionException, CannotGetJdbcConnectionException {
        int row;
        log.warn("Attempting to delete Firebase user {}", firebaseId);


        row = userEntityRepository.deleteByFirebaseId(firebaseId);
        if(row<=0)throw new ResourceNotFoundException("User was not found and thus couldn't be deleted");//todo:check


        log.info("Successfully deleted Firebase user {}", firebaseId);

    }

    @Recover
    public void notifyUserNotDeleted(Exception e, String firebaseId) {
        log.error("Could not delete user {} from database after retries. Will mark as cleanup-needed. Reason: {}",
                firebaseId, e.getMessage());
        emailService.sendSimpleEmailAsync(
                "bidnowapp@gmail.com",
                "CRITICAL: Failed to delete inconsistent user",
                "Could not delete user with Firebase UID: " + firebaseId + "\n" +
                        "Exception: " + e.getMessage() + "\n" +
                        "Manual investigation required."
        );

        throw new DatabaseException("User couldn't be deleted due to connection issues. Please try again: " + e.getMessage());
    }





//
//
//    @Retryable(recover = "notifyUserNotRetrievedScheduler",
//            retryFor = {
//                    TransientDataAccessException.class, // Spring translated
//                    JDBCConnectionException.class,       // Hibernate raw
//                    CannotGetJdbcConnectionException.class
//            },
//            maxAttempts = 3,
//            backoff = @Backoff(delay = 500, multiplier = 2)
//    )
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public boolean deleteUserFromDatabaseScheduler(String firebaseId){
//        log.warn("Attempting to delete Firebase user {} from scheduler", firebaseId);
//
//
//        int row = userEntityRepository.deleteByFirebaseId(firebaseId);
//
//
//        log.info("Successfully deleted Firebase user {} from scheduler", firebaseId);
//
//        return row > 0;
//    }
//
//    @Recover
//    public boolean recoverFromDatabaseDeleteFailureScheduler(Exception e, String firebaseId) {
//        log.error("Could not delete user {} from database after retries in scheduler. Will mark as cleanup-needed. Reason: {}",
//                firebaseId, e.getMessage());
//
//        return false;
//    }



    //Todo: dont delete this
//
//    @Retryable(recover = "recoverRetryAnonymizeUser",
//            retryFor = {
//                    TransientDataAccessException.class, // Spring translated
//                    JDBCConnectionException.class,       // Hibernate raw
//                    CannotGetJdbcConnectionException.class
//            },
//            maxAttempts = 3,
//            backoff = @Backoff(delay = 500, multiplier = 2)
//    )
//    @Transactional
//    public void retryAnonymizeUser(String firebaseId){
//        log.warn("Attempting to anonymize user");
//
//
//
//        userEntityRepository.anonymizeByFirebaseId(firebaseId);
//
//
//        log.info("Successfully anonymized user from database");
//    }
//
//    @Recover
//    public void recoverRetryAnonymizeUser(Exception e, String firebaseId) {
//        log.error("Could not anonymize user {} from database after retries. Will mark as cleanup-needed. Reason: {}",
//                firebaseId, e.getMessage());
//        emailService.sendSimpleEmail("bidnowapp@gmail.com",
//                "User : { " + firebaseId + " } couldn't be anonymized",
//                "After anonymization method was invoked something went wrong and user couldn't be anonymized");
//        throw new DatabaseException("User couldn't be anonymized " + e.getMessage());
//    }

/// ////////////////todo:dont delete this




}
