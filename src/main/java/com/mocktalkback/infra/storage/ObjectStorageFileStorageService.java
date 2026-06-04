package com.mocktalkback.infra.storage;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.file.service.FileStorage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;

@Service
@Profile({"dev", "prod"})
public class ObjectStorageFileStorageService implements FileStorage {

    private static final int DEFAULT_PRESIGN_EXPIRE_SECONDS = 300;
    private static final int DEFAULT_PROTECTED_VIEW_EXPIRE_SECONDS = 120;
    private static final int MAX_PRESIGN_EXPIRE_SECONDS = 604800;

    private final ObjectStorageProperties properties;
    private final MinioClient objectClient;
    private final MinioClient presignClient;

    public ObjectStorageFileStorageService(ObjectStorageProperties properties) {
        this.properties = properties;
        validateProperties(properties);
        this.objectClient = createClient(properties.getEndpoint());
        this.presignClient = createClient(resolvePresignEndpoint(properties));
    }

    @Override
    public byte[] read(String storageKey) {
        String normalizedKey = normalizeKey(storageKey);
        try (InputStream inputStream = objectClient.getObject(
            GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(normalizedKey)
                .build()
        )) {
            return inputStream.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("파일 조회에 실패했습니다.");
        }
    }

    @Override
    public void write(String storageKey, byte[] bytes, String mimeType) {
        if (bytes == null) {
            throw new ApiException(ErrorCode.FILE_BYTES_EMPTY);
        }
        String normalizedKey = normalizeKey(storageKey);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(normalizedKey)
                .stream(inputStream, bytes.length, -1);
            if (StringUtils.hasText(mimeType)) {
                builder.contentType(mimeType);
            }
            objectClient.putObject(builder.build());
        } catch (Exception ex) {
            throw new IllegalStateException("파일 저장에 실패했습니다.");
        }
    }

    @Override
    public void delete(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        String normalizedKey = normalizeKey(storageKey);
        try {
            objectClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(normalizedKey)
                    .build()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("파일 삭제에 실패했습니다.");
        }
    }

    @Override
    public String resolveViewUrl(String storageKey) {
        String normalizedKey = normalizeKey(storageKey);
        if (StringUtils.hasText(properties.getPublicBaseUrl())) {
            String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
            return base + "/" + normalizedKey;
        }
        int expireSeconds = normalizePresignExpireSeconds(properties.getPresignExpireSeconds());
        try {
            return presignClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(normalizedKey)
                    .expiry(expireSeconds)
                    .build()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("파일 조회 URL 생성에 실패했습니다.");
        }
    }

    @Override
    public String resolveProtectedViewUrl(String storageKey) {
        return resolveProtectedViewUrl(storageKey, null);
    }

    @Override
    public String resolveProtectedViewUrl(String storageKey, Duration maxTtl) {
        String normalizedKey = normalizeKey(storageKey);
        int expireSeconds = resolveProtectedViewExpireSeconds(maxTtl);
        try {
            String rawUrl = presignClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(normalizedKey)
                    .expiry(expireSeconds)
                    .build()
            );
            return toProxyUrl(rawUrl);
        } catch (Exception ex) {
            throw new IllegalStateException("보호 파일 조회 URL 생성에 실패했습니다.");
        }
    }

    @Override
    public String resolveDownloadUrl(String storageKey, String fileName, String mimeType) {
        String normalizedKey = normalizeKey(storageKey);
        int expireSeconds = normalizePresignExpireSeconds(properties.getPresignExpireSeconds());
        try {
            String rawUrl = presignClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(normalizedKey)
                    .expiry(expireSeconds)
                    .build()
            );
            return toProxyUrl(rawUrl);
        } catch (Exception ex) {
            throw new IllegalStateException("파일 다운로드 URL 생성에 실패했습니다.");
        }
    }

    @Override
    public PresignedUploadUrl createPresignedUploadUrl(String storageKey, String mimeType) {
        String normalizedKey = normalizeKey(storageKey);
        int expireSeconds = normalizePresignExpireSeconds(properties.getPresignExpireSeconds());
        try {
            String rawUrl = presignClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.getBucket())
                    .object(normalizedKey)
                    .expiry(expireSeconds)
                    .build()
            );
            return new PresignedUploadUrl(
                toProxyUrl(rawUrl),
                "PUT",
                resolveUploadHeaders(mimeType),
                Instant.now().plusSeconds(expireSeconds)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("파일 업로드 URL 생성에 실패했습니다.");
        }
    }

    @Override
    public StoredObjectMeta stat(String storageKey) {
        String normalizedKey = normalizeKey(storageKey);
        try {
            StatObjectResponse stat = objectClient.statObject(
                StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(normalizedKey)
                    .build()
            );
            return new StoredObjectMeta(
                stat.size(),
                stat.contentType(),
                stat.etag()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("저장소 객체 메타 조회에 실패했습니다.");
        }
    }

    private int normalizePresignExpireSeconds(long expireSeconds) {
        if (expireSeconds <= 0L) {
            return DEFAULT_PRESIGN_EXPIRE_SECONDS;
        }
        if (expireSeconds > MAX_PRESIGN_EXPIRE_SECONDS) {
            return MAX_PRESIGN_EXPIRE_SECONDS;
        }
        return (int) expireSeconds;
    }

    private int normalizeProtectedViewExpireSeconds(long expireSeconds) {
        if (expireSeconds <= 0L) {
            return DEFAULT_PROTECTED_VIEW_EXPIRE_SECONDS;
        }
        if (expireSeconds > MAX_PRESIGN_EXPIRE_SECONDS) {
            return MAX_PRESIGN_EXPIRE_SECONDS;
        }
        return (int) expireSeconds;
    }

    private int resolveProtectedViewExpireSeconds(Duration maxTtl) {
        int configuredExpireSeconds = normalizeProtectedViewExpireSeconds(properties.getProtectedViewExpireSeconds());
        if (maxTtl == null) {
            return configuredExpireSeconds;
        }

        long remainingSeconds = maxTtl.toSeconds();
        if (remainingSeconds <= 0L && !maxTtl.isZero() && !maxTtl.isNegative()) {
            remainingSeconds = 1L;
        }
        if (remainingSeconds <= 0L) {
            return 1;
        }
        return (int) Math.min(configuredExpireSeconds, remainingSeconds);
    }

    private Map<String, String> resolveUploadHeaders(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return Map.of();
        }
        return Map.of("Content-Type", mimeType);
    }

    private String toProxyUrl(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String path = uri.getRawPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("Presigned URL 경로가 비어있습니다.");
        }
        String prefix = normalizeUploadProxyPrefix(properties.getUploadProxyPrefix());
        String query = uri.getRawQuery();
        if (!StringUtils.hasText(query)) {
            return prefix + path;
        }
        return prefix + path + "?" + query;
    }

    private String normalizeUploadProxyPrefix(String rawPrefix) {
        if (!StringUtils.hasText(rawPrefix)) {
            return "/storage";
        }
        String normalized = rawPrefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        normalized = normalized.replaceAll("/+$", "");
        if (!StringUtils.hasText(normalized)) {
            return "/storage";
        }
        return normalized;
    }

    private MinioClient createClient(String endpoint) {
        return MinioClient.builder()
            .endpoint(endpoint)
            .credentials(properties.getAccessKey(), properties.getSecretKey())
            .region(properties.getRegion())
            .build();
    }

    private String resolvePresignEndpoint(ObjectStorageProperties props) {
        if (StringUtils.hasText(props.getPresignEndpoint())) {
            return props.getPresignEndpoint();
        }
        return props.getEndpoint();
    }

    private void validateProperties(ObjectStorageProperties props) {
        if (!StringUtils.hasText(props.getEndpoint())) {
            throw new IllegalStateException("오브젝트 스토리지 endpoint 설정이 비어있습니다.");
        }
        if (!StringUtils.hasText(props.getRegion())) {
            throw new IllegalStateException("오브젝트 스토리지 region 설정이 비어있습니다.");
        }
        if (!StringUtils.hasText(props.getBucket())) {
            throw new IllegalStateException("오브젝트 스토리지 bucket 설정이 비어있습니다.");
        }
        if (!StringUtils.hasText(props.getAccessKey())) {
            throw new IllegalStateException("오브젝트 스토리지 access-key 설정이 비어있습니다.");
        }
        if (!StringUtils.hasText(props.getSecretKey())) {
            throw new IllegalStateException("오브젝트 스토리지 secret-key 설정이 비어있습니다.");
        }
    }

    private String normalizeKey(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new ApiException(ErrorCode.STORAGE_KEY_EMPTY);
        }
        String normalized = storageKey.trim().replace('\\', '/');
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            URI uri = URI.create(normalized);
            String path = uri.getPath();
            if (!StringUtils.hasText(path)) {
                throw new ApiException(ErrorCode.STORAGE_KEY_PATH_EMPTY);
            }
            normalized = path;
        }
        normalized = normalized.replaceAll("^/+", "");
        String bucketPrefix = properties.getBucket() + "/";
        if (normalized.startsWith(bucketPrefix)) {
            normalized = normalized.substring(bucketPrefix.length());
        }
        if (!StringUtils.hasText(normalized)) {
            throw new ApiException(ErrorCode.STORAGE_KEY_PATH_EMPTY);
        }
        return normalized;
    }
}
