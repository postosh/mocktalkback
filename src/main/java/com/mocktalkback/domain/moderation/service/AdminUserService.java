package com.mocktalkback.domain.moderation.service;

import com.mocktalkback.global.common.dto.ErrorCode;
import com.mocktalkback.global.i18n.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mocktalkback.domain.common.policy.PageNormalizer;
import com.mocktalkback.domain.moderation.dto.AdminUserListItemResponse;
import com.mocktalkback.domain.moderation.type.AdminUserStatus;
import com.mocktalkback.domain.role.entity.RoleEntity;
import com.mocktalkback.domain.role.repository.RoleRepository;
import com.mocktalkback.domain.user.entity.UserEntity;
import com.mocktalkback.domain.user.repository.UserRepository;
import com.mocktalkback.global.common.dto.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PageNormalizer pageNormalizer;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> search(
        AdminUserStatus status,
        String keyword,
        int page,
        int size
    ) {
        int normalizedPage = pageNormalizer.normalizePage(page);
        int normalizedSize = pageNormalizer.normalizeSize(size, MAX_PAGE_SIZE);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        Pageable pageable = PageRequest.of(
            normalizedPage,
            normalizedSize,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        String statusCode = status != null ? status.name() : null;
        Page<UserEntity> result = userRepository.findAdminUsers(statusCode, normalizedKeyword, pageable);
        Page<AdminUserListItemResponse> mapped = result.map(AdminUserListItemResponse::from);
        return PageResponse.from(mapped);
    }

    @Transactional
    public AdminUserListItemResponse lock(Long userId) {
        UserEntity user = getUser(userId);
        user.lock();
        return AdminUserListItemResponse.from(user);
    }

    @Transactional
    public AdminUserListItemResponse unlock(Long userId) {
        UserEntity user = getUser(userId);
        user.unlock();
        return AdminUserListItemResponse.from(user);
    }

    @Transactional
    public AdminUserListItemResponse changeRole(Long userId, String roleName) {
        UserEntity user = getUser(userId);
        RoleEntity role = roleRepository.findByRoleName(roleName)
            .orElseThrow(() -> new ApiException(ErrorCode.ROLE_NOT_FOUND));
        user.changeRole(role);
        return AdminUserListItemResponse.from(user);
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

}
