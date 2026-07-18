package dev.tenzen.ca.web;

import dev.tenzen.ca.ca.ChainBundleService;
import dev.tenzen.ca.ca.CrlService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publicação das CRLs (nas 2 URLs distintas exigidas pelo leiaute) e do AIA (.p7b).
 * Cache curto: os clientes releem dentro da janela de revogação.
 */
@RestController
public class CrlController {

    private final CrlService crlService;
    private final ChainBundleService chainBundle;

    public CrlController(CrlService crlService, ChainBundleService chainBundle) {
        this.crlService = crlService;
        this.chainBundle = chainBundle;
    }

    @GetMapping({"/crl/tenzen-ca.crl", "/crl2/tenzen-ca.crl"})
    public ResponseEntity<byte[]> caCrl() {
        return crl(crlService.caCrl());
    }

    @GetMapping({"/crl/tenzen-root.crl", "/crl2/tenzen-root.crl"})
    public ResponseEntity<byte[]> rootCrl() {
        return crl(crlService.rootCrl());
    }

    @GetMapping("/aia/tenzen-ca.p7b")
    public ResponseEntity<byte[]> aiaChain() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/pkcs7-mime"))
                .body(chainBundle.caChainP7b());
    }

    private static ResponseEntity<byte[]> crl(byte[] bytes) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/pkix-crl"))
                .body(bytes);
    }
}
