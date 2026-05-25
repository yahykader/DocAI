package fr.docai.adapter.out.mongodb.tenant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fr.docai.application.common.exception.RepositoryException;
import fr.docai.domain.model.tenant.Plan;
import fr.docai.domain.model.tenant.Tenant;
import fr.docai.domain.model.tenant.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

@ExtendWith(MockitoExtension.class)
class TenantRepositoryAdapterTest {

    @Mock
    private TenantMongoRepository mongoRepository;

    @Mock
    private TenantMapper mapper;

    private TenantRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TenantRepositoryAdapter(mongoRepository, mapper);
    }

    @Test
    void shouldSaveTenantSuccessfully() {
        var tenantId = TenantId.of("acme-corp");
        var tenant = Tenant.create(tenantId, "ACME Corp", Plan.PRO);
        var document = new TenantDocument();

        when(mapper.toPersistence(tenant)).thenReturn(document);
        when(mongoRepository.save(document)).thenReturn(document);

        assertDoesNotThrow(() -> adapter.save(tenant));
        verify(mapper).toPersistence(tenant);
        verify(mongoRepository).save(document);
    }

    @Test
    void shouldThrowRepositoryExceptionOnSaveFailure() {
        var tenant = Tenant.create(TenantId.of("acme-corp"), "ACME", Plan.PRO);
        var document = new TenantDocument();
        var mongoException = mock(DataAccessException.class);

        when(mapper.toPersistence(tenant)).thenReturn(document);
        when(mongoRepository.save(document)).thenThrow(mongoException);

        var exception = assertThrows(RepositoryException.class, () -> adapter.save(tenant));
        assertEquals("SAVE", exception.getOperationType());
        assertEquals("Tenant", exception.getEntityName());
        assertTrue(exception.getMessage().contains("Failed to save tenant"));
        assertEquals(mongoException, exception.getCause());
    }

    @Test
    void shouldFindTenantByIdSuccessfully() {
        var tenantId = TenantId.of("acme-corp");
        var tenant = Tenant.create(tenantId, "ACME", Plan.PRO);
        var document = new TenantDocument();

        when(mongoRepository.findById("acme-corp")).thenReturn(java.util.Optional.of(document));
        when(mapper.toDomain(document)).thenReturn(tenant);

        var result = adapter.findById(tenantId);

        assertTrue(result.isPresent());
        assertEquals(tenant, result.get());
        verify(mongoRepository).findById("acme-corp");
    }

    @Test
    void shouldThrowRepositoryExceptionOnFindByIdFailure() {
        var tenantId = TenantId.of("acme-corp");
        var mongoException = mock(DataAccessException.class);

        when(mongoRepository.findById("acme-corp")).thenThrow(mongoException);

        var exception = assertThrows(RepositoryException.class, () -> adapter.findById(tenantId));
        assertEquals("FIND_BY_ID", exception.getOperationType());
        assertEquals("Tenant", exception.getEntityName());
        assertTrue(exception.getMessage().contains("Failed to find tenant by id"));
    }

    @Test
    void shouldCheckTenantExistenceSuccessfully() {
        var tenantId = TenantId.of("acme-corp");

        when(mongoRepository.existsById("acme-corp")).thenReturn(true);

        boolean exists = adapter.existsById(tenantId);

        assertTrue(exists);
        verify(mongoRepository).existsById("acme-corp");
    }

    @Test
    void shouldReturnFalseWhenTenantDoesNotExist() {
        var tenantId = TenantId.of("nonexistent");

        when(mongoRepository.existsById("nonexistent")).thenReturn(false);

        boolean exists = adapter.existsById(tenantId);

        assertFalse(exists);
    }

    @Test
    void shouldThrowRepositoryExceptionOnExistsByIdFailure() {
        var tenantId = TenantId.of("acme-corp");
        var mongoException = mock(DataAccessException.class);

        when(mongoRepository.existsById("acme-corp")).thenThrow(mongoException);

        var exception = assertThrows(RepositoryException.class, () -> adapter.existsById(tenantId));
        assertEquals("EXISTS_BY_ID", exception.getOperationType());
        assertEquals("Tenant", exception.getEntityName());
        assertTrue(exception.getMessage().contains("Failed to check tenant existence"));
    }
}
