package com.mocktalkback.domain.common.policy;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import org.springframework.stereotype.Component;

@Component
public class PageNormalizer {

    public int normalizePage(int page) {
        if (page < 0) {
            throw new ApiException(ErrorCode.PAGE_INVALID);
        }
        return page;
    }

    public int normalizePage(Integer page, int defaultPage) {
        int resolvedPage = page == null ? defaultPage : page;
        return normalizePage(resolvedPage);
    }

    public int normalizeSize(int size, int maxPageSize) {
        if (size <= 0 || size > maxPageSize) {
            throw new ApiException(ErrorCode.PAGE_SIZE_INVALID, maxPageSize);
        }
        return size;
    }

    public int normalizeSize(Integer size, int defaultSize, int maxPageSize) {
        int resolvedSize = size == null ? defaultSize : size;
        return normalizeSize(resolvedSize, maxPageSize);
    }
}

