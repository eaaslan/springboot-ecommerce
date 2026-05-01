package com.backendguru.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void usesIncomingHeaderWhenPresent() throws ServletException, IOException {
    var request = new MockHttpServletRequest();
    request.addHeader(LoggingConstants.CORRELATION_ID_HEADER, "abc-123");
    var response = new MockHttpServletResponse();
    var seen = new AtomicReference<String>();

    FilterChain chain = (req, res) -> seen.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
    filter.doFilter(request, response, chain);

    assertThat(seen.get()).isEqualTo("abc-123");
    assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isEqualTo("abc-123");
  }

  @Test
  void generatesUuidWhenHeaderMissing() throws ServletException, IOException {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    var seen = new AtomicReference<String>();

    FilterChain chain = (req, res) -> seen.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
    filter.doFilter(request, response, chain);

    var generated = seen.get();
    assertThat(generated).isNotBlank();
    UUID.fromString(generated);
    assertThat(response.getHeader(LoggingConstants.CORRELATION_ID_HEADER)).isEqualTo(generated);
  }

  @Test
  void clearsMdcAfterChainEvenWhenChainThrows() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    FilterChain throwing = mock(FilterChain.class);
    doAnswer(
            inv -> {
              throw new RuntimeException("boom");
            })
        .when(throwing)
        .doFilter(request, response);
    try {
      filter.doFilter(request, response, throwing);
    } catch (RuntimeException ignored) {
      // expected
    }
    assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
  }
}
