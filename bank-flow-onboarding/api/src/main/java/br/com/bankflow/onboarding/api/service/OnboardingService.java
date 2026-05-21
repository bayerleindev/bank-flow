package br.com.bankflow.onboarding.api.service;

import br.com.bankflow.onboarding.api.client.AuthCredentialsClient;
import br.com.bankflow.onboarding.api.dto.ConfirmDocumentUploadRequest;
import br.com.bankflow.onboarding.api.dto.CreateApplicationRequest;
import br.com.bankflow.onboarding.api.dto.CreateApplicationResponse;
import br.com.bankflow.onboarding.api.dto.CreateDocumentUploadRequest;
import br.com.bankflow.onboarding.api.dto.CreateDocumentUploadResponse;
import br.com.bankflow.onboarding.api.producer.AccountCreationRequestedProducer;
import br.com.bankflow.onboarding.api.producer.OnboardingEventProducer;
import br.com.bankflow.onboarding.api.repository.OnboardingApplicationRepository;
import br.com.bankflow.onboarding.api.repository.OnboardingDocumentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.TooManyMethods"})
public class OnboardingService {

    private final OnboardingApplicationRepository applicationRepository;
    private final OnboardingDocumentRepository documentRepository;
    private final ApplicationTokenService tokenService;
    private final RustFsPresignedUploadService uploadService;
    private final AuthCredentialsClient authCredentialsClient;
    private final OnboardingEventProducer eventProducer;
    private final AccountCreationRequestedProducer accountCreationRequestedProducer;
    private final Clock clock;

    public OnboardingService(
            OnboardingApplicationRepository applicationRepository,
            OnboardingDocumentRepository documentRepository,
            ApplicationTokenService tokenService,
            RustFsPresignedUploadService uploadService,
            AuthCredentialsClient authCredentialsClient,
            OnboardingEventProducer eventProducer,
            AccountCreationRequestedProducer accountCreationRequestedProducer,
            Clock clock) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.tokenService = tokenService;
        this.uploadService = uploadService;
        this.authCredentialsClient = authCredentialsClient;
        this.eventProducer = eventProducer;
        this.accountCreationRequestedProducer = accountCreationRequestedProducer;
        this.clock = clock;
    }

    @Transactional
    public CreateApplicationResponse create(CreateApplicationRequest request) {
        Instant now = Instant.now(clock);
        ApplicationTokenService.TokenIssue token = tokenService.issue();
        OnboardingApplication application =
                new OnboardingApplication(
                        UUID.randomUUID(),
                        ApplicationStatus.DRAFT,
                        request.fullName(),
                        request.documentNumber(),
                        request.email(),
                        request.motherName(),
                        request.socialName(),
                        request.phoneNumber(),
                        request.birthDate(),
                        request.address(),
                        request.isPoliticallyExposed(),
                        null,
                        null,
                        token.tokenHash(),
                        token.expiresAt(),
                        now,
                        now,
                        null,
                        null,
                        null);
        OnboardingApplication saved = applicationRepository.create(application);
        return new CreateApplicationResponse(
                saved.id(), saved.status(), token.token(), saved.applicationTokenExpiresAt());
    }

    @Transactional(readOnly = true)
    public OnboardingApplication findById(UUID applicationId, String applicationToken) {
        OnboardingApplication application = findApplication(applicationId);
        requireApplicationToken(application, applicationToken);
        return application;
    }

    @Transactional
    public CreateDocumentUploadResponse createDocumentUpload(
            UUID applicationId, String applicationToken, CreateDocumentUploadRequest request) {
        OnboardingApplication application = findById(applicationId, applicationToken);
        requireMutable(application);
        Instant now = Instant.now(clock);
        UUID documentId = UUID.randomUUID();
        String storageKey =
                "onboarding/%s/%s/%s"
                        .formatted(
                                applicationId,
                                request.type().name().toLowerCase(Locale.ROOT),
                                documentId);
        OnboardingDocument document =
                documentRepository.create(
                        new OnboardingDocument(
                                documentId,
                                applicationId,
                                request.type(),
                                DocumentStatus.PENDING_UPLOAD,
                                storageKey,
                                request.contentType(),
                                null,
                                null,
                                null,
                                now,
                                now,
                                null));
        RustFsPresignedUploadService.PresignedUpload upload =
                uploadService.presign(document.storageKey(), document.contentType());
        return new CreateDocumentUploadResponse(
                document.id(),
                document.type(),
                document.status(),
                document.storageKey(),
                upload.uploadUrl(),
                upload.expiresAt());
    }

    @Transactional
    public OnboardingDocument confirmDocumentUpload(
            UUID applicationId,
            UUID documentId,
            String applicationToken,
            ConfirmDocumentUploadRequest request) {
        OnboardingApplication application = findById(applicationId, applicationToken);
        requireMutable(application);
        OnboardingDocument document =
                documentRepository
                        .findById(documentId)
                        .filter(found -> found.applicationId().equals(applicationId))
                        .orElseThrow(
                                () ->
                                        new OnboardingException(
                                                HttpStatus.NOT_FOUND, "document_not_found"));
        OnboardingDocument uploaded =
                documentRepository.markUploaded(
                        document.id(), request.contentLength(), request.contentHash());
        markDocumentsUploadedWhenReady(applicationId);
        return uploaded;
    }

    @Transactional
    public OnboardingApplication createCredentials(
            UUID applicationId, String applicationToken, String password) {
        OnboardingApplication application = findById(applicationId, applicationToken);
        requireMutable(application);
        UUID credentialsId =
                authCredentialsClient.create(
                        application.id(), application.documentNumber(), password);
        return applicationRepository
                .markCredentialsCreated(applicationId, credentialsId)
                .orElseThrow(
                        () ->
                                new OnboardingException(
                                        HttpStatus.NOT_FOUND, "application_not_found"));
    }

    @Transactional
    public OnboardingApplication submit(UUID applicationId, String applicationToken) {
        OnboardingApplication application = findById(applicationId, applicationToken);
        if (application.credentialsId() == null) {
            throw new OnboardingException(HttpStatus.UNPROCESSABLE_CONTENT, "credentials_required");
        }
        if (!hasRequiredDocuments(applicationId)) {
            throw new OnboardingException(HttpStatus.UNPROCESSABLE_CONTENT, "documents_required");
        }
        return applicationRepository
                .markSubmitted(applicationId)
                .orElseThrow(
                        () ->
                                new OnboardingException(
                                        HttpStatus.NOT_FOUND, "application_not_found"));
    }

    @Transactional
    public OnboardingApplication approve(UUID applicationId) {
        OnboardingApplication application = findApplication(applicationId);
        if (application.credentialsId() == null || !hasRequiredDocuments(applicationId)) {
            throw new OnboardingException(
                    HttpStatus.UNPROCESSABLE_CONTENT, "application_incomplete");
        }
        if (!EnumSet.of(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SUBMITTED)
                .contains(application.status())) {
            throw new OnboardingException(HttpStatus.CONFLICT, "application_not_under_review");
        }
        OnboardingApplication approved =
                applicationRepository
                        .approve(applicationId)
                        .orElseThrow(
                                () ->
                                        new OnboardingException(
                                                HttpStatus.NOT_FOUND, "application_not_found"));
        eventProducer.publishApproved(approved);
        accountCreationRequestedProducer.publish(approved);
        return approved;
    }

    @Transactional
    public OnboardingApplication reject(UUID applicationId, String reasonCode) {
        findApplication(applicationId);
        return applicationRepository
                .reject(applicationId, reasonCode)
                .orElseThrow(
                        () ->
                                new OnboardingException(
                                        HttpStatus.NOT_FOUND, "application_not_found"));
    }

    private OnboardingApplication findApplication(UUID applicationId) {
        return applicationRepository
                .findById(applicationId)
                .orElseThrow(
                        () ->
                                new OnboardingException(
                                        HttpStatus.NOT_FOUND, "application_not_found"));
    }

    private void requireApplicationToken(
            OnboardingApplication application, String applicationToken) {
        if (!StringUtils.hasText(applicationToken)
                || !application
                        .applicationTokenHash()
                        .equals(tokenService.hash(applicationToken))) {
            throw new OnboardingException(HttpStatus.UNAUTHORIZED, "invalid_application_token");
        }
        if (application.applicationTokenExpiresAt().isBefore(Instant.now(clock))) {
            throw new OnboardingException(HttpStatus.UNAUTHORIZED, "application_token_expired");
        }
    }

    private void requireMutable(OnboardingApplication application) {
        if (!EnumSet.of(
                        ApplicationStatus.DRAFT,
                        ApplicationStatus.DOCUMENTS_UPLOADED,
                        ApplicationStatus.CREDENTIALS_CREATED)
                .contains(application.status())) {
            throw new OnboardingException(HttpStatus.CONFLICT, "application_not_mutable");
        }
    }

    private void markDocumentsUploadedWhenReady(UUID applicationId) {
        OnboardingApplication application = findApplication(applicationId);
        if (application.status() == ApplicationStatus.DRAFT
                && hasRequiredDocuments(applicationId)) {
            applicationRepository.updateStatus(applicationId, ApplicationStatus.DOCUMENTS_UPLOADED);
        }
    }

    private boolean hasRequiredDocuments(UUID applicationId) {
        List<OnboardingDocument> documents = documentRepository.findByApplicationId(applicationId);
        return hasUploaded(documents, DocumentType.DOCUMENT_FRONT)
                && hasUploaded(documents, DocumentType.DOCUMENT_BACK)
                && hasUploaded(documents, DocumentType.SELFIE);
    }

    private boolean hasUploaded(List<OnboardingDocument> documents, DocumentType type) {
        return documents.stream()
                .anyMatch(
                        document ->
                                document.type() == type
                                        && document.status() == DocumentStatus.UPLOADED);
    }
}
