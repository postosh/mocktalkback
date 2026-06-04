package com.mocktalkback.infra.newsbot;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.newsbot.config.NewsBotProperties;
import com.mocktalkback.domain.newsbot.service.NewsBotSourceItem;
import com.mocktalkback.domain.newsbot.service.NewsSourceClient;
import com.mocktalkback.domain.newsbot.type.NewsSourceType;

@Component
public class DevToSourceClient extends AbstractNewsSourceClient implements NewsSourceClient {

    private static final String BASE_URL = "https://dev.to";

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public DevToSourceClient(
        RestClient.Builder restClientBuilder,
        JsonMapper objectMapper,
        NewsBotProperties newsBotProperties
    ) {
        super(newsBotProperties);
        this.restClient = createRestClient(restClientBuilder, BASE_URL);
        this.objectMapper = objectMapper;
    }

    @Override
    public NewsSourceType supports() {
        return NewsSourceType.DEV_TO;
    }

    @Override
    public void validateConfig(Map<String, Object> sourceConfig) {
        String tag = optionalString(sourceConfig, "tag");
        String username = optionalString(sourceConfig, "username");
        if (tag == null && username == null) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_UNSUPPORTED);
        }
    }

    @Override
    public List<NewsBotSourceItem> fetchItems(Map<String, Object> sourceConfig, int limit) {
        String tag = optionalString(sourceConfig, "tag");
        String username = optionalString(sourceConfig, "username");
        try {
            String responseBody = restClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, tag, username, limit))
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            List<NewsBotSourceItem> items = new ArrayList<>();
            for (JsonNode articleNode : root) {
                String id = articleNode.path("id").asString();
                String title = articleNode.path("title").asString(null);
                String url = articleNode.path("url").asString(null);
                if (title == null || title.isBlank() || url == null || url.isBlank()) {
                    continue;
                }
                items.add(new NewsBotSourceItem(
                    id,
                    title,
                    url,
                    buildSummary(articleNode),
                    "DEV Community",
                    articleNode.path("user").path("name").asString(null),
                    parseInstant(articleNode.path("published_timestamp").asString(null)),
                    parseInstant(articleNode.path("edited_at").asString(null))
                ));
            }
            return items;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_READ_FAILED);
        }
    }

    private java.net.URI buildUri(UriBuilder uriBuilder, String tag, String username, int limit) {
        UriBuilder builder = uriBuilder.path("/api/articles")
            .queryParam("per_page", limit);
        if (tag != null) {
            builder.queryParam("tag", tag);
        }
        if (username != null) {
            builder.queryParam("username", username);
        }
        return builder.build();
    }

    private String buildSummary(JsonNode articleNode) {
        String description = articleNode.path("description").asString("");
        String readablePublishDate = articleNode.path("readable_publish_date").asString("");
        String tagList = articleNode.path("tag_list").isArray()
            ? joinTags(articleNode.path("tag_list"))
            : articleNode.path("tag_list").asString("");
        StringBuilder builder = new StringBuilder();
        if (!description.isBlank()) {
            builder.append(description.trim()).append("\n\n");
        }
        if (!tagList.isBlank()) {
            builder.append("- 태그: ").append(tagList.trim()).append('\n');
        }
        if (!readablePublishDate.isBlank()) {
            builder.append("- 노출 날짜: ").append(readablePublishDate.trim()).append('\n');
        }
        return builder.toString().trim();
    }

    private String joinTags(JsonNode tagArray) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tagNode : tagArray) {
            String tag = tagNode.asString(null);
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim());
            }
        }
        return String.join(", ", tags);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
