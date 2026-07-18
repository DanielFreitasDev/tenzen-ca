package dev.tenzen.ca.web;

import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.issuance.IssuanceService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class IssueController {

    private final IssuanceService issuanceService;

    public IssueController(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @ModelAttribute("profiles")
    public CertificateProfile[] profiles() {
        return CertificateProfile.values();
    }

    @GetMapping("/emitir")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new IssueForm());
        }
        return "issue";
    }

    @PostMapping("/emitir")
    public String issue(@Valid @ModelAttribute("form") IssueForm form, BindingResult binding,
            RedirectAttributes redirect, Model model) {
        if (!binding.hasErrors()) {
            form.validate(binding, true);
        }
        if (binding.hasErrors()) {
            return "issue";
        }
        IssuanceService.ValiditySpec validity = form.laboratory()
                ? IssuanceService.ValiditySpec.labDays(form.getValidadeDias())
                : IssuanceService.ValiditySpec.profileYears(form.getValidadeAnos());
        try {
            IssuanceService.IssuedResult result = issuanceService.issueWithGeneratedKey(
                    form.profile(), form.toSubjectData(), validity,
                    form.getSenha().toCharArray(), form.getAlias());
            redirect.addFlashAttribute("justIssued", true);
            return "redirect:/certificados/" + result.record().getId();
        } catch (IllegalArgumentException e) {
            binding.reject("emissao.invalida", e.getMessage());
            return "issue";
        }
    }
}
