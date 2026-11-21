package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetUploadPort;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.response.UserAvatarResponse;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Manages uploading, confirming, and removing the current user's profile avatar. */
@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private final UserRepository userRepository;
    private final PublicAssetUploadPort publicAssetUploadPort;

    @Transactional
    public PublicAssetUploadPort.UploadIntent createUploadIntent(
            UUID userId,
            PublicAssetUploadPort.UploadRequest request
    ) {
        requireActiveUser(userId);
        return publicAssetUploadPort.createAvatarIntent(userId, request);
    }

    @Transactional
    public UserAvatarResponse confirmAndUpdateAvatar(UUID userId, UUID uploadIntentId) {
        if (uploadIntentId == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "uploadIntentId không được để trống");
        }
        User user = requireActiveUser(userId);
        PublicAssetUploadPort.FileAssetMetadata metadata = publicAssetUploadPort.confirmAvatar(userId, uploadIntentId);
        user.setAvatarFileId(metadata.assetId());
        user.setAvatarUrl(metadata.publicUrl());
        userRepository.save(user);
        return new UserAvatarResponse(user.getId(), metadata.assetId(), metadata.publicUrl(), false);
    }

    @Transactional
    public UserAvatarResponse removeAvatar(UUID userId) {
        User user = requireActiveUser(userId);
        String mailAvatarUrl = (user.getOauthAvatarUrl() != null && !user.getOauthAvatarUrl().isBlank())
                ? user.getOauthAvatarUrl().trim()
                : null;
        user.setAvatarFileId(null);
        user.setAvatarUrl(mailAvatarUrl);
        userRepository.save(user);
        return new UserAvatarResponse(user.getId(), null, mailAvatarUrl, mailAvatarUrl != null);
    }

    private User requireActiveUser(UUID userId) {
        if (userId == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BaseException(ErrorCode.USER_BANNED, "Tài khoản của bạn đã bị khóa");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BaseException(ErrorCode.USER_INACTIVE, "Tài khoản của bạn chưa hoạt động");
        }
        if (user.getStatus() == UserStatus.DELETED || user.getDeletedAt() != null) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND, "Tài khoản đã bị xóa khỏi hệ thống");
        }
        return user;
    }
}
