package dev.tenzen.ca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Propriedades da aplicação.
 *
 * @param baseUrl  URL pública base; embutida nos certificados (CRL DP, AIA, CPS).
 *                 Precisa ser alcançável pelos clientes para a checagem de CRL funcionar.
 * @param dataDir  diretório de dados (keystores da AC + banco H2).
 * @param ca       parâmetros da cadeia simulada.
 * @param security acesso opcional por basic-auth quando exposta além de localhost.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        Path dataDir,
        @DefaultValue Ca ca,
        @DefaultValue Security security) {

    public String baseUrlNoSlash() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public enum PolicyMode {ICP_FORMAT, PRIVATE_2_25}

    public record Ca(
            @DefaultValue("tenzen-dev") String keystorePassword,
            /** icp-format = OID 2.16.76.1.2.* com leaf fictício; private-2.25 = OID derivado de UUID. */
            @DefaultValue("icp-format") PolicyMode policyMode,
            /** Algoritmo de assinatura dos certificados de titular (AC assina sempre com SHA-512). */
            @DefaultValue("SHA256withRSA") String subjectSignatureAlgorithm) {
    }

    public record Security(
            @DefaultValue BasicAuth basicAuth) {
    }

    public record BasicAuth(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("tenzen") String username,
            @DefaultValue("") String password) {
    }
}
