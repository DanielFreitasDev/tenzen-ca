package dev.tenzen.ca.issuance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tenzen.ca.IntegrationTestBase;
import dev.tenzen.ca.ca.CaMaterialManager;
import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.cert.SubjectData;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IssuanceServiceTest extends IntegrationTestBase {

    @Autowired
    private IssuanceService issuanceService;

    @Autowired
    private CaMaterialManager caMaterial;

    private IssuanceService.IssuedResult issue(String name, String password, String alias) {
        return issuanceService.issueWithGeneratedKey(
                CertificateProfile.RFB_ECPF_A1,
                SubjectData.builder().name(name).cpf("12345678909").build(),
                IssuanceService.ValiditySpec.profileYears(1),
                password.toCharArray(), alias);
    }

    @Test
    void p12SaiComCadeiaLeafFirstAliasESenha() throws Exception {
        IssuanceService.IssuedResult result = issue("Dona Do P12", "segredo-p12", "minha-chave");

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(new ByteArrayInputStream(result.record().getP12Bytes()),
                "segredo-p12".toCharArray());

        assertTrue(store.isKeyEntry("minha-chave"), "alias escolhido deve existir");
        Certificate[] chain = store.getCertificateChain("minha-chave");
        assertEquals(3, chain.length, "titular + Intermediária + Raiz");
        assertEquals(result.certificate(), chain[0], "leaf primeiro");
        assertEquals(caMaterial.issuingCertificate(), chain[1]);
        assertEquals(caMaterial.rootCertificate(), chain[2]);

        PrivateKey key = (PrivateKey) store.getKey("minha-chave", "segredo-p12".toCharArray());
        assertEquals(((RSAPublicKey) result.certificate().getPublicKey()).getModulus(),
                ((RSAPrivateKey) key).getModulus(),
                "a chave privada do .p12 deve corresponder à pública do certificado");

        String pem = result.record().getPemBundle();
        assertTrue(pem.contains("PRIVATE KEY"), "bundle PEM leva a chave");
        assertEquals(3, pem.split("BEGIN CERTIFICATE", -1).length - 1,
                "bundle PEM leva certificado + cadeia");
    }

    @Test
    void seriaisSaoUnicosSobConcorrencia() throws Exception {
        int total = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                String name = "Concorrente " + i;
                futures.add(pool.submit(() ->
                        issue(name, "senha123", null).record().getSerialHex()));
            }
            Set<String> serials = new HashSet<>();
            for (Future<String> future : futures) {
                serials.add(future.get(120, TimeUnit.SECONDS));
            }
            assertEquals(total, serials.size(), "nenhum serial pode se repetir");
        } finally {
            pool.shutdownNow();
        }
    }
}
