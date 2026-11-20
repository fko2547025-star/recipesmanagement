package com.example.recipesmanagement.contact;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    @EntityGraph(attributePaths = "user")
    List<Inquiry> findByStatusOrderByIdDesc(String status);

    long countByStatus(String status);
}
