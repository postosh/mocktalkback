package com.mocktalkback.domain.article.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import org.springframework.stereotype.Service;

import com.mocktalkback.domain.file.dto.FileResponse;
import com.mocktalkback.domain.file.service.EditorFileService;
import com.mocktalkback.domain.file.service.FileStorage;
import com.mocktalkback.domain.file.service.StorageDeleteRetryService;
import com.mocktalkback.domain.file.service.StorageDeleteSource;
import com.mocktalkback.domain.file.upload.service.UploadStorageKeyFactory;
import com.mocktalkback.domain.file.upload.type.UploadPurpose;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArticleImportAssetStorageService {

    private final FileStorage fileStorage;
    private final UploadStorageKeyFactory uploadStorageKeyFactory;
    private final EditorFileService editorFileService;
    private final StorageDeleteRetryService storageDeleteRetryService;

    public FileResponse storeEditorAsset(Long ownerId, String originalFileName, byte[] bytes, String mimeType) {
        if (ownerId == null) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_OWNER_EMPTY);
        }
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_ASSETS_EMPTY);
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new ApiException(ErrorCode.ARTICLE_IMPORT_ASSETS_MIME_EMPTY);
        }

        UploadPurpose purpose = mimeType.startsWith("image/")
            ? UploadPurpose.EDITOR_IMAGE
            : UploadPurpose.EDITOR_VIDEO;
        UploadStorageKeyFactory.PreparedUploadFile preparedFile = uploadStorageKeyFactory.prepare(
            purpose.toFileClassCode(),
            ownerId,
            originalFileName
        );

        fileStorage.write(preparedFile.storageKey(), bytes, mimeType);
        try {
            return editorFileService.completeEditorFileUpload(
                new FileStorage.StoredFile(
                    preparedFile.fileNameForDatabase(),
                    preparedFile.storageKey(),
                    (long) bytes.length,
                    mimeType
                ),
                false
            );
        } catch (RuntimeException exception) {
            storageDeleteRetryService.deleteNowOrEnqueue(
                preparedFile.storageKey(),
                StorageDeleteSource.UPLOAD_COMPLETE_ROLLBACK,
                "article-import-asset"
            );
            throw exception;
        }
    }
}
