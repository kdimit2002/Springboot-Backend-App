package com.example.webapp.BidNow.Controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {
        @Hidden
        @GetMapping("/bidnow/healthz")
        public String health() {
            return "ok";
        }


}
