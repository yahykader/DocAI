package fr.docai.commons.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PaginationParams: max-100 enforcement + defaults (BR-PAG-002/003/005/006).
 * T016 — fails until T021 (PaginationParams) is created.
 */
class PaginationParamsTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void size101ShouldFail() {
        PaginationParams params = new PaginationParams(0, 101, "createdAt,desc");
        var violations = validator.validate(params);
        assertEquals(1, violations.size(),
            "size=101 must produce exactly one @Max(100) constraint violation (BR-PAG-005)");
    }

    @Test
    void size100ShouldPass() {
        PaginationParams params = new PaginationParams(0, 100, "createdAt,desc");
        var violations = validator.validate(params);
        assertEquals(0, violations.size(),
            "size=100 is within limit and must produce no violations (BR-PAG-002)");
    }

    @Test
    void size0ShouldFail() {
        PaginationParams params = new PaginationParams(0, 0, "createdAt,desc");
        var violations = validator.validate(params);
        assertEquals(1, violations.size(),
            "size=0 must produce exactly one @Min(1) constraint violation (BR-PAG-001)");
    }

    @Test
    void defaultValuesApplied() {
        PaginationParams params = PaginationParams.defaults();
        assertEquals(PaginationParams.DEFAULT_SIZE, params.size(),
            "Default size must be 20 (BR-PAG-003)");
        assertEquals(PaginationParams.DEFAULT_SORT, params.sort(),
            "Default sort must be 'createdAt,desc' (BR-PAG-006)");
        assertEquals(0, params.page(),
            "Default page must be 0");
    }
}
