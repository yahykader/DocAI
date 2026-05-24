package fr.docai.application.common.exception;

public class RepositoryException extends RuntimeException {

    private final String operationType;
    private final String entityName;

    public RepositoryException(String message, String operationType, String entityName, Throwable cause) {
        super(message, cause);
        this.operationType = operationType;
        this.entityName = entityName;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getEntityName() {
        return entityName;
    }
}
