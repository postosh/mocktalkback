package com.mocktalkback.infra.newsbot;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Instant;
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
public class GitHubReleasesSourceClient extends AbstractNewsSourceClient implements NewsSourceClient {

    private static final String BASE_URL = "https://api.github.com";

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    public GitHubReleasesSourceClient(
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
        return NewsSourceType.GITHUB_RELEASES;
    }

    @Override
    public void validateConfig(Map<String, Object> sourceConfig) {
        requireString(sourceConfig, "owner", "GitHub owner");
        requireString(sourceConfig, "repo", "GitHub repo");
    }

    @Override
    public List<NewsBotSourceItem> fetchItems(Map<String, Object> sourceConfig, int limit) {
        String owner = requireString(sourceConfig, "owner", "GitHub owner");
        String repo = requireString(sourceConfig, "repo", "GitHub repo");
        try {
            String responseBody = restClient.get()
                .uri("/repos/{owner}/{repo}/releases/latest", owner, repo)
                .retrieve()
                .body(String.class);
            JsonNode releaseNode = objectMapper.readTree(responseBody);
            String releaseId = releaseNode.path("id").asString();
            String title = releaseNode.path("name").asString(null);
            if (title == null || title.isBlank()) {
                title = releaseNode.path("tag_name").asString(null);
            }
            String url = releaseNode.path("html_url").asString(null);
            if (title == null || title.isBlank() || url == null || url.isBlank()) {
                return List.of();
            }
            String summary = releaseNode.path("body").asString("");
            if (summary.length() > 4000) {
                summary = summary.substring(0, 4000);
            }
            return List.of(new NewsBotSourceItem(
                releaseId,
                title,
                url,
                summary.trim(),
                owner + "/" + repo,
                releaseNode.path("author").path("login").asString(null),
                parseInstant(releaseNode.path("published_at").asString(null)),
                parseInstant(releaseNode.path("updated_at").asString(null))
            ));
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_READ_FAILED);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
