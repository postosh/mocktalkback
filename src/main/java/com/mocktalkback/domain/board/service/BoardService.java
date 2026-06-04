package com.mocktalkback.domain.board.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mocktalkback.domain.board.dto.BoardCreateRequest;
import com.mocktalkback.domain.board.dto.BoardDetailResponse;
import com.mocktalkback.domain.board.dto.BoardMemberStatusResponse;
import com.mocktalkback.domain.board.dto.BoardResponse;
import com.mocktalkback.domain.board.dto.BoardSubscribeItemResponse;
import com.mocktalkback.domain.board.dto.BoardSubscribeStatusResponse;
import com.mocktalkback.domain.board.dto.BoardUpdateRequest;
import com.mocktalkback.domain.user.dto.MyBoardItemResponse;
import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.entity.BoardFileEntity;
import com.mocktalkback.domain.board.entity.BoardMemberEntity;
import com.mocktalkback.domain.board.entity.BoardSubscribeEntity;
import com.mocktalkback.domain.board.mapper.BoardMapper;
import com.mocktalkback.domain.board.repository.BoardFileRepository;
import com.mocktalkback.domain.board.repository.BoardMemberRepository;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.board.repository.BoardSubscribeRepository;
import com.mocktalkback.domain.common.policy.AuthorDisplayResolver;
import com.mocktalkback.domain.common.policy.BoardAccessPolicy;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.board.type.BoardRole;
import com.mocktalkback.domain.board.type.BoardVisibility;
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
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.PageResponse;
import com.mocktalkback.global.common.util.ActivityPointPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort BOARD_SORT = Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("updatedAt"),
        Sort.Order.desc("id")
    );
    private static final Sort BOARD_SUBSCRIBE_SORT = Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id")
    );
    private static final Sort MY_BOARD_SORT = Sort.by(
        Sort.Order.desc("board.createdAt"),
        Sort.Order.desc("id")
    );
    private static final List<BoardRole> MY_BOARD_ROLES = List.of(BoardRole.OWNER, BoardRole.MODERATOR);

    private final BoardRepository boardRepository;
    private final BoardFileRepository boardFileRepository;
    private final BoardMemberRepository boardMemberRepository;
    private final BoardSubscribeRepository boardSubscribeRepository;
    private final FileRepository fileRepository;
    private final FileClassRepository fileClassRepository;
    private final FileVariantRepository fileVariantRepository;
    private final ImageOptimizationService imageOptimizationService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final BoardMapper boardMapper;
    private final FileMapper fileMapper;
    private final BoardAccessPolicy boardAccessPolicy;
    private final PageNormalizer pageNormalizer;
    private final AuthorDisplayResolver authorDisplayResolver;

    @Transactional
    public BoardResponse create(BoardCreateRequest request) {
        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        int requiredPoint = Math.abs(ActivityPointPolicy.CREATE_BOARD.delta);
        if (user.getUserPoint() < requiredPoint) {
            throw new IllegalArgumentException("포인트가 부족합니다.");
        }

        BoardEntity entity = boardMapper.toEntity(request);
        BoardEntity saved = boardRepository.save(entity);
        user.changePoint(ActivityPointPolicy.CREATE_BOARD.delta);

        BoardMemberEntity member = BoardMemberEntity.builder()
            .user(user)
            .board(saved)
            .grantedByUser(user)
            .boardRole(BoardRole.OWNER)
            .build();
        boardMemberRepository.save(member);
        return boardMapper.toResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public BoardDetailResponse findById(Long id) {
        BoardEntity entity = getBoard(id);
        return loadDetail(entity);
    }

    @Transactional(readOnly = true)
    public BoardDetailResponse findBySlug(String slug) {
        BoardEntity entity = getBoardBySlug(slug);
        return loadDetail(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardResponse> findAll(int page, int size) {
        int resolvedPage = pageNormalizer.normalizePage(page);
        int resolvedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, BOARD_SORT);

        Long userId = currentUserService.getOptionalUserId().orElse(null);
        if (userId == null) {
            Page<BoardEntity> result = boardRepository.findAllByVisibilityInAndDeletedAtIsNull(
                List.of(BoardVisibility.PUBLIC),
                pageable
            );
            return toPageResponse(result);
        }

        UserEntity user = getUser(userId);
        if (boardAccessPolicy.isManagerOrAdmin(user)) {
            Page<BoardEntity> result = boardRepository.findAllByDeletedAtIsNull(pageable);
            return toPageResponse(result);
        }
        Page<BoardEntity> result = boardRepository.findAccessibleBoards(
            userId,
            List.of(BoardVisibility.PUBLIC, BoardVisibility.GROUP),
            BoardVisibility.PRIVATE,
            BoardRole.OWNER,
            BoardRole.BANNED,
            pageable
        );

        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardSubscribeItemResponse> findSubscribes(int page, int size) {
        int resolvedPage = pageNormalizer.normalizePage(page);
        int resolvedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, BOARD_SUBSCRIBE_SORT);

        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        Page<BoardSubscribeEntity> pageResult;
        if (boardAccessPolicy.isManagerOrAdmin(user)) {
            pageResult = boardSubscribeRepository.findAllByUserIdAndBoardDeletedAtIsNull(userId, pageable);
        } else {
            pageResult = boardSubscribeRepository.findAccessibleSubscribes(
                userId,
                List.of(BoardVisibility.PUBLIC, BoardVisibility.GROUP),
                BoardVisibility.PRIVATE,
                BoardRole.OWNER,
                BoardRole.BANNED,
                pageable
            );
        }
        List<BoardSubscribeEntity> subscribes = pageResult.getContent();
        List<BoardEntity> boards = subscribes.stream()
            .map(BoardSubscribeEntity::getBoard)
            .toList();

        Map<Long, FileResponse> boardImages = resolveBoardImages(boards);
        List<BoardSubscribeItemResponse> items = subscribes.stream()
            .map(subscribe -> toSubscribeItemResponse(subscribe, boardImages.get(subscribe.getBoard().getId())))
            .toList();

        return new PageResponse<>(
            items,
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages(),
            pageResult.hasNext(),
            pageResult.hasPrevious()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<MyBoardItemResponse> findMyBoards(int page, int size) {
        int resolvedPage = pageNormalizer.normalizePage(page);
        int resolvedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, MY_BOARD_SORT);

        Long userId = currentUserService.getUserId();
        Page<BoardMemberEntity> pageResult = boardMemberRepository.findAllByUserIdAndBoardRoleInAndBoard_DeletedAtIsNull(
            userId,
            MY_BOARD_ROLES,
            pageable
        );

        List<BoardMemberEntity> members = pageResult.getContent();
        List<BoardEntity> boards = members.stream()
            .map(BoardMemberEntity::getBoard)
            .toList();
        Map<Long, FileResponse> boardImages = resolveBoardImages(boards);
        List<MyBoardItemResponse> items = members.stream()
            .map(member -> toMyBoardItemResponse(member, boardImages.get(member.getBoard().getId())))
            .toList();

        return new PageResponse<>(
            items,
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages(),
            pageResult.hasNext(),
            pageResult.hasPrevious()
        );
    }

    @Transactional
    public BoardResponse update(Long id, BoardUpdateRequest request) {
        BoardEntity entity = getBoard(id);
        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        requireManagePermission(entity, user, userId);
        entity.update(request.boardName(), request.slug(), request.description(), request.visibility());
        return boardMapper.toResponse(entity, resolveBoardImage(entity.getId()));
    }

    @Transactional
    public void delete(Long id) {
        BoardEntity entity = getBoard(id);
        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        requireManagePermission(entity, user, userId);
        entity.softDelete();
    }

    @Transactional
    public BoardResponse completeBoardImageUpload(Long boardId, StoredFile storedFile, boolean preserveMetadata) {
        BoardEntity board = getBoard(boardId);
        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        requireManagePermission(board, user, userId);

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
    public BoardSubscribeStatusResponse subscribe(Long boardId) {
        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        BoardEntity board = getBoard(boardId);
        if (!canReadBoard(board, user, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "board not found");
        }
        BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(userId, boardId).orElse(null);
        if (member != null && member.getBoardRole() == BoardRole.BANNED) {
            throw new AccessDeniedException("구독 권한이 없습니다.");
        }
        if (boardSubscribeRepository.existsByUserIdAndBoardId(userId, boardId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 구독 중입니다.");
        }
        BoardSubscribeEntity entity = BoardSubscribeEntity.builder()
            .user(user)
            .board(board)
            .build();
        boardSubscribeRepository.save(entity);
        return new BoardSubscribeStatusResponse(boardId, true);
    }

    @Transactional
    public BoardSubscribeStatusResponse unsubscribe(Long boardId) {
        Long userId = currentUserService.getUserId();
        if (!boardSubscribeRepository.existsByUserIdAndBoardId(userId, boardId)) {
            return new BoardSubscribeStatusResponse(boardId, false);
        }
        boardSubscribeRepository.deleteByUserIdAndBoardId(userId, boardId);
        return new BoardSubscribeStatusResponse(boardId, false);
    }

    @Transactional
    public BoardMemberStatusResponse requestJoin(Long boardId) {
        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        BoardEntity board = getBoard(boardId);

        if (!canReadBoard(board, user, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "board not found");
        }

        if (board.getVisibility() == BoardVisibility.PRIVATE || board.getVisibility() == BoardVisibility.UNLISTED) {
            throw new AccessDeniedException("가입 요청이 허용되지 않습니다.");
        }

        BoardMemberEntity existing = boardMemberRepository.findByUserIdAndBoardId(userId, boardId).orElse(null);
        if (existing != null) {
            if (existing.getBoardRole() == BoardRole.BANNED) {
                throw new AccessDeniedException("가입 요청이 제한된 사용자입니다.");
            }
            if (existing.getBoardRole() == BoardRole.PENDING) {
                boardMemberRepository.delete(existing);
                return new BoardMemberStatusResponse(boardId, null);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 사용자입니다.");
        }

        BoardMemberEntity member = BoardMemberEntity.builder()
            .user(user)
            .board(board)
            .grantedByUser(null)
            .boardRole(BoardRole.PENDING)
            .build();
        boardMemberRepository.save(member);

        return new BoardMemberStatusResponse(boardId, BoardRole.PENDING);
    }

    @Transactional
    public void cancelOrRejectMember(Long boardId, Long targetUserId) {
        Long userId = currentUserService.getUserId();
        UserEntity user = getUser(userId);
        BoardEntity board = getBoard(boardId);

        if (!userId.equals(targetUserId)) {
            requireApprovePermission(board, user, userId);
        }

        BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(targetUserId, boardId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found"));
        boardMemberRepository.delete(member);
    }

    @Transactional
    public void cancelOwnMember(Long boardId) {
        Long userId = currentUserService.getUserId();
        cancelOrRejectMember(boardId, userId);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
    }

    private BoardEntity getBoard(Long id) {
        return boardRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "board not found"));
    }

    private BoardEntity getBoardBySlug(String slug) {
        return boardRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "board not found"));
    }

    private BoardDetailResponse loadDetail(BoardEntity entity) {
        Long userId = currentUserService.getOptionalUserId().orElse(null);
        if (userId == null) {
            if (entity.getVisibility() != BoardVisibility.PUBLIC) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "board not found");
            }
            return toDetailResponse(entity, null);
        }

        UserEntity user = getUser(userId);
        if (!canReadBoard(entity, user, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "board not found");
        }
        return toDetailResponse(entity, userId);
    }

    private BoardDetailResponse toDetailResponse(BoardEntity board, Long userId) {
        FileResponse boardImage = resolveBoardImage(board.getId());
        BoardMemberEntity ownerMember = boardMemberRepository.findFirstByBoardIdAndBoardRole(board.getId(), BoardRole.OWNER)
            .orElse(null);
        String ownerDisplayName = authorDisplayResolver.formatOwnerDisplay(ownerMember == null ? null : ownerMember.getUser());

        BoardRole memberStatus = null;
        boolean subscribed = false;
        if (userId != null) {
            BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(userId, board.getId())
                .orElse(null);
            if (member != null) {
                memberStatus = member.getBoardRole();
            }
            subscribed = boardSubscribeRepository.existsByUserIdAndBoardId(userId, board.getId());
        }

        return boardMapper.toDetailResponse(board, boardImage, ownerDisplayName, memberStatus, subscribed);
    }

    private boolean canReadBoard(BoardEntity board, UserEntity user, Long userId) {
        BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(userId, board.getId())
            .orElse(null);
        return boardAccessPolicy.canAccessBoard(board, user, member);
    }

    private PageResponse<BoardResponse> toPageResponse(Page<BoardEntity> page) {
        List<BoardResponse> items = mapBoardResponses(page.getContent());
        return new PageResponse<>(
            items,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
    }

    private BoardSubscribeItemResponse toSubscribeItemResponse(
        BoardSubscribeEntity subscribe,
        FileResponse boardImage
    ) {
        BoardEntity board = subscribe.getBoard();
        return new BoardSubscribeItemResponse(
            subscribe.getId(),
            board.getId(),
            board.getBoardName(),
            board.getSlug(),
            board.getDescription(),
            board.getVisibility(),
            boardImage,
            subscribe.getCreatedAt()
        );
    }

    private MyBoardItemResponse toMyBoardItemResponse(BoardMemberEntity member, FileResponse boardImage) {
        BoardEntity board = member.getBoard();
        return new MyBoardItemResponse(
            member.getId(),
            board.getId(),
            board.getBoardName(),
            board.getSlug(),
            board.getDescription(),
            board.getVisibility(),
            member.getBoardRole(),
            boardImage,
            member.getCreatedAt()
        );
    }

    private List<BoardResponse> mapBoardResponses(List<BoardEntity> boards) {
        Map<Long, FileResponse> boardImages = resolveBoardImages(boards);
        return boards.stream()
            .map(board -> boardMapper.toResponse(board, boardImages.get(board.getId())))
            .toList();
    }

    private Map<Long, FileResponse> resolveBoardImages(List<BoardEntity> boards) {
        if (boards.isEmpty()) {
            return Map.of();
        }
        List<Long> boardIds = boards.stream()
            .map(BoardEntity::getId)
            .toList();
        List<BoardFileEntity> files = boardFileRepository.findAllByBoardIdInOrderByCreatedAtDesc(boardIds);
        Map<Long, FileResponse> result = new HashMap<>();
        for (BoardFileEntity boardFile : files) {
            Long boardId = boardFile.getBoard().getId();
            if (result.containsKey(boardId)) {
                continue;
            }
            FileEntity file = boardFile.getFile();
            if (file.isDeleted()) {
                continue;
            }
            result.put(boardId, fileMapper.toResponse(file));
        }
        return result;
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

    private void requireManagePermission(BoardEntity board, UserEntity user, Long userId) {
        BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(userId, board.getId())
            .orElse(null);
        boardAccessPolicy.requireManagePermission(user, member, "게시판 관리 권한이 없습니다.");
    }

    private void requireApprovePermission(BoardEntity board, UserEntity user, Long userId) {
        BoardMemberEntity member = boardMemberRepository.findByUserIdAndBoardId(userId, board.getId())
            .orElse(null);
        boardAccessPolicy.requireApprovePermission(user, member, "가입 승인 권한이 없습니다.");
    }
}
