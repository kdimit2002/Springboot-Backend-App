package com.example.webapp.BidNow.Services;


import com.example.webapp.BidNow.Dtos.AuthUserDto;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Dtos.LocationDto;
import com.example.webapp.BidNow.Entities.Location;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.FirebaseUserDeleteException;
import com.example.webapp.BidNow.Repositories.*;
import com.example.webapp.BidNow.RetryServices.DatabaseRetryService;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.helpers.UserEntityHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.*;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getDominantRole;
import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * SignupService
 *
 * These service is used for the signup flow
 *
 */

@Service
public class SignupService {


    private final ReferralCodeRepository referralCodeRepository;
    private final UserActivityService userActivityService;
    private final UserEntityService userEntityService;
    private final FirebaseRetryService firebaseRetryService;
    private final EmailService emailService;

    private final UserEntityRepository userEntityRepository;
    private final FirebaseAuth firebaseAuth;
    private final DatabaseRetryService databaseRetryService;
    private static final Logger log = LoggerFactory.getLogger(SignupService.class);


    public SignupService(ReferralCodeRepository referralCodeRepository, UserActivityService userActivityService, UserEntityService userEntityService, FirebaseRetryService firebaseRetryService, EmailService emailService, UserEntityRepository userEntityRepository, FirebaseAuth firebaseAuth, DatabaseRetryService databaseRetryService) {
        this.referralCodeRepository = referralCodeRepository;
        this.userActivityService = userActivityService;
        this.userEntityService = userEntityService;
        this.firebaseRetryService = firebaseRetryService;
        this.emailService = emailService;
        this.userEntityRepository = userEntityRepository;
        this.firebaseAuth = firebaseAuth;
        this.databaseRetryService = databaseRetryService;
    }



    // Fetch user from firebase retry if firebase authentication exception.

    /**
     *
     * This is the main method called when a user want to create an account
     * Note:
     *  - This is not a signup for admin
     *  - This method is called after user is created in firebase
     *  - Database rollbacks if there is any exception(not only if runtime error occurs
     *
     * @param avatar , user sends the avatar info that wants to use for his account ( {@link Avatar}//todo urls must be changed to point from cloudflare's r2 to the frontend file structure
     * @param role , user must choose either a bidder or auctioneer {@link Role}, admin is must be created manually
     * @param locationDto, user must at least provide the country and region that he is from {@link LocationDto}
     * @return useful data for frontend to give the correct info depending on his role,username and if he is a referral code owner
     *
     * @throws FirebaseAuthException
     * @throws IOException
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthUserDto saveUser(Avatar avatar, String role, LocationDto locationDto) throws FirebaseAuthException, IOException {

        // Get firebase unique id
        String firebaseId = getUserFirebaseId();

        // User role must be either Auctioneer or Bidder
        if(role.isBlank() || !(role.equals("Auctioneer")  || role.equals("Bidder"))){
            log.warn("User [ {} ] provided a role that doesn't exist",firebaseId);
            throw new IllegalArgumentException("User provided a role that doesn't exists!");
        }

        UserRecord userRecord;

        //Get user details from firebase
        userRecord = fetchUser(firebaseId);//userRecord = firebaseAuth.getUser(firebaseId);

        String username = userRecord.getDisplayName();
        String email = userRecord.getEmail();
        String phone = userRecord.getPhoneNumber();
        // User that already exists cannot sign up again
        if(userRecord.getUid()!=null && userEntityRepository.existsByFirebaseId(userRecord.getUid())){
            throw new IllegalArgumentException("User: " + username + " already exists");
        }


        if (username != null && userEntityRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username: " + username + " is used by another user.");
        }

        if (email != null && userEntityRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email: '" + email + "' is already used.");
        }

        if (phone != null && userEntityRepository.existsByPhoneNumber(phone)) {
            throw new IllegalArgumentException("Phone number: '" + phone + "' is already used.");
        }

        Set<Role> roles = userEntityService.assignRoles(role);


        // Put roles to list to store it in a map
        List<String> roleList = UserEntityHelper.assignRolesList(roles);


        // Create user object in order to save him in database
        UserEntity user = new UserEntity(userRecord.getDisplayName(),userRecord.getEmail(),userRecord.getPhoneNumber(),firebaseId,avatar,roles);


        //todo:if user is not validated save in database but banned =true

        // Return user's useful info (role, username) if user entity is created
        AuthUserDto authUserDto = validateAndSaveUser(userRecord,user,locationDto);

        // Put list to map in order to send to firebase claims
        Map<String, Object> mapRole = Map.of("roles",roleList);

        // After commit -> assign roles to firebase claims
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // Retry service to avoid connectivity issues
                    firebaseRetryService.setFirebaseClaims(firebaseId, mapRole);
                }catch (Exception e) {
                    // Log error
                    log.error("Unexpected error while assigning Firebase claims after commit for user {}",
                            firebaseId, e);


                    emailService.sendSimpleEmailAsync("bidnowapp@gmail.com","Error in assigning claims to user","Unexpected error while assigning Firebase claims after commit, maybe firebase has claims or not we must check, for user with firebaseId: " + firebaseId);
                }
            }
        });
        return authUserDto;
    }

    public UserRecord fetchUser(String firebaseId)  {
        UserRecord userRecord = null;
        try {
            userRecord = firebaseRetryService.getUserFromFirebase(firebaseId);
        }catch (Exception ex) {
            // Unknown unexpected exception -> no retry
            log.error("Unexpected error fetching Firebase user {}: {}", firebaseId, ex.getMessage(), ex);
            emailService.sendSimpleEmailAsync(
                    "bidnowapp@gmail.com",
                    "User could not be fetched from firebase",
                    "User with Firebase ID: " + firebaseId  +
                            " attempting to fetch user from firebase failed with unexpected exception.\n" +
                            "Action: User couldn't be fetched from database."
            );
            throw new RuntimeException("Unexpected Firebase error User: " + firebaseId, ex);
        }

        return userRecord;
    }








    // ToDo: use retry services

    public AuthUserDto validateAndSaveUser(UserRecord userRecord,UserEntity user,LocationDto locationDto) throws FirebaseAuthException {
        // ToDo: more checks
        // Get user firebase info
        String firebaseId = userRecord.getUid()
                ,phoneNumber = userRecord.getPhoneNumber();

        String reason="";
        // Throw exception if user already exists
        if(userEntityRepository.existsByFirebaseId(userRecord.getUid()))throw new RuntimeException("User already exists");

        // User must have username - display name in firebase
        if(userRecord.getDisplayName() == null || userRecord.getDisplayName().isBlank()){

            log.warn("User {} with phone number {} didn't provide a display name", firebaseId,phoneNumber);

            reason = "User didn't provide a display name. Your account has been disabled please contact bidnow support";

        }

        // User must have phone number
        if(userRecord.getPhoneNumber()==null || userRecord.getPhoneNumber().isEmpty()){
            log.warn("User {}  with phone number {}  didn't provide phone number",userRecord.getUid(),phoneNumber);

            reason = "User didn't provide phone number. Your account has been disabled please contact bidnow support";
        }

        // if user doesn't have a phone number or a username then delete user from firebase (this can help if anything went wrong for user to restart his signup flow)
        if(!reason.isEmpty()){

            //saveDisabledUser(firebaseId,user,reason);

            // Delete user from firebase with retry service to avoid connectivity issues
            firebaseRetryService.deleteUserFromFirebase(getUserFirebaseId());

            throw new FirebaseUserDeleteException(reason);
        }
        return saveUserAfterSuccessfulValidations(user,locationDto);
    }


    /**
     * If user passed validations save usr to database
     * @param user
     * @param locationDto
     * @return
     */
    public AuthUserDto saveUserAfterSuccessfulValidations(UserEntity user,LocationDto locationDto){
        Location location = new Location(user,locationDto.country(),locationDto.region(),locationDto.city(),locationDto.addressLine(),
                locationDto.postalCode(),null,null);
        // Set user's location
        user.setLocation(location);
        userEntityRepository.save(user);
        // Logging
        userActivityService.saveUserActivityAsync(Endpoint.USER_SIGNUP,"User: " + getUserFirebaseId() + " has signed up");


        Boolean isReferralCodeOwner = referralCodeRepository.existsByOwner_FirebaseId(user.getFirebaseId());// Todo: this is not necessary i think

        return new AuthUserDto(user.getUsername(),getDominantRole(user.getRoles()),isReferralCodeOwner);
    }



    //ToDo: scheduler check ban and disabled consistency

    // Todo: this is not used maybe remove this in future
    public void saveDisabledUser(String uid,UserEntity user,String reason) throws FirebaseAuthException {
        try{

            emailService.sendSimpleEmailAsync(
                    "bidnowapp@gmail.com",
                    "🚨 Suspicious Signup Attempt (Invalid Role)",
                    "User with Firebase ID: " + uid + ", email: " + user.getEmail() + ", roles: " + user.getRoles().toString() + "and phone number: " + user.getPhoneNumber() +
                            " tried a suspicious signup. User is trying to be created as disabled user in database and firebase "+ ".\n" +
                            "Reason: " + reason);

            UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(uid)
                    .setDisabled(true);

            // Todo: retry service
            UserRecord userRecord = firebaseAuth.updateUser(request);

           // userEntityRepository.banUser(uid);


            user.setBanned(true);

            userEntityRepository.save(user);
            userActivityService.saveUserActivityAsync(Endpoint.USER_SIGNUP,"User: " + getUserFirebaseId() + " had requested to signup but is being disabled due to: "+ reason);

            //disableUserService.saveBannedUser(user,userActivity);

            log.info("User disabled: {}", userRecord.getUid());
        } catch (FirebaseAuthException e) {

        emailService.sendSimpleEmailAsync(
                "bidnowapp@gmail.com",
                "Firebase disable FAILED",
                "Could not disable user in Firebase: " + uid + "\nError: " + e.getMessage()
        );

        throw new RuntimeException("Firebase disable failed", e);

        } catch (Exception e) {
            emailService.sendSimpleEmailAsync(
                    "bidnowapp@gmail.com",
                    "Database or Email failure during creating disabled user",
                    "User " + uid + " could not be fully disabled.\nError: " + e.getMessage()
            );
            throw new RuntimeException("Unexpected exception occurred while saving disabled user");
            // throw e; ToDo: maybe remove this??
        }
    }

}
