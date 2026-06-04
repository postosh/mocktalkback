package com.mocktalkback.domain.comment.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.comment.dto.CommentReactionCreateRequest;
import com.mocktalkback.domain.comment.dto.CommentReactionResponse;
import com.mocktalkback.domain.comment.entity.CommentEntity;
import com.mocktalkback.domain.comment.entity.CommentReactionEntity;
import com.mocktalkback.domain.comment.mapper.CommentMapper;
import com.mocktalkback.domain.comment.repository.CommentReactionRepository;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentReactionService {

    private final CommentReactionRepository commentReactionRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    @Transactional
    public CommentReactionResponse create(CommentReactionCreateRequest request) {
        UserEntity user = getUser(request.userId());
        CommentEntity comment = getComment(request.commentId());
        CommentReactionEntity entity = commentMapper.toEntity(request, user, comment);
        CommentReactionEntity saved = commentReactionRepository.save(entity);
        return commentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CommentReactionResponse findById(Long id) {
        CommentReactionEntity entity = commentReactionRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_REACTION_NOT_FOUND));
        return commentMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<CommentReactionResponse> findAll() {
        return commentReactionRepository.findAll().stream()
            .map(commentMapper::toResponse)
            .toList();
    }

    @Transactional
    public void delete(Long id) {
        commentReactionRepository.deleteById(id);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private CommentEntity getComment(Long commentId) {
        return commentRepository.findById(commentId)
            .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
    }
}
