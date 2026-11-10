package com.fptu.exe.skillswap.modules.course.util;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YouTubeUrlUtilsTest {

    private static final String EXPECTED_VIDEO_ID = "dQw4w9WgXcQ";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "www.youtube.com/watch?v=dQw4w9WgXcQ",
            "youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=45s&feature=share",
            "https://youtu.be/dQw4w9WgXcQ",
            "http://youtu.be/dQw4w9WgXcQ?si=abcdef12345",
            "youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/v/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "dQw4w9WgXcQ"
    })
    void extractVideoId_validUrls_extractsIdCorrectly(String input) {
        String videoId = YouTubeUrlUtils.extractVideoId(input);
        assertThat(videoId).isEqualTo(EXPECTED_VIDEO_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://vimeo.com/12345678",
            "https://youtube.com/watch?v=",
            "https://youtube.com/watch",
            "invalid_id",
            "12345"
    })
    void extractVideoId_invalidInputs_throwsBadRequestException(String input) {
        assertThatThrownBy(() -> YouTubeUrlUtils.extractVideoId(input))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void extractVideoId_nullInput_throwsBadRequestException() {
        assertThatThrownBy(() -> YouTubeUrlUtils.extractVideoId(null))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void helperMethods_generateCorrectUrls() {
        String videoId = "dQw4w9WgXcQ";

        assertThat(YouTubeUrlUtils.buildCanonicalUrl(videoId))
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");

        assertThat(YouTubeUrlUtils.buildEmbedUrl(videoId))
                .isEqualTo("https://www.youtube.com/embed/dQw4w9WgXcQ");

        assertThat(YouTubeUrlUtils.buildThumbnailUrl(videoId))
                .isEqualTo("https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg");
    }
}
