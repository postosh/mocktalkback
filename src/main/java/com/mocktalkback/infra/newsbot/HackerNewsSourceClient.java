package com.mocktalkback.infra.newsbot;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.mocktalkback.domain.newsbot.config.NewsBotProperties;
import com.mocktalkback.domain.newsbot.service.NewsBotSourceItem;
import com.mocktalkback.domain.newsbot.service.NewsSourceClient;
import com.mocktalkback.domain.newsbot.type.NewsSourceType;

@Component
public class HackerNewsSourceClient extends AbstractNewsSourceClient implements NewsSourceClient {

    private static final String BASE_URL = "https://hacker-news.firebaseio.com";

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public HackerNewsSourceClient(
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
        return NewsSourceType.HACKER_NEWS;
    }

    @Override
    public void validateConfig(Map<String, Object> sourceConfig) {
        String storyType = optionalString(sourceConfig, "storyType");
        if (storyType == null) {
            return;
        }
        if (!List.of("topstories", "newstories", "beststories").contains(storyType)) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_UNSUPPORTED);
        }
    }

    @Override
    public List<NewsBotSourceItem> fetchItems(Map<String, Object> sourceConfig, int limit) {
        String storyType = optionalString(sourceConfig, "storyType");
        String endpoint = storyType == null ? "topstories" : storyType;
        try {
            String idsResponse = restClient.get()
                .uri("/v0/{endpoint}.json", endpoint)
                .retrieve()
                .body(String.class);
            JsonNode idsNode = objectMapper.readTree(idsResponse);
            List<NewsBotSourceItem> items = new ArrayList<>();
            for (int index = 0; index < idsNode.size() && items.size() < limit; index += 1) {
                long itemId = idsNode.get(index).asLong();
                String itemResponse = restClient.get()
                    .uri("/v0/item/{itemId}.json", itemId)
                    .retrieve()
                    .body(String.class);
                JsonNode itemNode = objectMapper.readTree(itemResponse);
                if (itemNode.path("deleted").asBoolean(false) || itemNode.path("dead").asBoolean(false)) {
                    continue;
                }
                if (!"story".equals(itemNode.path("type").asString())) {
                    continue;
                }
                String title = itemNode.path("title").asString(null);
                if (title == null || title.isBlank()) {
                    continue;
                }
                String externalUrl = itemNode.path("url").asString();
                if (externalUrl == null || externalUrl.isBlank()) {
                    externalUrl = "https://news.ycombinator.com/item?id=" + itemId;
                }
                String text = compactText(itemNode.path("text").asString(null));
                String summary = buildSummary(text, itemNode);
                items.add(new NewsBotSourceItem(
                    String.valueOf(itemId),
                    title,
                    externalUrl,
                    summary,
                    "Hacker News",
                    itemNode.path("by").asString(null),
                    itemNode.path("time").isNumber() ? Instant.ofEpochSecond(itemNode.path("time").asLong()) : null,
                    null
                ));
            }
            return items;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_READ_FAILED);
        }
    }

    private String buildSummary(String text, JsonNode itemNode) {
        StringBuilder builder = new StringBuilder();
        if (text != null && !text.isBlank()) {
            builder.append(text).append("\n\n");
        }
        if (itemNode.path("score").isNumber()) {
            builder.append("- 점수: ").append(itemNode.path("score").asInt()).append('\n');
        }
        if (itemNode.path("descendants").isNumber()) {
            builder.append("- 댓글 수: ").append(itemNode.path("descendants").asInt()).append('\n');
        }
        return builder.toString().trim();
    }

    private String compactText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?is)<[^>]+>", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
