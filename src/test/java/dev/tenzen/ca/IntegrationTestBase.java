package dev.tenzen.ca;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Contexto compartilhado dos testes de integração: data-dir temporário (cadeia
 * RSA-4096 gerada uma única vez por JVM) e H2 em memória.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    protected static final Path DATA_DIR = createTempDir();

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("tenzen-ca-test-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", DATA_DIR::toString);
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:tenzen-test;DB_CLOSE_DELAY=-1");
    }
}
