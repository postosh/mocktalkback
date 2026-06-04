package com.mocktalkback.domain.file.upload.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.board.dto.BoardResponse;
import com.mocktalkback.domain.board.service.BoardService;
import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.file.service.ArticleAttachmentFileService;
import com.mocktalkback.domain.file.service.EditorFileService;
import com.mocktalkback.domain.file.service.FileStorage;
import com.mocktalkback.domain.file.service.FileStorage.StoredFile;
import com.mocktalkback.domain.file.service.StorageDeleteRetryService;
import com.mocktalkback.domain.file.service.StorageDeleteSource;
import com.mocktalkback.domain.file.upload.config.UploadSessionProperties;
import com.mocktalkback.domain.file.upload.dto.UploadCompleteResponse;
import com.mocktalkback.domain.file.upload.dto.UploadInitContext;
import com.mocktalkback.domain.file.upload.dto.UploadInitRequest;
import com.mocktalkback.domain.file.upload.dto.UploadInitResponse;
import com.mocktalkback.domain.file.upload.type.BoardImageUploadChannel;
import com.mocktalkback.domain.file.upload.type.UploadPurpose;
import com.mocktalkback.domain.moderation.service.AdminBoardService;
import com.mocktalkback.domain.moderation.service.BoardSettingsAdminService;
import com.mocktalkback.domain.user.dto.UserProfileResponse;
import com.mocktalkback.domain.user.service.UserService;
import com.mocktalkback.global.auth.CurrentUserService;

@Service
public class UploadSessionService {

    private final FileStorage fileStorage;
    private final UploadStorageKeyFactory uploadStorageKeyFactory;
    private final UploadPolicyValidator uploadPolicyValidator;
    private final UploadSessionRedisStore uploadSessionRedisStore;
    private final UploadOrphanTrackerRedisStore uploadOrphanTrackerRedisStore;
    private final UploadSessionProperties uploadSessionProperties;
    private final CurrentUserService currentUserService;
    private final EditorFileService editorFileService;
    private final ArticleAttachmentFileService articleAttachmentFileService;
    private final BoardService boardService;
    private final AdminBoardService adminBoardService;
    private final BoardSettingsAdminService boardSettingsAdminService;
    private final UserService userService;
    private final StorageDeleteRetryService storageDeleteRetryService;

    public UploadSessionService(
        FileStorage fileStorage,
        UploadStorageKeyFactory uploadStorageKeyFactory,
        UploadPolicyValidator uploadPolicyValidator,
        UploadSessionRedisStore uploadSessionRedisStore,
        UploadOrphanTrackerRedisStore uploadOrphanTrackerRedisStore,
        UploadSessionProperties uploadSessionProperties,
        CurrentUserService currentUserService,
        EditorFileService editorFileService,
        ArticleAttachmentFileService articleAttachmentFileService,
        BoardService boardService,
        AdminBoardService adminBoardService,
        BoardSettingsAdminService boardSettingsAdminService,
        UserService userService,
        StorageDeleteRetryService storageDeleteRetryService
    ) {
        this.fileStorage = fileStorage;
        this.uploadStorageKeyFactory = uploadStorageKeyFactory;
        this.uploadPolicyValidator = uploadPolicyValidator;
        this.uploadSessionRedisStore = uploadSessionRedisStore;
        this.uploadOrphanTrackerRedisStore = uploadOrphanTrackerRedisStore;
        this.uploadSessionProperties = uploadSessionProperties;
        this.currentUserService = currentUserService;
        this.editorFileService = editorFileService;
        this.articleAttachmentFileService = articleAttachmentFileService;
        this.boardService = boardService;
        this.adminBoardService = adminBoardService;
        this.boardSettingsAdminService = boardSettingsAdminService;
        this.userService = userService;
        this.storageDeleteRetryService = storageDeleteRetryService;
    }

    public UploadInitResponse init(UploadInitRequest request) {
        UploadPurpose purpose = request.purpose();
        UploadInitContext context = request.context();
        uploadPolicyValidator.validateInit(
            purpose,
            request.originalFileName(),
            request.contentType(),
            request.fileSize(),
            context
        );

        Long ownerId = currentUserService.getUserId();
        String uploadToken = UUID.randomUUID().toString();
        String fileClassCode = purpose.toFileClassCode();
        UploadStorageKeyFactory.PreparedUploadFile preparedFile = uploadStorageKeyFactory
            .prepare(fileClassCode, ownerId, request.originalFileName());
        FileStorage.PresignedUploadUrl presignedUploadUrl = fileStorage.createPresignedUploadUrl(
            preparedFile.storageKey(),
            uploadPolicyValidator.normalizeMimeType(request.contentType())
        );

        UploadSessionState state = new UploadSessionState(
            uploadToken,
            ownerId,
            purpose,
            request.originalFileName().trim(),
            preparedFile.fileNameForDatabase(),
            uploadPolicyValidator.normalizeMimeType(request.contentType()),
            request.fileSize(),
            preparedFile.storageKey(),
            context == null ? null : context.boardId(),
            context == null ? null : context.channel(),
            context != null && Boolean.TRUE.equals(context.preserveMetadata()),
            Instant.now()
        );
        long ttlSeconds = normalizeTtlSeconds(uploadSessionProperties.getSessionTtlSeconds());
        uploadSessionRedisStore.save(state, Duration.ofSeconds(ttlSeconds));
        long deadlineEpochSeconds = Instant.now().plusSeconds(ttlSeconds + resolveOrphanCleanupGraceSeconds()).getEpochSecond();
        uploadOrphanTrackerRedisStore.track(uploadToken, preparedFile.storageKey(), deadlineEpochSeconds);

        return new UploadInitResponse(
            uploadToken,
            presignedUploadUrl.uploadUrl(),
            presignedUploadUrl.method(),
            presignedUploadUrl.headers(),
            presignedUploadUrl.expiresAt()
        );
    }

    @Transactional
    public UploadCompleteResponse complete(String uploadToken) {
        UploadSessionState state = uploadSessionRedisStore.consume(uploadToken)
            .orElseThrow(() -> new ApiException(ErrorCode.UPLOAD_TOKEN_INVALID));
        uploadOrphanTrackerRedisStore.untrack(uploadToken);

        try {
            verifyOwner(state.ownerId());
            FileStorage.StoredObjectMeta objectMeta = fileStorage.stat(state.storageKey());
            verifyUploadedFile(state, objectMeta);

            String resolvedMimeType = resolveMimeType(state.expectedMimeType(), objectMeta.mimeType());
            StoredFile storedFile = new StoredFile(
                state.fileNameForDatabase(),
                state.storageKey(),
                objectMeta.fileSize(),
                resolvedMimeType
            );

            return finalizeUpload(state, storedFile);
        } catch (RuntimeException ex) {
            safeDelete(state.storageKey(), StorageDeleteSource.UPLOAD_COMPLETE_ROLLBACK, state.uploadToken());
            throw ex;
        }
    }

    public void cancel(String uploadToken) {
        Optional<UploadSessionState> optional = uploadSessionRedisStore.find(uploadToken);
        if (optional.isEmpty()) {
            uploadOrphanTrackerRedisStore.untrack(uploadToken);
            return;
        }
        UploadSessionState state = optional.get();
        verifyOwner(state.ownerId());
        uploadSessionRedisStore.delete(uploadToken);
        uploadOrphanTrackerRedisStore.untrack(uploadToken);
        safeDelete(state.storageKey(), StorageDeleteSource.UPLOAD_CANCEL, uploadToken);
    }

    private UploadCompleteResponse finalizeUpload(UploadSessionState state, StoredFile storedFile) {
        UploadPurpose purpose = state.purpose();
        if (purpose == UploadPurpose.EDITOR_IMAGE || purpose == UploadPurpose.EDITOR_VIDEO) {
            FileResponse file = editorFileService.completeEditorFileUpload(storedFile, state.preserveMetadata());
            return new UploadCompleteResponse(purpose, file, null, null);
        }
        if (purpose == UploadPurpose.ARTICLE_ATTACHMENT) {
            FileResponse file = articleAttachmentFileService
                .completeArticleAttachmentFileUpload(storedFile, state.preserveMetadata());
            return new UploadCompleteResponse(purpose, file, null, null);
        }
        if (purpose == UploadPurpose.BOARD_IMAGE) {
            BoardResponse board = completeBoardImage(state, storedFile);
            return new UploadCompleteResponse(purpose, null, board, null);
        }
        UserProfileResponse userProfile = userService.completeProfileImageUpload(storedFile, state.preserveMetadata());
        return new UploadCompleteResponse(purpose, null, null, userProfile);
    }

    private BoardResponse completeBoardImage(UploadSessionState state, StoredFile storedFile) {
        Long boardId = state.boardId();
        if (boardId == null || boardId <= 0L) {
            throw new ApiException(ErrorCode.UPLOAD_BOARD_ID_INVALID);
        }
        BoardImageUploadChannel channel = state.boardChannel();
        if (channel == null) {
            throw new ApiException(ErrorCode.UPLOAD_BOARD_CHANNEL_EMPTY);
        }
        if (channel == BoardImageUploadChannel.BOARD_OWNER) {
            return boardService.completeBoardImageUpload(boardId, storedFile, state.preserveMetadata());
        }
        if (channel == BoardImageUploadChannel.ADMIN_BOARD) {
            return adminBoardService.completeBoardImageUpload(boardId, storedFile, state.preserveMetadata());
        }
        return boardSettingsAdminService.completeBoardImageUpload(boardId, storedFile, state.preserveMetadata());
    }

    private void verifyUploadedFile(UploadSessionState state, FileStorage.StoredObjectMeta objectMeta) {
        if (objectMeta.fileSize() == null || objectMeta.fileSize() <= 0L) {
            throw new ApiException(ErrorCode.UPLOAD_FILE_INFO_MISSING);
        }
        if (objectMeta.fileSize() != state.expectedFileSize()) {
            throw new ApiException(ErrorCode.UPLOAD_SIZE_MISMATCH);
        }

        String resolvedMimeType = resolveMimeType(state.expectedMimeType(), objectMeta.mimeType());
        uploadPolicyValidator.validateInit(
            state.purpose(),
            state.originalFileName(),
            resolvedMimeType,
            objectMeta.fileSize(),
            toContext(state)
        );
    }

    private String resolveMimeType(String expectedMimeType, String actualMimeType) {
        if (StringUtils.hasText(actualMimeType)) {
            return uploadPolicyValidator.normalizeMimeType(actualMimeType);
        }
        return uploadPolicyValidator.normalizeMimeType(expectedMimeType);
    }

    private UploadInitContext toContext(UploadSessionState state) {
        if (state.purpose() != UploadPurpose.BOARD_IMAGE) {
            return null;
        }
        return new UploadInitContext(
            state.boardId(),
            state.boardChannel(),
            state.preserveMetadata()
        );
    }

    private void verifyOwner(Long ownerId) {
        Long userId = currentUserService.getUserId();
        if (!userId.equals(ownerId)) {
            throw new ApiException(ErrorCode.UPLOAD_TOKEN_OWNER_MISMATCH);
        }
    }

    private long normalizeTtlSeconds(long ttlSeconds) {
        if (ttlSeconds <= 0L) {
            return 600L;
        }
        return ttlSeconds;
    }

    private long resolveOrphanCleanupGraceSeconds() {
        return Math.max(30L, uploadSessionProperties.getOrphanCleanupGraceSeconds());
    }

    private void safeDelete(String storageKey, StorageDeleteSource source, String contextId) {
        storageDeleteRetryService.deleteNowOrEnqueue(storageKey, source, contextId);
    }
}
