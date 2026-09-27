package com.crowdmanagement.controller;

import com.crowdmanagement.dto.ApiDtos.OrganizationRequest;
import com.crowdmanagement.dto.ApiDtos.OrganizationResponse;
import com.crowdmanagement.service.OrganizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public List<OrganizationResponse> listOrganizations() {
        return organizationService.listOrganizations();
    }

    @PostMapping
    public OrganizationResponse createOrganization(
        @Valid @RequestBody OrganizationRequest request
    ) {
        return organizationService.createOrganization(request);
    }
}
