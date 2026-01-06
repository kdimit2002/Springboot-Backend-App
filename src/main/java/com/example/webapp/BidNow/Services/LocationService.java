package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.LocationDto;
import com.example.webapp.BidNow.Entities.Location;
import com.example.webapp.BidNow.Entities.UserEntity;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Exceptions.ResourceNotFoundException;
import com.example.webapp.BidNow.Repositories.LocationRepository;
import com.example.webapp.BidNow.Repositories.UserEntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.webapp.BidNow.helpers.UserEntityHelper.getUserFirebaseId;

/**
 * LocationService
 *
 * Manages a user's location info (read + update) and maps it to/from LocationDto.
 */
@Service
public class LocationService {

    private final UserEntityRepository userEntityRepository;
    private final LocationRepository locationRepository;

    public LocationService(UserEntityRepository userEntityRepository,
                           LocationRepository locationRepository) {
        this.userEntityRepository = userEntityRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public LocationDto getUserLocation(String firebaseId) { // Used in setting up auctions location ( auction's location is same as it's owner)
        // Load user and return their stored location as DTO.
        UserEntity user = userEntityRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Location location = user.getLocation();

        return new LocationDto(
                location.getCountry(),
                location.getRegion(),
                location.getCity(),
                location.getAddressLine(),
                location.getPostalCode()
        );
    }

    /**
     * User can update his location details
     *
     * @param firebaseId
     * @param dto
     */
    @Transactional
    public void updateUserLocation(String firebaseId, LocationDto dto) {
        // Load user, update location fields, and persist changes.
        UserEntity user = userEntityRepository.findByFirebaseId(firebaseId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Location location = user.getLocation();

        location.setCountry(dto.country());
        location.setRegion(dto.region());
        location.setCity(dto.city());
        location.setAddressLine(dto.addressLine());
        location.setPostalCode(dto.postalCode());

        locationRepository.save(location);
    }
}