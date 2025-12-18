package com.example.webapp.BidNow.Repositories;

import com.example.webapp.BidNow.Entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {
    boolean existsByNameIgnoreCase(String name);

    Optional<Category> findByName(String category);

    boolean existsByName(String categoryName);
}
