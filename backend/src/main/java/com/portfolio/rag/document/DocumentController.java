package com.portfolio.rag.document;

import com.portfolio.rag.common.PageResponse;
import com.portfolio.rag.common.SecurityUtils;
import com.portfolio.rag.document.dto.DocumentDTO;
import com.portfolio.rag.document.dto.UploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        UploadResponse response = documentService.upload(SecurityUtils.getCurrentUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public PageResponse<DocumentDTO> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return documentService.list(SecurityUtils.getCurrentUserId(), page, size);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<UploadResponse> retry(@PathVariable Long id) {
        UploadResponse response = documentService.retry(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(SecurityUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
