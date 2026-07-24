package com.crowdmanagement.controller;

import com.crowdmanagement.dto.ApiDtos.AlertSubscriptionRequest;
import com.crowdmanagement.dto.ApiDtos.AlertSubscriptionResponse;
import com.crowdmanagement.service.AlertService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public AlertSubscriptionResponse create(
        Principal principal,
        @Valid @RequestBody AlertSubscriptionRequest request
    ) {
        return alertService.create(principal.getName(), request);
    }

    @GetMapping("/mine")
    public List<AlertSubscriptionResponse> mine(Principal principal) {
        return alertService.mine(principal.getName());
    }
}
