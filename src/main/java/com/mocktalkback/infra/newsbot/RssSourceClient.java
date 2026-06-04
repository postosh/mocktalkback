package com.mocktalkback.infra.newsbot;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.io.StringReader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.mocktalkback.domain.newsbot.config.NewsBotProperties;
import com.mocktalkback.domain.newsbot.service.NewsBotSourceItem;
import com.mocktalkback.domain.newsbot.service.NewsSourceClient;
import com.mocktalkback.domain.newsbot.type.NewsSourceType;

@Component
public class RssSourceClient extends AbstractNewsSourceClient implements NewsSourceClient {

    private final RestClient restClient;

    public RssSourceClient(
        RestClient.Builder restClientBuilder,
        NewsBotProperties newsBotProperties
    ) {
        super(newsBotProperties);
        this.restClient = createRestClient(restClientBuilder, null);
    }

    @Override
    public NewsSourceType supports() {
        return NewsSourceType.RSS;
    }

    @Override
    public void validateConfig(Map<String, Object> sourceConfig) {
        requireString(sourceConfig, "feedUrl", "RSS/Atom feedUrl");
    }

    @Override
    public List<NewsBotSourceItem> fetchItems(Map<String, Object> sourceConfig, int limit) {
        String feedUrl = requireString(sourceConfig, "feedUrl", "RSS/Atom feedUrl");
        try {
            String xml = restClient.get()
                .uri(feedUrl)
                .retrieve()
                .body(String.class);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            document.getDocumentElement().normalize();

            String feedTitle = firstText(document, "title");
            List<Element> entryElements = collectEntries(document);
            List<NewsBotSourceItem> items = new ArrayList<>();
            for (Element element : entryElements) {
                if (items.size() >= limit) {
                    break;
                }
                String title = childText(element, "title");
                String link = resolveLink(element);
                String guid = childText(element, "guid");
                String description = childText(element, "description");
                if (description == null || description.isBlank()) {
                    description = childText(element, "summary");
                }
                String author = childText(element, "author");
                if (author == null || author.isBlank()) {
                    author = childText(element, "dc:creator");
                }
                if (title == null || title.isBlank() || link == null || link.isBlank()) {
                    continue;
                }
                items.add(new NewsBotSourceItem(
                    guid == null || guid.isBlank() ? link : guid,
                    title.trim(),
                    link.trim(),
                    compactText(description),
                    feedTitle == null ? "RSS" : feedTitle.trim(),
                    author == null ? null : compactText(author),
                    parseDate(childText(element, "pubDate"), childText(element, "published")),
                    parseDate(childText(element, "updated"), childText(element, "pubDate"))
                ));
            }
            return items;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.NEWSBOT_SOURCE_READ_FAILED);
        }
    }

    private List<Element> collectEntries(Document document) {
        List<Element> entries = new ArrayList<>();
        NodeList itemNodes = document.getElementsByTagName("item");
        for (int index = 0; index < itemNodes.getLength(); index += 1) {
            Node node = itemNodes.item(index);
            if (node instanceof Element element) {
                entries.add(element);
            }
        }
        if (!entries.isEmpty()) {
            return entries;
        }
        NodeList entryNodes = document.getElementsByTagName("entry");
        for (int index = 0; index < entryNodes.getLength(); index += 1) {
            Node node = entryNodes.item(index);
            if (node instanceof Element element) {
                entries.add(element);
            }
        }
        return entries;
    }

    private String resolveLink(Element element) {
        String directLink = childText(element, "link");
        if (directLink != null && !directLink.isBlank()) {
            return directLink;
        }

        NodeList links = element.getElementsByTagName("link");
        for (int index = 0; index < links.getLength(); index += 1) {
            Node node = links.item(index);
            if (!(node instanceof Element linkElement)) {
                continue;
            }
            String href = linkElement.getAttribute("href");
            if (href != null && !href.isBlank()) {
                return href;
            }
        }
        return null;
    }

    private String firstText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }

    private String compactText(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?is)<[^>]+>", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private Instant parseDate(String primary, String fallback) {
        String target = primary;
        if (target == null || target.isBlank()) {
            target = fallback;
        }
        if (target == null || target.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(target, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(target).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return Instant.parse(target);
        } catch (Exception ignored) {
        }
        return null;
    }
}
