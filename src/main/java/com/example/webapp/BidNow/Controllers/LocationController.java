package com.example.webapp.BidNow.Controllers;

import com.example.webapp.BidNow.Dtos.LocationDto;
import com.example.webapp.BidNow.Services.LocationService;
import com.example.webapp.BidNow.Services.UserEntityService;
import com.example.webapp.BidNow.helpers.UserEntityHelper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;


/**
 * Controller for managing the current user's location.
 *
 *
 * This API is mainly used for showing the region of
 * the auction's owner.
 *
 * Base path: /user/location
 * Provides: get current location and update location for the authenticated user.
 */
@RestController
@RequestMapping("/user/location")
public class LocationController {


    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }


    /**
     * Get the authenticated user's location.
     *
     *
     * GET /user/location
     *
     * @return user's location as LocationDto
     */
    @GetMapping
    public ResponseEntity<LocationDto> getMyLocation() {
        LocationDto location = locationService.getUserLocation(getUserFirebaseId());
        return ResponseEntity.ok(location);
    }


    /**
     * Update the authenticated user's location.
     *
     * PUT /user/location/update
     *
     * @param dto new location data
     * @return 200 OK when update succeeds
     */
    @PutMapping("/update")
    public ResponseEntity<Void> updateMyLocation(@Valid @RequestBody LocationDto dto) {
        locationService.updateUserLocation(getUserFirebaseId(), dto);
        return ResponseEntity.ok().build();
    }

    //Todo: Post request if we decide to use all the fields in the future.




}
