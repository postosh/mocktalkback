package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.article.dto.ArticleCategoryCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleCategoryResponse;
import com.mocktalkback.domain.article.dto.ArticleCategoryUpdateRequest;
import com.mocktalkback.domain.article.entity.ArticleCategoryEntity;
import com.mocktalkback.domain.article.mapper.ArticleMapper;
import com.mocktalkback.domain.article.repository.ArticleCategoryRepository;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.repository.BoardRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleCategoryService {

    private final ArticleCategoryRepository articleCategoryRepository;
    private final BoardRepository boardRepository;
    private final ArticleMapper articleMapper;

    @Transactional
    public ArticleCategoryResponse create(ArticleCategoryCreateRequest request) {
        BoardEntity board = getBoard(request.boardId());
        ArticleCategoryEntity entity = articleMapper.toEntity(request, board);
        ArticleCategoryEntity saved = articleCategoryRepository.save(entity);
        return articleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ArticleCategoryResponse findById(Long id) {
        ArticleCategoryEntity entity = articleCategoryRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_CATEGORY_NOT_FOUND));
        return articleMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ArticleCategoryResponse> findAll() {
        return articleCategoryRepository.findAll().stream()
            .map(articleMapper::toResponse)
            .toList();
    }

    @Transactional
    public ArticleCategoryResponse update(Long id, ArticleCategoryUpdateRequest request) {
        ArticleCategoryEntity entity = articleCategoryRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_CATEGORY_NOT_FOUND));
        entity.updateName(request.categoryName());
        return articleMapper.toResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        articleCategoryRepository.deleteById(id);
    }

    private BoardEntity getBoard(Long boardId) {
        return boardRepository.findById(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }
}
