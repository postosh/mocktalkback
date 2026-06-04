package com.mocktalkback.domain.moderation.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.common.policy.RoleEvaluator;
import com.mocktalkback.domain.role.entity.RoleEntity;
import com.mocktalkback.domain.user.entity.UserEntity;

@ExtendWith(MockitoExtension.class)
class BoardAdminPermissionGuardTest {

    @Mock
    private BoardMemberRepository boardMemberRepository;

    // ADMIN 사용자는 멤버 조회 없이 게시판 관리자 권한을 통과해야 한다.
    @Test
    void require_board_admin_should_allow_admin_user() {
        // Given: ADMIN 사용자와 게시판
        BoardAdminPermissionGuard guard = new BoardAdminPermissionGuard(boardMemberRepository, new RoleEvaluator());
        UserEntity admin = createUser(1L, "ADMIN");
        BoardEntity board = createBoard(10L);

        // When, Then: 권한 검사에서 예외가 없어야 함
        assertThatCode(() -> guard.requireBoardAdmin(admin, board))
            .doesNotThrowAnyException();
    }

    // OWNER/MODERATOR 권한 사용자는 게시판 관리자 권한을 통과해야 한다.
    @Test
    void require_board_admin_should_allow_owner_member() {
        // Given: OWNER 보드 멤버인 일반 사용자
        BoardAdminPermissionGuard guard = new BoardAdminPermissionGuard(boardMemberRepository, new RoleEvaluator());
        UserEntity actor = createUser(2L, "USER");
        BoardEntity board = createBoard(20L);
        BoardMemberEntity member = createMember(actor, board, BoardRole.OWNER);
        when(boardMemberRepository.findByUserIdAndBoardId(2L, 20L)).thenReturn(java.util.Optional.of(member));

        // When, Then: 권한 검사에서 예외가 없어야 함
        assertThatCode(() -> guard.requireBoardAdmin(actor, board))
            .doesNotThrowAnyException();
    }

    // 게시판 멤버가 아니거나 권한이 낮으면 관리자 권한 검사에서 차단되어야 한다.
    @Test
    void require_board_admin_should_throw_for_non_admin_member() {
        // Given: MEMBER 권한 사용자
        BoardAdminPermissionGuard guard = new BoardAdminPermissionGuard(boardMemberRepository, new RoleEvaluator());
        UserEntity actor = createUser(3L, "USER");
        BoardEntity board = createBoard(30L);
        BoardMemberEntity member = createMember(actor, board, BoardRole.MEMBER);
        when(boardMemberRepository.findByUserIdAndBoardId(3L, 30L)).thenReturn(java.util.Optional.of(member));

        // When, Then: 권한 검사에서 예외가 발생해야 함
        assertThatThrownBy(() -> guard.requireBoardAdmin(actor, board))
            .isInstanceOf(AccessDeniedException.class);
    }

    // OWNER 대상 역할 변경은 ADMIN이 아니면 차단되어야 한다.
    @Test
    void ensure_owner_editable_should_throw_for_non_admin_actor() {
        // Given: OWNER 대상 멤버와 USER 사용자
        BoardAdminPermissionGuard guard = new BoardAdminPermissionGuard(boardMemberRepository, new RoleEvaluator());
        UserEntity actor = createUser(4L, "USER");
        BoardEntity board = createBoard(40L);
        BoardMemberEntity ownerMember = createMember(createUser(5L, "USER"), board, BoardRole.OWNER);

        // When, Then: OWNER 수정 권한 검사에서 예외가 발생해야 함
        assertThatThrownBy(() -> guard.ensureOwnerEditable(ownerMember, actor))
            .isInstanceOf(AccessDeniedException.class);
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

    private UserEntity createUser(Long id, String roleName) {
        RoleEntity role = RoleEntity.create(roleName, 0, "테스트");
        UserEntity user = UserEntity.createLocal(
            role,
            "login-" + id,
            "user" + id + "@test.com",
            "pw",
            "user-" + id,
            "display-" + id,
            "handle-" + id
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private BoardMemberEntity createMember(UserEntity user, BoardEntity board, BoardRole role) {
        return BoardMemberEntity.builder()
            .user(user)
            .board(board)
            .grantedByUser(null)
            .boardRole(role)
            .build();
    }
}

