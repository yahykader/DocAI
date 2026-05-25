package fr.docai.adapter.out.mongodb.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fr.docai.application.common.exception.RepositoryException;
import fr.docai.domain.model.tenant.TenantId;
import fr.docai.domain.model.user.Role;
import fr.docai.domain.model.user.User;
import fr.docai.domain.model.user.UserId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock
    private UserMongoRepository mongoRepository;

    @Mock
    private UserMapper mapper;

    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserRepositoryAdapter(mongoRepository, mapper);
    }

    @Test
    void shouldSaveUserSuccessfully() {
        var userId = UserId.of("user-123");
        var tenantId = TenantId.of("acme-corp");
        var user = User.create(userId, "admin@acme.test", tenantId, Role.TENANT_ADMIN, "hashed");
        var document = new UserDocument();

        when(mapper.toPersistence(user)).thenReturn(document);
        when(mongoRepository.save(document)).thenReturn(document);

        assertDoesNotThrow(() -> adapter.save(user));
        verify(mapper).toPersistence(user);
        verify(mongoRepository).save(document);
    }

    @Test
    void shouldThrowRepositoryExceptionOnSaveFailure() {
        var user = User.create(UserId.of("u1"), "test@acme.test", TenantId.of("acme-corp"), Role.ANALYST, "hash");
        var document = new UserDocument();
        var mongoException = mock(DataAccessException.class);

        when(mapper.toPersistence(user)).thenReturn(document);
        when(mongoRepository.save(document)).thenThrow(mongoException);

        var exception = assertThrows(RepositoryException.class, () -> adapter.save(user));
        assertEquals("SAVE", exception.getOperationType());
        assertEquals("User", exception.getEntityName());
        assertTrue(exception.getMessage().contains("Failed to save user"));
        assertEquals(mongoException, exception.getCause());
    }

    @Test
    void shouldFindUserByIdSuccessfully() {
        var userId = UserId.of("user-123");
        var user = User.create(userId, "test@acme.test", TenantId.of("acme-corp"), Role.ANALYST, "hash");
        var document = new UserDocument();

        when(mongoRepository.findById("user-123")).thenReturn(java.util.Optional.of(document));
        when(mapper.toDomain(document)).thenReturn(user);

        var result = adapter.findById(userId);

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(mongoRepository).findById("user-123");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        var userId = UserId.of("nonexistent");

        when(mongoRepository.findById("nonexistent")).thenReturn(java.util.Optional.empty());

        var result = adapter.findById(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowRepositoryExceptionOnFindByIdFailure() {
        var userId = UserId.of("user-123");
        var mongoException = mock(DataAccessException.class);

        when(mongoRepository.findById("user-123")).thenThrow(mongoException);

        var exception = assertThrows(RepositoryException.class, () -> adapter.findById(userId));
        assertEquals("FIND_BY_ID", exception.getOperationType());
        assertEquals("User", exception.getEntityName());
        assertTrue(exception.getMessage().contains("Failed to find user by id"));
    }

    @Test
    void shouldFindUserByEmailAndTenantSuccessfully() {
        var tenantId = TenantId.of("acme-corp");
        var email = "admin@acme.test";
        var user = User.create(UserId.of("u1"), email, tenantId, Role.TENANT_ADMIN, "hash");
        var document = new UserDocument();

        when(mongoRepository.findByTenantIdAndEmail("acme-corp", email))
            .thenReturn(java.util.Optional.of(document));
        when(mapper.toDomain(document)).thenReturn(user);

        var result = adapter.findByEmail(tenantId, email);

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(mongoRepository).findByTenantIdAndEmail("acme-corp", email);
    }

    @Test
    void shouldThrowRepositoryExceptionOnFindByEmailFailure() {
        var tenantId = TenantId.of("acme-corp");
        var email = "admin@acme.test";
        var mongoException = mock(DataAccessException.class);

        when(mongoRepository.findByTenantIdAndEmail("acme-corp", email))
            .thenThrow(mongoException);

        var exception = assertThrows(RepositoryException.class,
            () -> adapter.findByEmail(tenantId, email));
        assertEquals("FIND_BY_EMAIL", exception.getOperationType());
        assertEquals("User", exception.getEntityName());
        assertTrue(exception.getMessage().contains("Failed to find user by email"));
    }

    @Test
    void shouldCheckUserExistenceByEmailSuccessfully() {
        var tenantId = TenantId.of("acme-corp");
        var email = "admin@acme.test";

        when(mongoRepository.existsByTenantIdAndEmail("acme-corp", email)).thenReturn(true);

        boolean exists = adapter.existsByEmail(tenantId, email);

        assertTrue(exists);
        verify(mongoRepository).existsByTenantIdAndEmail("acme-corp", email);
    }

    @Test
    void shouldReturnFalseWhenUserDoesNotExistByEmail() {
        var tenantId = TenantId.of("acme-corp");
        var email = "nonexistent@acme.test";

        when(mongoRepository.existsByTenantIdAndEmail("acme-corp", email)).thenReturn(false);

        boolean exists = adapter.existsByEmail(tenantId, email);

        assertFalse(exists);
    }

    @Test
    void shouldThrowRepositoryExceptionOnExistsByEmailFailure() {
        var tenantId = TenantId.of("acme-corp");
        var email = "admin@acme.test";
        var mongoException = mock(DataAccessException.class);

        when(mongoRepository.existsByTenantIdAndEmail("acme-corp", email))
            .thenThrow(mongoException);

        var exception = assertThrows(RepositoryException.class,
            () -> adapter.existsByEmail(tenantId, email));
        assertEquals("EXISTS_BY_EMAIL", exception.getOperationType());
        assertEquals("User", exception.getEntityName());
        assertTrue(exception.getMessage().contains("Failed to check user existence by email"));
    }

    @Test
    void shouldEnforcePerTenantEmailUniqueness() {
        var tenantId1 = TenantId.of("acme-corp");
        var tenantId2 = TenantId.of("beta-assur");
        var email = "admin@test.com";

        when(mongoRepository.existsByTenantIdAndEmail("acme-corp", email)).thenReturn(true);
        when(mongoRepository.existsByTenantIdAndEmail("beta-assur", email)).thenReturn(false);

        assertTrue(adapter.existsByEmail(tenantId1, email));
        assertFalse(adapter.existsByEmail(tenantId2, email));
    }
}
