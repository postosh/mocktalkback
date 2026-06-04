package com.mocktalkback.domain.moderation.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.board.dto.BoardResponse;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardFileEntity;
import com.mocktalkback.domain.board.mapper.BoardMapper;
import com.mocktalkback.domain.board.repository.BoardFileRepository;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.file.entity.FileClassEntity;
import com.mocktalkback.domain.file.entity.FileEntity;
import com.mocktalkback.domain.file.entity.FileVariantEntity;
import com.mocktalkback.domain.file.mapper.FileMapper;
import com.mocktalkback.domain.file.repository.FileClassRepository;
import com.mocktalkback.domain.file.repository.FileRepository;
import com.mocktalkback.domain.file.repository.FileVariantRepository;
import com.mocktalkback.domain.file.service.FileStorage.StoredFile;
import com.mocktalkback.domain.file.service.ImageOptimizationService;
import com.mocktalkback.domain.file.type.FileClassCode;
import com.mocktalkback.domain.file.type.MediaKind;
import com.mocktalkback.domain.moderation.dto.BoardAdminSettingsUpdateRequest;
import com.mocktalkback.domain.moderation.policy.BoardAdminPermissionGuard;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardSettingsAdminService {

    private final BoardRepository boardRepository;
    private final BoardFileRepository boardFileRepository;
    private final FileRepository fileRepository;
    private final FileClassRepository fileClassRepository;
    private final FileVariantRepository fileVariantRepository;
    private final ImageOptimizationService imageOptimizationService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final BoardMapper boardMapper;
    private final FileMapper fileMapper;
    private final BoardAdminPermissionGuard boardAdminPermissionGuard;

    @Transactional(readOnly = true)
    public BoardResponse getSettings(Long boardId) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);
        return boardMapper.toResponse(board, resolveBoardImage(boardId));
    }

    @Transactional
    public BoardResponse updateSettings(Long boardId, BoardAdminSettingsUpdateRequest request) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);

        String description = request.description();
        if (description != null && description.isBlank()) {
            description = null;
        }

        board.update(
            request.boardName(),
            board.getSlug(),
            description,
            request.visibility(),
            request.articleWritePolicy()
        );
        return boardMapper.toResponse(board, resolveBoardImage(boardId));
    }

    @Transactional
    public BoardResponse completeBoardImageUpload(Long boardId, StoredFile storedFile, boolean preserveMetadata) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);

        ImageOptimizationService.OriginalFileResult processed = imageOptimizationService
            .processOriginal(storedFile, preserveMetadata);
        FileClassEntity fileClass = getBoardImageClass();

        removeExistingBoardImages(boardId);

        FileEntity fileEntity = FileEntity.builder()
            .fileClass(fileClass)
            .fileName(storedFile.fileName())
            .storageKey(storedFile.storageKey())
            .fileSize(processed.fileSize())
            .mimeType(processed.mimeType())
            .metadataPreserved(processed.metadataPreserved())
            .build();

        FileEntity savedFile = fileRepository.save(fileEntity);
        imageOptimizationService.enqueueVariantGeneration(savedFile);
        BoardFileEntity mapping = BoardFileEntity.builder()
            .file(savedFile)
            .board(board)
            .build();
        boardFileRepository.save(mapping);

        return boardMapper.toResponse(board, fileMapper.toResponse(savedFile));
    }

    @Transactional
    public BoardResponse deleteBoardImage(Long boardId) {
        BoardEntity board = getBoard(boardId);
        UserEntity actor = getCurrentUser();
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);

        removeExistingBoardImages(boardId);
        return boardMapper.toResponse(board, null);
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

    private FileResponse resolveBoardImage(Long boardId) {
        List<BoardFileEntity> files = boardFileRepository.findAllByBoardIdOrderByCreatedAtDesc(boardId);
        for (BoardFileEntity boardFile : files) {
            FileEntity file = boardFile.getFile();
            if (file.isDeleted()) {
                continue;
            }
            return fileMapper.toResponse(file);
        }
        return null;
    }

    private void removeExistingBoardImages(Long boardId) {
        List<BoardFileEntity> files = boardFileRepository.findAllByBoardIdOrderByCreatedAtDesc(boardId);
        for (BoardFileEntity boardFile : files) {
            FileEntity file = boardFile.getFile();
            boardFileRepository.delete(boardFile);
            softDeleteVariants(file.getId());
            file.softDelete();
        }
    }

    private void softDeleteVariants(Long fileId) {
        if (fileId == null) {
            return;
        }
        List<FileVariantEntity> variants = fileVariantRepository.findAllByFileIdAndDeletedAtIsNull(fileId);
        for (FileVariantEntity variant : variants) {
            variant.softDelete();
        }
    }

    private FileClassEntity getBoardImageClass() {
        return fileClassRepository.findByCode(FileClassCode.BOARD_IMAGE)
            .orElseGet(() -> fileClassRepository.save(
                FileClassEntity.builder()
                    .code(FileClassCode.BOARD_IMAGE)
                    .name("게시판 대표 이미지")
                    .description("게시판 대표 이미지")
                    .mediaKind(MediaKind.IMAGE)
                    .build()
            ));
    }
}
