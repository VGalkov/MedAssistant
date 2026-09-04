package ru.medassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/patient/start";
    }

    @GetMapping("/doctor/login")
    public String doctorLogin() {
        return "doctor/login";
    }
}
