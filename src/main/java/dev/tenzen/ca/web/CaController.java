package dev.tenzen.ca.web;

import dev.tenzen.ca.ca.CaMaterialManager;
import dev.tenzen.ca.ca.ChainBundleService;
import dev.tenzen.ca.cert.PemExporter;
import java.security.cert.X509Certificate;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Página da cadeia de confiança e downloads da Raiz/Intermediária. */
@Controller
public class CaController {

    private final CaMaterialManager caMaterial;
    private final ChainBundleService chainBundle;

    public CaController(CaMaterialManager caMaterial, ChainBundleService chainBundle) {
        this.caMaterial = caMaterial;
        this.chainBundle = chainBundle;
    }

    @GetMapping("/cadeia")
    public String page(Model model) {
        X509Certificate root = caMaterial.rootCertificate();
        X509Certificate issuing = caMaterial.issuingCertificate();
        model.addAttribute("rootSubject", root.getSubjectX500Principal().getName());
        model.addAttribute("issuingSubject", issuing.getSubjectX500Principal().getName());
        model.addAttribute("rootNotAfter", root.getNotAfter());
        model.addAttribute("issuingNotAfter", issuing.getNotAfter());
        model.addAttribute("fingerprint", caMaterial.rootFingerprintSha256());
        return "ca";
    }

    @GetMapping("/ca/tenzen-root.crt")
    public ResponseEntity<byte[]> rootDer() throws Exception {
        return download(caMaterial.rootCertificate().getEncoded(),
                "tenzen-root.crt", "application/pkix-cert");
    }

    @GetMapping("/ca/tenzen-root.pem")
    public ResponseEntity<byte[]> rootPem() {
        return download(PemExporter.certificatePem(caMaterial.rootCertificate()).getBytes(),
                "tenzen-root.pem", "application/x-pem-file");
    }

    @GetMapping("/ca/tenzen-issuing.pem")
    public ResponseEntity<byte[]> issuingPem() {
        return download(PemExporter.certificatePem(caMaterial.issuingCertificate()).getBytes(),
                "tenzen-issuing.pem", "application/x-pem-file");
    }

    @GetMapping("/ca/tenzen-chain.pem")
    public ResponseEntity<byte[]> chainPem() {
        return download(PemExporter.chainPem(caMaterial.chain()).getBytes(),
                "tenzen-chain.pem", "application/x-pem-file");
    }

    @GetMapping("/ca/tenzen-chain.p7b")
    public ResponseEntity<byte[]> chainP7b() {
        return download(chainBundle.caChainP7b(), "tenzen-chain.p7b", "application/pkcs7-mime");
    }

    static ResponseEntity<byte[]> download(byte[] body, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }
}
