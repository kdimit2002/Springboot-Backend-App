package com.example.webapp.BidNow.Controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {
        @GetMapping("/bidnow/healthz")
        public String health() {
            return "ok";
        }


}
