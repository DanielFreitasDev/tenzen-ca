package dev.tenzen.ca.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * DPC fictícia apontada pelo qualificador CPS dos certificados.
     */
    @GetMapping("/dpc")
    public String dpc() {
        return "dpc";
    }
}
