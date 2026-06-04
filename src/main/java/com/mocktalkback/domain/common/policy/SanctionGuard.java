package com.mocktalkback.domain.common.policy;

import java.time.Instant;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.moderation.repository.SanctionRepository;
import com.mocktalkback.domain.moderation.type.SanctionScopeType;
import com.mocktalkback.domain.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SanctionGuard {

    private final SanctionRepository sanctionRepository;

    public void requireNotSanctioned(UserEntity user, BoardEntity board) {
        Instant now = Instant.now();
        boolean sanctioned = sanctionRepository.existsActiveSanction(
            user.getId(),
            SanctionScopeType.GLOBAL,
            SanctionScopeType.BOARD,
            board.getId(),
            now
        );
        if (sanctioned) {
            throw new AccessDeniedException("Access Denied");
        }
    }
}

