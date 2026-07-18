package dev.tenzen.ca.cert;

import dev.tenzen.ca.IntegrationTestBase;
import dev.tenzen.ca.issuance.IssuanceService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

class CsrSignerTest extends IntegrationTestBase {

    @Autowired
    private IssuanceService issuanceService;

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String csrPem(KeyPair keyPair) throws Exception {
        PKCS10CertificationRequest csr = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=qualquer coisa, O=ignorado"), keyPair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate()));
        return PemExporter.toPem(csr);
    }

    @Test
    void extraiChavePublicaDeCsrValido() throws Exception {
        KeyPair keyPair = newKeyPair();
        PublicKey extracted = CsrSigner.extractVerifiedPublicKey(csrPem(keyPair));
        assertEquals(keyPair.getPublic(), extracted);
    }

    @Test
    void rejeitaCsrAdulterado() throws Exception {
        String pem = csrPem(newKeyPair());
        // troca um caractere no meio do corpo base64 (adultera a assinatura)
        int middle = pem.length() / 2;
        char original = pem.charAt(middle);
        char swapped = original == 'A' ? 'B' : 'A';
        String tampered = pem.substring(0, middle) + swapped + pem.substring(middle + 1);
        assertThrows(CsrSigner.InvalidCsrException.class,
                () -> CsrSigner.extractVerifiedPublicKey(tampered));
    }

    @Test
    void rejeitaCsrComChaveTrocada() throws Exception {
        // DER íntegro que declara a chave pública de A mas foi assinado com a de B:
        // exercita exatamente o ramo "assinatura não confere" (não o de parse)
        KeyPair a = newKeyPair();
        KeyPair b = newKeyPair();
        PKCS10CertificationRequest csr = new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=chave trocada"), a.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(b.getPrivate()));
        assertThrows(CsrSigner.InvalidCsrException.class,
                () -> CsrSigner.extractVerifiedPublicKey(PemExporter.toPem(csr)));
    }

    @Test
    void rejeitaConteudoQueNaoECsr() {
        assertThrows(CsrSigner.InvalidCsrException.class,
                () -> CsrSigner.extractVerifiedPublicKey("-----BEGIN CERTIFICATE-----\nabc\n-----END CERTIFICATE-----"));
        assertThrows(CsrSigner.InvalidCsrException.class,
                () -> CsrSigner.extractVerifiedPublicKey("   "));
    }

    @Test
    void emissaoViaCsrNaoCustodiaChave() throws Exception {
        KeyPair keyPair = newKeyPair();
        PublicKey publicKey = CsrSigner.extractVerifiedPublicKey(csrPem(keyPair));
        SubjectData data = SubjectData.builder()
                .name("Requerente Externo").cpf("12345678909")
                .email("req@exemplo.com.br").build();
        IssuanceService.IssuedResult result = issuanceService.issueForPublicKey(
                CertificateProfile.RFB_ECPF_A1, data,
                IssuanceService.ValiditySpec.profileYears(1), publicKey);

        assertEquals(keyPair.getPublic(), result.certificate().getPublicKey());
        assertFalse(result.record().hasPrivateKey());
        assertNull(result.record().getP12Bytes());
        assertEquals(dev.tenzen.ca.issuance.IssuedCertificate.Source.CSR,
                result.record().getSource());
    }
}
