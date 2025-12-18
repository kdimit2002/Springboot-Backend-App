package com.example.webapp.BidNow.Controllers;

import com.example.webapp.BidNow.Dtos.CategoryDto;
import com.example.webapp.BidNow.Entities.Category;
import com.example.webapp.BidNow.Services.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Public controller for category endpoints.
 *
 * User's can access auction categories
 *
 * Base path: /api/categories
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Get all available categories.
     *
     * This api is for filters in the frontend.
     *
     * GET /api/categories
     *
     * @return list of categories as CategoryDto
     */    @GetMapping
    public ResponseEntity<List<CategoryDto>> getAll() {
        return ResponseEntity.ok(categoryService.getAll());
    }


    // NOT USED
    @GetMapping("/{id}")
    public ResponseEntity<String> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }
}