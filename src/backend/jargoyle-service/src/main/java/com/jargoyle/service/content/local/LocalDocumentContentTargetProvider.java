package com.jargoyle.service.content.local;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.jargoyle.service.content.DocumentContentTargetProvider;

/**
 * Returns a backend-relative URL for serving original document content in local development.
 *
 * <p>Active in all profiles except {@code prod}. The URL points to the application's
 * own streaming endpoint, which loads the file from local filesystem storage.</p>
 */
@Service
@Profile("!prod")
public class LocalDocumentContentTargetProvider implements DocumentContentTargetProvider {

    @Override
    public String createContentUrl(UUID documentId, String storageKey, String originalFilename) {
        return "/api/documents/%s/original/stream".formatted(documentId);
    }
}
