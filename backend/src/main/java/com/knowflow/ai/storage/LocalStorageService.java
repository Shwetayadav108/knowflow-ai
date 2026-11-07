package com.knowflow.ai.storage;

import com.knowflow.ai.common.exception.KnowFlowException;
import com.knowflow.ai.common.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${knowflow.storage.local-dir:./storage/documents}")
    private String rootStorageDir;

    private Path rootPath;

    @PostConstruct
    public void init() {
        try {
            this.rootPath = Paths.get(rootStorageDir).toAbsolutePath().normalize();
            Files.createDirectories(this.rootPath);
            log.info("Initialized local document storage at: {}", this.rootPath);
        } catch (IOException e) {
            throw new KnowFlowException("Could not initialize document storage directory", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String originalFilename, UUID workspaceId) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new KnowFlowException("Filename cannot be empty");
        }

        // Prevent path traversal
        String cleanFilename = FilenameUtils.getName(originalFilename);
        if (cleanFilename.contains("..")) {
            throw new KnowFlowException("Security violation: path traversal sequence detected in filename");
        }

        String extension = FilenameUtils.getExtension(cleanFilename);
        String uniqueFileName = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension.toLowerCase());

        try {
            Path workspaceDir = this.rootPath.resolve(workspaceId.toString()).normalize();
            if (!workspaceDir.startsWith(this.rootPath)) {
                throw new KnowFlowException("Security violation: target path escapes root directory");
            }
            Files.createDirectories(workspaceDir);

            Path destinationPath = workspaceDir.resolve(uniqueFileName).normalize();
            if (!destinationPath.startsWith(workspaceDir)) {
                throw new KnowFlowException("Security violation: target path escapes workspace directory");
            }

            Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored document in: {}", destinationPath);

            return destinationPath.toString();
        } catch (IOException e) {
            throw new KnowFlowException("Failed to store file: " + cleanFilename, e);
        }
    }

    @Override
    public Resource loadAsResource(String storagePath) {
        try {
            Path file = Paths.get(storagePath).normalize();
            if (!file.startsWith(this.rootPath)) {
                throw new KnowFlowException("Access denied: path is outside document storage boundary");
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Document file not readable or not found");
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Malformed document URL path: " + storagePath);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path file = Paths.get(storagePath).normalize();
            if (file.startsWith(this.rootPath)) {
                Files.deleteIfExists(file);
                log.info("Deleted document file at: {}", storagePath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete physical file {}: {}", storagePath, e.getMessage());
        }
    }
}
