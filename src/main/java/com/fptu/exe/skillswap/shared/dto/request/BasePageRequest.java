package com.fptu.exe.skillswap.shared.dto.request;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import lombok.Data;

@Data
public class BasePageRequest {
    private int page = 0;
    private int size = 10;
    private String sortBy = "createdAt";
    private Sort.Direction direction = Sort.Direction.DESC;

    public Pageable getPageable() {
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}

