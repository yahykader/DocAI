package fr.docai.adapter.out.mongodb.tenant;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantMongoRepository extends MongoRepository<TenantDocument, String> {
}
