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
        if (storagePath == null || storagePath.isBlank()) {
            throw new ResourceNotFoundException("Document storage path cannot be empty");
        }
        try {
            Path file = Paths.get(storagePath).toAbsolutePath().normalize();
            if (!file.startsWith(this.rootPath)) {
                throw new KnowFlowException("Access denied: path is outside document storage boundary");
            }
            if (Files.isSymbolicLink(file)) {
                throw new KnowFlowException("Security violation: symbolic links are not permitted in document storage: " + storagePath);
            }
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new ResourceNotFoundException("Document file not found: " + storagePath);
            }
            Path realFile = file.toRealPath();
            Path realRoot = this.rootPath.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new KnowFlowException("Access denied: real path is outside document storage boundary");
            }
            Resource resource = new UrlResource(realFile.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Document file not readable or not found");
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Malformed document URL path: " + storagePath);
        } catch (IOException e) {
            throw new KnowFlowException("Failed to access document file: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storagePath) {
        delete(storagePath, null);
    }

    @Override
    public void delete(String storagePath, UUID workspaceId) {
        if (storagePath == null || storagePath.isBlank()) {
            log.warn("Storage path is empty. Skipping physical file deletion.");
            return;
        }

        try {
            Path file = Paths.get(storagePath).toAbsolutePath().normalize();

            // Syntactic check: prevent path traversal outside root storage boundary
            if (!file.startsWith(this.rootPath)) {
                throw new KnowFlowException("Security violation: path is outside document storage boundary: " + storagePath);
            }

            // Enforce workspace boundary if workspaceId is provided
            if (workspaceId != null) {
                Path workspaceDir = this.rootPath.resolve(workspaceId.toString()).normalize();
                if (!file.startsWith(workspaceDir)) {
                    throw new KnowFlowException("Security violation: path escapes workspace storage boundary: " + storagePath);
                }
            }

            // Handle missing files gracefully without following symlinks
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                log.warn("Physical file does not exist at path: {}. Skipping physical deletion.", storagePath);
                return;
            }

            // Reject symbolic links explicitly
            if (Files.isSymbolicLink(file)) {
                throw new KnowFlowException("Security violation: symbolic links are not permitted in document storage: " + storagePath);
            }

            // Resolve real canonical path to prevent symlink traversal outside root or workspace directory
            Path realFile = file.toRealPath();
            Path realRoot = this.rootPath.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new KnowFlowException("Security violation: real target path is outside document storage boundary: " + storagePath);
            }

            if (workspaceId != null) {
                Path workspaceDir = this.rootPath.resolve(workspaceId.toString()).normalize();
                Path realWorkspaceDir = Files.exists(workspaceDir) ? workspaceDir.toRealPath() : realRoot.resolve(workspaceId.toString());
                if (!realFile.startsWith(realWorkspaceDir)) {
                    throw new KnowFlowException("Security violation: real target path escapes workspace boundary: " + storagePath);
                }
            }

            // Ensure target is a regular file, not a directory
            if (!Files.isRegularFile(realFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new KnowFlowException("Security violation: storage path does not refer to a regular file: " + storagePath);
            }

            // Delete physical file
            Files.delete(realFile);
            log.info("Permanently deleted physical file at: {}", storagePath);

        } catch (IOException e) {
            log.error("Failed to delete physical file {}: {}", storagePath, e.getMessage());
            throw new KnowFlowException("Failed to delete physical file: " + e.getMessage(), e);
        }
    }

    public Path getRootPath() {
        return rootPath;
    }
}
