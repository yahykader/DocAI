package fr.docai.adapter.out.mongodb.tenant;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import fr.docai.domain.model.tenant.Plan;

@Document(collection = "tenants")
public class TenantDocument {
    @Id
    private String id;
    private String name;
    private Plan plan;
    private Instant createdAt;

    public TenantDocument() {
    }

    public TenantDocument(String id, String name, Plan plan, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.plan = plan;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
