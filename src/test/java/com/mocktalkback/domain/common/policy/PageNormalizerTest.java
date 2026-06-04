package com.mocktalkback.domain.common.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

class PageNormalizerTest {

    private final PageNormalizer pageNormalizer = new PageNormalizer();

    // page가 null이면 기본값을 반환해야 한다.
    @Test
    void normalize_page_should_return_default_when_null() {
        // Given: null page와 기본값
        Integer page = null;

        // When: page를 정규화
        int normalized = pageNormalizer.normalizePage(page, 0);

        // Then: 기본값이 반환되어야 함
        assertThat(normalized).isEqualTo(0);
    }

    // page가 음수이면 예외가 발생해야 한다.
    @Test
    void normalize_page_should_throw_when_negative() {
        // Given: 음수 page
        int page = -1;

        // When, Then: page 정규화 시 예외가 발생해야 함
        assertThatThrownBy(() -> pageNormalizer.normalizePage(page))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.PAGE_INVALID);
    }

    // size가 null이면 기본값을 반환해야 한다.
    @Test
    void normalize_size_should_return_default_when_null() {
        // Given: null size와 기본값
        Integer size = null;

        // When: size를 정규화
        int normalized = pageNormalizer.normalizeSize(size, 10, 50);

        // Then: 기본값이 반환되어야 함
        assertThat(normalized).isEqualTo(10);
    }

    // size가 허용 범위를 벗어나면 예외가 발생해야 한다.
    @Test
    void normalize_size_should_throw_when_out_of_range() {
        // Given: 최대값을 초과한 size
        int size = 51;

        // When, Then: size 정규화 시 예외가 발생해야 함
        assertThatThrownBy(() -> pageNormalizer.normalizeSize(size, 50))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getErrorCode())
            .isEqualTo(ErrorCode.PAGE_SIZE_INVALID);
    }
}

