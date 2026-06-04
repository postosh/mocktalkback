package com.mocktalkback.domain.common.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.moderation.repository.SanctionRepository;
import com.mocktalkback.domain.role.entity.RoleEntity;
import com.mocktalkback.domain.user.entity.UserEntity;

@ExtendWith(MockitoExtension.class)
class SanctionGuardTest {

    @Mock
    private SanctionRepository sanctionRepository;

    // 제재가 없으면 예외 없이 통과해야 한다.
    @Test
    void require_not_sanctioned_should_pass_when_no_active_sanction() {
        // Given: 활성 제재가 없는 사용자
        SanctionGuard sanctionGuard = new SanctionGuard(sanctionRepository);
        UserEntity user = createUser(10L);
        BoardEntity board = createBoard(20L);
        when(sanctionRepository.existsActiveSanction(anyLong(), any(), any(), anyLong(), any()))
            .thenReturn(false);

        // When, Then: 제재 검사에서 예외가 없어야 함
        assertThatCode(() -> sanctionGuard.requireNotSanctioned(user, board))
            .doesNotThrowAnyException();
    }

    // 활성 제재가 있으면 접근 거부 예외가 발생해야 한다.
    @Test
    void require_not_sanctioned_should_throw_when_active_sanction_exists() {
        // Given: 활성 제재가 있는 사용자
        SanctionGuard sanctionGuard = new SanctionGuard(sanctionRepository);
        UserEntity user = createUser(10L);
        BoardEntity board = createBoard(20L);
        when(sanctionRepository.existsActiveSanction(anyLong(), any(), any(), anyLong(), any()))
            .thenReturn(true);

        // When, Then: 제재 검사에서 접근 거부 예외가 발생해야 함
        assertThatThrownBy(() -> sanctionGuard.requireNotSanctioned(user, board))
            .isInstanceOf(AccessDeniedException.class);
    }

    private UserEntity createUser(Long id) {
        RoleEntity role = RoleEntity.create("USER", 0, "테스트");
        UserEntity user = UserEntity.createLocal(
            role,
            "login",
            "user@test.com",
            "pw",
            "user",
            "display",
            "handle"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private BoardEntity createBoard(Long id) {
        BoardEntity board = BoardEntity.builder()
            .boardName("board")
            .slug("board")
            .description("테스트")
            .visibility(BoardVisibility.PUBLIC)
            .build();
        ReflectionTestUtils.setField(board, "id", id);
        return board;
    }
}

