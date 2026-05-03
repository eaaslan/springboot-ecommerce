package com.backendguru.paymentservice.payment.iyzico;

import com.backendguru.paymentservice.payment.dto.ChargeRequest;
import com.iyzipay.Options;
import com.iyzipay.model.Address;
import com.iyzipay.model.BasketItem;
import com.iyzipay.model.BasketItemType;
import com.iyzipay.model.Buyer;
import com.iyzipay.model.Currency;
import com.iyzipay.model.Locale;
import com.iyzipay.model.Payment;
import com.iyzipay.model.PaymentCard;
import com.iyzipay.model.PaymentChannel;
import com.iyzipay.model.PaymentGroup;
import com.iyzipay.model.Refund;
import com.iyzipay.model.Status;
import com.iyzipay.request.CreatePaymentRequest;
import com.iyzipay.request.CreateRefundRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Real Iyzico calls. Wrapped behind {@link IyzicoProperties#isConfigured()} so the service can
 * still boot in a CI / hands-on demo environment without a sandbox account — in that case the
 * caller {@code PaymentService} keeps using its mock branch.
 *
 * <p>This is the Non-3D ("non-secure") flow. Sandbox accepts test cards from
 * https://docs.iyzico.com/api-referansi/odeme/test-kartlari without an SMS step. For production,
 * Iyzico requires 3DS — that's a saga refactor and lives behind a future feature flag.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class IyzicoClient {

  private final IyzicoProperties props;

  /** Build the per-request Options bag — Iyzico SDK is stateless so this is cheap. */
  public Options options() {
    Options o = new Options();
    o.setApiKey(props.apiKey());
    o.setSecretKey(props.secretKey());
    o.setBaseUrl(props.baseUrl());
    return o;
  }

  /** Returns the SDK Payment if Iyzico accepted, or throws if it declined. */
  public Payment charge(ChargeRequest req) {
    CreatePaymentRequest payload = new CreatePaymentRequest();
    payload.setLocale(Locale.TR.getValue());
    payload.setConversationId(String.valueOf(req.orderId()));
    payload.setPrice(req.amount());
    payload.setPaidPrice(req.amount());
    payload.setCurrency(currencyOf(req.currency()).name());
    payload.setInstallment(1);
    payload.setBasketId("ORDER-" + req.orderId());
    payload.setPaymentChannel(PaymentChannel.WEB.name());
    payload.setPaymentGroup(PaymentGroup.PRODUCT.name());

    PaymentCard card = new PaymentCard();
    card.setCardHolderName(req.card().holderName());
    card.setCardNumber(req.card().number());
    card.setExpireMonth(req.card().expireMonth());
    card.setExpireYear(req.card().expireYear());
    card.setCvc(req.card().cvc());
    card.setRegisterCard(0);
    payload.setPaymentCard(card);

    // Buyer + addresses are required by Iyzico but we don't track shipping here; use stubs that
    // match their sandbox examples. Real shipping/billing would come from order-service.
    Buyer buyer = new Buyer();
    buyer.setId("BY-" + req.orderId());
    buyer.setName(firstWord(req.card().holderName(), "Customer"));
    buyer.setSurname(lastWord(req.card().holderName(), "Demo"));
    buyer.setIdentityNumber("11111111111");
    buyer.setEmail("orders+" + req.orderId() + "@demo.local");
    buyer.setRegistrationAddress("Demo address Istanbul");
    buyer.setIp("85.34.78.112");
    buyer.setCity("Istanbul");
    buyer.setCountry("Turkey");
    buyer.setGsmNumber("+905350000000");
    payload.setBuyer(buyer);

    Address addr = new Address();
    addr.setContactName(req.card().holderName());
    addr.setCity("Istanbul");
    addr.setCountry("Turkey");
    addr.setAddress("Demo address Istanbul");
    payload.setShippingAddress(addr);
    payload.setBillingAddress(addr);

    BasketItem item = new BasketItem();
    item.setId("OI-" + req.orderId());
    item.setName("Order " + req.orderId());
    item.setCategory1("General");
    item.setItemType(BasketItemType.PHYSICAL.name());
    item.setPrice(req.amount());
    payload.setBasketItems(List.of(item));

    Payment response = Payment.create(payload, options());
    if (!Status.SUCCESS.getValue().equals(response.getStatus())) {
      log.warn(
          "Iyzico DECLINED order {}: errorCode={} errorMessage={}",
          req.orderId(),
          response.getErrorCode(),
          response.getErrorMessage());
      throw new IyzicoDeclinedException(
          response.getErrorMessage() == null
              ? "Card declined by Iyzico"
              : response.getErrorMessage());
    }
    log.info(
        "Iyzico SUCCESS order {} → paymentId={} authCode={}",
        req.orderId(),
        response.getPaymentId(),
        response.getAuthCode());
    return response;
  }

  /**
   * Refund the full amount. Iyzico requires the per-payment-item id, which we mirror to BasketItem
   * id.
   */
  public void refund(String iyzicoPaymentId, BigDecimal amount, String currency) {
    if (iyzicoPaymentId == null || iyzicoPaymentId.startsWith("MOCK-")) {
      log.info("Skipping Iyzico refund — payment id {} is from the mock branch", iyzicoPaymentId);
      return;
    }
    CreateRefundRequest req = new CreateRefundRequest();
    req.setLocale(Locale.TR.getValue());
    req.setConversationId(UUID.randomUUID().toString());
    req.setPaymentTransactionId(iyzicoPaymentId);
    req.setPrice(amount);
    req.setCurrency(currencyOf(currency).name());
    req.setIp("85.34.78.112");
    Refund r = Refund.create(req, options());
    if (!Status.SUCCESS.getValue().equals(r.getStatus())) {
      log.warn("Iyzico refund failed for paymentId={}: {}", iyzicoPaymentId, r.getErrorMessage());
      throw new IyzicoDeclinedException(
          "Refund failed: " + (r.getErrorMessage() == null ? "unknown" : r.getErrorMessage()));
    }
    log.info("Iyzico refund OK for paymentId={}", iyzicoPaymentId);
  }

  private static Currency currencyOf(String code) {
    if (code == null) return Currency.TRY;
    try {
      return Currency.valueOf(code.toUpperCase());
    } catch (IllegalArgumentException e) {
      return Currency.TRY;
    }
  }

  private static String firstWord(String s, String fallback) {
    if (s == null || s.isBlank()) return fallback;
    int sp = s.indexOf(' ');
    return sp <= 0 ? s : s.substring(0, sp);
  }

  private static String lastWord(String s, String fallback) {
    if (s == null || s.isBlank()) return fallback;
    int sp = s.lastIndexOf(' ');
    return sp <= 0 ? fallback : s.substring(sp + 1);
  }

  /** Iyzico responded with a non-SUCCESS status. Maps to PaymentDeclinedException upstream. */
  public static class IyzicoDeclinedException extends RuntimeException {
    public IyzicoDeclinedException(String message) {
      super(message);
    }
  }
}
