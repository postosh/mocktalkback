package com.mocktalkback.domain.newsbot.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.newsbot.entity.NewsCollectionJobEntity;
import com.mocktalkback.domain.newsbot.type.NewsSourceType;

@Service
public class NewsBotSourceFetchService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Map<NewsSourceType, NewsSourceClient> clients;
    private final JsonMapper objectMapper;

    public NewsBotSourceFetchService(List<NewsSourceClient> clients, JsonMapper objectMapper) {
        this.clients = new EnumMap<>(NewsSourceType.class);
        for (NewsSourceClient client : clients) {
            this.clients.put(client.supports(), client);
        }
        this.objectMapper = objectMapper;
    }

    public void validateConfig(NewsSourceType sourceType, Map<String, Object> sourceConfig) {
        clientFor(sourceType).validateConfig(sourceConfig);
    }

    public List<NewsBotSourceItem> fetchItems(NewsCollectionJobEntity job) {
        Map<String, Object> sourceConfig = deserialize(job.getSourceConfigJson());
        return clientFor(job.getSourceType()).fetchItems(sourceConfig, job.getFetchLimit());
    }

    public String serialize(Map<String, Object> sourceConfig) {
        try {
            return objectMapper.writeValueAsString(sourceConfig);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_SAVE_FAILED);
        }
    }

    public Map<String, Object> deserialize(String sourceConfigJson) {
        try {
            return objectMapper.readValue(sourceConfigJson, MAP_TYPE);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_READ_FAILED);
        }
    }

    private NewsSourceClient clientFor(NewsSourceType sourceType) {
        NewsSourceClient client = clients.get(sourceType);
        if (client == null) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_UNSUPPORTED, sourceType);
        }
        return client;
    }
}
