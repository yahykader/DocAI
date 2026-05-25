package fr.docai.domain.event;

public class DocumentUploadedEvent extends DomainEvent {

  private final String fileName;
  private final String mimeType;
  private final long fileSize;

  public DocumentUploadedEvent(String documentId, String tenantId, String fileName, String mimeType, long fileSize) {
    super(documentId, tenantId);
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.fileSize = fileSize;
  }

  @Override
  public String getEventType() {
    return "DOCUMENT_UPLOADED";
  }

  public String getFileName() {
    return fileName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getFileSize() {
    return fileSize;
  }
}
