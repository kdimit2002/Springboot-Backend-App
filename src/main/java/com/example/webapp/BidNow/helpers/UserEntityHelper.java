package com.example.webapp.BidNow.helpers;

import com.example.webapp.BidNow.Dtos.AdminUserEntityDto;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

public class UserEntityHelper {


    /**
     * Assign roles to set to store roles into user table
     */
    public static List<String> assignRolesList(Set<Role> roles){
        List<String> roleList = new ArrayList<>();
        for(Role role : roles)roleList.add(role.getName());
        return roleList;
    }


    /**
     * Get user from Security Context
     */
    public static String getUserFirebaseId(){
        var ctx = SecurityContextHolder.getContext();
        var auth = ctx.getAuthentication();
        return auth.getName();
    }


    /**
     * Check if role send was valid
     */
    public static boolean isRoleValid(String role){
        return role.equals("Auctioneer") || role.equals("Bidder");
    }



    public static String getDominantRole(Set<Role> roles) {
        return roles.stream()
                .map(Role::getName)                              // παίρνουμε το όνομα
                .filter(ROLE_PRIORITY::containsKey)              // πληρούν τα defined roles
                .max(Comparator.comparingInt(ROLE_PRIORITY::get)) // βρίσκουμε τον ισχυρότερο
                .orElse("No claim");                               // default fallback
    }


    private static final Map<String, Integer> ROLE_PRIORITY = Map.of(
            "Admin",3,
            "Auctioneer", 2,
            "Bidder", 1
    );



}
