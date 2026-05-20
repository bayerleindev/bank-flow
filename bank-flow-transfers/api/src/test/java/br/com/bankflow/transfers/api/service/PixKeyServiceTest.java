package br.com.bankflow.transfers.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.bankflow.transfers.api.client.BaasDictClient;
import br.com.bankflow.transfers.api.dto.response.PixKeyResponse;
import br.com.bankflow.transfers.shared.domain.PixAccount;
import br.com.bankflow.transfers.shared.domain.PixKeyInfo;
import br.com.bankflow.transfers.shared.domain.PixOwner;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
class PixKeyServiceTest {

    @Mock private BaasDictClient baasDictClient;
    @Mock private TransferRepository transferRepository;
    @Mock private PixKeyCacheService pixKeyCacheService;

    @Test
    void shouldStorePixKeyResponseUsingEndToEndIdAsRedisKey() {
        PixKeyService service =
                new PixKeyService(baasDictClient, transferRepository, pixKeyCacheService);
        when(baasDictClient.findByKey("user@example.com"))
                .thenReturn(
                        new PixKeyInfo(
                                new PixAccount("260", "12345-6", "0001"),
                                new PixOwner("Ada", "***.123.456-**"),
                                "E2E123"));

        PixKeyResponse response = service.findByKey("user@example.com");

        ArgumentCaptor<PixKeyResponse> responseCaptor =
                ArgumentCaptor.forClass(PixKeyResponse.class);
        verify(pixKeyCacheService).store(responseCaptor.capture());
        assertThat(response.endToEndId()).isEqualTo("E2E123");
        assertThat(responseCaptor.getValue().endToEndId()).isEqualTo("E2E123");
    }
}
