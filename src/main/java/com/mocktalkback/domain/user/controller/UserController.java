package com.mocktalkback.domain.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mocktalkback.domain.user.dto.UserDeleteRequest;
import com.mocktalkback.domain.user.dto.UserMentionResponse;
import com.mocktalkback.domain.user.dto.UserProfileResponse;
import com.mocktalkback.domain.user.dto.UserProfileUpdateRequest;
import com.mocktalkback.domain.board.service.BoardService;
import com.mocktalkback.domain.user.dto.MyArticleItemResponse;
import com.mocktalkback.domain.user.dto.MyBoardItemResponse;
import com.mocktalkback.domain.user.dto.MyCommentItemResponse;
import com.mocktalkback.domain.user.service.UserService;
import com.mocktalkback.global.common.dto.ApiEnvelope;
import com.mocktalkback.global.common.dto.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "MyPage", description = "마이페이지 API")
public class UserController {

    private final UserService userService;
    private final BoardService boardService;

    @GetMapping("/users/me")
    @Operation(summary = "내 프로필 조회", description = "로그인된 사용자 프로필을 조회합니다.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ApiEnvelope.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiEnvelope<UserProfileResponse> getMyProfile() {
        return ApiEnvelope.ok(userService.getMyProfile());
    }

    @PutMapping("/users/me")
    @Operation(summary = "내 프로필 수정", description = "이름/이메일/닉네임/핸들 및 비밀번호를 수정합니다.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "수정 성공",
            content = @Content(schema = @Schema(implementation = ApiEnvelope.class))
        ),
        @ApiResponse(responseCode = "400", description = "요청 값 오류"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiEnvelope<UserProfileResponse> updateMyProfile(
        @RequestBody @Valid UserProfileUpdateRequest request
    ) {
        return ApiEnvelope.ok(userService.updateMyProfile(request));
    }

    @DeleteMapping("/users/me")
    @Operation(summary = "계정 삭제", description = "탈퇴 문구 확인 후 계정을 소프트 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "400", description = "요청 값 오류"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiEnvelope<Void> deleteMyAccount(
        @RequestBody @Valid UserDeleteRequest request
    ) {
        userService.deleteMyAccount(request);
        return ApiEnvelope.ok();
    }

    @GetMapping("/users/me/boards")
    @Operation(summary = "내 게시판 목록", description = "로그인된 사용자가 운영 중인 게시판(소유자/운영자) 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ApiEnvelope.class))
        ),
        @ApiResponse(responseCode = "400", description = "요청 값 오류"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiEnvelope<PageResponse<MyBoardItemResponse>> getMyBoards(
        @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
        @RequestParam(name = "page", defaultValue = "0") int page,
        @Parameter(description = "페이지 크기(최대 50)", example = "10")
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiEnvelope.ok(boardService.findMyBoards(page, size));
    }

    @GetMapping("/users/me/articles")
    @Operation(summary = "내 게시글 목록", description = "로그인된 사용자의 게시글 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ApiEnvelope.class))
        ),
        @ApiResponse(responseCode = "400", description = "요청 값 오류"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiEnvelope<PageResponse<MyArticleItemResponse>> getMyArticles(
        @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
        @RequestParam(name = "page", defaultValue = "0") int page,
        @Parameter(description = "페이지 크기(최대 50)", example = "10")
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiEnvelope.ok(userService.getMyArticles(page, size));
    }

    @GetMapping("/users/me/comments")
    @Operation(summary = "내 댓글 목록", description = "로그인된 사용자의 댓글 목록을 조회합니다.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ApiEnvelope.class))
        ),
        @ApiResponse(responseCode = "400", description = "요청 값 오류"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiEnvelope<PageResponse<MyCommentItemResponse>> getMyComments(
        @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
        @RequestParam(name = "page", defaultValue = "0") int page,
        @Parameter(description = "페이지 크기(최대 50)", example = "10")
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiEnvelope.ok(userService.getMyComments(page, size));
    }

    @GetMapping("/users/mentions")
    @Operation(summary = "멘션 추천", description = "핸들 기준으로 멘션 후보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ApiEnvelope.class))
        ),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ApiEnvelope<List<UserMentionResponse>> getMentionSuggestions(
        @Parameter(description = "검색 키워드(핸들)", example = "mock")
        @RequestParam(name = "keyword") String keyword,
        @Parameter(description = "최대 반환 개수(기본 10)", example = "10")
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ApiEnvelope.ok(userService.getMentionSuggestions(keyword, size));
    }
}
