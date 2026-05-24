package fr.docai.bootstrap;

import fr.docai.application.seed.SeedingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("seed")
public class SeedingCommandLineRunner implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(SeedingCommandLineRunner.class);

    private final SeedingService seedingService;

    public SeedingCommandLineRunner(SeedingService seedingService) {
        this.seedingService = seedingService;
    }

    @Override
    public void run(String... args) {
        try {
            seedingService.seedDevData();
        } catch (Exception e) {
            LOG.error("Seeding failed during application startup", e);
            throw new RuntimeException("Seeding failed - application startup aborted", e);
        }
    }
}
