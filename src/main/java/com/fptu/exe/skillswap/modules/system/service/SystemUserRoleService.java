package com.fptu.exe.skillswap.modules.system.service;

import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.system.dto.response.AdminUserResponse;
import com.fptu.exe.skillswap.modules.system.dto.response.SystemUserResponse;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemUserRoleService {

    private final UserRepository userRepository;

    @Transactional
    public AdminUserResponse grantAdminRole(UUID systemAdminId, String email) {
        User targetUser = findTargetUser(email);
        if (targetUser.getRoles().contains(RoleCode.ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này đã có quyền admin");
        }

        User systemAdmin = findActor(systemAdminId);
        targetUser.getRoles().remove(RoleCode.MENTEE);
        targetUser.getRoles().remove(RoleCode.MENTOR);
        targetUser.getRoles().add(RoleCode.ADMIN);
        userRepository.save(targetUser);

        return AdminUserResponse.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .fullName(targetUser.getFullName())
                .avatarUrl(targetUser.getAvatarUrl())
                .status(targetUser.getStatus())
                .assignedBy(systemAdmin.getId())
                .assignedAt(DateTimeUtil.now())
                .build();
    }

    @Transactional
    public AdminUserResponse revokeAdminRole(String email) {
        User targetUser = findTargetUser(email);
        if (!targetUser.getRoles().contains(RoleCode.ADMIN)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này hiện không có quyền admin");
        }

        targetUser.getRoles().remove(RoleCode.ADMIN);
        targetUser.getRoles().remove(RoleCode.MENTOR);
        targetUser.getRoles().add(RoleCode.MENTEE);
        userRepository.save(targetUser);

        return AdminUserResponse.builder()
                .userId(targetUser.getId())
                .email(targetUser.getEmail())
                .fullName(targetUser.getFullName())
                .avatarUrl(targetUser.getAvatarUrl())
                .status(targetUser.getStatus())
                .assignedBy(null)
                .assignedAt(null)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAdminUsers(BasePageRequest pageRequest) {
        Page<User> page = userRepository.findUsersByRole(RoleCode.ADMIN, adminRolePageable(pageRequest));
        return PageResponse.<AdminUserResponse>builder()
                .content(page.getContent().stream().map(user -> AdminUserResponse.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .status(user.getStatus())
                        .assignedBy(null)
                        .assignedAt(null)
                        .build()).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemUserResponse> getAllUsers(BasePageRequest pageRequest) {
        Page<User> page = userRepository.findAll(systemUserPageable(pageRequest));
        Map<UUID, List<RoleCode>> rolesByUserId = loadRolesByUserId(page.getContent());

        return PageResponse.<SystemUserResponse>builder()
                .content(page.getContent().stream()
                        .map(user -> toSystemUserResponse(user, rolesByUserId.getOrDefault(user.getId(), List.of())))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private Pageable adminRolePageable(BasePageRequest request) {
        BasePageRequest safeRequest = request == null ? new BasePageRequest() : request;
        int page = Math.max(safeRequest.getPage(), 0);
        int size = Math.min(Math.max(safeRequest.getSize(), 1), 100);
        Sort.Direction direction = safeRequest.resolveDirection();
        String sortBy = switch (safeRequest.getSortBy() == null ? "" : safeRequest.getSortBy()) {
            case "email" -> "email";
            case "fullName" -> "fullName";
            default -> "createdAt";
        };
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private Pageable systemUserPageable(BasePageRequest request) {
        BasePageRequest safeRequest = request == null ? new BasePageRequest() : request;
        int page = Math.max(safeRequest.getPage(), 0);
        int size = Math.min(Math.max(safeRequest.getSize(), 1), 100);
        Sort.Direction direction = safeRequest.resolveDirection();
        String sortBy = switch (safeRequest.getSortBy() == null ? "" : safeRequest.getSortBy()) {
            case "email" -> "email";
            case "fullName" -> "fullName";
            case "lastLoginAt" -> "lastLoginAt";
            default -> "createdAt";
        };
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private User findTargetUser(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Email không được để trống");
        }
        return userRepository.findActiveByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng với email này"));
    }

    private User findActor(UUID systemAdminId) {
        if (systemAdminId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return userRepository.findById(systemAdminId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản system admin"));
    }

    private Map<UUID, List<RoleCode>> loadRolesByUserId(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<RoleCode>> rolesByUserId = new HashMap<>();
        for (User user : users) {
            rolesByUserId.put(user.getId(), new java.util.ArrayList<>(user.getRoles()));
        }
        return rolesByUserId;
    }

    private SystemUserResponse toSystemUserResponse(User user, List<RoleCode> roles) {
        return SystemUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}




