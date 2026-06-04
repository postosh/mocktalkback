package com.mocktalkback.global.common.util;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.Set;

/**
 * 리액션 엔티티에서 사용하는 reaction_type 값을 검증한다.
 * 허용 값은 -1, 0, 1만 사용한다.
 */
public final class ReactionTypeValidator {
    private static final Set<Short> ALLOWED_VALUES = Set.of((short) -1, (short) 0, (short) 1);

    private ReactionTypeValidator() {}

    /**
     * reactionType이 -1, 0, 1 중 하나이면 true를 반환한다.
     */
    public static boolean isValid(short reactionType) {
        return ALLOWED_VALUES.contains(reactionType);
    }

    /**
     * reactionType이 허용되지 않으면 ApiException을 던진다.
     */
    public static void validate(short reactionType) {
        if (!isValid(reactionType)) {
            throw new ApiException(ErrorCode.REACTION_TYPE_INVALID, reactionType);
        }
    }
}
