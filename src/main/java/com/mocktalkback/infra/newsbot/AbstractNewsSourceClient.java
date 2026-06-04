package com.mocktalkback.infra.newsbot;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.mocktalkback.domain.newsbot.config.NewsBotProperties;

abstract class AbstractNewsSourceClient {

    protected final NewsBotProperties newsBotProperties;

    protected AbstractNewsSourceClient(NewsBotProperties newsBotProperties) {
        this.newsBotProperties = newsBotProperties;
    }

    protected RestClient createRestClient(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(newsBotProperties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(newsBotProperties.getReadTimeoutMs());
        RestClient.Builder configuredBuilder = builder
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", newsBotProperties.getUserAgent());
        if (baseUrl != null) {
            configuredBuilder = configuredBuilder.baseUrl(baseUrl);
        }
        return configuredBuilder.build();
    }

    protected String requireString(Map<String, Object> sourceConfig, String key, String label) {
        Object value = sourceConfig.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_UNSUPPORTED, label);
        }
        return value.toString().trim();
    }

    protected String optionalString(Map<String, Object> sourceConfig, String key) {
        Object value = sourceConfig.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString().trim();
    }
}
