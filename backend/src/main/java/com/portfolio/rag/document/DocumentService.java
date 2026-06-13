package com.portfolio.rag.document;

import com.portfolio.rag.common.ApiException;
import com.portfolio.rag.common.EntityNotFoundException;
import com.portfolio.rag.common.PageResponse;
import com.portfolio.rag.document.dto.DocumentDTO;
import com.portfolio.rag.document.dto.UploadResponse;
import com.portfolio.rag.document.entity.Document;
import com.portfolio.rag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20 MB
    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("application/pdf", "text/plain", "text/markdown");

    private final DocumentRepository documentRepository;
    private final DocumentIngestionService ingestionService;

    public UploadResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "文件大小超过 20MB 限制");
        }

        String mimeType = detectMimeType(file);

        Path tempFile;
        try {
            tempFile = Files.createTempFile("rag-upload-", ".bin");
            file.transferTo(tempFile);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "文件保存失败");
        }

        Document document = Document.builder()
                .userId(userId)
                .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed")
                .fileSize(file.getSize())
                .mimeType(mimeType)
                .status(Document.STATUS_PENDING)
                .build();
        document = documentRepository.save(document);

        ingestionService.ingest(document.getId(), userId, tempFile, mimeType);
        return new UploadResponse(document.getId(), Document.STATUS_PENDING);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentDTO> list(Long userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(documentRepository.findByUserId(userId, pageable), DocumentDTO::from);
    }

    /**
     * Re-runs ingestion for a document stuck in the {@code error} state by
     * re-embedding its persisted chunks. The original upload is not retained,
     * so retry only applies to failures after chunking; parse-stage failures
     * leave no chunks and require a fresh upload.
     */
    public UploadResponse retry(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        if (!Document.STATUS_ERROR.equals(document.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_RETRYABLE",
                    "仅失败状态的文档可以重试");
        }

        document.setStatus(Document.STATUS_PENDING);
        document.setErrorMsg(null);
        documentRepository.save(document);

        ingestionService.reingest(document.getId(), userId);
        return new UploadResponse(document.getId(), Document.STATUS_PENDING);
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException("文档不存在"));
        // document_chunks rows cascade via FK ON DELETE CASCADE
        documentRepository.delete(document);
    }

    /**
     * Determines the actual MIME type from content, not just the extension:
     * PDFs are identified by the %PDF magic header; text files must contain no
     * NUL bytes in the sample. Anything else is rejected with 415.
     */
    private String detectMimeType(MultipartFile file) {
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(4096);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "文件读取失败");
        }

        if (head.length >= 4
                && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return "application/pdf";
        }

        if (isProbablyText(head)) {
            String name = file.getOriginalFilename() != null
                    ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
            String declared = file.getContentType();
            if (name.endsWith(".md") || name.endsWith(".markdown") || "text/markdown".equals(declared)) {
                return "text/markdown";
            }
            if (name.endsWith(".txt") || "text/plain".equals(declared)) {
                return "text/plain";
            }
            if (declared != null && SUPPORTED_MIME_TYPES.contains(declared)) {
                return declared;
            }
        }

        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "仅支持 PDF、TXT、Markdown 文件");
    }

    private boolean isProbablyText(byte[] sample) {
        if (sample.length == 0) {
            return false;
        }
        for (byte b : sample) {
            if (b == 0) {
                return false;
            }
        }
        // must be decodable as UTF-8 without garbage replacement at the start
        String decoded = new String(sample, StandardCharsets.UTF_8);
        return !decoded.isBlank();
    }
}
