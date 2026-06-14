package com.fptu.exe.skillswap.shared.dto.request;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;

@Getter
@Setter
public class BasePageRequest {
    private int page = 0;
    private int size = 10;
    private String sortBy = "createdAt";
    private String direction = Sort.Direction.DESC.name();

    public Pageable getPageable() {
        return PageRequest.of(page, size, Sort.by(getDirection(), sortBy));
    }

    public Sort.Direction getDirection() {
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

