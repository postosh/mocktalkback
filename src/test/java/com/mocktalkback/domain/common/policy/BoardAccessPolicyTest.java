package com.mocktalkback.domain.common.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.type.BoardArticleWritePolicy;
import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.role.entity.RoleEntity;
import com.mocktalkback.domain.role.type.ContentVisibility;
import com.mocktalkback.domain.user.entity.UserEntity;

class BoardAccessPolicyTest {

    private final BoardAccessPolicy boardAccessPolicy = new BoardAccessPolicy(new RoleEvaluator());

    // 비회원은 공개 게시판만 접근 가능해야 한다.
    @Test
    void can_access_board_should_allow_public_for_anonymous_user() {
        // Given: 공개 게시판
        BoardEntity board = createBoard(BoardVisibility.PUBLIC, BoardArticleWritePolicy.ALL_AUTHENTICATED);

        // When: 비회원 접근 가능 여부를 확인
        boolean result = boardAccessPolicy.canAccessBoard(board, null, null);

        // Then: 접근 가능해야 함
        assertThat(result).isTrue();
    }

    // 일반 사용자는 비공개 게시판에서 OWNER가 아니면 접근할 수 없어야 한다.
    @Test
    void can_access_board_should_block_private_board_for_non_owner() {
        // Given: 비공개 게시판과 MEMBER 권한 사용자
        BoardEntity board = createBoard(BoardVisibility.PRIVATE, BoardArticleWritePolicy.ALL_AUTHENTICATED);
        UserEntity user = createUser("USER");
        BoardMemberEntity member = createMember(user, board, BoardRole.MEMBER);

        // When: 접근 가능 여부를 확인
        boolean result = boardAccessPolicy.canAccessBoard(board, user, member);

        // Then: 접근 불가능해야 함
        assertThat(result).isFalse();
    }

    // 관리자/매니저는 게시판 접근 제한을 우회할 수 있어야 한다.
    @Test
    void can_access_board_should_allow_admin() {
        // Given: 비공개 게시판과 ADMIN 사용자
        BoardEntity board = createBoard(BoardVisibility.PRIVATE, BoardArticleWritePolicy.ALL_AUTHENTICATED);
        UserEntity admin = createUser("ADMIN");

        // When: 접근 가능 여부를 확인
        boolean result = boardAccessPolicy.canAccessBoard(board, admin, null);

        // Then: 접근 가능해야 함
        assertThat(result).isTrue();
    }

    // 그룹 게시판의 일반 멤버는 MEMBERS 가시성을 조회할 수 있어야 한다.
    @Test
    void resolve_allowed_visibilities_should_include_members_for_group_member() {
        // Given: 그룹 게시판과 MEMBER 권한 사용자
        BoardEntity board = createBoard(BoardVisibility.GROUP, BoardArticleWritePolicy.ALL_AUTHENTICATED);
        UserEntity user = createUser("USER");
        BoardMemberEntity member = createMember(user, board, BoardRole.MEMBER);

        // When: 허용 가시성 집합을 계산
        EnumSet<ContentVisibility> result = boardAccessPolicy.resolveAllowedVisibilities(board, user, member);

        // Then: PUBLIC/MEMBERS는 허용되어야 함
        assertThat(result).contains(ContentVisibility.PUBLIC, ContentVisibility.MEMBERS);
    }

    // 승인 대기 사용자는 글쓰기 권한 검사에서 차단되어야 한다.
    @Test
    void require_can_write_should_throw_for_pending_member() {
        // Given: 게시판과 PENDING 권한 사용자
        BoardEntity board = createBoard(BoardVisibility.PUBLIC, BoardArticleWritePolicy.ALL_AUTHENTICATED);
        UserEntity user = createUser("USER");
        BoardMemberEntity pending = createMember(user, board, BoardRole.PENDING);

        // When, Then: 글쓰기 권한 검사 시 예외가 발생해야 함
        assertThatThrownBy(() -> boardAccessPolicy.requireCanWrite(board, user, pending))
            .isInstanceOf(AccessDeniedException.class);
    }

    // 게시판 관리 권한은 OWNER 또는 ADMIN에게만 허용되어야 한다.
    @Test
    void require_manage_permission_should_throw_for_non_owner_user() {
        // Given: MEMBER 권한 사용자
        UserEntity user = createUser("USER");
        BoardEntity board = createBoard(BoardVisibility.PUBLIC, BoardArticleWritePolicy.ALL_AUTHENTICATED);
        BoardMemberEntity member = createMember(user, board, BoardRole.MEMBER);

        // When, Then: 게시판 관리 권한 검사 시 예외가 발생해야 함
        assertThatThrownBy(() -> boardAccessPolicy.requireManagePermission(user, member))
            .isInstanceOf(AccessDeniedException.class);
    }

    private BoardEntity createBoard(BoardVisibility visibility, BoardArticleWritePolicy policy) {
        return BoardEntity.builder()
            .boardName("board-" + visibility.name())
            .slug("board-" + visibility.name().toLowerCase())
            .description("테스트 게시판")
            .visibility(visibility)
            .articleWritePolicy(policy)
            .build();
    }

    private UserEntity createUser(String roleName) {
        RoleEntity role = RoleEntity.create(roleName, 0, "테스트");
        return UserEntity.createLocal(
            role,
            "login-" + roleName,
            roleName.toLowerCase() + "@test.com",
            "pw",
            "user-" + roleName,
            "display-" + roleName,
            "handle-" + roleName
        );
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

