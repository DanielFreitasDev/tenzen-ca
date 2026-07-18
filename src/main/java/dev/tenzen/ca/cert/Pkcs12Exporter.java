package dev.tenzen.ca.cert;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Monta o .p12 do titular com a cadeia completa na ordem leaf-first.
 */
public final class Pkcs12Exporter {

    public static final String DEFAULT_ALIAS = "certificado";

    private Pkcs12Exporter() {
    }

    public static byte[] export(PrivateKey key, X509Certificate certificate,
                                List<X509Certificate> caChain, String alias, char[] password) {
        try {
            Certificate[] chain = new Certificate[1 + caChain.size()];
            chain[0] = certificate;
            for (int i = 0; i < caChain.size(); i++) {
                chain[1 + i] = caChain.get(i);
            }
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            store.setKeyEntry(normalizeAlias(alias), key, password, chain);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            store.store(out, password);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o PKCS#12", e);
        }
    }

    public static String normalizeAlias(String alias) {
        return alias == null || alias.isBlank() ? DEFAULT_ALIAS : alias.trim();
    }
}
