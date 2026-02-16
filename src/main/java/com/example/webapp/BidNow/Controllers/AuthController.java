package com.example.webapp.BidNow.Controllers;


import com.example.webapp.BidNow.Dtos.*;
import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import com.example.webapp.BidNow.Services.SignupService;
import com.example.webapp.BidNow.Services.UserActivityService;
import com.example.webapp.BidNow.Services.UserEntityService;
import com.google.firebase.auth.FirebaseAuthException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

import static com.example.webapp.BidNow.helpers.GeneralHelper.lowerExceptFirst;
import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;


/**
 * @Author Kendeas
 *
 * Authentication & profile controller.
 *
 * Base path: /api/auth
 * Provides:
 * - Signup
 * - Login
 * - Profile
 * - Updates (avatar/username/location/role)
 * - Account deletion
 * - Checks for username/user availability.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {


    //TODO: STO SIGNUP AN O USER EKANE IDI FIREBASE ACCOUNT NA TOU DIXNI TA DETAILS USERNAME SE POPUP KE NA OU LEI EXETE IDI ACCOUNT THELETE NA SINEXISETE I DIAGRAFI

    private final UserEntityRepository userEntityRepository;
    private final UserActivityService userActivityService;
    private final UserEntityService userEntityService;
    private final SignupService signupService;

    public AuthController(UserEntityRepository userEntityRepository, UserActivityService userActivityService, UserEntityService userEntityService, SignupService signupService) {
        this.userEntityRepository = userEntityRepository;
        this.userActivityService = userActivityService;
        this.userEntityService = userEntityService;
        this.signupService = signupService;
    }


    /**
     * Register a new user.
     *
     * POST /api/auth/signup
     *
     * This controller runs a sequence of DB and Firebase auth DB requests
     * in order to make users' info across the two databases consistent
     *
     * @param signUpRequest signup payload (avatar, role, location, etc.)
     * @return AuthUserDto for the newly registered user
     */
    @PostMapping(value = "/signup")
    public ResponseEntity<AuthUserDto> registerUser(@Valid @RequestBody SignUpRequest signUpRequest) throws IOException, FirebaseAuthException {
        LocationDto location = signUpRequest.locationDto(); // todo: logic must be removed from here
        String country = lowerExceptFirst(location.country());
        if(!country.equals("Cyprus"))throw new IllegalArgumentException("Country must be Cyprus");// Clients must in Cyprus
        Avatar userAvatar = signUpRequest.avatar();
        String userRole = lowerExceptFirst(signUpRequest.roleName());

        AuthUserDto authUserDto = signupService.saveUser(userAvatar, userRole,location);
        return ResponseEntity.ok(authUserDto);
    }

    /**
     * Sign in the current user.
     *
     * This api is just for logging and
     * giving user the correct user parameters (AuthUseDto)
     * for the successful user experience
     *
     * GET /api/auth/login
     *
     * @return AuthUserDto for the signed-in user
     */
    @GetMapping(value = "/login")
    public ResponseEntity<AuthUserDto> signIn(){
        userActivityService.saveUserActivityAsync(Endpoint.USER_SIGNIN,"User:" + getUserFirebaseId() + " just signed in");
        AuthUserDto authUserDto = userEntityService.signIn();
        return ResponseEntity.ok(authUserDto);
    }

    /**
     * Delete the current user account.
     *
     * This api is called when user wants to delete his account.
     * It anonymizes user's private information in DB and delete
     * user's account from firebase.
     *
     * DELETE /api/auth/deleteUser
     *
     * Notes:
     * - Uses anonymization in DB
     * - Firebase deletion.
     *
     * @return 204 No Content when deletion/anonymization succeeds
     */
    @DeleteMapping(value = "/deleteUser")
    public ResponseEntity<Void> deleteUser() throws FirebaseAuthException {
        userEntityService.anonymizeUser();
        return ResponseEntity.noContent().build();
    }


    /**
     * Get current user's profile.
     *
     *
     * GET /api/auth/profile
     *
     * @return 200 OK with UserEntityDto if found, otherwise 404 Not Found
     */
    @GetMapping(value = "/profile")
    public ResponseEntity<UserEntityDto> getUserProfile(){
        UserEntityDto user = userEntityService.getUserProfile();
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }


    /** Request dto for updating username/name. */
    public record UserUpdateRequest(
            @NotBlank(message = "Cannot be blank")
            String name
    ) {}
    /** Simple response dto for update operations. */
    public record UserUpdateResponse(
            String name
    ) {}


    /**
     * Update user's avatar.
     *
     * PATCH /api/auth/updateAvatar
     *
     * @param avatar new avatar selection
     * @return updated avatar name
     */
    @PatchMapping(value = "/updateAvatar")
    public ResponseEntity<UserUpdateResponse> updateAvatar(@Valid @RequestBody Avatar avatar){
        userEntityService.updateAvatar(avatar);
        return ResponseEntity.ok(new UserUpdateResponse(avatar.name()));
    }

    /**
     * Update user's username.
     *
     * PATCH /api/auth/updateUsername
     *
     * @param usernameUpdateRequest new username payload
     * @return updated username
     */
    @PatchMapping(value = "/updateUsername")
    public ResponseEntity<UserUpdateResponse> updateUsername(@Valid@RequestBody UserUpdateRequest usernameUpdateRequest){
        userEntityService.updateUsername(usernameUpdateRequest.name());
        return ResponseEntity.ok(new UserUpdateResponse(usernameUpdateRequest.name()));
    }


    /**
     * Update user's location.
     *
     * PATCH /api/auth/updateLocation
     *
     * @param locationDto new location payload
     * @return the same location payload (confirmation)
     */
    @PatchMapping(value = "/updateLocation")
    public ResponseEntity<LocationDto> updateLocation(@Valid@RequestBody LocationDto locationDto){
        userEntityService.updateLocation(locationDto);
        return ResponseEntity.ok(locationDto);
    }

    /** Request payload for updating user role. */
    public record RoleRequest(
            @NotBlank(message = "Role name cannot be blank")
            String name
    ) {}



    /**
     * Update user's role.
     *
     * PATCH /api/auth/updateRole
     *
     * Note:
     *  - User's with role Auctioneer can't change their role to Bidder.
     *  - So only bidders can change their role to Auctioneer
     *
     * @param roleRequest role payload
     * @return updated role name
     */
    @PatchMapping(value = "/updateRole")
    public ResponseEntity<UserUpdateResponse> updateRole(@Valid @RequestBody RoleRequest roleRequest){
        userEntityService.updateRole(roleRequest.name());
        return ResponseEntity.ok(new UserUpdateResponse(roleRequest.name()));
    }


    /** Response for username availability check. */
    public record UsernameAvailabilityResponse(boolean exists) {}


    /**
     * Check if a username exists.
     *
     * This Api checks if a username is already picked by another user
     * because firebase can't provide this functionality for us
     *
     * GET /api/auth/username-availability?username=...
     *
     * @param username username to check
     * @return availability result
     *
     */
    @GetMapping(value = "/username-availability")
    public ResponseEntity<UsernameAvailabilityResponse> checkUsername(@RequestParam String username) {
        boolean exists = userEntityRepository.existsByUsername(username);
        return ResponseEntity.ok(new UsernameAvailabilityResponse(exists));
    }

    /** Request payload for checking whether a user exists (step 1 validation). */
    public record UserAvailabilityRequest(
            @Email
            String email,
            @NotBlank
            String phoneNumber
    ) {}


    /** Simple response wrapper for availability messages. */
    public record UserAvailabilityResponse(String response) {}


    /**
     * Check if user exists by email/phone number.
     * If neither email nor phone exists -> user can proceed to step 2
     *
     * POST /api/auth/user-availability
     *
     * @param request email + phone number
     * @return message describing the next step or conflict
     */
  @PostMapping(value = "/user-availability")
    public ResponseEntity<UserAvailabilityResponse> userExists(@Valid @RequestBody UserAvailabilityRequest request) {
        if(!userEntityRepository.existsByEmail(request.email()) && !userEntityRepository.existsByPhoneNumber(request.phoneNumber())){
            return ResponseEntity.ok(new UserAvailabilityResponse("You have already completed step 1. Go to step 2"));
        }
        if(userEntityRepository.existsByEmail(request.email())){
            return ResponseEntity.badRequest().body(new UserAvailabilityResponse("There is already a user with email " + request.email()));
        }
        if(userEntityRepository.existsByEmail(request.phoneNumber())){
            return ResponseEntity.badRequest().body(new UserAvailabilityResponse("There is already a user with phone number " + request.phoneNumber()));
        }

        return ResponseEntity.internalServerError().body(new UserAvailabilityResponse("Something went wrong"));
    }


    public record TokenDto(String token){}

    @GetMapping(value = "/getRefreshToken")
    public ResponseEntity<TokenDto> refreshToken(@RequestParam String username) throws FirebaseAuthException {
      String token = userEntityService.refreshToken();
      return ResponseEntity.ok(new TokenDto(token));
    }


}