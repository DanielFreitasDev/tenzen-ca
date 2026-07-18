package dev.tenzen.ca.ca;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Âncora da cadeia registrada no banco: fingerprint SHA-256 da Root.
 * Se o keystore divergir (ou sumir) com o banco ainda presente, a aplicação
 * recusa subir em vez de regenerar outra AC silenciosamente.
 */
@Entity
@Table(name = "ca_anchor")
public class CaAnchor {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private String rootFingerprintSha256;

    private Instant createdAt;

    protected CaAnchor() {
    }

    public CaAnchor(String rootFingerprintSha256) {
        this.rootFingerprintSha256 = rootFingerprintSha256;
        this.createdAt = Instant.now();
    }

    public String getRootFingerprintSha256() {
        return rootFingerprintSha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
