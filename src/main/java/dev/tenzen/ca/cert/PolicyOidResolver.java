package dev.tenzen.ca.cert;

import dev.tenzen.ca.ca.CaMaterialManager;
import dev.tenzen.ca.config.AppProperties;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Resolve o policy OID do certificado conforme o modo configurado.
 *
 * <p>{@code icp-format} (default): {@code 2.16.76.1.2.<ramo>.<numero-da-AC>} com número de AC
 * fictício ({@value #FICTITIOUS_AC_NUMBER}, não atribuído pelo ITI) — necessário para
 * aplicações que fazem prefix-match em {@code 2.16.76.1.2.*}. {@code private-2.25}: arco
 * privado {@code 2.25.<UUID>} derivado do fingerprint da Root, para quem preferir não usar
 * o arco da ICP-Brasil.</p>
 */
@Component
public class PolicyOidResolver {

    public static final String FICTITIOUS_AC_NUMBER = "999";

    private final AppProperties props;
    private final CaMaterialManager caMaterial;

    public PolicyOidResolver(AppProperties props, CaMaterialManager caMaterial) {
        this.props = props;
        this.caMaterial = caMaterial;
    }

    public ASN1ObjectIdentifier resolve(CertificateProfile profile) {
        return switch (props.ca().policyMode()) {
            case ICP_FORMAT -> new ASN1ObjectIdentifier(IcpBrasilOids.POLICY_ARC
                    + "." + profile.policyBranch() + "." + FICTITIOUS_AC_NUMBER);
            case PRIVATE_2_25 -> new ASN1ObjectIdentifier(
                    "2.25." + rootUuidDecimal() + "." + profile.policyBranch());
        };
    }

    /**
     * 2.25.&lt;inteiro de 128 bits derivado (SHA-256) da Root&gt;: estável por instalação.
     */
    private String rootUuidDecimal() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(caMaterial.rootCertificate().getEncoded());
            byte[] uuid = Arrays.copyOf(digest, 16);
            return new BigInteger(1, uuid).toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao derivar OID privado da Root", e);
        }
    }
}
