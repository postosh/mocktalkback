package com.mocktalkback.domain.moderation.policy;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.common.policy.RoleEvaluator;
import com.mocktalkback.domain.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoardAdminPermissionGuard {

    private final BoardMemberRepository boardMemberRepository;
    private final RoleEvaluator roleEvaluator;

    public void requireBoardAdmin(UserEntity actor, BoardEntity board) {
        if (roleEvaluator.isAdmin(actor)) {
            return;
        }
        BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(actor.getId(), board.getId())
            .orElse(null);
        if (member == null) {
            throw new AccessDeniedException("Access Denied");
        }
        BoardRole role = member.getBoardRole();
        if (role != BoardRole.OWNER && role != BoardRole.MODERATOR) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    public void ensureOwnerEditable(BoardMemberEntity member, UserEntity actor) {
        if (member.getBoardRole() == BoardRole.OWNER && !roleEvaluator.isAdmin(actor)) {
            throw new AccessDeniedException("Access Denied");
        }
    }
}

