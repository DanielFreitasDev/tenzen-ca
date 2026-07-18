package dev.tenzen.ca.web;

import dev.tenzen.ca.issuance.IssuanceService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RevocationController {

    private final IssuanceService issuanceService;

    public RevocationController(IssuanceService issuanceService) {
        this.issuanceService = issuanceService;
    }

    @PostMapping("/certificados/{id}/revogar")
    public String revoke(@PathVariable Long id,
                         @RequestParam(name = "motivo", defaultValue = "unspecified") String reason,
                         RedirectAttributes redirect) {
        issuanceService.revoke(id, reason);
        redirect.addFlashAttribute("revokedNow", true);
        return "redirect:/certificados/" + id;
    }
}
