package dev.tenzen.ca.ca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tenzen.ca.IntegrationTestBase;
import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.cert.SubjectData;
import dev.tenzen.ca.issuance.IssuanceService;
import dev.tenzen.ca.issuance.IssuedCertificate;
import java.math.BigInteger;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CrlServiceTest extends IntegrationTestBase {

    @Autowired
    private CrlService crlService;

    @Autowired
    private CaMaterialManager caMaterial;

    @Autowired
    private IssuanceService issuanceService;

    private static long crlNumber(X509CRL crl) throws Exception {
        byte[] extension = crl.getExtensionValue("2.5.29.20");
        assertNotNull(extension, "CRLNumber ausente");
        return ASN1Integer.getInstance(
                ASN1OctetString.getInstance(extension).getOctets()).getValue().longValueExact();
    }

    @Test
    void crlInicialEVaziaAssinadaEComNextUpdateDe24h() throws Exception {
        X509CRL crl = crlService.caCrlParsed();
        crl.verify(caMaterial.issuingCertificate().getPublicKey());
        assertNotNull(crl.getNextUpdate());
        long hours = Duration.between(Instant.now(), crl.getNextUpdate().toInstant()).toHours();
        assertTrue(hours >= 22 && hours <= 24, "nextUpdate deve ficar ~24h à frente");
        assertNotNull(crl.getExtensionValue("2.5.29.35"), "AKI obrigatória na CRL");
        assertTrue(crlNumber(crl) >= 1);
    }

    @Test
    void crlNumberEMonotonicoEntreRepublicacoes() throws Exception {
        long first = crlNumber(crlService.caCrlParsed());
        crlService.rebuildCaCrl();
        long second = crlNumber(crlService.caCrlParsed());
        crlService.rebuildCaCrl();
        long third = crlNumber(crlService.caCrlParsed());
        assertTrue(second == first + 1 && third == second + 1,
                "CRLNumber deve crescer 1 a cada publicação: " + first + "," + second + "," + third);
    }

    @Test
    void revogacaoEntraNaCrlComReasonCode() throws Exception {
        IssuanceService.IssuedResult issued = issuanceService.issueWithGeneratedKey(
                CertificateProfile.RFB_ECPF_A1,
                SubjectData.builder().name("Para Revogar").cpf("12345678909").build(),
                IssuanceService.ValiditySpec.profileYears(1),
                "senha123".toCharArray(), "teste");
        BigInteger serial = issued.certificate().getSerialNumber();

        // antes: não consta
        assertNull(crlService.caCrlParsed().getRevokedCertificate(serial));

        IssuedCertificate revoked = issuanceService.revoke(issued.record().getId(),
                "keyCompromise");
        assertEquals(IssuedCertificate.Status.REVOKED, revoked.getStatus());

        X509CRL crl = crlService.caCrlParsed();
        crl.verify(caMaterial.issuingCertificate().getPublicKey());
        X509CRLEntry entry = crl.getRevokedCertificate(serial);
        assertNotNull(entry, "serial revogado precisa constar na CRL");
        assertEquals("KEY_COMPROMISE", entry.getRevocationReason().name());
    }

    @Test
    void crlDaRaizEVaziaEAssinadaPelaRaiz() throws Exception {
        var holder = new org.bouncycastle.cert.X509CRLHolder(crlService.rootCrl());
        X509CRL crl = new org.bouncycastle.cert.jcajce.JcaX509CRLConverter().getCRL(holder);
        crl.verify(caMaterial.rootCertificate().getPublicKey());
        assertNull(crl.getRevokedCertificates());
    }

    @Test
    void pkixComRevogacaoReprovaRevogadoEAprovaValido() throws Exception {
        IssuanceService.IssuedResult valido = issuanceService.issueWithGeneratedKey(
                CertificateProfile.RFB_ECPF_A1,
                SubjectData.builder().name("Valido No Pkix").cpf("12345678909").build(),
                IssuanceService.ValiditySpec.profileYears(1),
                "senha123".toCharArray(), null);
        IssuanceService.IssuedResult revogado = issuanceService.issueWithGeneratedKey(
                CertificateProfile.RFB_ECPF_A1,
                SubjectData.builder().name("Revogado No Pkix").cpf("12345678909").build(),
                IssuanceService.ValiditySpec.profileYears(1),
                "senha123".toCharArray(), null);
        issuanceService.revoke(revogado.record().getId(), "superseded");

        validateWithRevocation(valido.certificate());

        CertPathValidatorException failure = assertThrows(CertPathValidatorException.class,
                () -> validateWithRevocation(revogado.certificate()));
        assertEquals(CertPathValidatorException.BasicReason.REVOKED, failure.getReason(),
                "o caminho do revogado deve reprovar exatamente por revogação");
    }

    /** PKIX completo com revocação por CRL (a da Intermediária cobre o titular; a da Raiz, a Intermediária). */
    private void validateWithRevocation(X509Certificate leaf) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        CertPath path = factory.generateCertPath(
                List.of(leaf, caMaterial.issuingCertificate()));
        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(caMaterial.rootCertificate(), null)));
        params.setRevocationEnabled(true);
        X509CRL rootCrl = new org.bouncycastle.cert.jcajce.JcaX509CRLConverter()
                .getCRL(new org.bouncycastle.cert.X509CRLHolder(crlService.rootCrl()));
        params.addCertStore(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(
                        List.of(crlService.caCrlParsed(), rootCrl))));
        CertPathValidator.getInstance("PKIX").validate(path, params);
    }
}
