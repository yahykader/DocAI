package fr.docai.commons.pagination;

import java.util.List;

/**
 * Generic paginated response contract — the ONLY pagination wrapper in DocAI (BR-PAG-008).
 * Never reimplement this record in other modules.
 *
 * @param <T> element type
 */
public record ApiResponse<T>(List<T> data, PageInfo page) {

    /** Canonical constructor ensuring data is never null (empty list when no results). */
    public ApiResponse {
        data = data != null ? List.copyOf(data) : List.of();
    }
}
