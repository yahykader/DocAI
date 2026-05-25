package fr.docai.bootstrap.config;

import fr.docai.application.seed.PasswordEncoder;
import fr.docai.application.seed.SeedingService;
import fr.docai.domain.port.out.TenantRepositoryPort;
import fr.docai.domain.port.out.UserRepositoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@Profile("seed")
public class SeedingConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoderAdapter(new BCryptPasswordEncoder());
    }

    @Bean
    public SeedingService seedingService(
            TenantRepositoryPort tenantRepository,
            UserRepositoryPort userRepository,
            PasswordEncoder passwordEncoder) {
        return new SeedingService(tenantRepository, userRepository, passwordEncoder);
    }

    static class PasswordEncoderAdapter implements PasswordEncoder {
        private final BCryptPasswordEncoder delegate;

        PasswordEncoderAdapter(BCryptPasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(String rawPassword) {
            return delegate.encode(rawPassword);
        }
    }
}
