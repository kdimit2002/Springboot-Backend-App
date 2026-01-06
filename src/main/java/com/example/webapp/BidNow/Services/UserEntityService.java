package com.example.webapp.BidNow.Services;


import com.example.webapp.BidNow.Dtos.AuthUserDto;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Dtos.LocationDto;
import com.example.webapp.BidNow.Dtos.UserEntityDto;
import com.example.webapp.BidNow.Entities.Location;
import com.example.webapp.BidNow.Entities.ReferralCodeUsage;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.FirebaseConnectionException;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.*;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.helpers.UserEntityHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import jakarta.validation.constraints.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static com.example.webapp.BidNow.helpers.GeneralHelper.lowerExceptFirst;
import static com.example.webapp.BidNow.helpers.UserEntityHelper.*;

/**
 *
 * Service for managing users
 *
 */
@Service
public class UserEntityService {


    private static final Logger log = LoggerFactory.getLogger(UserEntityService.class);
    private final ReferralCodeUsageRepository referralCodeUsageRepository;
    private final RoleRepository roleRepository;
    private final UserEntityRepository userEntityRepository;
    private final FirebaseAuth firebaseAuth;
    private final ReferralCodeRepository referralCodeRepository;
    private final FirebaseRetryService firebaseRetryService;
    private final LocationRepository locationRepository;
    private final UserActivityService userActivityService;
    private final EmailService emailService;



    public UserEntityService(ReferralCodeUsageRepository referralCodeUsageRepository, RoleRepository roleRepository, UserEntityRepository userEntityRepository, FirebaseAuth firebaseAuth, ReferralCodeRepository referralCodeRepository, FirebaseRetryService firebaseRetryService, LocationRepository locationRepository, UserActivityService userActivityService, EmailService emailService) {
        this.referralCodeUsageRepository = referralCodeUsageRepository;
        this.roleRepository = roleRepository;
        this.userEntityRepository = userEntityRepository;
        this.firebaseAuth = firebaseAuth;
        this.referralCodeRepository = referralCodeRepository;
        this.firebaseRetryService = firebaseRetryService;
        this.locationRepository = locationRepository;
        this.userActivityService = userActivityService;
        this.emailService = emailService;
    }

    /**
     * Users can view their account information
     * such as role, username, phone number, avatar, points, location,
     * if user is a referral code owner or used a referral code before, etc
     *
     * @return
     */
    @Transactional(readOnly = true)
    public UserEntityDto getUserProfile(){
        String userFirebaseId = getUserFirebaseId();
        UserEntity userEntity = userEntityRepository.findByFirebaseId(userFirebaseId).orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        return  userEntityToDto(userEntity);
    }

    private UserEntityDto userEntityToDto(UserEntity userEntity){

        // Get user's dominant role (1. admin, 2. auctioneer, 3. bidder)
        String userRole = getDominantRole(userEntity.getRoles());

        // Get user's location
        Location location = userEntity.getLocation();

        LocationDto locationDto = new LocationDto(
                location.getCountry(),
                location.getRegion(),
                location.getCity(),
                location.getAddressLine(),
                location.getPostalCode()
        );

        //Todo: can also return owner's referral code to view in his profile

        // Find if user is a referral code owner
        Boolean isReferralCodeOwner  = referralCodeRepository.existsByOwner_FirebaseId(getUserFirebaseId());
        // Initialize the parameter that shows if a user has used a referral code with false
        Boolean hasUsedReferralCode = Boolean.FALSE;
        // find the referral code that user used, if user didn't use a referral code return null
        ReferralCodeUsage usedReferralCode = referralCodeUsageRepository.findByUser_FirebaseId(getUserFirebaseId()).orElse(null);
        // Initialize the parameter that shows the used referral code with Not Used
        String code = "Not Used";
        if(usedReferralCode != null) { // If user used a referral code get the code and mark with true
            hasUsedReferralCode = Boolean.TRUE;
            code = usedReferralCode.getReferralCode().getCode();
        }

        return new UserEntityDto(
                userEntity.getUsername(),userEntity.getEmail(),userEntity.getPhoneNumber()
                ,userEntity.getAvatar().getUrl(), userEntity.getAvatar().name(),userEntity.getRewardPoints(),
                userRole,userEntity.isEligibleForChat(),locationDto,userEntity.getAllTimeRewardPoints(),isReferralCodeOwner,hasUsedReferralCode,code);
    }


    /**
     * This method is called when a user
     * wants to change his username
     *
     * @param username ,  user's new username
     */
    @Transactional
    public void updateUsername(String username){

        // Get user's current record from database
        UserEntity userEntity = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(()-> new ResourceNotFoundException("User not found"));

        String pastUserName = userEntity.getUsername();

        // New username cannot be the same as the past username
        if(pastUserName.equals(username))
            throw new IllegalArgumentException("You already have username: " + username);

        // All users' usernames must be different from each other
        if(userEntityRepository.existsByUsername(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"User with username "+ username + "already exists");

        userEntity.setUsername(username);

        // Update updated date info (auditing, analytics)
        userEntity.setProfileLastUpdatedAt(LocalDateTime.now());

        // Save user
        userEntityRepository.save(userEntity);

        // Logging in database
        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_USERNAME, "User: " + getUserFirebaseId()+ " changed his username from: " + pastUserName + "to: " + userEntity.getUsername() );

        // Update user's username (display name) in firebase
        UpdateRequest request = new UpdateRequest(getUserFirebaseId())
                .setDisplayName(username);
        try {
            firebaseAuth.updateUser( //todo:  apply retry service and after commit
                    new UpdateRequest(getUserFirebaseId()).setDisplayName(username)
            );
        } catch (FirebaseAuthException e) {
            emailService.sendSimpleEmailAsync("bidnowapp@gmail.com","Username not updated in firebase",
                    "User with past username: " + pastUserName + " and firebaseId: " + getUserFirebaseId() +
                            " changed his username to " + username
                            + " in database but an error: " + e +  " , has occurred and it wasn't changed on Firebase.\n\n" +
                            "PLEASE CHECK AND CHANGE USER'S FIREBASE DISPLAY NAME TO:\n " + username);

        }
    }

    /**
     * This method is called when a user
     * wants to change his avatar
     *
     * @param avatar , Enum parameter of user's {@link Avatar} choice
     */
    @Transactional
    public void updateAvatar(Avatar avatar){
        // Retrieve user's record from DB
        UserEntity userEntity = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                        .orElseThrow(()-> new ResourceNotFoundException("User not found"));

        userEntity.setAvatar(avatar);

        // Update updated date info (auditing, analytics)
        userEntity.setProfileLastUpdatedAt(LocalDateTime.now());
        // Save user's record with the new avatar to database
        userEntityRepository.save(userEntity);
        // Logging
        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_AVATAR, "User " + getUserFirebaseId() + " changed his avatar to: " + userEntity.getAvatar());
    }


    /**
     * This method is called when a user
     * wants to delete his account
     *
     * @throws FirebaseAuthException
     */
    @Transactional
    public void anonymizeUser() throws FirebaseAuthException {
        String firebaseId = getUserFirebaseId();
        // Retrieve user's record from DB
        UserEntity userEntity = userEntityRepository.findByFirebaseId(firebaseId).orElseThrow(()->new ResourceNotFoundException("User was not found"));
        // Anonymize data that a user can be identified from
        userEntity.setUsername("deleted_user_" + userEntity.getId());
        userEntity.setEmail("anonymized_" + userEntity.getId() + "@example.com");
        userEntity.setAnonymized(true);
        userEntity.setFirebaseId("deleted_firebase_" + userEntity.getId());
        userEntity.setPhoneNumber("XXXXXX");
        userEntity.setAvatar(Avatar.DEFAULT);

        // Delete location row
        Location location = userEntity.getLocation();
        if (location != null) {
            userEntity.setLocation(null);
            location.setUser(null);
            locationRepository.delete(location); // Delete user's location row from DB
        }
        userEntityRepository.save(userEntity);
        // Logging
        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_USERNAME, "User: " + firebaseId  + " deleted his account");

        firebaseRetryService.deleteUserFromFirebase(firebaseId);
    }



    /**
     * Assign roles to set to store roles into user table
     */
    public Set<Role> assignRoles(String role){
        Set<Role> roles = new HashSet<>();

        if(role.equals("Auctioneer")) {
            roles.add(roleRepository.findByName("Auctioneer").orElseThrow(() -> new ResourceNotFoundException("Role not found!")));
            roles.add(roleRepository.findByName("Bidder").orElseThrow(() -> new ResourceNotFoundException("Role not found!")));
        }else if(role.equals("Bidder")){
            roles.add(roleRepository.findByName("Bidder").orElseThrow(() -> new ResourceNotFoundException("Role not found!")));
        }else return null;
        return roles;
    }


    /**
     * This method is called when a user
     * wants to change his location
     *
     * @param locationDto, user's new location info
     */
    @Transactional
    public void updateLocation(LocationDto locationDto) {
        if(!lowerExceptFirst(locationDto.country()).equals("Cyprus"))
            throw new IllegalArgumentException("Country must be Cyprus");

        UserEntity user = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(()->new ResourceNotFoundException("User not found"));

        Location location = locationRepository.findByUserFirebaseId(getUserFirebaseId())
                .orElseThrow(()->new ResourceNotFoundException("Location was not found"));

        location.setCountry(locationDto.country());
        location.setRegion(locationDto.region());
        location.setCity(locationDto.city());
        location.setAddressLine(locationDto.addressLine());
        location.setPostalCode(locationDto.postalCode());
        locationRepository.save(location);
        // Update updated date info (auditing, analytics)
        user.setProfileLastUpdatedAt(LocalDateTime.now());
        userEntityRepository.save(user);
        // Logging
        userActivityService.saveUserActivityAsync(Endpoint.USER_UPDATE_LOCATION, "User: " + getUserFirebaseId() +  " changed his Location");

    }


    /**
     * This method is called when a user
     * wants to upgrade his role from Bidder to Auctioneer
     *
     * todo: must change function name
     *
     * @param newRole, user's new {@link Role}
     */
    @Transactional
    public void updateRole(String newRole){
        // User can no longer go back to Bidder role
        if(newRole.equals("Bidder"))
            throw new IllegalArgumentException("You cannot go back to role Bidder");
        if (!isRoleValid(newRole))
            throw new IllegalArgumentException("Provided an invalid role: " + newRole);

        // Get user's firebase id
        String firebaseId = getUserFirebaseId();

        // Retrieve user's record from DB
        UserEntity userEntity = userEntityRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User with firebase id: { " + firebaseId + " } was not found"));

        // If user already has role Auctioneer or is admin throw exception
        String oldRole = getDominantRole(userEntity.getRoles());
        if (oldRole.equalsIgnoreCase("Admin") || oldRole.equalsIgnoreCase("Auctioneer") )
            throw new IllegalArgumentException("You cannot change your role!");

        // Create a set with the new role
        Set<Role> roles = assignRoles(newRole);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("User didn't provide role");
        }

        userEntity.setProfileLastUpdatedAt(LocalDateTime.now());
        // Set user's roles
        userEntity.setRoles(roles);
        userEntityRepository.save(userEntity);

        // Logging
        userActivityService.saveUserActivityAsync(
                Endpoint.USER_UPDATE_ROLE,
                "User " + firebaseId + " updated role from " + oldRole + " to " + newRole
        );

        // Update user's roles in firebase
        List<String> roleList = UserEntityHelper.assignRolesList(userEntity.getRoles());
        try{
            firebaseRetryService.setFirebaseClaims(firebaseId, Map.of("roles", roleList));
        }catch (Exception e) {
            throw new FirebaseConnectionException("Unexpected exception when communicating with firebase " + e.getMessage(), e);
        }
    }

    /**
     * This method is called when a user logins,
     * and it's purpose is to send user's information to
     * the fronted for the proper user experience
     *
     * @return, AuthUserDto with user information for the frontend
     */
    @Transactional(readOnly = true)
    public AuthUserDto signIn() {
        UserEntity userEntity = userEntityRepository.findByFirebaseId(getUserFirebaseId())
                .orElseThrow(()-> new IllegalArgumentException("User Not Found"));

        Boolean isReferralCodeOwner = referralCodeRepository.existsByOwner_FirebaseId(getUserFirebaseId());

        return new AuthUserDto(userEntity.getUsername(),getDominantRole(userEntity.getRoles()),isReferralCodeOwner);

    }
}




