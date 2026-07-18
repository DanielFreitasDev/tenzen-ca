package dev.tenzen.ca.ca;

import java.security.cert.X509Certificate;
import java.util.List;
import org.bouncycastle.cms.CMSAbsentContent;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.springframework.stereotype.Service;

/** Empacota a cadeia da AC num PKCS#7 degenerado (.p7b), o formato apontado pelo AIA. */
@Service
public class ChainBundleService {

    private final CaMaterialManager caMaterial;

    public ChainBundleService(CaMaterialManager caMaterial) {
        this.caMaterial = caMaterial;
    }

    /** Cadeia da AC (Intermediária + Raiz). */
    public byte[] caChainP7b() {
        return toP7b(caMaterial.chain());
    }

    /** Certificado do titular + cadeia, leaf-first. */
    public byte[] fullChainP7b(X509Certificate leaf) {
        return toP7b(List.of(leaf, caMaterial.issuingCertificate(), caMaterial.rootCertificate()));
    }

    private static byte[] toP7b(List<X509Certificate> certs) {
        try {
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addCertificates(new JcaCertStore(certs));
            CMSSignedData signedData = generator.generate(new CMSAbsentContent());
            return signedData.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o pacote .p7b da cadeia", e);
        }
    }
}
