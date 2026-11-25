package com.aistudy.assistant.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/**
 * Pagination metadata supporting both offset-based and cursor-based REST responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageMeta {

    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private String nextCursor;

    public static PageMeta ofPage(Page<?> pageResult) {
        return PageMeta.builder()
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    public static PageMeta ofCursor(int size, String nextCursor) {
        return PageMeta.builder()
                .size(size)
                .nextCursor(nextCursor)
                .build();
    }
}
