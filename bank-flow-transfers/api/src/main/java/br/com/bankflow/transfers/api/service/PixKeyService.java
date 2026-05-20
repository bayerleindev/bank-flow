package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.api.client.BaasDictClient;
import br.com.bankflow.transfers.api.dto.response.PixKeyResponse;
import br.com.bankflow.transfers.shared.domain.PixKeyInfo;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import org.springframework.stereotype.Service;

@Service
public class PixKeyService {

    private final BaasDictClient baasDictClient;
    private final TransferRepository transferRepository;
    private final PixKeyCacheService pixKeyCacheService;

    public PixKeyService(
            BaasDictClient baasDictClient,
            TransferRepository transferRepository,
            PixKeyCacheService pixKeyCacheService) {
        this.baasDictClient = baasDictClient;
        this.transferRepository = transferRepository;
        this.pixKeyCacheService = pixKeyCacheService;
    }

    public PixKeyResponse findByKey(String key) {
        transferRepository.checkDatabaseConnection();
        PixKeyInfo keyInfo = baasDictClient.findByKey(key);
        PixKeyResponse response = PixKeyResponse.from(keyInfo);
        pixKeyCacheService.store(response);
        return response;
    }
}
