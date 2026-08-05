package com.fptu.exe.skillswap.infrastructure.bunny.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BunnyCreateVideoResponse {
    private String videoLibraryId;
    private String guid;
    private String title;
    private String dateUploaded;
    private int views;
    private boolean isPublic;
    private int length;
    private int status;
    private int framerate;
    private int width;
    private int height;
    private String availableResolutions;
    private int thumbnailCount;
    private String encodeProgress;
    private String storageSize;
}
