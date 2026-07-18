package dev.tenzen.ca.cert;

import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

/**
 * Valida um PKCS#10 e extrai a chave pública. O DN e o SAN do certificado saem
 * dos dados do formulário (como numa AC real, que não copia o subject do CSR);
 * a chave privada nunca passa por aqui.
 */
public final class CsrSigner {

    private CsrSigner() {
    }

    public static class InvalidCsrException extends RuntimeException {
        public InvalidCsrException(String message) {
            super(message);
        }

        public InvalidCsrException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Faz o parse do PEM, confere a autoassinatura do CSR e devolve a chave pública. */
    public static PublicKey extractVerifiedPublicKey(String pem) {
        PKCS10CertificationRequest csr = parse(pem);
        try {
            boolean valid = csr.isSignatureValid(new JcaContentVerifierProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(csr.getSubjectPublicKeyInfo()));
            if (!valid) {
                throw new InvalidCsrException(
                        "A assinatura do CSR não confere com a chave pública dele");
            }
            return new JcaPKCS10CertificationRequest(csr)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getPublicKey();
        } catch (InvalidCsrException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCsrException("Não foi possível verificar o CSR", e);
        }
    }

    private static PKCS10CertificationRequest parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new InvalidCsrException("Cole ou envie um CSR em PEM");
        }
        try (PEMParser parser = new PEMParser(new StringReader(pem.trim()))) {
            Object object = parser.readObject();
            if (object instanceof PKCS10CertificationRequest csr) {
                return csr;
            }
            throw new InvalidCsrException(
                    "O conteúdo não é um CSR PKCS#10 (esperado bloco CERTIFICATE REQUEST)");
        } catch (IOException e) {
            throw new InvalidCsrException("PEM ilegível: " + e.getMessage(), e);
        }
    }
}
