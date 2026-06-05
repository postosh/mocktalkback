package com.mocktalkback.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.object-storage")
public class ObjectStorageProperties {

    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess = true;
    private String keyPrefix = "uploads";
    private String publicBaseUrl;
    private String presignEndpoint;
    private long presignExpireSeconds = 300L;
    private long protectedViewExpireSeconds = 120L;
    private int viewTicketBatchMaxItems = 100;
    private String uploadProxyPrefix = "/storage";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getPresignEndpoint() {
        return presignEndpoint;
    }

    public void setPresignEndpoint(String presignEndpoint) {
        this.presignEndpoint = presignEndpoint;
    }

    public long getPresignExpireSeconds() {
        return presignExpireSeconds;
    }

    public void setPresignExpireSeconds(long presignExpireSeconds) {
        this.presignExpireSeconds = presignExpireSeconds;
    }

    public long getProtectedViewExpireSeconds() {
        return protectedViewExpireSeconds;
    }

    public void setProtectedViewExpireSeconds(long protectedViewExpireSeconds) {
        this.protectedViewExpireSeconds = protectedViewExpireSeconds;
    }

    public int getViewTicketBatchMaxItems() {
        return viewTicketBatchMaxItems;
    }

    public void setViewTicketBatchMaxItems(int viewTicketBatchMaxItems) {
        this.viewTicketBatchMaxItems = viewTicketBatchMaxItems;
    }

    public String getUploadProxyPrefix() {
        return uploadProxyPrefix;
    }

    public void setUploadProxyPrefix(String uploadProxyPrefix) {
        this.uploadProxyPrefix = uploadProxyPrefix;
    }
}
