package com.mocktalkback.domain.board.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.type.BoardRole;

public interface BoardMemberRepository extends JpaRepository<BoardMemberEntity, Long> {
    boolean existsByUserIdAndBoardId(Long userId, Long boardId);

    Optional<BoardMemberEntity> findByUserIdAndBoardId(Long userId, Long boardId);

    Optional<BoardMemberEntity> findFirstByBoardIdAndBoardRole(Long boardId, BoardRole boardRole);

    @EntityGraph(attributePaths = {"user", "grantedByUser"})
    Page<BoardMemberEntity> findAllByBoardIdAndBoardRoleIn(Long boardId, Collection<BoardRole> boardRoles, Pageable pageable);

    @EntityGraph(attributePaths = {"board"})
    Page<BoardMemberEntity> findAllByUserIdAndBoardRoleInAndBoard_DeletedAtIsNull(
        Long userId,
        Collection<BoardRole> boardRoles,
        Pageable pageable
    );

    List<BoardMemberEntity> findAllByUserId(Long userId);

    List<BoardMemberEntity> findAllByUserIdAndBoardIdIn(Long userId, Collection<Long> boardIds);
}
