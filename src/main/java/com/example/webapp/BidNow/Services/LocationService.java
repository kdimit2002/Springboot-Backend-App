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


@Service
public class LocationService {


    private final UserActivityService userActivityService;
    private final UserEntityRepository userEntityRepository;
    private final LocationRepository locationRepository;


    public LocationService(UserActivityService userActivityService, UserEntityRepository userEntityRepository, LocationRepository locationRepository) {
        this.userActivityService = userActivityService;
        this.userEntityRepository = userEntityRepository;
        this.locationRepository = locationRepository;
    }



    @Transactional(readOnly = true)
    public LocationDto getUserLocation(String firebaseId) {

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



    @Transactional
    public void updateUserLocation(String firebaseId,LocationDto dto) {

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
