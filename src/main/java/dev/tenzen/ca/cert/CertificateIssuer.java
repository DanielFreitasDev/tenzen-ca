package dev.tenzen.ca.cert;

import dev.tenzen.ca.ca.CaCertificateFactory;
import dev.tenzen.ca.ca.CaMaterialManager;
import dev.tenzen.ca.config.AppProperties;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.PolicyQualifierInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

/**
 * Emite o certificado do titular assinado pela Intermediária.
 *
 * <p>Extensões conforme o leiaute: basicConstraints não crítica CA:FALSE; keyUsage
 * crítica digitalSignature+nonRepudiation+keyEncipherment; EKU clientAuth+emailProtection
 * (+ MS Smartcard Logon no e-CPF legado); AKI sem SKI (o SKI do titular saiu do leiaute);
 * dois CRL DPs; AIA caIssuers apontando o .p7b da cadeia; certificatePolicies com CPS.</p>
 */
@Component
public class CertificateIssuer {

    private final CaMaterialManager caMaterial;
    private final AppProperties props;
    private final PolicyOidResolver policyResolver;

    public CertificateIssuer(CaMaterialManager caMaterial, AppProperties props,
            PolicyOidResolver policyResolver) {
        this.caMaterial = caMaterial;
        this.props = props;
        this.policyResolver = policyResolver;
    }

    public record ValidityPeriod(Date notBefore, Date notAfter) {
    }

    public X509Certificate issue(CertificateProfile profile, SubjectData data,
            PublicKey subjectKey, ValidityPeriod validity, BigInteger serial) {
        try {
            X509Certificate issuingCert = caMaterial.issuingCertificate();
            // nenhum certificado excede a validade da cadeia
            Date notAfter = validity.notAfter().after(issuingCert.getNotAfter())
                    ? issuingCert.getNotAfter()
                    : validity.notAfter();

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuingCert,
                    serial,
                    validity.notBefore(),
                    notAfter,
                    DnBuilder.build(profile, data),
                    subjectKey);

            String base = props.baseUrlNoSlash();
            JcaX509ExtensionUtils utils = new JcaX509ExtensionUtils();

            builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(
                    KeyUsage.digitalSignature | KeyUsage.nonRepudiation | KeyUsage.keyEncipherment));
            builder.addExtension(Extension.extendedKeyUsage, false, extendedKeyUsage(profile));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    utils.createAuthorityKeyIdentifier(issuingCert.getPublicKey()));
            builder.addExtension(Extension.cRLDistributionPoints, false,
                    CaCertificateFactory.crlDistPoint(
                            base + "/crl/tenzen-ca.crl", base + "/crl2/tenzen-ca.crl"));
            builder.addExtension(Extension.authorityInfoAccess, false,
                    new AuthorityInformationAccess(new AccessDescription(
                            AccessDescription.id_ad_caIssuers,
                            new GeneralName(GeneralName.uniformResourceIdentifier,
                                    base + "/aia/tenzen-ca.p7b"))));
            builder.addExtension(Extension.certificatePolicies, false,
                    new CertificatePolicies(new PolicyInformation(
                            policyResolver.resolve(profile),
                            new DERSequence(new PolicyQualifierInfo(base + "/dpc")))));
            builder.addExtension(Extension.subjectAlternativeName, false,
                    SubjectAltNameBuilder.build(profile, data));

            ContentSigner signer = new JcaContentSignerBuilder(
                    props.ca().subjectSignatureAlgorithm())
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(caMaterial.issuingKey());
            X509CertificateHolder holder = builder.build(signer);
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(holder);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao emitir o certificado", e);
        }
    }

    private static ExtendedKeyUsage extendedKeyUsage(CertificateProfile profile) {
        List<KeyPurposeId> purposes = new ArrayList<>();
        purposes.add(KeyPurposeId.id_kp_clientAuth);
        purposes.add(KeyPurposeId.id_kp_emailProtection);
        if (profile.includeSmartcardLogon()) {
            purposes.add(KeyPurposeId.getInstance(IcpBrasilOids.MS_SMARTCARD_LOGON));
        }
        return new ExtendedKeyUsage(purposes.toArray(new KeyPurposeId[0]));
    }
}
