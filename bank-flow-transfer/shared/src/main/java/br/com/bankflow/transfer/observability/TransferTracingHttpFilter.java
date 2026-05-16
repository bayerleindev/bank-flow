package br.com.bankflow.transfer.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static br.com.bankflow.transfer.observability.TraceConstants.TRANSFER_ID;
import static br.com.bankflow.transfer.observability.TraceConstants.TRANSFER_ID_HEADER;

@Component
public class TransferTracingHttpFilter extends OncePerRequestFilter {

    private final TransferTracing transferTracing;

    public TransferTracingHttpFilter(TransferTracing transferTracing) {
        this.transferTracing = transferTracing;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String transferIdHeader = request.getHeader(TRANSFER_ID_HEADER);

        if (transferIdHeader == null || transferIdHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID transferId = UUID.fromString(transferIdHeader);

            transferTracing.withTransferId(transferId, () -> {
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (IllegalArgumentException e) {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRANSFER_ID);
        }
    }
}
