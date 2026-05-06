package br.com.bankflow.transfer.services;

import br.com.bankflow.transfer.clients.balance.BalanceClient;
import br.com.bankflow.transfer.clients.balance.BalanceHoldResponse;
import br.com.bankflow.transfer.clients.balance.CreateBalanceHoldRequest;
import br.com.bankflow.transfer.clients.psp.PspClient;
import br.com.bankflow.transfer.clients.psp.PspPaymentResponse;
import br.com.bankflow.transfer.clients.psp.PspPaymentStatus;
import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.PspWebhookCommand;
import br.com.bankflow.transfer.domain.PspWebhookStatus;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferStatus;
import br.com.bankflow.transfer.repositories.TransferRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferOrchestrationServiceTests {
	private final FakeTransferRepository repository = new FakeTransferRepository();
	private final FakeBalanceClient balanceClient = new FakeBalanceClient();
	private final FakePspClient pspClient = new FakePspClient();
	private final TransferOrchestrationService service = new TransferOrchestrationService(
			repository,
			balanceClient,
			pspClient,
			Clock.fixed(Instant.ofEpochMilli(1_778_100_000_000L), ZoneOffset.UTC)
	);

	@Test
	void createsTransferWithHoldAndPendingPspPayment() {
		Transfer transfer = service.createTransfer(command("idem-1"));

		assertEquals(TransferStatus.PSP_PENDING, transfer.status());
		assertEquals("hold-" + transfer.transferId(), transfer.holdId());
		assertEquals("psp-" + transfer.transferId(), transfer.pspPaymentId());
		assertEquals(1, balanceClient.createdHolds);
		assertEquals(0, balanceClient.releasedHolds);
	}

	@Test
	void returnsExistingTransferForSameIdempotencyKey() {
		Transfer first = service.createTransfer(command("idem-1"));
		Transfer duplicate = service.createTransfer(command("idem-1"));

		assertEquals(first.transferId(), duplicate.transferId());
		assertEquals(1, balanceClient.createdHolds);
	}

	@Test
	void marksTransferAsPspConfirmedWhenWebhookConfirmsPayment() {
		Transfer transfer = service.createTransfer(command("idem-1"));

		Transfer confirmed = service.handlePspWebhook(new PspWebhookCommand(
				transfer.pspPaymentId(),
				PspWebhookStatus.CONFIRMED,
				null
		));

		assertEquals(TransferStatus.PSP_CONFIRMED, confirmed.status());
		assertEquals(0, balanceClient.releasedHolds);
	}

	@Test
	void releasesHoldAndFailsTransferWhenWebhookFailsPayment() {
		Transfer transfer = service.createTransfer(command("idem-1"));

		Transfer failed = service.handlePspWebhook(new PspWebhookCommand(
				transfer.pspPaymentId(),
				PspWebhookStatus.FAILED,
				"PSP_REJECTED"
		));

		assertEquals(TransferStatus.FAILED, failed.status());
		assertEquals("PSP_REJECTED", failed.failureReason());
		assertEquals(1, balanceClient.releasedHolds);
	}

	private CreateTransferCommand command(String idempotencyKey) {
		return new CreateTransferCommand(
				idempotencyKey,
				1001L,
				2002L,
				1500L,
				"BRL",
				"Test transfer"
		);
	}

	private static class FakeBalanceClient implements BalanceClient {
		private int createdHolds;
		private int releasedHolds;

		@Override
		public BalanceHoldResponse createHold(CreateBalanceHoldRequest request) {
			createdHolds++;
			return new BalanceHoldResponse(
					"hold-" + request.transferId(),
					request.transferId(),
					request.accountId(),
					request.amountMinor(),
					request.currency(),
					"HELD",
					request.reason(),
					request.expiresAt(),
					1L,
					1L
			);
		}

		@Override
		public void releaseHold(String holdId) {
			releasedHolds++;
		}
	}

	private static class FakePspClient implements PspClient {
		@Override
		public PspPaymentResponse createPayment(Transfer transfer) {
			return new PspPaymentResponse(
					"psp-" + transfer.transferId(),
					PspPaymentStatus.PENDING,
					null
			);
		}
	}

	private static class FakeTransferRepository implements TransferRepository {
		private final Map<UUID, Transfer> byId = new HashMap<>();
		private final Map<String, UUID> byIdempotencyKey = new HashMap<>();
		private final Map<String, UUID> byPspPaymentId = new HashMap<>();

		@Override
		public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
			return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey)).map(byId::get);
		}

		@Override
		public Optional<Transfer> findByTransferId(UUID transferId) {
			return Optional.ofNullable(byId.get(transferId));
		}

		@Override
		public Optional<Transfer> findByPspPaymentId(String pspPaymentId) {
			return Optional.ofNullable(byPspPaymentId.get(pspPaymentId)).map(byId::get);
		}

		@Override
		public Transfer create(UUID transferId, CreateTransferCommand command, long now) {
			Transfer transfer = new Transfer(
					transferId,
					command.idempotencyKey(),
					command.sourceAccountId(),
					command.destinationAccountId(),
					command.amountMinor(),
					command.currency(),
					command.description(),
					null,
					null,
					TransferStatus.RECEIVED,
					null,
					now,
					now
			);
			byId.put(transferId, transfer);
			byIdempotencyKey.put(command.idempotencyKey(), transferId);
			return transfer;
		}

		@Override
		public Transfer updateHold(UUID transferId, String holdId, TransferStatus status, long now) {
			Transfer current = byId.get(transferId);
			Transfer updated = copy(current, holdId, current.pspPaymentId(), status, current.failureReason(), now);
			byId.put(transferId, updated);
			return updated;
		}

		@Override
		public Transfer updatePspPayment(UUID transferId, String pspPaymentId, TransferStatus status, long now) {
			Transfer current = byId.get(transferId);
			Transfer updated = copy(current, current.holdId(), pspPaymentId, status, current.failureReason(), now);
			byId.put(transferId, updated);
			byPspPaymentId.put(pspPaymentId, transferId);
			return updated;
		}

		@Override
		public Transfer updateStatus(UUID transferId, TransferStatus status, String failureReason, long now) {
			Transfer current = byId.get(transferId);
			Transfer updated = copy(current, current.holdId(), current.pspPaymentId(), status, failureReason, now);
			byId.put(transferId, updated);
			return updated;
		}

		private Transfer copy(
				Transfer current,
				String holdId,
				String pspPaymentId,
				TransferStatus status,
				String failureReason,
				long updatedAt
		) {
			return new Transfer(
					current.transferId(),
					current.idempotencyKey(),
					current.sourceAccountId(),
					current.destinationAccountId(),
					current.amountMinor(),
					current.currency(),
					current.description(),
					holdId,
					pspPaymentId,
					status,
					failureReason,
					current.createdAt(),
					updatedAt
			);
		}
	}
}
