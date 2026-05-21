package br.com.bankflow.onboarding.api.observability;

import br.com.bankflow.onboarding.api.service.OnboardingApplication;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@SuppressWarnings("PMD.TooManyMethods")
public class OnboardingObservabilityAspect {

    private final ObservationRegistry observationRegistry;
    private final Counter applicationsCreatedCounter;
    private final Counter documentUploadsRequestedCounter;
    private final Counter documentsUploadedCounter;
    private final Counter credentialsCreatedCounter;
    private final Counter applicationsSubmittedCounter;
    private final Counter applicationsApprovedCounter;
    private final Counter applicationsRejectedCounter;
    private final Counter onboardingApprovedPublishedCounter;
    private final Counter accountCreationRequestedPublishedCounter;

    public OnboardingObservabilityAspect(
            ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        this.observationRegistry = observationRegistry;
        this.applicationsCreatedCounter =
                counter(meterRegistry, "onboarding.application.created", "Applications created");
        this.documentUploadsRequestedCounter =
                counter(
                        meterRegistry,
                        "onboarding.document_upload.requested",
                        "Document uploads requested");
        this.documentsUploadedCounter =
                counter(meterRegistry, "onboarding.document.uploaded", "Documents uploaded");
        this.credentialsCreatedCounter =
                counter(meterRegistry, "onboarding.credentials.created", "Credentials created");
        this.applicationsSubmittedCounter =
                counter(
                        meterRegistry,
                        "onboarding.application.submitted",
                        "Applications submitted");
        this.applicationsApprovedCounter =
                counter(meterRegistry, "onboarding.application.approved", "Applications approved");
        this.applicationsRejectedCounter =
                counter(meterRegistry, "onboarding.application.rejected", "Applications rejected");
        this.onboardingApprovedPublishedCounter =
                counter(
                        meterRegistry,
                        "onboarding.kafka.approved.published",
                        "onboarding.application.approved events published");
        this.accountCreationRequestedPublishedCounter =
                counter(
                        meterRegistry,
                        "onboarding.kafka.account_creation_requested.published",
                        "account.creation.requested events published");
    }

    @Around("execution(* br.com.bankflow.onboarding.api.service.OnboardingService.create(..))")
    public Object observeApplicationCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(
                "onboarding.application.create",
                "create onboarding application",
                joinPoint,
                ignored -> applicationsCreatedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.onboarding.api.service.OnboardingService.createDocumentUpload(..))")
    public Object observeDocumentUploadRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(
                "onboarding.document_upload.create",
                "create onboarding document upload",
                joinPoint,
                ignored -> documentUploadsRequestedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.onboarding.api.service.OnboardingService.confirmDocumentUpload(..))")
    public Object observeDocumentUploadConfirmation(ProceedingJoinPoint joinPoint)
            throws Throwable {
        return observe(
                "onboarding.document.confirm_upload",
                "confirm onboarding document upload",
                joinPoint,
                ignored -> documentsUploadedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.onboarding.api.service.OnboardingService.createCredentials(..))")
    public Object observeCredentialsCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(
                "onboarding.credentials.create",
                "create onboarding credentials",
                joinPoint,
                ignored -> credentialsCreatedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.onboarding.api.service.OnboardingService.submit(..))")
    public Object observeApplicationSubmit(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(
                "onboarding.application.submit",
                "submit onboarding application",
                joinPoint,
                ignored -> applicationsSubmittedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.onboarding.api.service.OnboardingService.approve(..))")
    public Object observeApplicationApprove(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(
                "onboarding.application.approve",
                "approve onboarding application",
                joinPoint,
                ignored -> applicationsApprovedCounter.increment());
    }

    @Around("execution(* br.com.bankflow.onboarding.api.service.OnboardingService.reject(..))")
    public Object observeApplicationReject(ProceedingJoinPoint joinPoint) throws Throwable {
        return observe(
                "onboarding.application.reject",
                "reject onboarding application",
                joinPoint,
                ignored -> applicationsRejectedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.onboarding.api.producer.OnboardingEventProducer.publishApproved(..))")
    public Object observeApprovedPublish(ProceedingJoinPoint joinPoint) throws Throwable {
        OnboardingApplication application = (OnboardingApplication) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "onboarding.kafka.publish.approved", observationRegistry)
                        .contextualName("publish onboarding approved")
                        .lowCardinalityKeyValue(
                                "messaging.destination", "onboarding.application.approved")
                        .highCardinalityKeyValue(
                                "onboarding.application.id", application.id().toString());
        return observe(
                observation, joinPoint, ignored -> onboardingApprovedPublishedCounter.increment());
    }

    @Around(
            "execution(* br.com.bankflow.onboarding.api.producer.AccountCreationRequestedProducer.publish(..))")
    public Object observeAccountCreationRequestedPublish(ProceedingJoinPoint joinPoint)
            throws Throwable {
        OnboardingApplication application = (OnboardingApplication) joinPoint.getArgs()[0];
        Observation observation =
                Observation.createNotStarted(
                                "onboarding.kafka.publish.account_creation_requested",
                                observationRegistry)
                        .contextualName("publish account creation requested")
                        .lowCardinalityKeyValue(
                                "messaging.destination", "account.creation.requested")
                        .highCardinalityKeyValue(
                                "onboarding.application.id", application.id().toString());
        return observe(
                observation,
                joinPoint,
                ignored -> accountCreationRequestedPublishedCounter.increment());
    }

    private Counter counter(MeterRegistry meterRegistry, String name, String description) {
        return Counter.builder(name).description(description).register(meterRegistry);
    }

    private Object observe(
            String name,
            String contextualName,
            ProceedingJoinPoint joinPoint,
            ObservationSuccess success)
            throws Throwable {
        Observation observation =
                Observation.createNotStarted(name, observationRegistry)
                        .contextualName(contextualName);
        return observe(observation, joinPoint, success);
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private Object observe(
            Observation observation, ProceedingJoinPoint joinPoint, ObservationSuccess success)
            throws Throwable {
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            Object result = joinPoint.proceed();
            success.accept(result);
            return result;
        } catch (Exception exception) {
            observation.error(exception);
            throw exception;
        } finally {
            observation.stop();
        }
    }

    @FunctionalInterface
    private interface ObservationSuccess {
        void accept(Object result);
    }
}
