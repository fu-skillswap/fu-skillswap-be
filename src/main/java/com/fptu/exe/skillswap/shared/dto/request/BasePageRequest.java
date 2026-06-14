package com.fptu.exe.skillswap.shared.dto.request;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;

@Getter
@Setter
public class BasePageRequest {
    @Schema(description = "Số trang bắt đầu từ 0", example = "0", defaultValue = "0")
    private int page = 0;

    @Schema(description = "Số phần tử trên mỗi trang", example = "10", defaultValue = "10")
    private int size = 10;

    @Schema(description = "Trường dùng để sắp xếp", example = "createdAt", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Chiều sắp xếp", example = "DESC", allowableValues = {"ASC", "DESC"}, defaultValue = "DESC")
    private String direction = Sort.Direction.DESC.name();

    @Hidden
    public Pageable getPageable() {
        return PageRequest.of(page, size, Sort.by(resolveDirection(), sortBy));
    }

    @Hidden
    public Sort.Direction resolveDirection() {
        if (direction == null || direction.isBlank()) {
            return Sort.Direction.DESC;
        }
        try {
            return Sort.Direction.valueOf(direction.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Sort.Direction.DESC;
        }
    }

    public void setDirection(Sort.Direction direction) {
        this.direction = direction == null ? null : direction.name();
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}

