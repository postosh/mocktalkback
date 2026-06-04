package com.mocktalkback.domain.board.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.board.dto.BoardSubscribeCreateRequest;
import com.mocktalkback.domain.board.dto.BoardSubscribeResponse;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardSubscribeEntity;
import com.mocktalkback.domain.board.mapper.BoardMapper;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.board.repository.BoardSubscribeRepository;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardSubscribeService {

    private final BoardSubscribeRepository boardSubscribeRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final BoardMapper boardMapper;

    @Transactional
    public BoardSubscribeResponse create(BoardSubscribeCreateRequest request) {
        UserEntity user = getUser(request.userId());
        BoardEntity board = getBoard(request.boardId());
        BoardSubscribeEntity entity = boardMapper.toEntity(request, user, board);
        BoardSubscribeEntity saved = boardSubscribeRepository.save(entity);
        return boardMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BoardSubscribeResponse findById(Long id) {
        BoardSubscribeEntity entity = boardSubscribeRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_SUBSCRIBE_NOT_FOUND));
        return boardMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<BoardSubscribeResponse> findAll() {
        return boardSubscribeRepository.findAll().stream()
            .map(boardMapper::toResponse)
            .toList();
    }

    @Transactional
    public void delete(Long id) {
        boardSubscribeRepository.deleteById(id);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private BoardEntity getBoard(Long boardId) {
        return boardRepository.findById(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }
}
