package com.example.webapp.BidNow.Schedulers;

import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.RoleRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.example.webapp.BidNow.RetryServices.DatabaseRetryService;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.Services.AdminUserEntityService;
import com.example.webapp.BidNow.Services.AnonymizeUserService;
import com.example.webapp.BidNow.Services.EmailService;
import com.google.firebase.auth.*;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @Author Kendeas
 */
@Service
public class NightlyConsistencyScheduler {

    private static final Logger log = LoggerFactory.getLogger(NightlyConsistencyScheduler.class);
    private final AdminUserEntityService adminUserEntityService;
    private final AnonymizeUserService anonymizeUserService;
    private final UserEntityRepository userRepository;
    private final FirebaseAuth firebaseAuth;
    private int executedToday = 0; // For checking if it runs once then don't retry
    private final EmailService emailService;
    private final DatabaseRetryService databaseRetryService;
    private final FirebaseRetryService firebaseRetryService;
    private final RoleRepository roleRepository;



    //todo: Check if there is a chance a user has differnet firebase id in firebase than database

    public NightlyConsistencyScheduler(AdminUserEntityService adminUserEntityService, AnonymizeUserService anonymizeUserService, UserEntityRepository userRepository, FirebaseAuth firebaseAuth, EmailService emailService, DatabaseRetryService databaseRetryService, FirebaseRetryService firebaseRetryService, RoleRepository roleRepository) {
        this.adminUserEntityService = adminUserEntityService;
        this.anonymizeUserService = anonymizeUserService;
        this.userRepository = userRepository;
        this.firebaseAuth = firebaseAuth;
        this.emailService = emailService;
        this.databaseRetryService = databaseRetryService;
        this.firebaseRetryService = firebaseRetryService;
        this.roleRepository = roleRepository;
    }

    @Scheduled(cron = "0 */10 * * * *") // every 10 minutes
    public void scheduledNightlyCheck() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        boolean execute = true;

        // Checking if we are in the low load time window (1am to 5am)
        if (hour >= 1 && hour <= 5 && executedToday<5) {
            double load = getSystemLoad();
            if (load < 0.7) {//ToDO: calculate actual load, maybe remove this
                log.info("Low load ({}) — running consistency check...", load);//todo: cleanup this logs once every 3 -7 days
                emailService.sendSimpleEmailAsync(
                                "bidnowapp@gmail.com",
                        "The nightly scheduler started",
                        "Nightly consistency scheduler started at "+ LocalTime.now() + " hour = " + hour +
                                " . Scheduled service is trying to check for any inconsistencies..."
                );
                performCheckFetchingDatabaseUsers();
                performCheckFetchingFirebaseUsers();
                executedToday++;
                execute = false;
            } else {
                log.info("⚠ High load ({}) — skipping for now.",load);
            }
        }
        if (hour >= 2 && hour <= 5 && executedToday < (hour - 1) && execute) {
            log.info("Forcing consistency check due to missed low-load window...");
            emailService.sendSimpleEmailAsync(
                    "bidnowapp@gmail.com",
                    "Nightly scheduler forced run",
                    "Forced consistency check at " + LocalTime.now() +
                            " because low-load condition was not met earlier."
            );
            performCheckFetchingDatabaseUsers();
            performCheckFetchingFirebaseUsers();
            executedToday++;
        }


        // Reset flag every day at 00:00
        if (hour == 0 && LocalTime.now().getMinute() < 31) {
            executedToday = 0;
            log.info("Resetting nightly scheduler execution counter.");
        }

    }

    private double getSystemLoad() {
        double load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        return load < 0 ? 0.0 : load;
    }


    // ToDo: MAYBE REMOVE THIS
    // ToDo: when banning users maybe will have to change some things
    // Check all users if are correctly assigned in the two databases
    private void performCheckFetchingDatabaseUsers() {
        log.info("Performing check from scheduler");
        var users = userRepository.findAll();
        // cutoff for checking if a user was created more than 10 minutes before
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

        for (var user : users) {
            try {

                // Skip records of users that was created in less than 10 minutes
                if (user.getCreatedAt().isAfter(cutoff)) {
                    continue;
                }


                // Find the related firebase user record
                var firebaseUserId = firebaseAuth.getUser(user.getFirebaseId());


                Map<String, Object> customClaims = firebaseUserId.getCustomClaims();
                boolean firebaseHasClaims = customClaims != null && customClaims.containsKey("roles");
                boolean dbHasRoles = !user.getRoles().isEmpty();

                // Case 1: if user has roles in db apply them also in firebase(claims in firebase are never user, but we want to keep consistency)
                if (dbHasRoles && !firebaseHasClaims) {
                    List<String> roleNames = user.getRoles()
                            .stream()
                            .map(Role::getName)
                            .toList();

                    Map<String, Object> claims = Map.of("roles", roleNames);
                    firebaseAuth.setCustomUserClaims(user.getFirebaseId(), claims);
                }
                // Case 2: This case is almost impossible, we have to research if flow goes here
                else if (!dbHasRoles && firebaseHasClaims) {
                    @SuppressWarnings("unchecked")
                    List<String> firebaseRoles = (List<String>) customClaims.get("roles");

                    // var roleSet = new HashSet<>(roleRepository.findByNameIn(firebaseRoles));
                    // user.setRoles(roleSet);
                    userRepository.save(user);
                    emailService.sendSimpleEmailAsync(
                            "bidnowapp@gmail.com",
                            "⚠️ Critical Consistency: Missing Roles in Database",
                            "The nightly consistency scheduler detected a critical mismatch:\n\n" +
                                    "➡ Firebase has custom role claims for user:\n" +
                                    "   Firebase ID: " + user.getFirebaseId() + "\n" +
                                    "   Firebase Roles: " + firebaseRoles.toString() + "\n\n" +
                                    "❗ Database had NO roles assigned for this user.\n\n" +
                                    " ❗❗❗ WARNING: ❗❗❗  Please review as this is almost impossible to happen. Please DO your research"
                    );
                }



                if (firebaseUserId.getDisplayName() == null || !user.getUsername().equals(firebaseUserId.getDisplayName())) {
                    log.error("User didn't have display name on firebase database");
                    emailService.sendSimpleEmailAsync(
                            "bidnowapp@gmail.com",
                            "The nightly scheduler found inconsistent firebase displayName",
                            "Nightly consistency scheduler found inconsistent displayName for user with Firebase ID: " +
                                    user.getFirebaseId() + ". It will be updated to match DB username: " + user.getUsername()
                    );
                    updateFirebaseDisplayName(user.getFirebaseId(), user.getUsername());
                }
                //ToDo: Check for all inconsistencies
            } catch (FirebaseAuthException ex) {

                boolean notFound = ex.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND;

                // if user exists in database and not into firebase and is not anonymized, then anonymize user record
                if (notFound && !user.getAnonymized()) {

                    log.warn("User {} exists in DB but NOT in Firebase. Triggering anonymization.",
                            user.getFirebaseId());

                    emailService.sendSimpleEmailAsync(
                            "bidnowapp@gmail.com",
                            "⚠️ Inconsistency Detected: User Missing in Firebase",
                            "A user inconsistency was detected during the nightly check:\n\n" +
                                    "- Database ID: " + user.getId() + "\n" +
                                    "- Firebase ID: " + user.getFirebaseId() + "\n\n" +
                                    "Firebase returned USER_NOT_FOUND.\n" +
                                    "System will now attempt to anonymize this user to maintain consistency."
                    );

                    anonymizeUserService.handleDbUserAnonymize(user);

                } else {
                    // Different error please research
                    log.error("Unexpected Firebase error while checking user {}",
                            user.getFirebaseId(), ex);

                    emailService.sendSimpleEmailAsync(
                            "bidnowapp@gmail.com",
                            "❌ Firebase Error During Consistency Check",
                            "An unexpected Firebase error occurred while checking user:\n\n" +
                                    "- Firebase ID: " + user.getFirebaseId() + "\n" +
                                    "- Database ID: " + user.getId() + "\n\n" +
                                    "Error Message: " + ex.getMessage() + "\n" +
                                    "This requires manual inspection."
                    );
                }
            }catch(Exception e){
                    log.error("Error checking {}: {}", user.getFirebaseId(), e.getMessage());
                }
            }
        log.info("Nightly Consistency check completed.");
    }


    private void updateFirebaseDisplayName(String firebaseUid, String newDisplayName){
        UpdateRequest request = new UpdateRequest(firebaseUid)
                .setDisplayName(newDisplayName);
        try{
            UserRecord updatedUser = firebaseAuth.updateUser(request);
            log.info("Successfully updated user: {}", updatedUser.getDisplayName());
        }catch (FirebaseAuthException ex){
            log.error("Couldn't assign user {} display name",firebaseUid);
            emailService.sendSimpleEmailAsync(
                    "bidnowapp@gmail.com",
                    "The nightly scheduler found inconsistent user with no display name but couldn't assign name",
                    "Nightly consistency scheduler started at "+ LocalTime.now() +
                            " found inconsistent user: " +firebaseUid +  "on firebase database but couldn't assign display name to him"
            );
        }
    }




    // If a user deletes himself in firebase we must store him in ours in order to not delete and then create again accounts
    // So no check for users not in firebase but in database
    private void performCheckFetchingFirebaseUsers() {
        try {
            // Start fetching users
            ListUsersPage page = firebaseAuth.listUsers(null);

            while (page != null) {
                // Get a set of users
                for (ExportedUserRecord user : page.getValues()) {
                    String userFirebaseId = user.getUid();
                    // If a user exists in firebase but not in database -> delete user from firebase
                    if(!userRepository.existsByFirebaseId(userFirebaseId)){
                        emailService.sendSimpleEmailAsync( // todo: some of these emails must be changed to be after commit
                                "bidnowapp@gmail.com",
                                "Inconsistent Firebase User",
                                "A Firebase user record was found without a matching database entry.\n\n" +
                                        "Firebase ID: " + userFirebaseId + "\n" +
                                        "Timestamp: " + LocalDateTime.now() + "\n\n" +
                                        "This message was automatically generated by the night consistency scheduler service."
                        );
                        if (firebaseRetryService.deleteUserFromFirebase(userFirebaseId)) {
                            log.info("✅ Successfully deleted inconsistent Firebase user [{}].", userFirebaseId);
                            emailService.sendSimpleEmailAsync(
                                    "bidnowapp@gmail.com",
                                    "Inconsistent Firebase User Deleted",
                                    "A Firebase user record was found without a matching database entry.\n\n" +
                                            "Firebase ID: " + userFirebaseId + "\n" +
                                            "Action: The user has been deleted from Firebase for consistency.\n\n" +
                                            "Timestamp: " + LocalDateTime.now() + "\n\n" +
                                            "This message was automatically generated by the consistency service."
                            );
                        }
                        continue;
                    }
                    UserEntity userEntity= userRepository.findByFirebaseId(userFirebaseId).orElseThrow(() -> new ResourceNotFoundException("Scheduler exception user not found"));//todo: this should never be executed

                    // if user is disabled in firebase and not in database
                    if (user.isDisabled() && !userEntity.getBanned()) { // todo: review this too, also the opposite is more important
                        log.warn("Inconsistency detected: Firebase disabled user but in DB not banned. Fixing... {}", user.getUid());
                        userEntity.setBanned(true);
                        userRepository.save(userEntity);

                        adminUserEntityService.disableUserActions(userEntity);//Trasnactional

                        emailService.sendSimpleEmailAsync(
                                "bidnowapp@gmail.com",
                                "Ban Sync Fix Applied",
                                "Firebase shows user " + user.getUid() + " as disabled, " +
                                        "but database was not marked as banned. The DB value was corrected."
                        );
                    }
                }
                // Get the next set of users from firebase
                page = page.getNextPage();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
