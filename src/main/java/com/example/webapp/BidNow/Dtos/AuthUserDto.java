package com.example.webapp.BidNow.Dtos;

/**
 * Authentication response DTO.
 *
 * Loaded by the frontend on:
 * - login
 * - signup
 * - user re-entering the site (after refresh token flow)
 *
 * Contains basic user identity and role information.
 *
 * These must be stored globally in-memory of the users
 * browser to be accessed from any page
 */
public record AuthUserDto(
        String username,
        String roleName,
        Boolean isReferralCodeOwner
) {}

