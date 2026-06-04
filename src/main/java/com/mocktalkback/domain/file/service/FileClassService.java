package com.mocktalkback.domain.file.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.file.dto.FileClassCreateRequest;
import com.mocktalkback.domain.file.dto.FileClassResponse;
import com.mocktalkback.domain.file.dto.FileClassUpdateRequest;
import com.mocktalkback.domain.file.entity.FileClassEntity;
import com.mocktalkback.domain.file.mapper.FileMapper;
import com.mocktalkback.domain.file.repository.FileClassRepository;
import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileClassService {

    private final FileClassRepository fileClassRepository;
    private final FileMapper fileMapper;

    @Transactional
    public FileClassResponse create(FileClassCreateRequest request) {
        FileClassEntity entity = fileMapper.toEntity(request);
        FileClassEntity saved = fileClassRepository.save(entity);
        return fileMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FileClassResponse findById(Long id) {
        FileClassEntity entity = fileClassRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.FILE_CLASS_NOT_FOUND));
        return fileMapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<FileClassResponse> findAll() {
        return fileClassRepository.findAll().stream()
            .map(fileMapper::toResponse)
            .toList();
    }

    @Transactional
    public FileClassResponse update(Long id, FileClassUpdateRequest request) {
        FileClassEntity entity = fileClassRepository.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.FILE_CLASS_NOT_FOUND));
        entity.update(request.name(), request.description(), request.mediaKind());
        return fileMapper.toResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        fileClassRepository.deleteById(id);
    }
}
