package dev.tenzen.ca.issuance;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Certificado emitido: metadados para o histórico + bytes do .p12/PEM para re-download.
 * No fluxo CSR não há chave privada custodiada, então só o certificado fica guardado.
 */
@Entity
@Table(name = "issued_certificate")
public class IssuedCertificate {

    public enum Status { VALID, REVOKED }

    public enum Source { FORM, CSR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String serialHex;

    @Column(nullable = false, length = 20)
    private String profileId;

    @Column(nullable = false, length = 80)
    private String subjectCn;

    @Column(length = 20)
    private String document;

    @Column(length = 120)
    private String email;

    @Column(nullable = false)
    private Instant notBefore;

    @Column(nullable = false)
    private Instant notAfter;

    @Column(nullable = false)
    private Instant issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.VALID;

    private Instant revokedAt;

    @Column(length = 30)
    private String revocationReason;

    private Integer revocationReasonCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Source source = Source.FORM;

    /** Emitido no modo "laboratório" (validade fora dos tetos normativos). */
    @Column(nullable = false)
    private boolean labMode;

    @Column(nullable = false, length = 100)
    private String fingerprintSha256;

    @Column(length = 64)
    private String keyAlias;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] p12Bytes;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(length = 1_000_000)
    private String pemBundle;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(length = 100_000)
    private String certPem;

    protected IssuedCertificate() {
    }

    public IssuedCertificate(String serialHex, String profileId, String subjectCn,
            String document, String email, Instant notBefore, Instant notAfter,
            Source source, boolean labMode, String fingerprintSha256, String keyAlias,
            byte[] p12Bytes, String pemBundle, String certPem) {
        this.serialHex = serialHex;
        this.profileId = profileId;
        this.subjectCn = subjectCn;
        this.document = document;
        this.email = email;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.issuedAt = Instant.now();
        this.source = source;
        this.labMode = labMode;
        this.fingerprintSha256 = fingerprintSha256;
        this.keyAlias = keyAlias;
        this.p12Bytes = p12Bytes;
        this.pemBundle = pemBundle;
        this.certPem = certPem;
    }

    public void revoke(String reason, int reasonCode) {
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
        this.revocationReason = reason;
        this.revocationReasonCode = reasonCode;
    }

    public Long getId() {
        return id;
    }

    public String getSerialHex() {
        return serialHex;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getSubjectCn() {
        return subjectCn;
    }

    public String getDocument() {
        return document;
    }

    public String getEmail() {
        return email;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public Integer getRevocationReasonCode() {
        return revocationReasonCode;
    }

    public Source getSource() {
        return source;
    }

    public boolean isLabMode() {
        return labMode;
    }

    public String getFingerprintSha256() {
        return fingerprintSha256;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public byte[] getP12Bytes() {
        return p12Bytes;
    }

    public String getPemBundle() {
        return pemBundle;
    }

    public String getCertPem() {
        return certPem;
    }

    public boolean hasPrivateKey() {
        return p12Bytes != null;
    }
}
