package fr.docai.commons.pagination;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request parameters for paginated list endpoints (BR-PAG-001 to 006).
 * Use {@link #defaults()} to get the standard defaults (page=0, size=20, sort=createdAt,desc).
 *
 * @param page 0-based page index (must be &gt;= 0)
 * @param size page size (1–100, BR-PAG-002/003)
 * @param sort sort expression, e.g. "createdAt,desc"
 */
public record PaginationParams(
        @Min(0) int page,
        @Min(1) @Max(100) int size,
        String sort) {

    /** Default page size per BR-PAG-003. */
    public static final int DEFAULT_SIZE = 20;

    /** Maximum allowed page size per BR-PAG-002. */
    public static final int MAX_SIZE = 100;

    /** Default sort expression per BR-PAG-006. */
    public static final String DEFAULT_SORT = "createdAt,desc";

    /**
     * Returns a PaginationParams with BR-PAG defaults applied.
     *
     * @return default pagination params (page=0, size=20, sort=createdAt,desc)
     */
    public static PaginationParams defaults() {
        return new PaginationParams(0, DEFAULT_SIZE, DEFAULT_SORT);
    }
}
