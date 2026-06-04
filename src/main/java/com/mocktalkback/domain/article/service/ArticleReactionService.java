package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.article.dto.ArticleReactionCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleReactionResponse;
import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.entity.ArticleReactionEntity;
import com.mocktalkback.domain.article.mapper.ArticleMapper;
import com.mocktalkback.domain.article.repository.ArticleReactionRepository;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleReactionService {

    private final ArticleReactionRepository articleReactionRepository;
    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final ArticleMapper articleMapper;

    @Transactional
    public ArticleReactionResponse create(ArticleReactionCreateRequest request) {
        UserEntity user = getUser(request.userId());
        ArticleEntity article = getArticle(request.articleId());
        ArticleReactionEntity entity = articleMapper.toEntity(request, user, article);
        ArticleReactionEntity saved = articleReactionRepository.save(entity);
        return articleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ArticleReactionResponse findById(Long id) {
        ArticleReactionEntity entity = articleReactionRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_REACTION_NOT_FOUND));
        return articleMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ArticleReactionResponse> findAll() {
        return articleReactionRepository.findAll().stream()
            .map(articleMapper::toResponse)
            .toList();
    }

    @Transactional
    public void delete(Long id) {
        articleReactionRepository.deleteById(id);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private ArticleEntity getArticle(Long articleId) {
        return articleRepository.findById(articleId)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));
    }
}
