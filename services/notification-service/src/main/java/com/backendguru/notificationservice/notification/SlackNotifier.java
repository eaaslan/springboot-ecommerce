package com.backendguru.notificationservice.notification;

import com.backendguru.common.event.OrderConfirmedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Optional Slack webhook notifier. Disabled by default — set {@code app.slack.enabled=true} and
 * provide {@code app.slack.webhook-url} via env to activate. Failures are swallowed (best-effort)
 * because the order is already CONFIRMED.
 */
@Component
@Slf4j
public class SlackNotifier {

  private final boolean enabled;
  private final String webhookUrl;
  private final MeterRegistry meterRegistry;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public SlackNotifier(
      @Value("${app.slack.enabled:false}") boolean enabled,
      @Value("${app.slack.webhook-url:}") String webhookUrl,
      MeterRegistry meterRegistry) {
    this.enabled = enabled;
    this.webhookUrl = webhookUrl;
    this.meterRegistry = meterRegistry;
  }

  public void notifyOrderConfirmed(OrderConfirmedEvent event) {
    if (!enabled || webhookUrl == null || webhookUrl.isBlank()) return;

    String text =
        "🛒 Order #%d confirmed — user %d, total %s %s (eventId=%s, at %s)"
            .formatted(
                event.orderId(),
                event.userId(),
                event.totalAmount(),
                event.currency(),
                event.eventId(),
                event.occurredAt());
    String body = "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(webhookUrl))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    try {
      HttpResponse<String> resp =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
        Counter.builder("slack.posted").register(meterRegistry).increment();
        log.debug("Slack notify ok for order {}", event.orderId());
      } else {
        Counter.builder("slack.failed").tag("reason", "non2xx").register(meterRegistry).increment();
        log.warn("Slack notify non-2xx ({}) for order {}: {}",
            resp.statusCode(), event.orderId(), resp.body());
      }
    } catch (Exception ex) {
      Counter.builder("slack.failed").tag("reason", "exception").register(meterRegistry).increment();
      log.warn("Slack notify failed for order {}: {}", event.orderId(), ex.toString());
    }
  }
}
