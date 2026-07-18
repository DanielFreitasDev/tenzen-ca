package dev.tenzen.ca.cert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tenzen.ca.IntegrationTestBase;
import dev.tenzen.ca.ca.CaMaterialManager;
import dev.tenzen.ca.ca.CaCertificateFactory;
import dev.tenzen.ca.config.AppProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Golden tests ASN.1: emite um certificado por perfil e confere DN, otherNames
 * (largura exata e codificação OCTET STRING), criticidades, EKU, policy, CRL DPs,
 * AIA, assinatura e caminho PKIX completo até a Raiz.
 */
class CertificateGoldenTest extends IntegrationTestBase {

    @Autowired
    private CertificateIssuer issuer;

    @Autowired
    private CaMaterialManager caMaterial;

    @Autowired
    private AppProperties props;

    private static SubjectData person() {
        return SubjectData.builder()
                .name("João da Silva")
                .cpf("01672780838")
                .birthDate(LocalDate.of(1980, 3, 25))
                .nis("123456789")
                .rg("998877")
                .rgIssuerUf("SSPCE")
                .voterId("1234567890")
                .voterZone("12")
                .voterSection("345")
                .ceiNit("55555")
                .city("Fortaleza")
                .uf("CE")
                .email("joao@exemplo.com.br")
                .validationType("videoconferencia")
                .build();
    }

    private static SubjectData company() {
        return SubjectData.builder()
                .razaoSocial("Casa Liquidação")
                .cnpj("99999999000191")
                .companyCei("123456")
                .responsibleName("Maria Souza")
                .responsibleCpf("01672780838")
                .responsibleBirthDate(LocalDate.of(1975, 12, 1))
                .responsibleNis("987654321")
                .responsibleRg("112233")
                .responsibleRgIssuerUf("SSPSP")
                .city("São Paulo")
                .uf("SP")
                .email("contato@exemplo.com.br")
                .build();
    }

    private X509Certificate issue(CertificateProfile profile, SubjectData data) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(profile.keyBits());
        KeyPair pair = generator.generateKeyPair();
        Instant now = Instant.now();
        CertificateIssuer.ValidityPeriod validity = new CertificateIssuer.ValidityPeriod(
                Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)));
        X509Certificate cert = issuer.issue(profile, data, pair.getPublic(), validity,
                CaCertificateFactory.randomSerial());
        cert.verify(caMaterial.issuingCertificate().getPublicKey());
        validatePkixPath(cert);
        return cert;
    }

    private void validatePkixPath(X509Certificate leaf) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        var path = factory.generateCertPath(List.of(leaf, caMaterial.issuingCertificate()));
        PKIXParameters params = new PKIXParameters(
                Set.of(new TrustAnchor(caMaterial.rootCertificate(), null)));
        params.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(path, params);
    }

    private static byte[] otherNameValue(X509Certificate cert, String oid) throws Exception {
        byte[] extension = cert.getExtensionValue("2.5.29.17");
        assertNotNull(extension, "SAN ausente");
        ASN1OctetString wrapped = ASN1OctetString.getInstance(extension);
        GeneralNames san = GeneralNames.getInstance(ASN1Primitive.fromByteArray(wrapped.getOctets()));
        for (GeneralName name : san.getNames()) {
            if (name.getTagNo() != GeneralName.otherName) {
                continue;
            }
            ASN1Sequence seq = ASN1Sequence.getInstance(name.getName());
            if (seq.getObjectAt(0).toString().equals(oid)) {
                ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(seq.getObjectAt(1));
                return ASN1OctetString.getInstance(tagged.getExplicitBaseObject()).getOctets();
            }
        }
        return null;
    }

    private void assertCommonProfile(X509Certificate cert, CertificateProfile profile)
            throws Exception {
        // keyUsage crítica com DS+NR+KE (leiaute legado; permitidos na nova geração)
        assertTrue(cert.getCriticalExtensionOIDs().contains("2.5.29.15"));
        boolean[] keyUsage = cert.getKeyUsage();
        assertTrue(keyUsage[0], "digitalSignature");
        assertTrue(keyUsage[1], "nonRepudiation");
        assertTrue(keyUsage[2], "keyEncipherment");

        // basicConstraints não crítica CA:FALSE; SKI ausente (item revogado do leiaute)
        assertEquals(-1, cert.getBasicConstraints());
        assertFalse(cert.getCriticalExtensionOIDs().contains("2.5.29.19"));
        assertNull(cert.getExtensionValue("2.5.29.14"), "titular não leva SKI");
        assertNotNull(cert.getExtensionValue("2.5.29.35"), "AKI obrigatória");

        // EKU: clientAuth + emailProtection (+ smartcard só no e-CPF legado)
        List<String> eku = cert.getExtendedKeyUsage();
        assertTrue(eku.contains("1.3.6.1.5.5.7.3.2"));
        assertTrue(eku.contains("1.3.6.1.5.5.7.3.4"));
        assertEquals(profile.includeSmartcardLogon(),
                eku.contains("1.3.6.1.4.1.311.20.2.2"));

        // exatamente 2 URLs de CRL distintas
        byte[] crlDp = cert.getExtensionValue("2.5.29.31");
        assertNotNull(crlDp);
        CRLDistPoint points = CRLDistPoint.getInstance(
                ASN1Primitive.fromByteArray(ASN1OctetString.getInstance(crlDp).getOctets()));
        assertEquals(2, points.getDistributionPoints().length);
        String base = props.baseUrlNoSlash();
        DistributionPointName first = points.getDistributionPoints()[0].getDistributionPoint();
        String firstUrl = GeneralNames.getInstance(first.getName()).getNames()[0].getName().toString();
        assertEquals(base + "/crl/tenzen-ca.crl", firstUrl);

        // AIA caIssuers -> p7b da cadeia
        byte[] aia = cert.getExtensionValue("1.3.6.1.5.5.7.1.1");
        assertNotNull(aia);
        AuthorityInformationAccess access = AuthorityInformationAccess.getInstance(
                ASN1Primitive.fromByteArray(ASN1OctetString.getInstance(aia).getOctets()));
        AccessDescription description = access.getAccessDescriptions()[0];
        assertEquals(AccessDescription.id_ad_caIssuers, description.getAccessMethod());
        assertEquals(base + "/aia/tenzen-ca.p7b", description.getAccessLocation().getName().toString());

        // policy no ramo esperado, com CPS apontando a DPC local
        byte[] policies = cert.getExtensionValue("2.5.29.32");
        assertNotNull(policies);
        CertificatePolicies certPolicies = CertificatePolicies.getInstance(
                ASN1Primitive.fromByteArray(ASN1OctetString.getInstance(policies).getOctets()));
        String policyId = certPolicies.getPolicyInformation()[0].getPolicyIdentifier().getId();
        assertEquals("2.16.76.1.2." + profile.policyBranch() + "."
                + PolicyOidResolver.FICTITIOUS_AC_NUMBER, policyId);

        // titular assinado com SHA-256; chave RSA do tamanho do perfil
        assertEquals("SHA256withRSA", cert.getSigAlgName().replace("WITHRSA", "withRSA"));
        assertEquals(profile.keyBits(),
                ((RSAPublicKey) cert.getPublicKey()).getModulus().bitLength());

        assertFalse(cert.getNotAfter().after(caMaterial.issuingCertificate().getNotAfter()),
                "não pode exceder a validade da cadeia");
    }

    @Test
    void ecpfA3LegadoSaiFielAoLeiaute() throws Exception {
        X509Certificate cert = issue(CertificateProfile.RFB_ECPF_A3, person());
        assertCommonProfile(cert, CertificateProfile.RFB_ECPF_A3);

        String subject = cert.getSubjectX500Principal().getName();
        assertTrue(subject.contains("CN=JOAO DA SILVA:01672780838"), subject);

        byte[] dados = otherNameValue(cert, "2.16.76.1.3.1");
        assertNotNull(dados);
        assertEquals(51, dados.length);
        String block = new String(dados);
        assertEquals("25031980", block.substring(0, 8));
        assertEquals("01672780838", block.substring(8, 19));

        assertEquals(41, otherNameValue(cert, "2.16.76.1.3.5").length);
        assertEquals(12, otherNameValue(cert, "2.16.76.1.3.6").length);
        assertNull(otherNameValue(cert, "2.16.76.1.3.9"), "RIC não faz parte do SAN obrigatório");
    }

    @Test
    void ecnpjA1LegadoSaiFielAoLeiaute() throws Exception {
        X509Certificate cert = issue(CertificateProfile.RFB_ECNPJ_A1, company());
        assertCommonProfile(cert, CertificateProfile.RFB_ECNPJ_A1);

        String subject = cert.getSubjectX500Principal().getName();
        assertTrue(subject.contains("CN=CASA LIQUIDACAO:99999999000191"), subject);
        assertTrue(subject.contains("L=SAO PAULO"), subject);
        assertTrue(subject.contains("ST=SP"), subject);

        assertEquals(14, otherNameValue(cert, "2.16.76.1.3.3").length);
        assertEquals(55, otherNameValue(cert, "2.16.76.1.3.4").length,
                "órgão/UF do responsável ocupa 10 posições no e-CNPJ");
        assertEquals(12, otherNameValue(cert, "2.16.76.1.3.7").length);
        assertNotNull(otherNameValue(cert, "2.16.76.1.3.2"));
        assertNull(otherNameValue(cert, "2.16.76.1.3.8"), ".3.8 não é SAN obrigatório");
    }

    @Test
    void novaGeracaoPfUsaSerialNumberEOrgaoUfEm10() throws Exception {
        X509Certificate cert = issue(CertificateProfile.NG_PF_A3, person());
        assertCommonProfile(cert, CertificateProfile.NG_PF_A3);

        String subject = cert.getSubjectX500Principal().getName();
        assertTrue(subject.contains("2.5.4.5=#") || subject.contains("SERIALNUMBER=")
                || subject.contains("serialNumber="), subject);
        assertEquals(55, otherNameValue(cert, "2.16.76.1.3.1").length,
                "nova geração usa órgão/UF em 10 posições: total 55");
    }

    @Test
    void seloEletronicoSeSEmiteComPolicy201() throws Exception {
        X509Certificate cert = issue(CertificateProfile.NG_PJ_SE_S, company());
        assertCommonProfile(cert, CertificateProfile.NG_PJ_SE_S);
    }

    @Test
    void a4UsaChaveRsa4096() throws Exception {
        X509Certificate cert = issue(CertificateProfile.NG_PF_A4, person());
        assertEquals(4096, ((RSAPublicKey) cert.getPublicKey()).getModulus().bitLength());
    }

    @Test
    void cadeiaSegueOPerfilDeAc() throws Exception {
        X509Certificate root = caMaterial.rootCertificate();
        X509Certificate issuing = caMaterial.issuingCertificate();

        assertEquals("SHA512withRSA", root.getSigAlgName().replace("WITHRSA", "withRSA"));
        assertEquals("SHA512withRSA", issuing.getSigAlgName().replace("WITHRSA", "withRSA"));
        assertEquals(4096, ((RSAPublicKey) root.getPublicKey()).getModulus().bitLength());
        assertEquals(4096, ((RSAPublicKey) issuing.getPublicKey()).getModulus().bitLength());

        // BC crítica CA:TRUE; pathLen=0 na Intermediária; KU crítica keyCertSign+cRLSign
        assertTrue(root.getCriticalExtensionOIDs().contains("2.5.29.19"));
        assertEquals(Integer.MAX_VALUE, root.getBasicConstraints());
        assertEquals(0, issuing.getBasicConstraints());
        assertTrue(issuing.getCriticalExtensionOIDs().contains("2.5.29.15"));
        assertTrue(root.getKeyUsage()[5], "keyCertSign");
        assertTrue(root.getKeyUsage()[6], "cRLSign");

        // SKI/AKI presentes nos certificados de AC
        assertNotNull(root.getExtensionValue("2.5.29.14"));
        assertNotNull(issuing.getExtensionValue("2.5.29.14"));
        assertNotNull(issuing.getExtensionValue("2.5.29.35"));

        issuing.verify(root.getPublicKey());
        root.verify(root.getPublicKey());
    }
}
