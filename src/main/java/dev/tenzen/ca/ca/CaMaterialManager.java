package dev.tenzen.ca.ca;

import dev.tenzen.ca.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

/**
 * Material criptográfico da AC simulada, em dois keystores separados no data-dir:
 * {@code root.p12} (Raiz, usada só para assinar a Intermediária e a própria CRL,
 * "offline" por convenção) e {@code issuing.p12} (Intermediária, operacional).
 * Na primeira execução gera tudo; depois apenas carrega, com fail-safe de fingerprint.
 */
@Component
public class CaMaterialManager implements InitializingBean {

    public static final String ROOT_KEYSTORE = "root.p12";
    public static final String ISSUING_KEYSTORE = "issuing.p12";
    private static final String ROOT_ALIAS = "tenzen-root";
    private static final String ISSUING_ALIAS = "tenzen-issuing";

    private static final Logger log = LoggerFactory.getLogger(CaMaterialManager.class);

    private final AppProperties props;
    private final CaAnchorRepository anchorRepository;

    private X509Certificate rootCert;
    private PrivateKey rootKey;
    private X509Certificate issuingCert;
    private PrivateKey issuingKey;
    private String rootFingerprint;

    public CaMaterialManager(AppProperties props, CaAnchorRepository anchorRepository) {
        this.props = props;
        this.anchorRepository = anchorRepository;
    }

    private static KeyStore loadKeystore(Path path, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            store.load(in, password);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Não foi possível abrir " + path + ". Senha incorreta (app.ca.keystore-password"
                            + " / APP_CA_KEYSTORE_PASSWORD) ou arquivo corrompido.", e);
        }
        return store;
    }

    private static void store(KeyStore store, Path path, char[] password) throws Exception {
        try (OutputStream out = Files.newOutputStream(path)) {
            store.store(out, password);
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Path dataDir = props.dataDir();
        Files.createDirectories(dataDir);
        Path rootPath = dataDir.resolve(ROOT_KEYSTORE);
        Path issuingPath = dataDir.resolve(ISSUING_KEYSTORE);
        boolean keystoresPresent = Files.exists(rootPath) && Files.exists(issuingPath);
        CaAnchor anchor = anchorRepository.findById(CaAnchor.SINGLETON_ID).orElse(null);

        if (!keystoresPresent && anchor != null) {
            throw new IllegalStateException("""
                    O banco de dados registra uma AC (fingerprint %s), mas os keystores \
                    %s/%s não foram encontrados em %s. Recusando gerar outra AC em silêncio. \
                    Restaure os keystores ou, para recomeçar do zero, apague o diretório %s inteiro."""
                    .formatted(anchor.getRootFingerprintSha256(), ROOT_KEYSTORE,
                            ISSUING_KEYSTORE, dataDir, dataDir));
        }

        char[] password = props.ca().keystorePassword().toCharArray();
        if (keystoresPresent) {
            load(rootPath, issuingPath, password);
        } else {
            generate(rootPath, issuingPath, password);
        }

        rootFingerprint = sha256Hex(rootCert.getEncoded());
        if (anchor == null) {
            anchorRepository.save(new CaAnchor(rootFingerprint));
        } else if (!anchor.getRootFingerprintSha256().equalsIgnoreCase(rootFingerprint)) {
            throw new IllegalStateException("""
                    O keystore %s em %s contém uma Root com fingerprint %s, mas o banco \
                    registra %s. O histórico pertence a outra cadeia. Restaure o keystore \
                    original ou apague o diretório %s inteiro para recomeçar."""
                    .formatted(ROOT_KEYSTORE, props.dataDir(), rootFingerprint,
                            anchor.getRootFingerprintSha256(), props.dataDir()));
        }
        log.info("Cadeia Tenzen CA pronta. Fingerprint da Root (SHA-256): {}", rootFingerprint);
    }

    private void load(Path rootPath, Path issuingPath, char[] password) throws Exception {
        KeyStore rootStore = loadKeystore(rootPath, password);
        rootCert = (X509Certificate) rootStore.getCertificate(ROOT_ALIAS);
        rootKey = (PrivateKey) rootStore.getKey(ROOT_ALIAS, password);
        KeyStore issuingStore = loadKeystore(issuingPath, password);
        issuingCert = (X509Certificate) issuingStore.getCertificate(ISSUING_ALIAS);
        issuingKey = (PrivateKey) issuingStore.getKey(ISSUING_ALIAS, password);
        if (rootCert == null || rootKey == null || issuingCert == null || issuingKey == null) {
            throw new IllegalStateException(
                    "Keystores da AC em " + props.dataDir() + " não contêm os aliases esperados ("
                            + ROOT_ALIAS + ", " + ISSUING_ALIAS + "). Arquivo corrompido ou senha trocada?");
        }
    }

    private void generate(Path rootPath, Path issuingPath, char[] password) throws Exception {
        log.info("Primeira execução: gerando Root e Intermediária RSA-4096 (SHA-512)...");
        KeyPair rootPair = CaCertificateFactory.newCaKeyPair();
        rootCert = CaCertificateFactory.newRootCertificate(rootPair);
        rootKey = rootPair.getPrivate();

        KeyPair issuingPair = CaCertificateFactory.newCaKeyPair();
        issuingCert = CaCertificateFactory.newIssuingCertificate(
                issuingPair, rootCert, rootKey, props.baseUrlNoSlash());
        issuingKey = issuingPair.getPrivate();

        KeyStore rootStore = KeyStore.getInstance("PKCS12");
        rootStore.load(null, null);
        rootStore.setKeyEntry(ROOT_ALIAS, rootKey, password, new Certificate[]{rootCert});
        store(rootStore, rootPath, password);

        KeyStore issuingStore = KeyStore.getInstance("PKCS12");
        issuingStore.load(null, null);
        issuingStore.setKeyEntry(ISSUING_ALIAS, issuingKey, password,
                new Certificate[]{issuingCert, rootCert});
        store(issuingStore, issuingPath, password);
        log.info("Keystores gravados em {} e {}", rootPath, issuingPath);
    }

    public X509Certificate rootCertificate() {
        return rootCert;
    }

    public PrivateKey rootKey() {
        return rootKey;
    }

    public X509Certificate issuingCertificate() {
        return issuingCert;
    }

    public PrivateKey issuingKey() {
        return issuingKey;
    }

    /**
     * Cadeia na ordem leaf-first esperada em PKCS#12 e P7B: Intermediária, Raiz.
     */
    public List<X509Certificate> chain() {
        return List.of(issuingCert, rootCert);
    }

    public String rootFingerprintSha256() {
        return rootFingerprint;
    }
}
