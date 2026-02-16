package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.*;
import com.example.webapp.BidNow.Entities.*;
import com.example.webapp.BidNow.Enums.AuctionStatus;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Enums.NotificationType;
import com.example.webapp.BidNow.Exceptions.FirebaseConnectionException;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.*;
import com.example.webapp.BidNow.RetryServices.FirebaseRetryService;
import com.example.webapp.BidNow.helpers.UserEntityHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static com.example.webapp.BidNow.Configs.CacheConfig.AUCTIONS_DEFAULT_CACHE;
import static com.example.webapp.BidNow.helpers.UserEntityHelper.getDominantRole;
import static com.example.webapp.BidNow.helpers.UserEntityHelper.isRoleValid;


/**
 * Admin service for managing users
 *
 */
@Service
public class AdminUserEntityService {


    private static final Logger log = LoggerFactory.getLogger(AdminUserEntityService.class);
    private final BidRepository bidRepository;

    private final ApplicationEventPublisher eventPublisher;


    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralCodeUsageRepository referralCodeUsageRepository;
    private final AuctionChatService auctionChatService;
    private final AuctionRepository auctionRepository;
    private final FirebaseRetryService firebaseRetryService;
    private final UserEntityRepository userEntityRepository;
    private final UserEntityService userEntityService;
    private final UserActivityService userActivityService;

    private final FirebaseAuth firebaseAuth;

    public AdminUserEntityService(BidRepository bidRepository, ApplicationEventPublisher eventPublisher, ReferralCodeRepository referralCodeRepository, ReferralCodeUsageRepository referralCodeUsageRepository, AuctionChatService auctionChatService, AuctionRepository auctionRepository, FirebaseRetryService firebaseRetryService, UserEntityRepository userEntityRepository, UserEntityService userEntityService, UserActivityService userActivityService, FirebaseAuth firebaseAuth) {
        this.bidRepository = bidRepository;
        this.eventPublisher = eventPublisher;
        this.referralCodeRepository = referralCodeRepository;
        this.referralCodeUsageRepository = referralCodeUsageRepository;
        this.auctionChatService = auctionChatService;
        this.auctionRepository = auctionRepository;
        this.firebaseRetryService = firebaseRetryService;
        this.userEntityRepository = userEntityRepository;
        this.userEntityService = userEntityService;
        this.userActivityService = userActivityService;
        this.firebaseAuth = firebaseAuth;
    }


    /**
     *
     * Editing user's information
     *
     * Notes:
     *  - Phone number cannot be changed
     *  - User can be anonymized here
     *  - User can be disabled here
     *  - Firebase auth is updated along with system's DB if needed.
     *
     *
     * @param firebaseId firebases' id is used for user's identification id
     * @param userEntityUpdateAdmin dto that is sent from admin with the changes
     * @return , dto with the changes
     * @throws FirebaseAuthException
     */
    //todo: remove firebaseId from method's parameters
    @Transactional
    @CacheEvict(cacheNames = AUCTIONS_DEFAULT_CACHE, allEntries = true)
    public AdminUserEntityDto updateUser(String firebaseId, UserEntityUpdateAdmin userEntityUpdateAdmin) throws FirebaseAuthException {


        // Find user in database, If no user exists with this firebase id, throw exception
        UserEntity userEntity = userEntityRepository.findByFirebaseId(firebaseId).
                orElseThrow(() -> new ResourceNotFoundException("User: " + firebaseId + " not found"));



        // If admin pressed to anonymize user in database and delete user from firebase
        // todo: make a func
        if (userEntityUpdateAdmin.isAnonymized()) {

            disableUserActions(userEntity);//Disable users active bids and auctions

            //todo delete location row
            userEntity.setUsername("deleted_user_" + userEntity.getId());
            userEntity.setEmail("anonymized_" + userEntity.getId() + "@example.com");
            userEntity.setAnonymized(true);
            userEntity.setFirebaseId("deleted_firebase_" + userEntity.getId());
            userEntity.setPhoneNumber("XXXXXX");
            userEntity.setAvatar(Avatar.DEFAULT);
            userEntity.setEligibleForChat(false);
            userEntityRepository.save(userEntity);
            firebaseRetryService.deleteUserFromFirebase(firebaseId);// todo: delete in async thread
            return userEntityToDto(userEntity);
        }

        // For checking if there is a reason calling firebase update user api
        boolean hasFirebaseChanges = !(userEntity.getBanned() == userEntityUpdateAdmin.isBanned() &&
                userEntity.getEmail().equals(userEntityUpdateAdmin.email()) &&
                userEntity.getUsername().equals(userEntityUpdateAdmin.username()));

        // For checking if there is a reason calling firebase user set claims api
        boolean hasClaimsChange = !getDominantRole(userEntity.getRoles()).equals(userEntityUpdateAdmin.role());

        // Check if admin provided an invalid role ( for example: biddr instead of bidder)
        if (!isRoleValid(userEntityUpdateAdmin.role()))
            throw new RuntimeException("Provided an invalid role: " + userEntityUpdateAdmin.role());

        Set<Role> roles = userEntityService.assignRoles(userEntityUpdateAdmin.role());
        if (roles == null) throw new IllegalArgumentException("User didn't provide role");
        // Update users fields
        updateFromDtoToUserEntity(userEntity, userEntityUpdateAdmin, roles);

        //Save user to database
        userEntityRepository.save(userEntity);
        userActivityService.saveUserActivityAsync(Endpoint.ADMIN_UPDATE_USER,"Admin updated user: "+ userEntity.getFirebaseId());


        // Find if user is a referral code owner
        Boolean isReferralCodeOwner = false;
        String ownerCode="";
        ReferralCode usersReferralCode = referralCodeRepository.findByOwner_FirebaseId(userEntity.getFirebaseId())
                .orElse(null);

        if(usersReferralCode != null){
            isReferralCodeOwner = true;
            ownerCode = usersReferralCode.getCode();
        }

        // Find if user has used a referral code
        ReferralCodeUsage usedReferralCode = referralCodeUsageRepository.findByUser_FirebaseId(userEntity.getFirebaseId()).orElse(null);
        String code = "Not used";
        Boolean hasUsedReferralCode = false;
        if(usedReferralCode!=null){
            hasUsedReferralCode=true;
            code = usedReferralCode.getReferralCode().getCode();
        }

        // if there are not any firebase changes stop here
        if (!hasFirebaseChanges) return new AdminUserEntityDto
                (
                        userEntity.getId(),
                        userEntityUpdateAdmin.username(), userEntityUpdateAdmin.email(), userEntity.getPhoneNumber()
                        , userEntity.getFirebaseId(), userEntityUpdateAdmin.rewardPoints(),userEntity.getAllTimeRewardPoints(), userEntityUpdateAdmin.avatar().getUrl(),
                        userEntityUpdateAdmin.role(), userEntity.getBanned(), userEntity.getAnonymized(), userEntity.isEligibleForChat(),
                        userEntityUpdateAdmin.locationDto(),isReferralCodeOwner,ownerCode,hasUsedReferralCode, code
                );

        // Create a firebase update request object with the values of the changed user entity
        UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(firebaseId);

        updateRequest.setEmail(userEntityUpdateAdmin.email());
        updateRequest.setDisplayName(userEntityUpdateAdmin.username());
        updateRequest.setDisabled(userEntityUpdateAdmin.isBanned());


        //Todo:retry service
        try {
            // Send the request with the changes to update user in firebase

            firebaseAuth.updateUser(updateRequest);
            if(userEntityUpdateAdmin.isBanned())firebaseAuth.revokeRefreshTokens(firebaseId);//Todo: check if correct also apply retry service

            List<String> roleList = UserEntityHelper.assignRolesList(userEntity.getRoles());

            if (hasClaimsChange) firebaseAuth.setCustomUserClaims(firebaseId, Map.of("roles", roleList));//todo: retry service
        } catch (Exception e) {
            throw new FirebaseConnectionException("Unexpected exception when communicating with firebase " + e.getMessage(), e);
        }

        return new AdminUserEntityDto
                (
                        userEntity.getId(),
                        userEntityUpdateAdmin.username(), userEntityUpdateAdmin.email(), userEntity.getPhoneNumber()
                        , userEntity.getFirebaseId(), userEntityUpdateAdmin.rewardPoints(), userEntity.getAllTimeRewardPoints(), userEntityUpdateAdmin.avatar().getUrl(),
                        userEntityUpdateAdmin.role(), userEntity.getBanned(), userEntity.getAnonymized(), userEntityUpdateAdmin.eligibleForChat(), userEntityUpdateAdmin.locationDto()
                        ,isReferralCodeOwner,ownerCode,hasUsedReferralCode,code
                );
    }

    /**
     * Applies the admin update DTO onto an existing UserEntity.
     *
     * Notes:
     * - If the admin bans a user (and the user was not banned before), we also disable active user actions
     *   (bids and auctions) to prevent further activity.
     * - allTimeRewardPoints is treated as a lifetime earned metric:
     *   it increases only when rewardPoints increases (reductions do not decrease the all-time total).
     *
     * @param user existing user entity loaded from the database (will be mutated)
     * @param userEntityUpdateAdmin admin-provided updated values
     * @param roles resolved set of roles to assign to the user
     */
    private void updateFromDtoToUserEntity(UserEntity user, UserEntityUpdateAdmin userEntityUpdateAdmin, Set<Role> roles) {
        if (userEntityUpdateAdmin.isBanned() && !user.getBanned())
            //Disable users active bids and auctions if user was banned by admin
            disableUserActions(user);// todo: move this in main service method

        user.setBanned(userEntityUpdateAdmin.isBanned());

        Long oldPoints = user.getRewardPoints();
        Long newPoints = userEntityUpdateAdmin.rewardPoints();

        user.setRewardPoints(userEntityUpdateAdmin.rewardPoints());
        // All-time reward points increases only when rewardPoints increases (reductions do not decrease the all-time total)
        user.setAllTimeRewardPoints((newPoints >  oldPoints) ? (user.getAllTimeRewardPoints() + newPoints - oldPoints) : user.getAllTimeRewardPoints() );// 50 , 50     125 , 125   ~   25,  50   ->    125, 150   -> 5
        user.setEmail(userEntityUpdateAdmin.email());
        user.setAnonymized(userEntityUpdateAdmin.isAnonymized());
        user.setRoles(roles);
        user.setAvatar(userEntityUpdateAdmin.avatar());
        user.setEligibleForChat(userEntityUpdateAdmin.eligibleForChat());

        Location location = user.getLocation();
        LocationDto locationDto = userEntityUpdateAdmin.locationDto();

        location.setCountry(locationDto.country());
        location.setRegion(locationDto.region());
        location.setCity(locationDto.city());
        location.setAddressLine(locationDto.addressLine());
        location.setPostalCode(locationDto.postalCode());
    }


    /**
     * Disable user Actions
     *
     * If user is set disabled by admin cancel his bids
     * and his auctions.
     *
     * @param userEntity
     */
    @Transactional
    public void disableUserActions(UserEntity userEntity) {
        //Todo: admin message in chat or notification that user was disabled so his bid was deleted

        // Find disabled user's list of bids
        List<Bid> bids = bidRepository.findByBidderId(userEntity.getId());

        // Disable user's bids
        for (Bid bid : bids) bid.setEnabled(false);

        // Notify Users that this user's bids were disabled //

        // Find auctions that user bid
        List<Auction> auctionsWhereUserBid = auctionRepository.findDistinctByBids_Bidder_Id(userEntity.getId());


        // Find the auction's that user bided
        for (Auction auction : auctionsWhereUserBid){
            // Notify every bidder in each auction that the disabled user bidded
            for(Bid bid : auction.getBids()){
                eventPublisher.publishEvent(new NotificationEvent(bid.getBidder().getId(), NotificationType.BID_CANCELLED,"Bidder +" + bid.getBidder().getUsername() + "'s bids were cancelled",
                        "We would like to inform you that Bidder "+ bid.getBidder().getUsername()  +"'s bids were disabled in auction " + auction.getTitle() + " has been cancelled, due to bidder's inappropriate behaviour",
                        "{"
                                + "\"auctionId\":" + auction.getId()
                                + "}"
                ) );
            }

        }

        // Find the auctions that the disabled user created
        List<Auction> auctions = auctionRepository.findByOwnerId(userEntity.getId());
        if (!auctions.isEmpty()) {
            // Notify - send email to bidnow and auction participants for the disabling event of the user.
            for (Auction auction : auctions) {
                    auction.setStatus(AuctionStatus.CANCELLED);

                    // Send email to all participants that auctioneer x cancelled the auction
                    List<UserEntity> disabledUserAuctionBidders = bidRepository.findDistinctBiddersByAuctionId(auction.getId());
                    eventPublisher.publishEvent(new EmailEvent(
                            "bidnowapp@gmail.com",
                            "User Actions (bids, auctions) were disabled",
                            "User with id: " + userEntity.getId() + " has had his auctions and bids disabled."
                    ));
                    disabledUserAuctionBidders.forEach(u -> {
                        String subject = "Auction \"" + auction.getTitle() + "\" was cancelled";

                        String body = """
                                Hello %s,
                                
                                We would like to inform you that the auction "%s" has been cancelled.
                                
                                Description:
                                %s
                                
                                We apologize for any inconvenience this may cause.
                                
                                Best regards,
                                BidNow Team
                                """.formatted(
                                u.getUsername(),
                                auction.getTitle(),
                                auction.getDescription() != null ? auction.getDescription() : "-"
                        );

                        // email, title(subject), description(body)
                        eventPublisher.publishEvent(new EmailEvent(
                                u.getEmail(),
                                subject,body
                        ));
                        eventPublisher.publishEvent(new NotificationEvent(u.getId(), NotificationType.AUCTION_CANCELLED,"Auction \"" + auction.getTitle() + "\" was cancelled",
                                "We would like to inform you that the auction " + auction.getTitle()  + " has been cancelled, due to auctioneers inappropriate behaviour",
                                 "{"
                                + "\"auctionId\":" + auction.getId()
                                + "}"
                        ) );
                    });


            }
        }
    }


    /**
     * Admin to get user's detailed information
     *
     * @param firebaseId
     * @return
     */
    @Transactional(readOnly = true)
    public AdminUserEntityDto getUser(String firebaseId) {
        UserEntity userEntity = userEntityRepository.findByFirebaseId(firebaseId).orElseThrow(() -> new ResourceNotFoundException("User with firebase id: { " + firebaseId + " }  was not found"));
        return userEntityToDto(userEntity);
    }

    /**
     *
     * Turn the existing UserEntity to a DTO in order to give it to admin
     *
     * @param userEntity
     * @return
     */
    public AdminUserEntityDto userEntityToDto(UserEntity userEntity) {
        if (userEntity.getRoles() == null || userEntity.getRoles().isEmpty()) {// Good to check if user doesn't have a role
            log.error("User {} doesn't have a role something is wrong", userEntity.getFirebaseId());
            eventPublisher.publishEvent(new EmailEvent(
                    "bidnowapp@gmail.com",
                    "User " + userEntity.getFirebaseId() + " was found without roles",
                    "There was a user found without roles when admin tried to access him when admin tried to access him."
            ));

        }
        String userRole = getDominantRole(userEntity.getRoles());

        Location location = userEntity.getLocation();
        LocationDto locationDto = new LocationDto(
                location.getCountry(),
                location.getRegion(),
                location.getCity(),
                location.getAddressLine(),
                location.getPostalCode()
        );
        Long p = userEntity.getAllTimeRewardPoints();


        Boolean isReferralCodeOwner = false;
        String ownerCode="";
        ReferralCode usersReferralCode = referralCodeRepository.findByOwner_FirebaseId(userEntity.getFirebaseId())
                .orElse(null);

        if(usersReferralCode != null){
            isReferralCodeOwner = true;
            ownerCode = usersReferralCode.getCode();
        }

        ReferralCodeUsage usedReferralCode = referralCodeUsageRepository.findByUser_FirebaseId(userEntity.getFirebaseId()).orElse(null);
        String code = "Not Used";
        Boolean hasUsedReferralCode = false;
        if(usedReferralCode!=null){
            hasUsedReferralCode=true;
            code = usedReferralCode.getReferralCode().getCode();
        }



        return new AdminUserEntityDto(
                userEntity.getId(),
                userEntity.getUsername(), userEntity.getEmail(), userEntity.getPhoneNumber()
                , userEntity.getFirebaseId(), userEntity.getRewardPoints(), userEntity.getAllTimeRewardPoints() ,  userEntity.getAvatar().getUrl(),
                userRole, userEntity.getBanned(), userEntity.getAnonymized(), userEntity.isEligibleForChat(),
                locationDto,isReferralCodeOwner,ownerCode,hasUsedReferralCode,code);
    }

    /**
     * Get users (paginated), with optional search.
     *
     * Admin can view system's users
     *
     * @param page
     * @param size
     * @param search
     * @param searchBy
     * @return
     */
    @Transactional(readOnly = true)
    public Page<AdminUserEntityDto> getUsersPage(int page, int size,
                                                 String search,
                                                 String searchBy) {

        if (size > 100) size = 100;
        if (size <= 0) size = 20;
        if (page < 0) page = 0;

        if ((long) page * size > 200000) { // Todo: Maybe change in future
            throw new IllegalArgumentException("Page number too large");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());

        Page<UserEntity> userPage;

        // Αν δεν έχει δοθεί search -> γύρνα όλα
        if (search == null || search.isBlank()) {
            userPage = userEntityRepository.findAll(pageable);
        } else {
            switch (searchBy) {
                case "firebaseId":
                    userPage = userEntityRepository
                            .findByFirebaseIdContainingIgnoreCase(search, pageable);
                    break;

                case "username":
                    userPage = userEntityRepository
                            .findByUsernameContainingIgnoreCase(search, pageable);
                    break;

                case "id":
                default:
                    Long id;
                    try {
                        id = Long.parseLong(search);
                    } catch (NumberFormatException ex) {
                        // Αν δεν είναι αριθμός, γύρνα empty σελίδα
                        userPage = Page.empty(pageable);
                        return userPage.map(this::userEntityToDto);
                    }
                    userPage = userEntityRepository.findById(id, pageable);
                    break;
            }
        }

        return userPage.map(this::userEntityToDto);
    }

    public AdminUserEntityDto getUserByUsername(String username) {
        UserEntity user = userEntityRepository.findByUsername(username).orElseThrow(()->new IllegalArgumentException("User doesn't exists"));
        return userEntityToDto(user);
    }


}