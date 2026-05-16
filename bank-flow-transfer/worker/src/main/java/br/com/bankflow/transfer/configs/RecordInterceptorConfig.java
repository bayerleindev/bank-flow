package br.com.bankflow.transfer.configs;

import br.com.bankflow.transfer.observability.TransferTracing;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RecordInterceptorConfig implements RecordInterceptor<String, String> {

    private final TransferTracing transferTracing;

    public RecordInterceptorConfig(TransferTracing transferTracing) {
        this.transferTracing = transferTracing;
    }

    @Override
    public @Nullable ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        Header header = record.headers().lastHeader("transfer_id");

        if (header == null) {
            return record;
        }

        String transferIdValue = new String(header.value(), StandardCharsets.UTF_8);

        try {
            UUID transferId = UUID.fromString(transferIdValue);
            transferTracing.attachTransferId(transferId);
        } catch (IllegalArgumentException ignored) {
            // header inválido; não quebra consumo
        }

        return record;
    }
}
