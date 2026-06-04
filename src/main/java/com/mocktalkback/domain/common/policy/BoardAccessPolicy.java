package com.mocktalkback.domain.common.policy;

import java.util.EnumSet;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.type.BoardArticleWritePolicy;
import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.board.type.BoardVisibility;
import com.mocktalkback.domain.role.type.ContentVisibility;
import com.mocktalkback.domain.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoardAccessPolicy {

    private final RoleEvaluator roleEvaluator;

    public boolean canAccessBoard(BoardEntity board, UserEntity user, BoardMemberEntity member) {
        BoardVisibility visibility = board.getVisibility();
        if (user == null) {
            return visibility == BoardVisibility.PUBLIC;
        }
        if (roleEvaluator.isManagerOrAdmin(user)) {
            return true;
        }
        if (member != null && member.getBoardRole() == BoardRole.BANNED) {
            return false;
        }
        if (visibility == BoardVisibility.PUBLIC || visibility == BoardVisibility.GROUP) {
            return true;
        }
        if (visibility == BoardVisibility.PRIVATE) {
            return member != null && member.getBoardRole() == BoardRole.OWNER;
        }
        return false;
    }

    public void requireCanWrite(BoardEntity board, UserEntity user, BoardMemberEntity member) {
        if (roleEvaluator.isManagerOrAdmin(user)) {
            return;
        }
        if (member != null && member.getBoardRole() == BoardRole.PENDING) {
            throw new AccessDeniedException("Access Denied");
        }

        BoardArticleWritePolicy policy = board.getArticleWritePolicy();
        if (policy == null || policy == BoardArticleWritePolicy.ALL_AUTHENTICATED) {
            return;
        }

        BoardRole role = member == null ? null : member.getBoardRole();
        if (policy == BoardArticleWritePolicy.MEMBER && isActiveMember(member)) {
            return;
        }
        if (policy == BoardArticleWritePolicy.MODERATOR && (role == BoardRole.OWNER || role == BoardRole.MODERATOR)) {
            return;
        }
        if (policy == BoardArticleWritePolicy.OWNER && role == BoardRole.OWNER) {
            return;
        }
        throw new AccessDeniedException("Access Denied");
    }

    public EnumSet<ContentVisibility> resolveAllowedVisibilities(
        BoardEntity board,
        UserEntity user,
        BoardMemberEntity member
    ) {
        if (user == null) {
            return EnumSet.of(ContentVisibility.PUBLIC);
        }
        if (roleEvaluator.isManagerOrAdmin(user)) {
            return EnumSet.allOf(ContentVisibility.class);
        }
        BoardVisibility visibility = board.getVisibility();
        if (visibility == BoardVisibility.UNLISTED) {
            return EnumSet.noneOf(ContentVisibility.class);
        }
        if (visibility == BoardVisibility.PRIVATE) {
            if (member != null && member.getBoardRole() == BoardRole.OWNER) {
                return EnumSet.of(ContentVisibility.PUBLIC, ContentVisibility.MEMBERS, ContentVisibility.MODERATORS);
            }
            return EnumSet.noneOf(ContentVisibility.class);
        }
        EnumSet<ContentVisibility> allowed = EnumSet.of(ContentVisibility.PUBLIC);
        if (visibility == BoardVisibility.PUBLIC) {
            allowed.add(ContentVisibility.MEMBERS);
        }
        if (visibility == BoardVisibility.GROUP && isActiveMember(member)) {
            allowed.add(ContentVisibility.MEMBERS);
        }
        if (member != null && (member.getBoardRole() == BoardRole.OWNER || member.getBoardRole() == BoardRole.MODERATOR)) {
            allowed.add(ContentVisibility.MODERATORS);
        }
        return allowed;
    }

    public void requireManagePermission(UserEntity user, BoardMemberEntity member) {
        if (roleEvaluator.isManagerOrAdmin(user)) {
            return;
        }
        if (member == null || member.getBoardRole() != BoardRole.OWNER) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    public void requireApprovePermission(UserEntity user, BoardMemberEntity member) {
        if (roleEvaluator.isManagerOrAdmin(user)) {
            return;
        }
        if (member == null) {
            throw new AccessDeniedException("Access Denied");
        }
        BoardRole role = member.getBoardRole();
        if (role != BoardRole.OWNER && role != BoardRole.MODERATOR) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    public boolean isManagerOrAdmin(UserEntity user) {
        return roleEvaluator.isManagerOrAdmin(user);
    }

    private boolean isActiveMember(BoardMemberEntity member) {
        if (member == null) {
            return false;
        }
        BoardRole role = member.getBoardRole();
        return role == BoardRole.OWNER || role == BoardRole.MODERATOR || role == BoardRole.MEMBER;
    }
}

