package com.mocktalkback.domain.comment.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.comment.dto.CommentFileCreateRequest;
import com.mocktalkback.domain.comment.dto.CommentFileResponse;
import com.mocktalkback.domain.comment.entity.CommentEntity;
import com.mocktalkback.domain.comment.entity.CommentFileEntity;
import com.mocktalkback.domain.comment.mapper.CommentMapper;
import com.mocktalkback.domain.comment.repository.CommentFileRepository;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.repository.FileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentFileService {

    private final CommentFileRepository commentFileRepository;
    private final CommentRepository commentRepository;
    private final FileRepository fileRepository;
    private final CommentMapper commentMapper;

    @Transactional
    public CommentFileResponse create(CommentFileCreateRequest request) {
        FileEntity file = getFile(request.fileId());
        CommentEntity comment = getComment(request.commentId());
        CommentFileEntity entity = commentMapper.toEntity(request, file, comment);
        CommentFileEntity saved = commentFileRepository.save(entity);
        return commentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CommentFileResponse findById(Long id) {
        CommentFileEntity entity = commentFileRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_FILE_NOT_FOUND));
        return commentMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CommentFileResponse> findAll() {
        return commentFileRepository.findAll().stream()
            .map(commentMapper::toResponse)
            .toList();
    }

    @Transactional
    public void delete(Long id) {
        commentFileRepository.deleteById(id);
    }

    private FileEntity getFile(Long fileId) {
        return fileRepository.findById(fileId)
            .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));
    }

    private CommentEntity getComment(Long commentId) {
        return commentRepository.findById(commentId)
            .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
    }
}
