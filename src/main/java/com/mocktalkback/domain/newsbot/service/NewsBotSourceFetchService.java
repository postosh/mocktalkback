package com.mocktalkback.domain.newsbot.service;

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
            throw new IllegalArgumentException("외부 소스 설정을 저장할 수 없습니다.", exception);
        }
    }

    public Map<String, Object> deserialize(String sourceConfigJson) {
        try {
            return objectMapper.readValue(sourceConfigJson, MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalArgumentException("외부 소스 설정을 읽을 수 없습니다.", exception);
        }
    }

    private NewsSourceClient clientFor(NewsSourceType sourceType) {
        NewsSourceClient client = clients.get(sourceType);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 외부 소스입니다: " + sourceType);
        }
        return client;
    }
}
