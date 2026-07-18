package dev.tenzen.ca.web;

import dev.tenzen.ca.cert.CertificateProfile;
import dev.tenzen.ca.issuance.IssuanceRepository;
import dev.tenzen.ca.issuance.IssuedCertificate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class HistoryController {

    private final IssuanceRepository repository;

    public HistoryController(IssuanceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/historico")
    public String history(@RequestParam(name = "q", required = false) String query, Model model) {
        List<IssuedCertificate> certificates;
        if (query == null || query.isBlank()) {
            certificates = repository.findAllByOrderByIssuedAtDesc();
        } else {
            String term = query.trim();
            certificates = repository.search(term, term.replaceAll("\\D", ""));
        }
        Map<String, String> profileLabels = java.util.Arrays.stream(CertificateProfile.values())
                .collect(Collectors.toMap(CertificateProfile::id, CertificateProfile::label));
        model.addAttribute("certificates", certificates);
        model.addAttribute("profileLabels", profileLabels);
        model.addAttribute("query", query == null ? "" : query);
        return "history";
    }
}
