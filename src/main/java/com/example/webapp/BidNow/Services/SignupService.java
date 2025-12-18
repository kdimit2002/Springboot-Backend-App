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
 * @Author Kendeas
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

    // edo tha kaleso ke to post photo giafto to transactional
    // Trasnactional apo mono tou den kani rollback gia check exceptions alla mono gia
    // runtime exceptions firebaseAuthException ine checked exception
    // ToDo: may rollback exception.class not be necesary]
    // ToDO: add more exceptions
    @Transactional(rollbackFor = Exception.class)
    public AuthUserDto saveUser(Avatar avatar, String role, LocationDto locationDto) throws FirebaseAuthException, IOException {
        //Not a signup for admin

        // Get user from security context (filter loaded user details there from jwt token)
        // Get firebase unique id
        String firebaseId = SecurityContextHolder.getContext().getAuthentication().getName();

        // User role must be either Auctioneer or Bidder
        if(role.isBlank() || !(role.equals("Auctioneer")  || role.equals("Bidder"))){
            log.warn("User [ {} ] provided a role that doesn't exist",firebaseId);
            throw new IllegalArgumentException("User provided a role that doesn't exists!");
        }

        UserRecord userRecord;

        //Get user from firebase database
        userRecord = fetchUser(firebaseId);//userRecord = firebaseAuth.getUser(firebaseId);

        String username = userRecord.getDisplayName();
        String email = userRecord.getEmail();
        String phone = userRecord.getPhoneNumber();
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


        UserEntity user = new UserEntity(userRecord.getDisplayName(),userRecord.getEmail(),userRecord.getPhoneNumber(),firebaseId,avatar,roles);


        //todo:if user os not validated save in database but banned =true

        AuthUserDto authUserDto = validateAndSaveUser(userRecord,user,locationDto);





        // 2) Μετά το COMMIT → κάνε set τα Firebase claims

        // Put list to map in order to send to firebase claims
        Map<String, Object> mapRole = Map.of("roles",roleList);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            // ToDo: Prepi na do periptosis opou exoume network failture xristis graftike stin firebase alla den piasame sxetiko minima graftike
            @Override
            public void afterCommit() {
                try {
                    // Όλο το retry να γίνεται μέσα στο FirebaseRetryService
                    // edo an
                    firebaseRetryService.setFirebaseClaims(firebaseId, mapRole);
                    //todo: remove markFirebaseCompatible(firebaseId);
                }catch (Exception e) {
                    // Τίποτα προς τα πάνω – απλώς log. Το DB έχει ήδη γίνει commit.
                    log.error("Unexpected error while assigning Firebase claims after commit for user {}",
                            firebaseId, e);//ToDo:delete user if set claims failed

                    emailService.sendSimpleEmailAsync("bidnowapp@gmail.com","Error in assigning claims to user","Unexpected error while assigning Firebase claims after commit, maybe firebase has claims or not we must check, for user with firebaseId: " + firebaseId);
                    // STEP 1 — delete from DB (retryable)
                    databaseRetryService.deleteUserFromDatabase(firebaseId);
                    // STEP 2 — cleanup Firebase user
                    try {
                        firebaseRetryService.deleteUserFromFirebase(firebaseId);
                        log.info("Cleanup success: User {} removed from both DB and Firebase.", firebaseId);
                    } catch (FirebaseAuthException ex) {
                        log.warn("Cleanup WARNING: User {} was deleted from DB but FAILED to delete from Firebase.",
                                firebaseId, ex);

                        emailService.sendSimpleEmailAsync(
                                "bidnowapp@gmail.com",
                                "WARNING: Firebase Delete Failed",
                                "User cleanup was partially completed.\n\n" +
                                        "User with Firebase ID: " + firebaseId + " was deleted from the database, " +
                                        "but could NOT be deleted from Firebase.\n\n" +
                                        "The user still exists in Firebase WITHOUT CLAIMS, and they cannot login.\n" +
                                        "Exception message: " + ex.getMessage() + "\n\n" +
                                        "A scheduler will retry cleanup automatically."
                        );

                        throw new RuntimeException(ex);//ToDo: na baloume custome exception
                    }
                    emailService.sendSimpleEmailAsync(
                            "bidnowapp@gmail.com",
                            "WARNING: Firebase roles assign failed",
                            "User was deleted from database but maybe couldn't be deleted from firebase \n\n" +
                                    "User with Firebase ID: " + firebaseId + " was deleted from the database, " +
                                    "but could maybe exists in Firebase.\n\n" +
                                    "Exception message: " + e.getMessage());

                    throw new RuntimeException(e);//ToDo: na baloume custome exception
                }                        // deleteUserByFirebaseId(firebaseId); // ToDo: Clean up code
            }
        });




        return authUserDto;
    }



    // Fetch user from firebase retry if firebase authentication exception.
    private UserRecord fetchUser(String firebaseId)  {
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




//TODO: KALITERA NA KANO DELETE AN PAI KATI STRAVA STO FIREBASE SIGN UP




    // ToDo: will have to make retryable the disable user service.

    public AuthUserDto validateAndSaveUser(UserRecord userRecord,UserEntity user,LocationDto locationDto) throws FirebaseAuthException {
        // Loading user data from database
        // ToDo: more checks
        String firebaseId = userRecord.getUid()
                ,phoneNumber = userRecord.getPhoneNumber();

        String reason="";
        // User must have username - display name
        if(userEntityRepository.existsByFirebaseId(userRecord.getUid()))throw new RuntimeException("User already exists");
        if(userRecord.getDisplayName() == null || userRecord.getDisplayName().isBlank()){

            log.warn("User {} with phone number {} didn't provide a display name", firebaseId,phoneNumber);

            reason = "User didn't provide a display name. Your account has been disabled please contact bidnow support";

        }

        // User must have phone number
        if(userRecord.getPhoneNumber()==null || userRecord.getPhoneNumber().isEmpty()){
            log.warn("User {}  with phone number {}  didn't provide phone number",userRecord.getUid(),phoneNumber);

            reason = "User didn't provide phone number. Your account has been disabled please contact bidnow support";
        }

        //todo:
        if(!reason.isEmpty()){

            //saveDisabledUser(firebaseId,user,reason);

            firebaseRetryService.deleteUserFromFirebase(getUserFirebaseId());

            throw new FirebaseUserDeleteException(reason);
        }
        return saveUserAfterSuccessfulValidations(user,locationDto);
    }


    public AuthUserDto saveUserAfterSuccessfulValidations(UserEntity user,LocationDto locationDto){
        Location location = new Location(user,locationDto.country(),locationDto.region(),locationDto.city(),locationDto.addressLine(),
                locationDto.postalCode(),null,null);
        user.setLocation(location);
        userEntityRepository.save(user);
        userActivityService.saveUserActivityAsync(Endpoint.USER_SIGNUP,"User: " + getUserFirebaseId() + " has signed up");


        Boolean isReferralCodeOwner = referralCodeRepository.existsByOwner_FirebaseId(user.getFirebaseId());

        return new AuthUserDto(user.getUsername(),getDominantRole(user.getRoles()),isReferralCodeOwner);
    }



    //ToDo: scheduler check ban and disabled consistency
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

        // Αν αποτύχει το Firebase disable
        emailService.sendSimpleEmailAsync(
                "bidnowapp@gmail.com",
                "Firebase disable FAILED",
                "Could not disable user in Firebase: " + uid + "\nError: " + e.getMessage()
        );

        throw new RuntimeException("Firebase disable failed", e);

        } catch (Exception e) {
            // Για κάθε άλλο σφάλμα (π.χ. DB)
            emailService.sendSimpleEmailAsync(
                    "bidnowapp@gmail.com",
                    "Database or Email failure during creating disabled user",
                    "User " + uid + " could not be fully disabled.\nError: " + e.getMessage()
            );
            throw new RuntimeException("Unexpected exception occurred while saving disabled user");
            // throw e; ToDo: maybe leave this??
        }
    }



    ////////////////////////// FORE IMAGE COMPRESSION AND SAVING ///////////////////////////////////////

//    /**
//     *
//     * Extract image metadata in order to store in sql database
//     */
//    private UserPhoto extractImageInfo(CustomImage customImage) throws IOException {
//        String filename = customImage.username() + "." + customImage.format();
//        String url = "https://" + filename;//ToDo: this is prototype
//        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(customImage.image()));
//        int width = bufferedImage.getWidth(),height = bufferedImage.getHeight();
//        double sizeMB = customImage.image().length / (1024.00 * 1024.00);
//        return new UserPhoto(filename,url,sizeMB,customImage.format(),width,height,false);
//    }

//    public String getImageFormat(MultipartFile file){
//        return Objects.requireNonNull(file.getContentType()).substring(file.getContentType().lastIndexOf("/") + 1);
//    }
//
//    private static String detectImageFormat(byte[] bytes) {
//
//        if (bytes == null || bytes.length < 12) {
//            return "unknown";
//        }
//
//        // ---- PNG ----
//        // Hex signature: 89 50 4E 47 0D 0A 1A 0A
//        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
//            return "png";
//        }
//
//        // ---- JPEG/JPG ----
//        // SOI marker: FF D8 ... FF D9
//        if (bytes.length > 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
//            return "jpg";
//        }
//
//        // ---- WEBP ----
//        // RIFF header: "RIFF....WEBP"
//        if (bytes.length > 12 &&
//                bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
//                bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
//            return "webp";
//        }
//
//        return "unknown";
//    }
//







 //ToDo: put this in auction app to validate and compress images




//    byte[] compressedImage;
//    UserPhoto userPhoto=null;

// ToDo Load image to a database

//                if(ImageFileValidator.validate(file)){
//        compressedImage = imageCompressionService.compressAndResize(file, Purpose.USER);
//        String format = detectImageFormat(compressedImage),username = userRecord.getDisplayName();
//
//        if(format.equals("unknown")) {
//            throw new IllegalArgumentException("Unsupported file type");
//        }
//
//        CustomImage customImage = new CustomImage(compressedImage,username,format);//ToDo: change username, it may not be always unique
//        userPhoto = extractImageInfo(customImage);
//    }


//    /**
//     *
//     * Extract image metadata in order to store in sql database
//     */
//    private UserPhoto extractImageInfo(CustomImage customImage) throws IOException {
//        String filename = customImage.username() + "." + customImage.format();
//        String url = "https://" + filename;//ToDo: this is prototype
//        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(customImage.image()));
//        int width = bufferedImage.getWidth(),height = bufferedImage.getHeight();
//        double sizeMB = customImage.image().length / (1024.00 * 1024.00);
//        return new UserPhoto(filename,url,sizeMB,customImage.format(),width,height,false);
//    }


//
//    private static String detectImageFormat(byte[] bytes) {
//
//        if (bytes == null || bytes.length < 12) {
//            return "unknown";
//        }
//
//        // ---- PNG ----
//        // Hex signature: 89 50 4E 47 0D 0A 1A 0A
//        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
//            return "png";
//        }
//
//        // ---- JPEG/JPG ----
//        // SOI marker: FF D8 ... FF D9
//        if (bytes.length > 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
//            return "jpg";
//        }
//
//        // ---- WEBP ----
//        // RIFF header: "RIFF....WEBP"
//        if (bytes.length > 12 &&
//                bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
//                bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
//            return "webp";
//        }
//
//        return "unknown";
//    }



}
