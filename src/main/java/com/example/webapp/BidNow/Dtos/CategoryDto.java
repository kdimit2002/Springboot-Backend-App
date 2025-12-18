package com.example.webapp.BidNow.Dtos;

/**
 *
 * Response category dto
 * for sending categories of auctions to users
 *
 * Apis:
 *  - update
 *  - create
 *  in AdminCategoryController
 *
 *  - getAll
 *  in CategoryController
 */
public record CategoryDto(Long id, String name) {
}
