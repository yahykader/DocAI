package fr.docai.adapter.out.mongodb.user;

import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserMongoRepository extends MongoRepository<UserDocument, String> {
    Optional<UserDocument> findByTenantIdAndEmail(String tenantId, String email);

    boolean existsByTenantIdAndEmail(String tenantId, String email);
}
