package dev.tenzen.ca.web;

import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.cert.CsrSigner;
import dev.tenzen.ca.issuance.IssuanceService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Fluxo secundário: assinar um PKCS#10 do requerente sem custodiar a chave. */
@Controller
public class CsrController {

    private final IssuanceService issuanceService;

    public CsrController(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @ModelAttribute("profiles")
    public CertificateProfile[] profiles() {
        return CertificateProfile.values();
    }

    @GetMapping("/csr")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            IssueForm form = new IssueForm();
            // sem .p12 no fluxo CSR: os campos de senha não aparecem nem são validados
            form.setSenha("sem-p12");
            form.setSenhaConfirma("sem-p12");
            model.addAttribute("form", form);
        }
        return "csr";
    }

    @PostMapping("/csr")
    public String sign(@RequestParam(name = "csrPem", required = false) String csrPem,
            @RequestParam(name = "csrFile", required = false) MultipartFile csrFile,
            @Valid @ModelAttribute("form") IssueForm form, BindingResult binding,
            RedirectAttributes redirect, Model model) {
        form.setSenha("sem-p12");
        form.setSenhaConfirma("sem-p12");
        if (!binding.hasErrors()) {
            form.validate(binding);
        }

        String pem = csrPem;
        if ((pem == null || pem.isBlank()) && csrFile != null && !csrFile.isEmpty()) {
            try {
                pem = new String(csrFile.getBytes(), StandardCharsets.US_ASCII);
            } catch (Exception e) {
                binding.reject("csr.ilegivel", "Não foi possível ler o arquivo enviado");
            }
        }

        PublicKey publicKey = null;
        if (!binding.hasErrors()) {
            try {
                publicKey = CsrSigner.extractVerifiedPublicKey(pem);
            } catch (CsrSigner.InvalidCsrException e) {
                binding.reject("csr.invalido", e.getMessage());
            }
        }
        if (binding.hasErrors()) {
            model.addAttribute("csrPem", csrPem);
            return "csr";
        }

        IssuanceService.ValiditySpec validity = form.laboratory()
                ? IssuanceService.ValiditySpec.labDays(form.getValidadeDias())
                : IssuanceService.ValiditySpec.profileYears(form.getValidadeAnos());
        try {
            IssuanceService.IssuedResult result = issuanceService.issueForPublicKey(
                    form.profile(), form.toSubjectData(), validity, publicKey);
            redirect.addFlashAttribute("justIssued", true);
            return "redirect:/certificados/" + result.record().getId();
        } catch (IllegalArgumentException e) {
            binding.reject("emissao.invalida", e.getMessage());
            model.addAttribute("csrPem", csrPem);
            return "csr";
        }
    }
}
