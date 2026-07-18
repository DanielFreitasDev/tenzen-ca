package dev.tenzen.ca.issuance;

import dev.tenzen.ca.ca.CaCertificateFactory;
import dev.tenzen.ca.ca.CaMaterialManager;
import dev.tenzen.ca.cert.CertificateIssuer;
import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.cert.PemExporter;
import dev.tenzen.ca.cert.Pkcs12Exporter;
import dev.tenzen.ca.cert.SubjectData;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra a emissão: valida validade, gera par de chaves (fluxo formulário),
 * emite pelo {@link CertificateIssuer}, exporta .p12/PEM e persiste no histórico.
 */
@Service
public class IssuanceService {

    /** Teto do modo laboratório: 20 anos. */
    public static final int MAX_LAB_DAYS = 7300;

    private final CertificateIssuer issuer;
    private final CaMaterialManager caMaterial;
    private final IssuanceRepository repository;
    private final dev.tenzen.ca.ca.CrlService crlService;

    public IssuanceService(CertificateIssuer issuer, CaMaterialManager caMaterial,
            IssuanceRepository repository, dev.tenzen.ca.ca.CrlService crlService) {
        this.issuer = issuer;
        this.caMaterial = caMaterial;
        this.repository = repository;
        this.crlService = crlService;
    }

    /** Motivos de revogação expostos na UI, mapeados para o reasonCode da RFC 5280. */
    public static final java.util.Map<String, Integer> REVOCATION_REASONS = java.util.Map.of(
            "unspecified", 0,
            "keyCompromise", 1,
            "affiliationChanged", 3,
            "superseded", 4,
            "cessationOfOperation", 5);

    /** Marca como revogado e republica a CRL imediatamente. */
    @Transactional
    public IssuedCertificate revoke(Long id, String reason) {
        IssuedCertificate cert = repository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("Certificado não encontrado"));
        if (cert.getStatus() == IssuedCertificate.Status.REVOKED) {
            return cert;
        }
        Integer code = REVOCATION_REASONS.get(reason);
        if (code == null) {
            throw new IllegalArgumentException("Motivo de revogação desconhecido: " + reason);
        }
        cert.revoke(reason, code);
        repository.save(cert);
        crlService.rebuildCaCrl();
        return cert;
    }

    /**
     * Especificação de validade: modo "conforme o perfil" (anos dentro do teto normativo)
     * ou "laboratório" (dias livres, marcado como não conforme).
     */
    public record ValiditySpec(boolean laboratory, int years, int days) {

        public static ValiditySpec profileYears(int years) {
            return new ValiditySpec(false, years, 0);
        }

        public static ValiditySpec labDays(int days) {
            return new ValiditySpec(true, 0, days);
        }
    }

    public record IssuedResult(IssuedCertificate record, X509Certificate certificate) {
    }

    /** Fluxo principal: a aplicação gera o par de chaves e entrega .p12 + PEM. */
    @Transactional
    public IssuedResult issueWithGeneratedKey(CertificateProfile profile, SubjectData data,
            ValiditySpec validity, char[] p12Password, String alias) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(
                    "RSA", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(profile.keyBits());
            KeyPair keyPair = generator.generateKeyPair();

            X509Certificate cert = issueCertificate(profile, data, validity, keyPair.getPublic());

            String normalizedAlias = Pkcs12Exporter.normalizeAlias(alias);
            byte[] p12 = Pkcs12Exporter.export(keyPair.getPrivate(), cert,
                    caMaterial.chain(), normalizedAlias, p12Password);
            String pemBundle = PemExporter.bundlePem(keyPair.getPrivate(), cert,
                    caMaterial.chain());

            IssuedCertificate record = save(cert, profile, data, validity,
                    IssuedCertificate.Source.FORM, normalizedAlias, p12, pemBundle);
            return new IssuedResult(record, cert);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha na emissão", e);
        }
    }

    /** Fluxo CSR: a chave é do requerente; só o certificado é persistido. */
    @Transactional
    public IssuedResult issueForPublicKey(CertificateProfile profile, SubjectData data,
            ValiditySpec validity, PublicKey publicKey) {
        try {
            X509Certificate cert = issueCertificate(profile, data, validity, publicKey);
            IssuedCertificate record = save(cert, profile, data, validity,
                    IssuedCertificate.Source.CSR, null, null, null);
            return new IssuedResult(record, cert);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha na emissão a partir do CSR", e);
        }
    }

    private X509Certificate issueCertificate(CertificateProfile profile, SubjectData data,
            ValiditySpec validity, PublicKey publicKey) {
        Instant now = Instant.now();
        Instant notBefore = now.minus(1, ChronoUnit.HOURS);
        Instant notAfter = notBefore.plus(validityDays(profile, validity), ChronoUnit.DAYS);

        BigInteger serial = uniqueSerial();
        return issuer.issue(profile, data, publicKey,
                new CertificateIssuer.ValidityPeriod(Date.from(notBefore), Date.from(notAfter)),
                serial);
    }

    private static long validityDays(CertificateProfile profile, ValiditySpec validity) {
        if (validity.laboratory()) {
            if (validity.days() < 1 || validity.days() > MAX_LAB_DAYS) {
                throw new IllegalArgumentException(
                        "Validade de laboratório deve ficar entre 1 e " + MAX_LAB_DAYS + " dias");
            }
            return validity.days();
        }
        if (validity.years() < 1 || validity.years() > profile.maxValidityYears()) {
            throw new IllegalArgumentException("Validade do perfil " + profile.label()
                    + " deve ficar entre 1 e " + profile.maxValidityYears() + " anos");
        }
        return validity.years() * 365L;
    }

    private BigInteger uniqueSerial() {
        for (int attempt = 0; attempt < 5; attempt++) {
            BigInteger serial = CaCertificateFactory.randomSerial();
            if (!repository.existsBySerialHex(serial.toString(16))) {
                return serial;
            }
        }
        throw new IllegalStateException("Não foi possível sortear um serial único");
    }

    private IssuedCertificate save(X509Certificate cert, CertificateProfile profile,
            SubjectData data, ValiditySpec validity, IssuedCertificate.Source source,
            String alias, byte[] p12, String pemBundle) throws Exception {
        String cn = profile.holder() == CertificateProfile.Holder.PF
                ? valueOrEmpty(data.name()) : valueOrEmpty(data.razaoSocial());
        String document = profile.holder() == CertificateProfile.Holder.PF
                ? valueOrEmpty(data.cpf()) : valueOrEmpty(data.cnpj());
        String fingerprint = HexFormat.ofDelimiter(":").withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        IssuedCertificate record = new IssuedCertificate(
                cert.getSerialNumber().toString(16),
                profile.id(),
                cn.length() > 80 ? cn.substring(0, 80) : cn,
                document,
                valueOrEmpty(data.email()),
                cert.getNotBefore().toInstant(),
                cert.getNotAfter().toInstant(),
                source,
                validity.laboratory(),
                fingerprint,
                alias,
                p12,
                pemBundle,
                PemExporter.certificatePem(cert));
        return repository.save(record);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
