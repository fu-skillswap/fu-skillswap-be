package com.fptu.exe.skillswap.infrastructure.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fptu.exe.skillswap.infrastructure.config.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(Cloudinary.class)
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final CloudinaryProperties cloudinaryProperties;

    public CloudinaryUploadResult upload(MultipartFile file, String subFolder) throws IOException {
        String folder = buildFolder(subFolder);
        String publicId = folder + "/" + UUID.randomUUID();

        Map<?, ?> result;
        try (var inputStream = file.getInputStream()) {
            result = cloudinary.uploader().upload(
                    inputStream,
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "overwrite", true,
                            "resource_type", "image",
                            "quality", "auto",
                            "fetch_format", "auto"
                    )
            );
        }

        String secureUrl = (String) result.get("secure_url");
        String returnedId = (String) result.get("public_id");

        log.info("Cloudinary upload success: publicId={}, url={}", returnedId, secureUrl);
        return new CloudinaryUploadResult(returnedId, secureUrl);
    }

    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Cloudinary delete success: publicId={}", publicId);
        } catch (IOException e) {
            log.warn("Cloudinary delete failed: publicId={}, reason={}", publicId, e.getMessage());
        }
    }

    public String buildUrl(String publicId) {
        return cloudinary.url().secure(true).generate(publicId);
    }

    private String buildFolder(String subFolder) {
        String root = cloudinaryProperties.getFolder();
        if (subFolder == null || subFolder.isBlank()) {
            return root;
        }
        return root + "/" + subFolder.trim();
    }

    public record CloudinaryUploadResult(String publicId, String secureUrl) {
    }
}
