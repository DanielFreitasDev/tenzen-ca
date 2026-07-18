package dev.tenzen.ca.ca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tenzen.ca.IntegrationTestBase;
import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.cert.SubjectData;
import dev.tenzen.ca.issuance.IssuanceService;
import dev.tenzen.ca.issuance.IssuedCertificate;
import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.time.Duration;
import java.time.Instant;
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
}
