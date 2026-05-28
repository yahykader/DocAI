package fr.docai.commons.pagination;

import org.springframework.data.domain.Page;

/**
 * Pagination metadata in every ApiResponse (BR-PAG-004).
 *
 * @param number        0-based page index
 * @param size          number of elements per page
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages
 */
public record PageInfo(int number, int size, long totalElements, int totalPages) {

    /**
     * Creates a PageInfo from a Spring Data {@link Page}.
     *
     * @param page the Spring Data page result
     * @return mapped PageInfo
     */
    public static PageInfo from(Page<?> page) {
        return new PageInfo(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
