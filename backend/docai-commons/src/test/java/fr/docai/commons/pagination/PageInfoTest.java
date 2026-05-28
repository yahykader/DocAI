package fr.docai.commons.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

/**
 * Tests PageInfo.from(Page<?>) factory mapping from Spring pagination (BR-PAG-004).
 * T018 — fails until T020 (PageInfo) is created.
 */
class PageInfoTest {

    @Test
    @SuppressWarnings("unchecked")
    void fromSpringPageMapsAllFields() {
        Page<String> mockPage = mock(Page.class);
        when(mockPage.getNumber()).thenReturn(2);
        when(mockPage.getSize()).thenReturn(10);
        when(mockPage.getTotalElements()).thenReturn(95L);
        when(mockPage.getTotalPages()).thenReturn(10);

        PageInfo info = PageInfo.from(mockPage);

        assertEquals(2, info.number(), "page.number must map from Page.getNumber()");
        assertEquals(10, info.size(), "page.size must map from Page.getSize()");
        assertEquals(95L, info.totalElements(),
            "page.totalElements must map from Page.getTotalElements()");
        assertEquals(10, info.totalPages(),
            "page.totalPages must map from Page.getTotalPages()");
    }

    @Test
    @SuppressWarnings("unchecked")
    void fromSpringPageFirstPage() {
        Page<Object> mockPage = mock(Page.class);
        when(mockPage.getNumber()).thenReturn(0);
        when(mockPage.getSize()).thenReturn(20);
        when(mockPage.getTotalElements()).thenReturn(0L);
        when(mockPage.getTotalPages()).thenReturn(0);

        PageInfo info = PageInfo.from(mockPage);

        assertEquals(0, info.number(), "First page number must be 0");
        assertEquals(0, info.totalPages(), "Empty result must have 0 totalPages");
    }
}
