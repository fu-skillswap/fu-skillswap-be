package com.fptu.exe.skillswap.modules.system.service;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.system.dto.AdminUserResponse;
import com.fptu.exe.skillswap.modules.system.dto.SystemUserResponse;
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
    private final UserRoleRepository userRoleRepository;

    @Transactional
    public AdminUserResponse grantAdminRole(UUID systemAdminId, String email) {
        User targetUser = findTargetUser(email);
        UserRoleId roleId = new UserRoleId(targetUser.getId(), RoleCode.ADMIN);
        if (userRoleRepository.existsById(roleId)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này đã có quyền admin");
        }

        User systemAdmin = findActor(systemAdminId);
        UserRole role = userRoleRepository.save(UserRole.builder()
                .id(roleId)
                .user(targetUser)
                .assignedBy(systemAdmin)
                .assignedAt(LocalDateTime.now())
                .build());

        return toResponse(role);
    }

    @Transactional
    public AdminUserResponse revokeAdminRole(String email) {
        User targetUser = findTargetUser(email);
        UserRoleId roleId = new UserRoleId(targetUser.getId(), RoleCode.ADMIN);
        UserRole role = userRoleRepository.findById(roleId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "Người dùng này hiện không có quyền admin"));

        AdminUserResponse response = toResponse(role);
        userRoleRepository.delete(role);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAdminUsers(BasePageRequest pageRequest) {
        Page<UserRole> page = userRoleRepository.findByIdRole(RoleCode.ADMIN, adminRolePageable(pageRequest));
        return PageResponse.<AdminUserResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
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
            case "email" -> "user.email";
            case "fullName" -> "user.fullName";
            case "assignedAt" -> "assignedAt";
            default -> "assignedAt";
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

    private AdminUserResponse toResponse(UserRole role) {
        User user = role.getUser();
        User assignedBy = role.getAssignedBy();
        return AdminUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .assignedBy(assignedBy == null ? null : assignedBy.getId())
                .assignedAt(role.getAssignedAt())
                .build();
    }

    private Map<UUID, List<RoleCode>> loadRolesByUserId(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<RoleCode>> rolesByUserId = new HashMap<>();
        userRoleRepository.findByIdUserIdIn(users.stream().map(User::getId).toList())
                .forEach(userRole -> rolesByUserId.computeIfAbsent(userRole.getId().getUserId(), ignored -> new java.util.ArrayList<>())
                        .add(userRole.getId().getRole()));
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
