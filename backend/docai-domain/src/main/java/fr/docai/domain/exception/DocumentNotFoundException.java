package fr.docai.domain.exception;

public class DocumentNotFoundException extends DomainException {

  public DocumentNotFoundException(String documentId) {
    super("Document not found: " + documentId);
  }
}
