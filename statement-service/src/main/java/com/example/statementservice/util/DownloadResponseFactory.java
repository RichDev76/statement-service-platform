package com.example.statementservice.util;

import com.example.statementservice.service.DownloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DownloadResponseFactory {

    public ResponseEntity<Resource> build(String fileName, DownloadService.DownloadStreamResult result) {
        switch (result.outcome()) {
            case OK -> {
                var resource = new InputStreamResource(result.stream().get());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", fileName);
                headers.setCacheControl("no-store, no-cache, must-revalidate");
                headers.add("Pragma", "no-cache");
                return ResponseEntity.ok().headers(headers).body(resource);
            }
            case INVALID_SIGNATURE -> {
                log.warn("Invalid signature for download - fileName: {}", fileName);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            case LINK_EXPIRED_OR_USED, STATEMENT_NOT_FOUND, FILE_MISSING -> {
                log.warn("Download link invalid/expired or resource missing - fileName: {}", fileName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            case DECRYPTION_FAILED -> {
                log.error("Decryption failed during download for fileName: {}", fileName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            default -> {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
    }
}
