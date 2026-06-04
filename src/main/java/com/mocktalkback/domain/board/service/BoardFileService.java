package com.mocktalkback.domain.board.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.board.dto.BoardFileCreateRequest;
import com.mocktalkback.domain.board.dto.BoardFileResponse;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardFileEntity;
import com.mocktalkback.domain.board.mapper.BoardMapper;
import com.mocktalkback.domain.board.repository.BoardFileRepository;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.repository.FileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardFileService {

    private final BoardFileRepository boardFileRepository;
    private final BoardRepository boardRepository;
    private final FileRepository fileRepository;
    private final BoardMapper boardMapper;

    @Transactional
    public BoardFileResponse create(BoardFileCreateRequest request) {
        FileEntity file = getFile(request.fileId());
        BoardEntity board = getBoard(request.boardId());
        BoardFileEntity entity = boardMapper.toEntity(request, file, board);
        BoardFileEntity saved = boardFileRepository.save(entity);
        return boardMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BoardFileResponse findById(Long id) {
        BoardFileEntity entity = boardFileRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_FILE_NOT_FOUND));
        return boardMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<BoardFileResponse> findAll() {
        return boardFileRepository.findAll().stream()
            .map(boardMapper::toResponse)
            .toList();
    }

    @Transactional
    public void delete(Long id) {
        boardFileRepository.deleteById(id);
    }

    private FileEntity getFile(Long fileId) {
        return fileRepository.findById(fileId)
            .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));
    }

    private BoardEntity getBoard(Long boardId) {
        return boardRepository.findById(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }
}
