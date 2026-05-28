package fr.docai.commons.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Tests ApiResponse<T> JSON contract: data array + page metadata (BR-PAG-004/008).
 * T017 — fails until T019 (ApiResponse) is created.
 */
class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializationContainsDataArray() throws Exception {
        PageInfo page = new PageInfo(0, 20, 5L, 1);
        ApiResponse<String> response = new ApiResponse<>(List.of("a", "b"), page);

        String json = mapper.writeValueAsString(response);
        JsonNode node = mapper.readTree(json);

        assertNotNull(node.get("data"), "JSON must contain 'data' field (BR-PAG-008)");
        assertEquals(2, node.get("data").size(),
            "'data' array size must match the response list size");
    }

    @Test
    void serializationContainsPageMetadata() throws Exception {
        PageInfo page = new PageInfo(0, 20, 42L, 3);
        ApiResponse<String> response = new ApiResponse<>(List.of("x"), page);

        String json = mapper.writeValueAsString(response);
        JsonNode node = mapper.readTree(json);
        JsonNode pageNode = node.get("page");

        assertNotNull(pageNode, "JSON must contain 'page' metadata (BR-PAG-004)");
        assertEquals(0, pageNode.get("number").asInt(), "page.number must be 0");
        assertEquals(20, pageNode.get("size").asInt(), "page.size must be 20");
        assertEquals(42L, pageNode.get("totalElements").asLong(),
            "page.totalElements must match");
        assertEquals(3, pageNode.get("totalPages").asInt(), "page.totalPages must be 3");
    }

    @Test
    void emptyDataProducesEmptyArray() throws Exception {
        PageInfo page = new PageInfo(0, 20, 0L, 0);
        ApiResponse<String> response = new ApiResponse<>(List.of(), page);

        String json = mapper.writeValueAsString(response);
        JsonNode node = mapper.readTree(json);

        assertNotNull(node.get("data"), "'data' must not be null even when empty (BR-PAG-008)");
        assertEquals(0, node.get("data").size(), "'data' must be empty array, not null");
    }
}
