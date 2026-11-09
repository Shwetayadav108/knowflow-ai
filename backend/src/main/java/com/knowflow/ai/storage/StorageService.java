package com.knowflow.ai.storage;

import org.springframework.core.io.Resource;
import java.io.InputStream;
import java.util.UUID;

public interface StorageService {

    String store(InputStream inputStream, String originalFilename, UUID workspaceId);

    Resource loadAsResource(String storagePath);

    void delete(String storagePath);

    default void delete(String storagePath, UUID workspaceId) {
        delete(storagePath);
    }
}
