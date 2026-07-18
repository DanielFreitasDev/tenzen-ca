package dev.tenzen.ca.cert;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Serialização PEM (certificados, chaves e pacotes) via JcaPEMWriter.
 */
public final class PemExporter {

    private PemExporter() {
    }

    public static String toPem(Object... objects) {
        try (StringWriter out = new StringWriter(); JcaPEMWriter writer = new JcaPEMWriter(out)) {
            for (Object object : objects) {
                writer.writeObject(object);
            }
            writer.flush();
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar PEM", e);
        }
    }

    public static String certificatePem(X509Certificate certificate) {
        return toPem(certificate);
    }

    public static String chainPem(List<X509Certificate> chain) {
        return toPem(chain.toArray());
    }

    /**
     * Pacote completo: chave privada (PKCS#8 sem senha), certificado e cadeia.
     */
    public static String bundlePem(PrivateKey key, X509Certificate certificate,
                                   List<X509Certificate> caChain) {
        Object[] objects = new Object[2 + caChain.size()];
        objects[0] = key;
        objects[1] = certificate;
        for (int i = 0; i < caChain.size(); i++) {
            objects[2 + i] = caChain.get(i);
        }
        return toPem(objects);
    }
}
