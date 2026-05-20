package br.com.bankflow.transfers.api.service;

import br.com.bankflow.transfers.api.client.BaasDictClient;
import br.com.bankflow.transfers.shared.domain.PixKeyInfo;
import br.com.bankflow.transfers.shared.repository.TransferRepository;
import org.springframework.stereotype.Service;

@Service
public class PixKeyService {

    private final BaasDictClient baasDictClient;
    private final TransferRepository transferRepository;

    public PixKeyService(BaasDictClient baasDictClient, TransferRepository transferRepository) {
        this.baasDictClient = baasDictClient;
        this.transferRepository = transferRepository;
    }

    public PixKeyInfo findByKey(String key) {
        transferRepository.checkDatabaseConnection();
        return baasDictClient.findByKey(key);
    }
}
