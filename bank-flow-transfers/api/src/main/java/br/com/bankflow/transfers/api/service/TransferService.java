package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.api.producer.TransferEventProducer;
import br.com.bankflow.transfers.shared.domain.Transfer;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransferEventProducer transferEventProducer;
    private final PixKeyTransferValidator pixKeyTransferValidator;
    private final Clock clock;

    public TransferService(
            TransferRepository transferRepository,
            TransferEventProducer transferEventProducer,
            PixKeyTransferValidator pixKeyTransferValidator,
            Clock clock) {
        this.transferRepository = transferRepository;
        this.transferEventProducer = transferEventProducer;
        this.pixKeyTransferValidator = pixKeyTransferValidator;
        this.clock = clock;
    }

    @Transactional
    public Transfer create(CreateTransferCommand command) {
        pixKeyTransferValidator.validate(command);

        Instant now = Instant.now(clock);
        Transfer transfer =
                new Transfer(
                        UUID.randomUUID(),
                        null,
                        command.creditParty(),
                        command.idempotencyKey(),
                        command.amountMinor(),
                        command.description(),
                        command.currency().toUpperCase(Locale.ROOT),
                        command.type(),
                        command.type().requestedStatus(),
                        null,
                        command.debitAccountId(),
                        null,
                        now,
                        now);

        Transfer savedTransfer = transferRepository.save(transfer);
        transferEventProducer.publishRequested(savedTransfer);
        return savedTransfer;
    }

    @Transactional(readOnly = true)
    public Transfer findById(UUID transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}
