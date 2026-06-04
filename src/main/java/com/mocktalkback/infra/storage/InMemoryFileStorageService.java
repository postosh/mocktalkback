package com.mocktalkback.infra.storage;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.file.service.FileStorage;

@Service
@Profile("test")
public class InMemoryFileStorageService implements FileStorage {

    private final Map<String, StoredObject> store = new ConcurrentHashMap<>();

    @Override
    public byte[] read(String storageKey) {
        StoredObject stored = store.get(normalizeKey(storageKey));
        if (stored == null) {
            throw new IllegalStateException("파일을 찾을 수 없습니다.");
        }
        return stored.bytes();
    }

    @Override
    public void write(String storageKey, byte[] bytes, String mimeType) {
        if (bytes == null) {
            throw new ApiException(ErrorCode.FILE_BYTES_EMPTY);
        }
        store.put(normalizeKey(storageKey), new StoredObject(bytes, mimeType));
    }

    @Override
    public void delete(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        store.remove(normalizeKey(storageKey));
    }

    @Override
    public String resolveViewUrl(String storageKey) {
        return "/" + normalizeKey(storageKey);
    }

    @Override
    public String resolveDownloadUrl(String storageKey, String fileName, String mimeType) {
        String normalizedKey = normalizeKey(storageKey);
        String resolvedFileName = StringUtils.hasText(fileName) ? fileName : "attachment";
        String encodedFileName = URLEncoder.encode(resolvedFileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "/" + normalizedKey + "?download=1&filename=" + encodedFileName;
    }

    @Override
    public PresignedUploadUrl createPresignedUploadUrl(String storageKey, String mimeType) {
        String normalizedKey = normalizeKey(storageKey);
        return new PresignedUploadUrl(
            "/storage/" + normalizedKey,
            "PUT",
            StringUtils.hasText(mimeType) ? Map.of("Content-Type", mimeType) : Map.of(),
            Instant.now().plusSeconds(300)
        );
    }

    @Override
    public StoredObjectMeta stat(String storageKey) {
        StoredObject stored = store.get(normalizeKey(storageKey));
        if (stored == null) {
            throw new IllegalStateException("파일을 찾을 수 없습니다.");
        }
        return new StoredObjectMeta((long) stored.bytes().length, stored.mimeType(), null);
    }

    private String normalizeKey(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new ApiException(ErrorCode.STORAGE_KEY_EMPTY);
        }
        return storageKey.trim().replace('\\', '/').replaceAll("^/+", "");
    }

    private record StoredObject(
        byte[] bytes,
        String mimeType
    ) {
    }
}
