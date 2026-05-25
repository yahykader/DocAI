package fr.docai.domain.model.tenant;

public enum Plan {
    STARTER("Starter plan"),
    PRO("Professional plan"),
    ENTERPRISE("Enterprise plan");

    private final String description;

    Plan(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
