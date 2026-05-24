package fr.docai.domain.user;

public enum Role {
    TENANT_ADMIN("Tenant administrator"),
    ANALYST("Document analyst"),
    VIEWER("Read-only viewer"),
    FRAUD_REVIEWER("Fraud analysis reviewer");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
