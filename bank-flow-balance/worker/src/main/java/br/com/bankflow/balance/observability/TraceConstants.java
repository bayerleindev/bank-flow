package br.com.bankflow.balance.observability;

public class TraceConstants {

    private TraceConstants() {}

    public static final String TRANSFER_ID = "transfer_id";
    public static final String TRANSFER_ID_HEADER = "X-Transfer-Id";
    public static final String KAFKA_TRANSFER_ID_HEADER = "transfer_id";

    public static final String TRANSFER_ID_SPAN_ATTR = "transfer_id";
}
