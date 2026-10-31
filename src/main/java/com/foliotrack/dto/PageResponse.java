package com.foliotrack.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// Our own page DTO instead of serialising Spring's PageImpl, whose JSON shape is an internal detail
// that Spring Data warns may change. This keeps the API contract stable and explicit.
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
