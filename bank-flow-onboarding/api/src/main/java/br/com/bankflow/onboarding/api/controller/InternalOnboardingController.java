package br.com.bankflow.onboarding.api.controller;

import br.com.bankflow.onboarding.api.dto.ApplicationResponse;
import br.com.bankflow.onboarding.api.dto.RejectApplicationRequest;
import br.com.bankflow.onboarding.api.service.OnboardingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/onboarding/applications")
public class InternalOnboardingController {

    private final OnboardingService onboardingService;

    public InternalOnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<ApplicationResponse> approve(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(
                ApplicationResponse.from(onboardingService.approve(applicationId)));
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<ApplicationResponse> reject(
            @PathVariable UUID applicationId,
            @Valid @RequestBody RejectApplicationRequest request) {
        return ResponseEntity.ok(
                ApplicationResponse.from(
                        onboardingService.reject(applicationId, request.reasonCode())));
    }
}
