package fr.docai.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "fr.docai")
public class DocaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocaiApplication.class, args);
    }
}
