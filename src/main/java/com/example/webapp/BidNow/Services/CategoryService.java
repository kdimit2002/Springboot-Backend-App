package com.example.webapp.BidNow.Services;

import com.example.webapp.BidNow.Dtos.CategoryDto;
import com.example.webapp.BidNow.Entities.Category;
import com.example.webapp.BidNow.Enums.Endpoint;
import com.example.webapp.BidNow.Repositories.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CategoryService {

    private final UserActivityService userActivityService;
    private final CategoryRepository categoryRepository;

    public CategoryService(UserActivityService userActivityService, CategoryRepository categoryRepository) {
        this.userActivityService = userActivityService;
        this.categoryRepository = categoryRepository;
    }

    // 🔹 helper για καθάρισμα ονόματος
    private String normalizeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
            name = name.substring(1, name.length() - 1);
        }
        return name;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAll() {
        return categoryRepository.findAll()
                .stream()
                .map(c -> new CategoryDto(c.getId(), normalizeName(c.getName())))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found"));
        return normalizeName(category.getName());
    }

    @Transactional
    public Category create(String categoryName) {
        String cleaned = normalizeName(categoryName);

        // activity log
        userActivityService.saveUserActivityAsync(
                Endpoint.CREATE_CATEGORY,
                "Admin created category: " + cleaned
        );

        // έλεγχος για διπλό όνομα
        if (categoryRepository.existsByNameIgnoreCase(cleaned)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category name already exists"
            );
        }

        Category category = new Category();
        category.setName(cleaned);

        try {
            return categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Category name must be unique",
                    e
            );
        }
    }

    @Transactional
    public Category update(Long id, String categoryName) {

        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found"));

        String cleaned = normalizeName(categoryName);

        userActivityService.saveUserActivityAsync(
                Endpoint.UPDATE_CATEGORY,
                "Admin updated category name from: " + existing.getName()
                        + " to: " + cleaned
        );

        // αν θες, μπορείς να ελέγξεις ξανά για duplicate όνομα
        if (!existing.getName().equalsIgnoreCase(cleaned) &&
                categoryRepository.existsByNameIgnoreCase(cleaned)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Category name already exists");
        }

        existing.setName(cleaned);

        return categoryRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Category not found");
        }
        userActivityService.saveUserActivityAsync(
                Endpoint.DELETE_CATEGORY,
                "Admin deleted category: " + id
        );
        categoryRepository.deleteById(id);
    }
}