package com.portfolio.rag.repository;

import com.portfolio.rag.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByUserId(Long userId, Pageable pageable);

    Optional<Document> findByIdAndUserId(Long id, Long userId);
}
