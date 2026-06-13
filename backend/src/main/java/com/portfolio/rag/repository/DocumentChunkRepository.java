package com.portfolio.rag.repository;

import com.portfolio.rag.document.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * Cosine similarity search, scoped to the owning user. The query vector is
     * passed in pgvector text format "[v1,v2,...]" and cast server-side.
     */
    @Query(value = """
            SELECT dc.id          AS "chunkId",
                   dc.document_id AS "documentId",
                   dc.content     AS "content",
                   dc.page_num    AS "pageNum",
                   d.filename     AS "filename",
                   1 - (dc.embedding <=> CAST(:queryVector AS vector)) AS "score"
            FROM document_chunks dc
            JOIN documents d ON d.id = dc.document_id
            WHERE dc.user_id = :userId
              AND dc.embedding IS NOT NULL
              AND 1 - (dc.embedding <=> CAST(:queryVector AS vector)) >= :threshold
            ORDER BY dc.embedding <=> CAST(:queryVector AS vector)
            LIMIT :k
            """, nativeQuery = true)
    List<ChunkSearchResult> searchSimilar(@Param("userId") Long userId,
                                          @Param("queryVector") String queryVector,
                                          @Param("threshold") double threshold,
                                          @Param("k") int k);

    interface ChunkSearchResult {
        Long getChunkId();

        Long getDocumentId();

        String getContent();

        Integer getPageNum();

        String getFilename();

        Double getScore();
    }
}
