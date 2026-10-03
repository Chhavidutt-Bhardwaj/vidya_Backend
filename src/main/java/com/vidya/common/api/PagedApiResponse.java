package com.vidya.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Paged variant of the API response envelope.
 *
 * <p>Used for list/search endpoints that return paginated data.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final List<T> data;
    private final PageMetadata page;
    private final Instant timestamp;
    private final String correlationId;

    @Getter
    @Builder
    public static class PageMetadata {
        private final int number;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final boolean first;
        private final boolean last;
    }

    public static <T> PagedApiResponse<T> of(String code, String message,
                                             List<T> data,
                                             org.springframework.data.domain.Page<?> page) {
        return PagedApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .page(PageMetadata.builder()
                        .number(page.getNumber())
                        .size(page.getSize())
                        .totalElements(page.getTotalElements())
                        .totalPages(page.getTotalPages())
                        .first(page.isFirst())
                        .last(page.isLast())
                        .build())
                .timestamp(Instant.now())
                .build();
    }
}
