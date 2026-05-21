package br.com.bankflow.onboarding.api.controller;

import br.com.bankflow.onboarding.api.dto.ApplicationResponse;
import br.com.bankflow.onboarding.api.dto.ConfirmDocumentUploadRequest;
import br.com.bankflow.onboarding.api.dto.CreateApplicationRequest;
import br.com.bankflow.onboarding.api.dto.CreateApplicationResponse;
import br.com.bankflow.onboarding.api.dto.CreateCredentialsRequest;
import br.com.bankflow.onboarding.api.dto.CreateDocumentUploadRequest;
import br.com.bankflow.onboarding.api.dto.CreateDocumentUploadResponse;
import br.com.bankflow.onboarding.api.dto.DocumentResponse;
import br.com.bankflow.onboarding.api.service.OnboardingApplication;
import br.com.bankflow.onboarding.api.service.OnboardingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/onboarding/applications")
public class OnboardingController {

    private static final String APPLICATION_TOKEN_HEADER = "X-Onboarding-Application-Token";

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    public ResponseEntity<CreateApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request) {
        CreateApplicationResponse response = onboardingService.create(request);
        return ResponseEntity.created(URI.create("/onboarding/applications/" + response.id()))
                .body(response);
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ApplicationResponse> findById(
            @PathVariable UUID applicationId,
            @RequestHeader(APPLICATION_TOKEN_HEADER) String applicationToken) {
        return ResponseEntity.ok(
                ApplicationResponse.from(
                        onboardingService.findById(applicationId, applicationToken)));
    }

    @PostMapping("/{applicationId}/documents")
    public ResponseEntity<CreateDocumentUploadResponse> createDocumentUpload(
            @PathVariable UUID applicationId,
            @RequestHeader(APPLICATION_TOKEN_HEADER) String applicationToken,
            @Valid @RequestBody CreateDocumentUploadRequest request) {
        CreateDocumentUploadResponse response =
                onboardingService.createDocumentUpload(applicationId, applicationToken, request);
        return ResponseEntity.created(
                        URI.create(
                                "/onboarding/applications/"
                                        + applicationId
                                        + "/documents/"
                                        + response.documentId()))
                .body(response);
    }

    @PostMapping("/{applicationId}/documents/{documentId}/confirm")
    public ResponseEntity<DocumentResponse> confirmDocumentUpload(
            @PathVariable UUID applicationId,
            @PathVariable UUID documentId,
            @RequestHeader(APPLICATION_TOKEN_HEADER) String applicationToken,
            @Valid @RequestBody ConfirmDocumentUploadRequest request) {
        return ResponseEntity.ok(
                DocumentResponse.from(
                        onboardingService.confirmDocumentUpload(
                                applicationId, documentId, applicationToken, request)));
    }

    @PostMapping("/{applicationId}/credentials")
    public ResponseEntity<ApplicationResponse> createCredentials(
            @PathVariable UUID applicationId,
            @RequestHeader(APPLICATION_TOKEN_HEADER) String applicationToken,
            @Valid @RequestBody CreateCredentialsRequest request) {
        OnboardingApplication application =
                onboardingService.createCredentials(
                        applicationId, applicationToken, request.password());
        return ResponseEntity.ok(ApplicationResponse.from(application));
    }

    @PostMapping("/{applicationId}/submit")
    public ResponseEntity<ApplicationResponse> submit(
            @PathVariable UUID applicationId,
            @RequestHeader(APPLICATION_TOKEN_HEADER) String applicationToken) {
        return ResponseEntity.ok(
                ApplicationResponse.from(
                        onboardingService.submit(applicationId, applicationToken)));
    }
}
