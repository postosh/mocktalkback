package com.mocktalkback.global.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.config.LocaleConfig;

@SpringJUnitConfig(ApiMessageResolverTest.Config.class)
class ApiMessageResolverTest {

    @TestConfiguration
    @Import({LocaleConfig.class, ApiMessageResolver.class})
    static class Config {
    }

    @Autowired
    private ApiMessageResolver messageResolver;

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolves_korean_by_default() {
        assertThat(messageResolver.resolve(ErrorCode.COMMON_BAD_REQUEST))
                .isEqualTo("잘못된 요청입니다.");
    }

    @Test
    void resolves_english_when_locale_is_en() {
        LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);
        assertThat(messageResolver.resolve(ErrorCode.COMMON_BAD_REQUEST))
                .isEqualTo("Invalid request.");
    }
}