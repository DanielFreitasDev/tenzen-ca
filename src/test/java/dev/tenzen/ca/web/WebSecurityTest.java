package dev.tenzen.ca.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.tenzen.ca.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class WebSecurityTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postSemCsrfERecusado() throws Exception {
        mockMvc.perform(post("/emitir").param("profileId", "rfb-ecpf-a1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postComCsrfPassaPelaProtecao() throws Exception {
        // sem os demais campos o form volta com erros de validação (200), não 403
        mockMvc.perform(post("/emitir").with(csrf()).param("profileId", "rfb-ecpf-a1"))
                .andExpect(status().isOk());
    }

    @Test
    void paginasSaemComCspNonceEHeadersDeSeguranca() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self' 'nonce-")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Referrer-Policy"));
    }

    @Test
    void crlServidaNasDuasUrlsComContentTypeCerto() throws Exception {
        mockMvc.perform(get("/crl/tenzen-ca.crl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pkix-crl"));
        mockMvc.perform(get("/crl2/tenzen-ca.crl"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pkix-crl"));
        mockMvc.perform(get("/crl/tenzen-root.crl")).andExpect(status().isOk());
        mockMvc.perform(get("/crl2/tenzen-root.crl")).andExpect(status().isOk());
    }

    @Test
    void aiaEDownloadsSensiveisSaemComNoStore() throws Exception {
        mockMvc.perform(get("/aia/tenzen-ca.p7b"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pkcs7-mime"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/ca/tenzen-root.pem"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void paginasPrincipaisRespondem() throws Exception {
        for (String path : new String[] {"/", "/emitir", "/csr", "/historico", "/cadeia", "/dpc"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }
}
