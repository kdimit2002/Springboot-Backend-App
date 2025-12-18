//package com.example.webapp.BidNow.Schedulers;
//
//import com.example.webapp.BidNow.Entities.UserEntity;
//import com.example.webapp.BidNow.Repositories.UserEntityRepository;
//import com.example.webapp.BidNow.RetryServices.DatabaseRetryService;
//import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
//import com.example.webapp.BidNow.Services.EmailService;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseAuthException;
//import org.hibernate.exception.JDBCConnectionException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.dao.TransientDataAccessException;
//import org.springframework.jdbc.CannotGetJdbcConnectionException;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
///**
// * @Author Kendeas
// *
// */
//@Service
//public class CleanUsersScheduler {
//
//
//    private final UserEntityRepository userEntityRepository;
//    private final DatabaseRetryService databaseRetryService;
//    private final FirebaseRetryService firebaseRetryService;
//    private final EmailService emailService;
//
//
//    private static final Logger log = LoggerFactory.getLogger(CleanUsersScheduler.class);
//
//    public CleanUsersScheduler(UserEntityRepository userEntityRepository, DatabaseRetryService databaseRetryService, FirebaseAuth firebaseAuth, FirebaseRetryService firebaseRetryService, EmailService emailService) {
//        this.userEntityRepository = userEntityRepository;
//        this.databaseRetryService = databaseRetryService;
//        this.firebaseRetryService = firebaseRetryService;
//        this.emailService = emailService;
//    }
//
//    // Clean users without roles every 30 minutes.
//    // ToDo: This is an edge case so i think 30 minutes is ok but maybe we increase more the time!
//    @Scheduled(cron = "0 */30 * * * *")
//    public void cleanupUsersWithoutClaims() {
//
//        // αν user δημιουργήθηκε πριν 15 λεπτά
//        // We take 15 mins before because users that are being processed now do not have claims yet!
//        List<String> users_firebaseIds = userEntityRepository.findOldUsersWithoutFirebaseClaims(LocalDateTime.now().minusMinutes(15));
//
//        for (String userFirebaseId : users_firebaseIds) {
//            try {
//                if (userEntityRepository.deleteByFirebaseId(userFirebaseId) == 0) {
//                    log.error("User {} couldn't be deleted from database but exists in it.", userFirebaseId);
//                    emailService.sendSimpleEmail(
//                            "bidnowapp@gmail.com",
//                            "Something is wrong",
//                            "User with Firebase ID: " + userFirebaseId +
//                                    " attempted to delete but not found in database."
//                    );
//                    //Todo: check something is off here
//                }
//                firebaseRetryService.deleteUserFromFirebase(userFirebaseId);
//
//            } catch (TransientDataAccessException | CannotGetJdbcConnectionException |
//                     JDBCConnectionException exception) {
//                if (!databaseRetryService.deleteUserFromDatabase(userFirebaseId)) {
//                    // ToDo: send Email
//                    log.error("User {} couldn't be deleted from database after 3 retries but exists in it.", userFirebaseId);
//                    emailService.sendSimpleEmail(
//                            "bidnowapp@gmail.com",
//                            "Something is wrong",
//                            "User with Firebase ID: " + userFirebaseId +
//                                    " attempted to delete but not found in database after 3 retries."
//                    );
//                    continue;
//                }
//            } catch (FirebaseAuthException e) {
//                // ToDo: send Email
//                log.error("Error deleting user {} from firebase database", userFirebaseId);
//                emailService.sendSimpleEmail(
//                        "bidnowapp@gmail.com",
//                        "Something is wrong",
//                        "User with Firebase ID: " + userFirebaseId +
//                                " attempted to be deleted but something went wrong in firebase."
//                );
//                continue;
//            } catch (Exception e) {
//                // ToDo: send Email
//                log.error("Error deleting user {} unexpected exception", userFirebaseId);
//                emailService.sendSimpleEmail(
//                        "bidnowapp@gmail.com",
//                        "Something is wrong",
//                        "User with Firebase ID: " + userFirebaseId +
//                                " attempted to be deleted but something went wrong."
//                );
//                continue;
//            }
//            // ToDo: send Email
//            log.warn("Deleted orphan user {}", userFirebaseId);
//            emailService.sendSimpleEmail(
//                    "bidnowapp@gmail.com",
//                    "Something is wrong",
//                    "User with Firebase ID: " + userFirebaseId +
//                            " found without roles, user was deleted."
//            );
//        }
//
//
//    }
//
//
//    //ToDo: scheduled service to check if a user is in this database but not firebase's and opposite
//
//
//}
