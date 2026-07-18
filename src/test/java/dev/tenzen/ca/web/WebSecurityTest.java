package dev.tenzen.ca.web;

import dev.tenzen.ca.IntegrationTestBase;
import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.cert.PemExporter;
import dev.tenzen.ca.cert.SubjectData;
import dev.tenzen.ca.issuance.IssuanceService;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class WebSecurityTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IssuanceService issuanceService;

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
        for (String path : new String[]{"/", "/emitir", "/csr", "/historico", "/cadeia", "/dpc"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void csrEmiteSemCamposDeSenha() throws Exception {
        // o formulário /csr não tem senha (não há .p12 no fluxo); o POST precisa emitir
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String csrPem = PemExporter.toPem(new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=qualquer"), keyPair.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA")
                        .build(keyPair.getPrivate())));

        mockMvc.perform(post("/csr").with(csrf())
                        .param("profileId", "rfb-ecpf-a1")
                        .param("csrPem", csrPem)
                        .param("nome", "Titular Sem Senha")
                        .param("cpf", "12345678909")
                        .param("validadeModo", "perfil")
                        .param("validadeAnos", "1")
                        .param("validationType", "videoconferencia"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/certificados/*"));
    }

    @Test
    void emitirSemSenhaVoltaComErroNoCampo() throws Exception {
        mockMvc.perform(post("/emitir").with(csrf())
                        .param("profileId", "rfb-ecpf-a1")
                        .param("nome", "Sem Senha")
                        .param("cpf", "12345678909")
                        .param("validadeModo", "perfil")
                        .param("validadeAnos", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "senha"));
    }

    @Test
    void buscaDoHistoricoFiltraDeVerdade() throws Exception {
        issuanceService.issueWithGeneratedKey(
                CertificateProfile.RFB_ECPF_A1,
                SubjectData.builder().name("Zybrafiltro Unico").cpf("12345678909").build(),
                IssuanceService.ValiditySpec.profileYears(1),
                "senha123".toCharArray(), null);

        mockMvc.perform(get("/historico").param("q", "zybrafiltro"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("certificates", hasSize(1)));
        mockMvc.perform(get("/historico").param("q", "nome-sem-nenhum-match"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("certificates", hasSize(0)));
    }
}
