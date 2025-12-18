package com.example.webapp.BidNow.Controllers.Admin;

import com.example.webapp.BidNow.Dtos.CategoryDto;
import com.example.webapp.BidNow.Entities.Category;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Services.CategoryService;
import com.example.webapp.BidNow.Services.UserActivityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 *
 * Admin-only REST controller for managing product/auction categories.
 *
 * Responsibilities:
 * - Create new categories
 * - Update existing categories
 * - Delete categories
 *
 * Base path: /api/admin/categories
 *
 */
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }


    /**
     * POST api for admin to create a category
     *
     * @param category category name provided as a plain text request body
     * @return 200 OK with the created category as {@link CategoryDto}
     */
    @PostMapping("/createCategory")
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody String category) {
        Category created = categoryService.create(category);
        CategoryDto dto = new CategoryDto(created.getId(), created.getName());
        return ResponseEntity.ok(dto);
    }

    /**
     * Put api for admin to update a category
     *
     * @param id       identifier of the category to update
     * @param category new category name provided as a plain text request body
     * @return 200 OK with the updated category as {@link CategoryDto}
     */
    @PutMapping("/updateCategory/{id}")
    public ResponseEntity<CategoryDto> update(@PathVariable Long id,
                                              @Valid @RequestBody String category) {
        Category updated = categoryService.update(id, category);
        CategoryDto dto = new CategoryDto(updated.getId(), updated.getName());
        return ResponseEntity.ok(dto);
    }

    /**
     * Put api for admin to update a category
     *
     * @param id identifier of the category to delete
     * @return 204 No Content when deletion succeeds
     */
    @DeleteMapping("/deleteCategory/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}