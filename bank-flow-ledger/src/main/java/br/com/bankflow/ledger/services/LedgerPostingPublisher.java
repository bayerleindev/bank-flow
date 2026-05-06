package br.com.bankflow.ledger.services;

import br.com.bankflow.ledger.domain.LedgerPosting;
import com.fasterxml.jackson.core.JsonProcessingException;

public interface LedgerPostingPublisher {
	void publish(LedgerPosting posting) throws JsonProcessingException;
}
