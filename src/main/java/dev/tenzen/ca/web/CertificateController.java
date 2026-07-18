package dev.tenzen.ca.web;

import dev.tenzen.ca.ca.ChainBundleService;
import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.issuance.IssuanceRepository;
import dev.tenzen.ca.issuance.IssuedCertificate;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** Página de resultado/detalhe de um certificado emitido + downloads (sempre no-store). */
@Controller
@Transactional(readOnly = true)
public class CertificateController {

    private final IssuanceRepository repository;
    private final ChainBundleService chainBundle;

    public CertificateController(IssuanceRepository repository, ChainBundleService chainBundle) {
        this.repository = repository;
        this.chainBundle = chainBundle;
    }

    @GetMapping("/certificados/{id}")
    public String detail(@PathVariable Long id, Model model) {
        IssuedCertificate cert = find(id);
        model.addAttribute("cert", cert);
        model.addAttribute("profile", CertificateProfile.fromId(cert.getProfileId()));
        return "result";
    }

    @GetMapping("/certificados/{id}/certificado.p12")
    public ResponseEntity<byte[]> p12(@PathVariable Long id) {
        IssuedCertificate cert = find(id);
        if (!cert.hasPrivateKey()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Emitido via CSR: a AC não custodia a chave, não há .p12");
        }
        return CaController.download(cert.getP12Bytes(),
                filename(cert, "p12"), "application/x-pkcs12");
    }

    @GetMapping("/certificados/{id}/bundle.pem")
    public ResponseEntity<byte[]> bundlePem(@PathVariable Long id) {
        IssuedCertificate cert = find(id);
        if (cert.getPemBundle() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return CaController.download(cert.getPemBundle().getBytes(StandardCharsets.US_ASCII),
                filename(cert, "bundle.pem"), "application/x-pem-file");
    }

    @GetMapping("/certificados/{id}/certificado.pem")
    public ResponseEntity<byte[]> certPem(@PathVariable Long id) {
        IssuedCertificate cert = find(id);
        return CaController.download(cert.getCertPem().getBytes(StandardCharsets.US_ASCII),
                filename(cert, "pem"), "application/x-pem-file");
    }

    @GetMapping("/certificados/{id}/cadeia-completa.p7b")
    public ResponseEntity<byte[]> fullChain(@PathVariable Long id) {
        IssuedCertificate cert = find(id);
        return CaController.download(chainBundle.fullChainP7b(parse(cert.getCertPem())),
                filename(cert, "p7b"), "application/pkcs7-mime");
    }

    private IssuedCertificate find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Certificado não encontrado"));
    }

    private static String filename(IssuedCertificate cert, String extension) {
        String slug = cert.getSubjectCn().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "certificado";
        }
        return slug + "-" + cert.getSerialHex().substring(0, 8) + "." + extension;
    }

    static X509Certificate parse(String pem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("Certificado armazenado ilegível", e);
        }
    }
}
