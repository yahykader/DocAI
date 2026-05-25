package fr.docai.domain.port.out;

public interface StoragePort {

  void upload(String documentId, byte[] fileContent);

  byte[] download(String documentId);

  void delete(String documentId);

  boolean exists(String documentId);
}
