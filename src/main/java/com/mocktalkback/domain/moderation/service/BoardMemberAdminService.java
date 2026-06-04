package com.mocktalkback.domain.moderation.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.moderation.dto.BoardMemberListItemResponse;
import com.mocktalkback.domain.moderation.policy.BoardAdminPermissionGuard;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardMemberAdminService {

    private static final int MAX_PAGE_SIZE = 50;

    private final BoardMemberRepository boardMemberRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PageNormalizer pageNormalizer;
    private final BoardAdminPermissionGuard boardAdminPermissionGuard;

    @Transactional(readOnly = true)
    public PageResponse<BoardMemberListItemResponse> findMembers(
        Long boardId,
        BoardRole status,
        int page,
        int size
    ) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);

        List<BoardRole> roles = status == null ? Arrays.asList(BoardRole.values()) : List.of(status);
        Pageable pageable = toPageable(page, size);
        Page<BoardMemberEntity> result = boardMemberRepository.findAllByBoardIdAndBoardRoleIn(boardId, roles, pageable);
        Page<BoardMemberListItemResponse> mapped = result.map(BoardMemberListItemResponse::from);
        return PageResponse.from(mapped);
    }

    @Transactional
    public BoardMemberListItemResponse approve(Long boardId, Long memberId) {
        BoardMemberEntity member = getBoardMember(boardId, memberId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, member.getBoard());

        if (member.getBoardRole() != BoardRole.PENDING) {
            throw new ApiException(ErrorCode.MEMBER_PENDING_APPROVAL_ONLY);
        }
        member.approve(actor);
        return BoardMemberListItemResponse.from(member);
    }

    @Transactional
    public void reject(Long boardId, Long memberId) {
        BoardMemberEntity member = getBoardMember(boardId, memberId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, member.getBoard());

        if (member.getBoardRole() != BoardRole.PENDING) {
            throw new ApiException(ErrorCode.MEMBER_PENDING_APPROVAL_ONLY);
        }
        boardMemberRepository.delete(member);
    }

    @Transactional
    public BoardMemberListItemResponse changeRole(Long boardId, Long memberId, BoardRole targetRole) {
        BoardMemberEntity member = getBoardMember(boardId, memberId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, member.getBoard());
        boardAdminPermissionGuard.ensureOwnerEditable(member, actor);

        if (targetRole != BoardRole.MEMBER && targetRole != BoardRole.MODERATOR) {
            throw new ApiException(ErrorCode.MEMBER_ROLE_INVALID);
        }
        if (member.getBoardRole() == BoardRole.PENDING || member.getBoardRole() == BoardRole.BANNED) {
            throw new ApiException(ErrorCode.MEMBER_ROLE_CHANGE_INVALID);
        }
        member.changeRole(targetRole, actor);
        return BoardMemberListItemResponse.from(member);
    }

    @Transactional
    public BoardMemberListItemResponse changeStatus(Long boardId, Long memberId, BoardRole targetRole) {
        BoardMemberEntity member = getBoardMember(boardId, memberId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, member.getBoard());
        boardAdminPermissionGuard.ensureOwnerEditable(member, actor);

        if (targetRole == BoardRole.BANNED) {
            if (member.getBoardRole() == BoardRole.BANNED) {
                return BoardMemberListItemResponse.from(member);
            }
            if (member.getBoardRole() == BoardRole.PENDING) {
                throw new ApiException(ErrorCode.MEMBER_BLOCK_PENDING_INVALID);
            }
            member.changeRole(BoardRole.BANNED, actor);
            return BoardMemberListItemResponse.from(member);
        }
        if (targetRole == BoardRole.MEMBER) {
            if (member.getBoardRole() != BoardRole.BANNED) {
                throw new ApiException(ErrorCode.MEMBER_UNBLOCK_INVALID);
            }
            member.changeRole(BoardRole.MEMBER, actor);
            return BoardMemberListItemResponse.from(member);
        }
        throw new ApiException(ErrorCode.MEMBER_STATUS_CHANGE_INVALID);
    }

    private BoardMemberEntity getBoardMember(Long boardId, Long memberId) {
        BoardEntity board = getBoard(boardId);
        BoardMemberEntity member = boardMemberRepository.findById(memberId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_MEMBER_NOT_FOUND));
        if (!board.getId().equals(member.getBoard().getId())) {
            throw new ApiException(ErrorCode.MEMBER_NOT_BOARD_MEMBER);
        }
        return member;
    }

    private BoardEntity getBoard(Long boardId) {
        return boardRepository.findByIdAndDeletedAtIsNull(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }

    private UserEntity getCurrentUser() {
        Long userId = currentUserService.getUserId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Pageable toPageable(int page, int size) {
        int normalizedPage = pageNormalizer.normalizePage(page);
        int normalizedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
