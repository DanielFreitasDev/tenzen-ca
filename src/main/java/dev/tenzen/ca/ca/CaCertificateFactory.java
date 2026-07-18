package dev.tenzen.ca.ca;

import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * Gera os certificados da cadeia simulada seguindo o perfil de AC do leiaute:
 * Root e Intermediária RSA-4096 assinadas com SHA-512 (DOC-ICP-01.01), basicConstraints
 * crítica CA:TRUE (pathLen=0 na Intermediária), keyUsage crítica keyCertSign+cRLSign,
 * SKI/AKI, e na Intermediária os 2 CRL DPs e certificatePolicies.
 */
public final class CaCertificateFactory {

    /**
     * Algoritmo de assinatura de certificados de AC (DOC-ICP-01.01).
     */
    public static final String CA_SIGNATURE_ALGORITHM = "SHA512withRSA";
    public static final int CA_KEY_BITS = 4096;
    public static final int ROOT_VALIDITY_YEARS = 20;
    public static final int ISSUING_VALIDITY_YEARS = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private CaCertificateFactory() {
    }

    public static KeyPair newCaKeyPair() throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(CA_KEY_BITS, RANDOM);
        return generator.generateKeyPair();
    }

    public static X500Name rootDn() {
        return caDn("AC Raiz de Testes Tenzen v1");
    }

    public static X500Name issuingDn() {
        return caDn("AC Tenzen de Testes v1");
    }

    private static X500Name caDn(String cn) {
        return new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "BR")
                .addRDN(BCStyle.O, "ICP-Brasil")
                .addRDN(BCStyle.OU, "Tenzen CA de Testes")
                .addRDN(BCStyle.CN, cn)
                .build();
    }

    public static X509Certificate newRootCertificate(KeyPair keyPair) throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                rootDn(),
                randomSerial(),
                Date.from(now.minusHours(1).toInstant()),
                Date.from(now.plusYears(ROOT_VALIDITY_YEARS).toInstant()),
                rootDn(),
                keyPair.getPublic());

        JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                utils.createSubjectKeyIdentifier(keyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                utils.createAuthorityKeyIdentifier(keyPair.getPublic()));

        return sign(builder, keyPair.getPrivate());
    }

    public static X509Certificate newIssuingCertificate(KeyPair issuingKeyPair,
                                                        X509Certificate rootCert, PrivateKey rootKey, String baseUrl) throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                rootCert,
                randomSerial(),
                Date.from(now.minusHours(1).toInstant()),
                Date.from(now.plusYears(ISSUING_VALIDITY_YEARS).toInstant()),
                issuingDn(),
                issuingKeyPair.getPublic());

        JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                utils.createSubjectKeyIdentifier(issuingKeyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                utils.createAuthorityKeyIdentifier(rootCert.getPublicKey()));
        builder.addExtension(Extension.cRLDistributionPoints, false, crlDistPoint(
                baseUrl + "/crl/tenzen-root.crl", baseUrl + "/crl2/tenzen-root.crl"));
        builder.addExtension(Extension.certificatePolicies, false, new CertificatePolicies(
                cpsPolicy(new org.bouncycastle.asn1.ASN1ObjectIdentifier("2.5.29.32.0"),
                        baseUrl + "/dpc")));

        return sign(builder, rootKey);
    }

    /**
     * CRL DP com dois endereços web distintos, como exige o leiaute.
     */
    public static CRLDistPoint crlDistPoint(String url1, String url2) {
        DistributionPoint[] points = new DistributionPoint[]{
                uriDistPoint(url1),
                uriDistPoint(url2),
        };
        return new CRLDistPoint(points);
    }

    private static DistributionPoint uriDistPoint(String url) {
        GeneralName name = new GeneralName(GeneralName.uniformResourceIdentifier,
                new DERIA5String(url));
        return new DistributionPoint(
                new DistributionPointName(new GeneralNames(name)), null, null);
    }

    public static PolicyInformation cpsPolicy(org.bouncycastle.asn1.ASN1ObjectIdentifier policyOid,
                                              String cpsUrl) {
        PolicyQualifierInfo qualifier = new PolicyQualifierInfo(cpsUrl);
        return new PolicyInformation(policyOid,
                new org.bouncycastle.asn1.DERSequence(qualifier));
    }

    public static BigInteger randomSerial() {
        return new BigInteger(127, RANDOM).setBit(126);
    }

    private static X509Certificate sign(JcaX509v3CertificateBuilder builder, PrivateKey key)
            throws Exception {
        ContentSigner signer = new JcaContentSignerBuilder(CA_SIGNATURE_ALGORITHM)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(key);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }
}
