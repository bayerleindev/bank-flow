package br.com.bankflow.onboarding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.bankflow.onboarding.api.client.AuthCredentialsClient;
import br.com.bankflow.onboarding.api.dto.CreateApplicationRequest;
import br.com.bankflow.onboarding.api.dto.CreateApplicationResponse;
import br.com.bankflow.onboarding.api.producer.AccountCreationRequestedProducer;
import br.com.bankflow.onboarding.api.producer.OnboardingEventProducer;
import br.com.bankflow.onboarding.api.repository.OnboardingApplicationRepository;
import br.com.bankflow.onboarding.api.repository.OnboardingDocumentRepository;
import br.com.bankflow.onboarding.api.service.ApplicationStatus;
import br.com.bankflow.onboarding.api.service.ApplicationTokenService;
import br.com.bankflow.onboarding.api.service.DocumentStatus;
import br.com.bankflow.onboarding.api.service.DocumentType;
import br.com.bankflow.onboarding.api.service.OnboardingApplication;
import br.com.bankflow.onboarding.api.service.OnboardingDocument;
import br.com.bankflow.onboarding.api.service.OnboardingService;
import br.com.bankflow.onboarding.api.service.RustFsPresignedUploadService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({
    "PMD.ExcessiveImports",
    "PMD.TooManyMethods",
    "PMD.UnitTestContainsTooManyAsserts"
})
class OnboardingServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-21T12:00:00Z");
    private static final UUID APPLICATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CREDENTIALS_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Mock private OnboardingApplicationRepository applicationRepository;
    @Mock private OnboardingDocumentRepository documentRepository;
    @Mock private RustFsPresignedUploadService uploadService;
    @Mock private AuthCredentialsClient authCredentialsClient;
    @Mock private OnboardingEventProducer eventProducer;
    @Mock private AccountCreationRequestedProducer accountCreationRequestedProducer;

    @Test
    void shouldCreateApplicationWithContinuationToken() {
        OnboardingService service = service();
        when(applicationRepository.create(any()))
                .thenAnswer(invocation -> withId(invocation.getArgument(0)));

        CreateApplicationResponse response = service.create(createRequest());

        ArgumentCaptor<OnboardingApplication> applicationCaptor =
                ArgumentCaptor.forClass(OnboardingApplication.class);
        verify(applicationRepository).create(applicationCaptor.capture());
        assertThat(response.id()).isEqualTo(APPLICATION_ID);
        assertThat(response.status()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(response.applicationToken()).isNotBlank();
        assertThat(applicationCaptor.getValue().applicationTokenHash())
                .isNotEqualTo(response.applicationToken());
        assertThat(response.tokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void shouldSubmitApplicationWhenCredentialsAndRequiredDocumentsExist() {
        OnboardingService service = service();
        ApplicationTokenService.TokenIssue token = tokenIssue();
        OnboardingApplication application =
                application(ApplicationStatus.CREDENTIALS_CREATED, token.tokenHash());
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(documentRepository.findByApplicationId(APPLICATION_ID))
                .thenReturn(requiredDocuments());
        when(applicationRepository.markSubmitted(APPLICATION_ID))
                .thenReturn(Optional.of(application(ApplicationStatus.UNDER_REVIEW)));

        OnboardingApplication submitted = service.submit(APPLICATION_ID, token.token());

        assertThat(submitted.status()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        verify(applicationRepository).markSubmitted(APPLICATION_ID);
    }

    @Test
    void shouldPublishApprovedEventWhenApplicationIsApproved() {
        OnboardingService service = service();
        OnboardingApplication underReview = application(ApplicationStatus.UNDER_REVIEW);
        OnboardingApplication approved = application(ApplicationStatus.APPROVED);
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(underReview));
        when(documentRepository.findByApplicationId(APPLICATION_ID))
                .thenReturn(requiredDocuments());
        when(applicationRepository.approve(APPLICATION_ID)).thenReturn(Optional.of(approved));

        OnboardingApplication result = service.approve(APPLICATION_ID);

        assertThat(result.status()).isEqualTo(ApplicationStatus.APPROVED);
        verify(eventProducer).publishApproved(approved);
        verify(accountCreationRequestedProducer).publish(approved);
    }

    private OnboardingService service() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new OnboardingService(
                applicationRepository,
                documentRepository,
                new ApplicationTokenService(clock, Duration.ofHours(24)),
                uploadService,
                authCredentialsClient,
                eventProducer,
                accountCreationRequestedProducer,
                clock);
    }

    private ApplicationTokenService.TokenIssue tokenIssue() {
        return new ApplicationTokenService(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24))
                .issue();
    }

    private OnboardingApplication withId(OnboardingApplication application) {
        return new OnboardingApplication(
                APPLICATION_ID,
                application.status(),
                application.fullName(),
                application.documentNumber(),
                application.email(),
                application.motherName(),
                application.socialName(),
                application.phoneNumber(),
                application.birthDate(),
                application.address(),
                application.politicallyExposed(),
                application.credentialsId(),
                application.rejectionReasonCode(),
                application.applicationTokenHash(),
                application.applicationTokenExpiresAt(),
                application.createdAt(),
                application.updatedAt(),
                application.submittedAt(),
                application.approvedAt(),
                application.rejectedAt());
    }

    private OnboardingApplication application(ApplicationStatus status) {
        return application(status, "unused");
    }

    private OnboardingApplication application(ApplicationStatus status, String tokenHash) {
        return new OnboardingApplication(
                APPLICATION_ID,
                status,
                "Ada Lovelace",
                "12345678900",
                "ada@example.com",
                "Anne",
                null,
                "+5511999999999",
                LocalDate.parse("1815-12-10"),
                "Rua Um",
                false,
                CREDENTIALS_ID,
                null,
                tokenHash,
                NOW.plus(Duration.ofHours(24)),
                NOW,
                NOW,
                status == ApplicationStatus.UNDER_REVIEW ? NOW : null,
                status == ApplicationStatus.APPROVED ? NOW : null,
                null);
    }

    private CreateApplicationRequest createRequest() {
        return new CreateApplicationRequest(
                "Ada Lovelace",
                "12345678900",
                "ada@example.com",
                "Anne",
                null,
                "+5511999999999",
                LocalDate.parse("1815-12-10"),
                "Rua Um",
                false);
    }

    private List<OnboardingDocument> requiredDocuments() {
        return List.of(
                document(DocumentType.DOCUMENT_FRONT),
                document(DocumentType.DOCUMENT_BACK),
                document(DocumentType.SELFIE));
    }

    private OnboardingDocument document(DocumentType type) {
        return new OnboardingDocument(
                UUID.randomUUID(),
                APPLICATION_ID,
                type,
                DocumentStatus.UPLOADED,
                "key",
                "image/jpeg",
                1024L,
                null,
                null,
                NOW,
                NOW,
                NOW);
    }
}
