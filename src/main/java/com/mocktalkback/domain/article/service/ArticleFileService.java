package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.article.dto.ArticleFileCreateRequest;
import com.mocktalkback.domain.article.dto.ArticleFileResponse;
import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.entity.ArticleFileEntity;
import com.mocktalkback.domain.article.mapper.ArticleMapper;
import com.mocktalkback.domain.article.repository.ArticleFileRepository;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.repository.FileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleFileService {

    private final ArticleFileRepository articleFileRepository;
    private final ArticleRepository articleRepository;
    private final FileRepository fileRepository;
    private final ArticleMapper articleMapper;

    @Transactional
    public ArticleFileResponse create(ArticleFileCreateRequest request) {
        FileEntity file = getFile(request.fileId());
        ArticleEntity article = getArticle(request.articleId());
        ArticleFileEntity entity = articleMapper.toEntity(request, file, article);
        ArticleFileEntity saved = articleFileRepository.save(entity);
        return articleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ArticleFileResponse findById(Long id) {
        ArticleFileEntity entity = articleFileRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_FILE_LINK_NOT_FOUND));
        return articleMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ArticleFileResponse> findAll() {
        return articleFileRepository.findAll().stream()
            .map(articleMapper::toResponse)
            .toList();
    }

    @Transactional
    public void delete(Long id) {
        articleFileRepository.deleteById(id);
    }

    private FileEntity getFile(Long fileId) {
        return fileRepository.findById(fileId)
            .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));
    }

    private ArticleEntity getArticle(Long articleId) {
        return articleRepository.findById(articleId)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));
    }
}
