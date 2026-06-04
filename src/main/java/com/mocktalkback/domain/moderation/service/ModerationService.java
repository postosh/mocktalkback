package com.mocktalkback.domain.moderation.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mocktalkback.domain.board.entity.BoardEntity;
import com.mocktalkback.domain.board.repository.BoardRepository;
import com.mocktalkback.domain.article.entity.ArticleEntity;
import com.mocktalkback.domain.article.repository.ArticleRepository;
import com.mocktalkback.domain.comment.entity.CommentEntity;
import com.mocktalkback.domain.comment.repository.CommentRepository;
import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.common.policy.RoleEvaluator;
import com.mocktalkback.domain.moderation.dto.AdminAuditLogResponse;
import com.mocktalkback.domain.moderation.dto.ReportCreateRequest;
import com.mocktalkback.domain.moderation.dto.ReportDetailResponse;
import com.mocktalkback.domain.moderation.dto.ReportListItemResponse;
import com.mocktalkback.domain.moderation.dto.ReportProcessRequest;
import com.mocktalkback.domain.moderation.dto.SanctionCreateRequest;
import com.mocktalkback.domain.moderation.dto.SanctionResponse;
import com.mocktalkback.domain.moderation.dto.SanctionRevokeRequest;
import com.mocktalkback.domain.moderation.entity.AdminAuditLogEntity;
import com.mocktalkback.domain.moderation.entity.ReportEntity;
import com.mocktalkback.domain.moderation.entity.SanctionEntity;
import com.mocktalkback.domain.moderation.repository.AdminAuditLogRepository;
import com.mocktalkback.domain.moderation.repository.ReportRepository;
import com.mocktalkback.domain.moderation.repository.SanctionRepository;
import com.mocktalkback.domain.moderation.policy.BoardAdminPermissionGuard;
import com.mocktalkback.domain.moderation.type.AdminActionType;
import com.mocktalkback.domain.moderation.type.AdminTargetType;
import com.mocktalkback.domain.moderation.type.ReportTargetType;
import com.mocktalkback.domain.moderation.type.ReportStatus;
import com.mocktalkback.domain.moderation.type.SanctionScopeType;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.auth.CurrentUserService;
import com.mocktalkback.global.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModerationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Duration REPORT_COOLDOWN = Duration.ofHours(24);
    private static final Sort REPORT_SORT = Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id")
    );
    private static final Sort SANCTION_SORT = Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id")
    );
    private static final Sort AUDIT_SORT = Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id")
    );

    private final ReportRepository reportRepository;
    private final SanctionRepository sanctionRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ArticleRepository articleRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;
    private final CurrentUserService currentUserService;
    private final BoardAdminPermissionGuard boardAdminPermissionGuard;
    private final RoleEvaluator roleEvaluator;
    private final PageNormalizer pageNormalizer;

    @Transactional(readOnly = true)
    public PageResponse<ReportListItemResponse> getAdminReports(ReportStatus status, int page, int size) {
        UserEntity actor = getCurrentUser();
        requireAdmin(actor);
        Pageable pageable = toPageable(page, size, REPORT_SORT);
        Page<ReportEntity> result = status == null
            ? reportRepository.findAll(pageable)
            : reportRepository.findAllByStatus(status, pageable);
        Page<ReportListItemResponse> mapped = result.map(this::toReportItem);
        return PageResponse.from(mapped);
    }

    @Transactional
    public ReportDetailResponse createReport(ReportCreateRequest request) {
        UserEntity reporter = getCurrentUser();
        if (request.targetType() == ReportTargetType.USER && reporter.getId().equals(request.targetId())) {
            throw new ApiException(ErrorCode.REPORT_SELF);
        }
        boolean duplicated = reportRepository.existsByReporterUserIdAndTargetTypeAndTargetIdAndStatusIn(
            reporter.getId(),
            request.targetType(),
            request.targetId(),
            List.of(ReportStatus.PENDING, ReportStatus.IN_REVIEW)
        );
        if (duplicated) {
            throw new ApiException(ErrorCode.REPORT_ALREADY_IN_PROGRESS);
        }
        ReportEntity latest = reportRepository
            .findTopByReporterUserIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
                reporter.getId(),
                request.targetType(),
                request.targetId()
            )
            .orElse(null);
        if (latest != null && isCooldownTarget(latest)) {
            throw new ApiException(ErrorCode.REPORT_DUPLICATE_24H);
        }
        ReportTargetContext context = resolveReportTarget(request);
        ReportEntity report = ReportEntity.builder()
            .reporterUser(reporter)
            .targetUser(context.targetUser)
            .board(context.board)
            .targetType(request.targetType())
            .targetId(request.targetId())
            .targetSnapshot(context.snapshot)
            .reasonCode(request.reasonCode())
            .reasonDetail(request.reasonDetail())
            .status(ReportStatus.PENDING)
            .build();
        reportRepository.save(report);
        return toReportDetail(report);
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse getAdminReport(Long reportId) {
        UserEntity actor = getCurrentUser();
        requireAdmin(actor);
        ReportEntity report = getReport(reportId);
        return toReportDetail(report);
    }

    @Transactional
    public ReportDetailResponse processAdminReport(
        Long reportId,
        ReportProcessRequest request,
        String ipAddress,
        String userAgent
    ) {
        UserEntity actor = getCurrentUser();
        requireAdmin(actor);
        ReportEntity report = getReport(reportId);
        report.process(request.status(), actor, request.processedNote());
        saveAuditLog(
            actor,
            AdminActionType.REPORT_PROCESS,
            AdminTargetType.REPORT,
            report.getId(),
            report.getBoard(),
            "신고 처리: " + report.getId() + " -> " + request.status(),
            null,
            ipAddress,
            userAgent
        );
        return toReportDetail(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportListItemResponse> getBoardReports(Long boardId, ReportStatus status, int page, int size) {
        UserEntity actor = getCurrentUser();
        BoardEntity board = getBoard(boardId);
        requireBoardAdmin(actor, board);
        Pageable pageable = toPageable(page, size, REPORT_SORT);
        Page<ReportEntity> result = status == null
            ? reportRepository.findAllByBoardId(board.getId(), pageable)
            : reportRepository.findAllByBoardIdAndStatus(board.getId(), status, pageable);
        Page<ReportListItemResponse> mapped = result.map(this::toReportItem);
        return PageResponse.from(mapped);
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse getBoardReport(Long boardId, Long reportId) {
        UserEntity actor = getCurrentUser();
        BoardEntity board = getBoard(boardId);
        requireBoardAdmin(actor, board);
        ReportEntity report = getReport(reportId);
        if (report.getBoard() == null || !board.getId().equals(report.getBoard().getId())) {
            throw new ApiException(ErrorCode.BOARD_REPORT_NOT_FOUND);
        }
        return toReportDetail(report);
    }

    @Transactional
    public ReportDetailResponse processBoardReport(
        Long boardId,
        Long reportId,
        ReportProcessRequest request,
        String ipAddress,
        String userAgent
    ) {
        UserEntity actor = getCurrentUser();
        BoardEntity board = getBoard(boardId);
        requireBoardAdmin(actor, board);
        ReportEntity report = getReport(reportId);
        if (report.getBoard() == null || !board.getId().equals(report.getBoard().getId())) {
            throw new ApiException(ErrorCode.BOARD_REPORT_NOT_FOUND);
        }
        report.process(request.status(), actor, request.processedNote());
        saveAuditLog(
            actor,
            AdminActionType.REPORT_PROCESS,
            AdminTargetType.REPORT,
            report.getId(),
            board,
            "게시판 신고 처리: " + report.getId() + " -> " + request.status(),
            null,
            ipAddress,
            userAgent
        );
        return toReportDetail(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<SanctionResponse> getAdminSanctions(
        SanctionScopeType scopeType,
        Long boardId,
        int page,
        int size
    ) {
        UserEntity actor = getCurrentUser();
        requireAdmin(actor);
        Pageable pageable = toPageable(page, size, SANCTION_SORT);
        Page<SanctionEntity> result = findSanctions(scopeType, boardId, pageable);
        Page<SanctionResponse> mapped = result.map(this::toSanctionResponse);
        return PageResponse.from(mapped);
    }

    @Transactional
    public SanctionResponse createAdminSanction(
        SanctionCreateRequest request,
        String ipAddress,
        String userAgent
    ) {
        UserEntity actor = getCurrentUser();
        requireAdmin(actor);
        SanctionEntity entity = createSanction(request, actor, null, false);
        saveAuditLog(
            actor,
            AdminActionType.SANCTION_CREATE,
            AdminTargetType.SANCTION,
            entity.getId(),
            entity.getBoard(),
            "제재 등록: " + entity.getId(),
            null,
            ipAddress,
            userAgent
        );
        return toSanctionResponse(entity);
    }

    @Transactional
    public SanctionResponse revokeAdminSanction(
        Long sanctionId,
        SanctionRevokeRequest request,
        String ipAddress,
        String userAgent
    ) {
        UserEntity actor = getCurrentUser();
        requireAdmin(actor);
        SanctionEntity sanction = getSanction(sanctionId);
        revokeSanction(sanction, actor, request.revokedReason());
        saveAuditLog(
            actor,
            AdminActionType.SANCTION_REVOKE,
            AdminTargetType.SANCTION,
            sanction.getId(),
            sanction.getBoard(),
            "제재 해제: " + sanction.getId(),
            null,
            ipAddress,
            userAgent
        );
        return toSanctionResponse(sanction);
    }

    @Transactional(readOnly = true)
    public PageResponse<SanctionResponse> getBoardSanctions(Long boardId, int page, int size) {
        UserEntity actor = getCurrentUser();
        BoardEntity board = getBoard(boardId);
        requireBoardAdmin(actor, board);
        Pageable pageable = toPageable(page, size, SANCTION_SORT);
        Page<SanctionEntity> result = sanctionRepository.findAllByBoardId(board.getId(), pageable);
        Page<SanctionResponse> mapped = result.map(this::toSanctionResponse);
        return PageResponse.from(mapped);
    }

    @Transactional
    public SanctionResponse createBoardSanction(
        Long boardId,
        SanctionCreateRequest request,
        String ipAddress,
        String userAgent
    ) {
        UserEntity actor = getCurrentUser();
        BoardEntity board = getBoard(boardId);
        requireBoardAdmin(actor, board);
        SanctionEntity entity = createSanction(request, actor, board, true);
        saveAuditLog(
            actor,
            AdminActionType.SANCTION_CREATE,
            AdminTargetType.SANCTION,
            entity.getId(),
            board,
            "게시판 제재 등록: " + entity.getId(),
            null,
            ipAddress,
            userAgent
        );
        return toSanctionResponse(entity);
    }

    @Transactional
    public SanctionResponse revokeBoardSanction(
        Long boardId,
        Long sanctionId,
        SanctionRevokeRequest request,
        String ipAddress,
        String userAgent
    ) {
        UserEntity actor = getCurrentUser();
        BoardEntity board = getBoard(boardId);
        requireBoardAdmin(actor, board);
        SanctionEntity sanction = getSanction(sanctionId);
        if (sanction.getBoard() == null || !board.getId().equals(sanction.getBoard().getId())) {
            throw new ApiException(ErrorCode.BOARD_SANCTION_NOT_FOUND);
        }
        revokeSanction(sanction, actor, request.revokedReason());
        saveAuditLog(
            actor,
            AdminActionType.SANCTION_REVOKE,
            AdminTargetType.SANCTION,
            sanction.getId(),
            board,
            "게시판 제재 해제: " + sanction.getId(),
            null,
            ipAddress,
            userAgent
        );
        return toSanctionResponse(sanction);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogResponse> getAdminAuditLogs(
        AdminActionType actionType,
        Long actorUserId,
        AdminTargetType targetType,
        Long targetId,
        Instant fromAt,
        Instant toAt,
        int page,
        int size
    ) {
        UserEntity actor = getCurrentUser();
        requireAdmin(actor);
        Pageable pageable = toPageable(page, size, AUDIT_SORT);
        Page<AdminAuditLogEntity> result = adminAuditLogRepository.search(
            actionType,
            actorUserId,
            targetType,
            targetId,
            fromAt,
            toAt,
            pageable
        );
        Page<AdminAuditLogResponse> mapped = result.map(this::toAuditLogResponse);
        return PageResponse.from(mapped);
    }

    private SanctionEntity createSanction(
        SanctionCreateRequest request,
        UserEntity actor,
        BoardEntity fixedBoard,
        boolean boardOnly
    ) {
        SanctionScopeType scopeType = request.scopeType();
        if (boardOnly && scopeType != SanctionScopeType.BOARD) {
            throw new AccessDeniedException("Access Denied");
        }
        BoardEntity board = resolveSanctionBoard(scopeType, request.boardId(), fixedBoard);
        Instant startsAt = request.startsAt() == null ? Instant.now() : request.startsAt();
        Instant endsAt = request.endsAt();
        if (endsAt != null && endsAt.isBefore(startsAt)) {
            throw new ApiException(ErrorCode.SANCTION_END_BEFORE_START);
        }
        UserEntity target = getUser(request.userId());
        ReportEntity report = request.reportId() == null ? null : getReport(request.reportId());

        SanctionEntity entity = SanctionEntity.builder()
            .user(target)
            .scopeType(scopeType)
            .board(board)
            .sanctionType(request.sanctionType())
            .reason(request.reason())
            .startsAt(startsAt)
            .endsAt(endsAt)
            .report(report)
            .createdBy(actor)
            .build();
        return sanctionRepository.save(entity);
    }

    private void revokeSanction(SanctionEntity sanction, UserEntity actor, String reason) {
        if (sanction.getRevokedAt() != null) {
            throw new ApiException(ErrorCode.SANCTION_ALREADY_REVOKED);
        }
        sanction.revoke(actor, reason);
    }

    private Page<SanctionEntity> findSanctions(
        SanctionScopeType scopeType,
        Long boardId,
        Pageable pageable
    ) {
        if (scopeType != null && boardId != null) {
            return sanctionRepository.findAllByScopeTypeAndBoardId(scopeType, boardId, pageable);
        }
        if (scopeType != null) {
            return sanctionRepository.findAllByScopeType(scopeType, pageable);
        }
        if (boardId != null) {
            return sanctionRepository.findAllByBoardId(boardId, pageable);
        }
        return sanctionRepository.findAll(pageable);
    }

    private BoardEntity resolveSanctionBoard(
        SanctionScopeType scopeType,
        Long requestBoardId,
        BoardEntity fixedBoard
    ) {
        if (fixedBoard != null) {
            return fixedBoard;
        }
        if (scopeType == SanctionScopeType.BOARD) {
            if (requestBoardId == null) {
                throw new ApiException(ErrorCode.SANCTION_BOARD_ID_REQUIRED);
            }
            return getBoard(requestBoardId);
        }
        return null;
    }

    private void saveAuditLog(
        UserEntity actor,
        AdminActionType actionType,
        AdminTargetType targetType,
        Long targetId,
        BoardEntity board,
        String summary,
        String detailJson,
        String ipAddress,
        String userAgent
    ) {
        AdminAuditLogEntity log = AdminAuditLogEntity.builder()
            .actorUser(actor)
            .actionType(actionType)
            .targetType(targetType)
            .targetId(targetId)
            .board(board)
            .summary(summary)
            .detailJson(detailJson)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .build();
        adminAuditLogRepository.save(log);
    }

    private ReportListItemResponse toReportItem(ReportEntity report) {
        return new ReportListItemResponse(
            report.getId(),
            report.getStatus(),
            report.getTargetType(),
            report.getTargetId(),
            report.getReasonCode(),
            report.getReporterUser().getId(),
            report.getTargetUser() == null ? null : report.getTargetUser().getId(),
            report.getBoard() == null ? null : report.getBoard().getId(),
            report.getProcessedAt(),
            report.getCreatedAt()
        );
    }

    private ReportDetailResponse toReportDetail(ReportEntity report) {
        return new ReportDetailResponse(
            report.getId(),
            report.getStatus(),
            report.getTargetType(),
            report.getTargetId(),
            report.getReasonCode(),
            report.getReasonDetail(),
            report.getTargetSnapshot(),
            report.getReporterUser().getId(),
            report.getTargetUser() == null ? null : report.getTargetUser().getId(),
            report.getBoard() == null ? null : report.getBoard().getId(),
            report.getProcessedBy() == null ? null : report.getProcessedBy().getId(),
            report.getProcessedNote(),
            report.getProcessedAt(),
            report.getCreatedAt(),
            report.getUpdatedAt()
        );
    }

    private SanctionResponse toSanctionResponse(SanctionEntity sanction) {
        return new SanctionResponse(
            sanction.getId(),
            sanction.getUser().getId(),
            sanction.getScopeType(),
            sanction.getBoard() == null ? null : sanction.getBoard().getId(),
            sanction.getSanctionType(),
            sanction.getReason(),
            sanction.getStartsAt(),
            sanction.getEndsAt(),
            sanction.getReport() == null ? null : sanction.getReport().getId(),
            sanction.getCreatedBy().getId(),
            sanction.getRevokedAt(),
            sanction.getRevokedBy() == null ? null : sanction.getRevokedBy().getId(),
            sanction.getRevokedReason(),
            sanction.getCreatedAt(),
            sanction.getUpdatedAt()
        );
    }

    private AdminAuditLogResponse toAuditLogResponse(AdminAuditLogEntity log) {
        return new AdminAuditLogResponse(
            log.getId(),
            log.getActorUser().getId(),
            log.getActionType(),
            log.getTargetType(),
            log.getTargetId(),
            log.getBoard() == null ? null : log.getBoard().getId(),
            log.getSummary(),
            log.getDetailJson(),
            log.getIpAddress(),
            log.getUserAgent(),
            log.getCreatedAt()
        );
    }

    private ReportEntity getReport(Long reportId) {
        return reportRepository.findById(reportId)
            .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
    }

    private ReportTargetContext resolveReportTarget(ReportCreateRequest request) {
        ReportTargetType targetType = request.targetType();
        if (targetType == ReportTargetType.ARTICLE) {
            ArticleEntity article = getArticle(request.targetId());
            UserEntity targetUser = article.getUser();
            String snapshot = buildArticleSnapshot(article);
            return new ReportTargetContext(targetUser, article.getBoard(), snapshot);
        }
        if (targetType == ReportTargetType.COMMENT) {
            CommentEntity comment = getComment(request.targetId());
            UserEntity targetUser = comment.getUser();
            BoardEntity board = comment.getArticle().getBoard();
            String snapshot = buildCommentSnapshot(comment);
            return new ReportTargetContext(targetUser, board, snapshot);
        }
        if (targetType == ReportTargetType.USER) {
            UserEntity targetUser = getActiveUser(request.targetId());
            String snapshot = buildUserSnapshot(targetUser);
            return new ReportTargetContext(targetUser, null, snapshot);
        }
        if (targetType == ReportTargetType.BOARD) {
            BoardEntity board = getBoard(request.targetId());
            String snapshot = buildBoardSnapshot(board);
            return new ReportTargetContext(null, board, snapshot);
        }
        throw new ApiException(ErrorCode.REPORT_TYPE_UNSUPPORTED);
    }

    private boolean isCooldownTarget(ReportEntity latest) {
        ReportStatus status = latest.getStatus();
        if (status != ReportStatus.RESOLVED && status != ReportStatus.REJECTED) {
            return false;
        }
        Instant createdAt = latest.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        Instant cutoff = Instant.now().minus(REPORT_COOLDOWN);
        return createdAt.isAfter(cutoff);
    }

    private ArticleEntity getArticle(Long articleId) {
        return articleRepository.findByIdAndDeletedAtIsNull(articleId)
            .orElseThrow(() -> new ApiException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    private CommentEntity getComment(Long commentId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
            .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private SanctionEntity getSanction(Long sanctionId) {
        return sanctionRepository.findById(sanctionId)
            .orElseThrow(() -> new ApiException(ErrorCode.SANCTION_NOT_FOUND));
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private UserEntity getActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private BoardEntity getBoard(Long boardId) {
        return boardRepository.findByIdAndDeletedAtIsNull(boardId)
            .orElseThrow(() -> new ApiException(ErrorCode.BOARD_NOT_FOUND));
    }

    private UserEntity getCurrentUser() {
        Long userId = currentUserService.getUserId();
        return getUser(userId);
    }

    private void requireAdmin(UserEntity actor) {
        if (!roleEvaluator.isAdmin(actor)) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    private void requireBoardAdmin(UserEntity actor, BoardEntity board) {
        boardAdminPermissionGuard.requireBoardAdmin(actor, board);
    }

    private Pageable toPageable(int page, int size, Sort sort) {
        int normalizedPage = pageNormalizer.normalizePage(page);
        int normalizedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize, sort);
    }

    private String buildArticleSnapshot(ArticleEntity article) {
        String title = escapeJson(article.getTitle());
        String content = escapeJson(truncate(article.getContent(), 500));
        return "{\"title\":\"" + title + "\",\"content\":\"" + content + "\",\"userId\":" + article.getUser().getId()
            + ",\"boardId\":" + article.getBoard().getId() + "}";
    }

    private String buildCommentSnapshot(CommentEntity comment) {
        String content = escapeJson(truncate(comment.getContent(), 300));
        return "{\"content\":\"" + content + "\",\"userId\":" + comment.getUser().getId()
            + ",\"articleId\":" + comment.getArticle().getId() + "}";
    }

    private String buildUserSnapshot(UserEntity user) {
        String displayName = escapeJson(user.getDisplayName());
        String handle = escapeJson(user.getHandle());
        return "{\"userId\":" + user.getId() + ",\"displayName\":\"" + displayName + "\",\"handle\":\"" + handle + "\"}";
    }

    private String buildBoardSnapshot(BoardEntity board) {
        String name = escapeJson(board.getBoardName());
        String slug = escapeJson(board.getSlug());
        String description = escapeJson(truncate(board.getDescription(), 200));
        return "{\"boardId\":" + board.getId() + ",\"boardName\":\"" + name + "\",\"slug\":\"" + slug
            + "\",\"description\":\"" + description + "\"}";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ReportTargetContext(UserEntity targetUser, BoardEntity board, String snapshot) {}
}
