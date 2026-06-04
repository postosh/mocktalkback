package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.mocktalkback.domain.article.type.ArticleContentFormat;
import com.mocktalkback.global.common.sanitize.HtmlSanitizer;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

@Service
public class ArticleContentService {

    private static final Pattern FRONTMATTER_BOUNDARY_PATTERN = Pattern.compile("^---\\s*$");
    private static final Pattern FRONTMATTER_END_PATTERN = Pattern.compile("^(---|\\.\\.\\.)\\s*$");
    private static final Pattern TABLE_DELIMITER_PATTERN = Pattern.compile(
        "^\\s*\\|?(\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?\\s*$"
    );
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile("!youtube\\[(.+?)]");
    private final HtmlSanitizer htmlSanitizer;
    private final Parser markdownParser;
    private final HtmlRenderer markdownRenderer;

    public ArticleContentService(HtmlSanitizer htmlSanitizer) {
        this.htmlSanitizer = htmlSanitizer;

        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListExtension.create(),
            AutolinkExtension.create()
        ));

        this.markdownParser = Parser.builder(options).build();
        this.markdownRenderer = HtmlRenderer.builder(options).build();
    }

    public RenderedContent render(String contentSource, ArticleContentFormat contentFormat) {
        if (contentFormat == null) {
            throw new ApiException(ErrorCode.ARTICLE_CONTENT_FORMAT_REQUIRED);
        }
        if (contentSource == null) {
            throw new ApiException(ErrorCode.ARTICLE_CONTENT_SOURCE_REQUIRED);
        }

        if (contentFormat == ArticleContentFormat.HTML) {
            String sanitized = htmlSanitizer.sanitize(contentSource);
            return new RenderedContent(sanitized, sanitized);
        }

        String normalizedMarkdown = normalizeMarkdownTables(normalizeMarkdownYouTubeEmbeds(stripMarkdownFrontmatter(contentSource)));
        String rendered = markdownRenderer.render(markdownParser.parse(normalizedMarkdown));
        String sanitized = htmlSanitizer.sanitize(rendered);
        return new RenderedContent(sanitized, contentSource);
    }

    public record RenderedContent(String content, String contentSource) {
    }

    private String normalizeMarkdownTables(String markdown) {
        if (markdown.isBlank()) {
            return markdown;
        }

        List<String> lines = List.of(markdown.split("\\R", -1));
        List<String> normalized = new ArrayList<>();
        boolean inCodeFence = false;

        for (int index = 0; index < lines.size(); index += 1) {
            String line = lines.get(index);

            if (isCodeFenceLine(line)) {
                inCodeFence = !inCodeFence;
                normalized.add(line);
                continue;
            }

            if (!inCodeFence && isTableHeaderLine(line) && index + 1 < lines.size() && isTableDelimiterLine(lines.get(index + 1))) {
                if (!normalized.isEmpty() && !normalized.get(normalized.size() - 1).isBlank()) {
                    normalized.add("");
                }

                normalized.add(line);
                normalized.add(lines.get(index + 1));
                index += 2;

                while (index < lines.size() && isTableRowLine(lines.get(index))) {
                    normalized.add(lines.get(index));
                    index += 1;
                }

                if (index < lines.size() && !lines.get(index).isBlank() && !normalized.get(normalized.size() - 1).isBlank()) {
                    normalized.add("");
                }

                index -= 1;
                continue;
            }

            normalized.add(line);
        }

        return String.join("\n", normalized);
    }

    private String normalizeMarkdownYouTubeEmbeds(String markdown) {
        if (markdown.isBlank()) {
            return markdown;
        }

        Matcher matcher = YOUTUBE_PATTERN.matcher(markdown);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rawValue = matcher.group(1) == null ? null : matcher.group(1).trim();
            String videoId = resolveYouTubeVideoId(rawValue);
            if (videoId == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String iframe = """
                <iframe class="article-youtube-embed" src="https://www.youtube.com/embed/%s" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
                """.formatted(videoId).trim();
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(iframe));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String stripMarkdownFrontmatter(String markdown) {
        if (markdown.isBlank()) {
            return markdown;
        }

        String normalized = markdown.startsWith("\uFEFF") ? markdown.substring(1) : markdown;
        String[] lines = normalized.split("\\R", -1);
        if (lines.length == 0 || !FRONTMATTER_BOUNDARY_PATTERN.matcher(lines[0].trim()).matches()) {
            return normalized;
        }

        int closingIndex = -1;
        for (int index = 1; index < lines.length; index += 1) {
            if (FRONTMATTER_END_PATTERN.matcher(lines[index].trim()).matches()) {
                closingIndex = index;
                break;
            }
        }

        if (closingIndex < 0) {
            return normalized;
        }

        String body = String.join("\n", List.of(lines).subList(closingIndex + 1, lines.length));
        return body.replaceFirst("^(\\r?\\n)+", "");
    }

    private boolean isCodeFenceLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }

    private boolean isTableHeaderLine(String line) {
        String trimmed = line.trim();
        return !trimmed.isEmpty() && trimmed.contains("|") && !TABLE_DELIMITER_PATTERN.matcher(trimmed).matches();
    }

    private boolean isTableDelimiterLine(String line) {
        return TABLE_DELIMITER_PATTERN.matcher(line.trim()).matches();
    }

    private boolean isTableRowLine(String line) {
        String trimmed = line.trim();
        return !trimmed.isEmpty() && trimmed.contains("|") && !isCodeFenceLine(trimmed);
    }

    private String resolveYouTubeVideoId(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.matches("^[A-Za-z0-9_-]{11}$")) {
            return normalized;
        }
        try {
            java.net.URI uri = java.net.URI.create(normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().trim();
            if ("youtu.be".equals(normalizedHost) && !path.isBlank()) {
                String candidate = path.startsWith("/") ? path.substring(1) : path;
                return candidate.matches("^[A-Za-z0-9_-]{11}$") ? candidate : null;
            }
            if (normalizedHost.endsWith("youtube.com") || normalizedHost.endsWith("youtube-nocookie.com")) {
                if (path.startsWith("/embed/")) {
                    String candidate = path.substring("/embed/".length());
                    int slashIndex = candidate.indexOf('/');
                    if (slashIndex >= 0) {
                        candidate = candidate.substring(0, slashIndex);
                    }
                    return candidate.matches("^[A-Za-z0-9_-]{11}$") ? candidate : null;
                }
                if (path.startsWith("/shorts/")) {
                    String candidate = path.substring("/shorts/".length());
                    int slashIndex = candidate.indexOf('/');
                    if (slashIndex >= 0) {
                        candidate = candidate.substring(0, slashIndex);
                    }
                    return candidate.matches("^[A-Za-z0-9_-]{11}$") ? candidate : null;
                }
                String query = uri.getQuery();
                if (query != null && !query.isBlank()) {
                    for (String queryPart : query.split("&")) {
                        String[] pair = queryPart.split("=", 2);
                        if (pair.length == 2 && "v".equals(pair[0])) {
                            return pair[1].matches("^[A-Za-z0-9_-]{11}$") ? pair[1] : null;
                        }
                    }
                }
            }
            return null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
