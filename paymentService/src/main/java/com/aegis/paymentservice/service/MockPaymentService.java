package com.aegis.paymentservice.service;

import com.aegis.paymentservice.dto.AuthorizeRequest;
import com.aegis.paymentservice.dto.AuthorizeResponse;
import com.aegis.paymentservice.dto.CaptureRequest;
import com.aegis.paymentservice.dto.CaptureResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock payment service: configurable latency, random 5xx, simulated timeout.
 * Force behavior via X-Mock-Result: success | fail | timeout | 503 | 502 | 500
 */
@Service
public class MockPaymentService {

	private static final Logger log = LoggerFactory.getLogger(MockPaymentService.class);

	private final int latencyMinMs;
	private final int latencyMaxMs;
	private final double random5xxProbability;
	private final int simulatedTimeoutDelayMs;

	public MockPaymentService(
			@Value("${app.mock.latencyMinMs:200}") int latencyMinMs,
			@Value("${app.mock.latencyMaxMs:800}") int latencyMaxMs,
			@Value("${app.mock.random5xxProbability:0.05}") double random5xxProbability,
			@Value("${app.mock.simulatedTimeoutDelayMs:0}") int simulatedTimeoutDelayMs) {
		this.latencyMinMs = latencyMinMs;
		this.latencyMaxMs = latencyMaxMs;
		this.random5xxProbability = Math.max(0, Math.min(1, random5xxProbability));
		this.simulatedTimeoutDelayMs = Math.max(0, simulatedTimeoutDelayMs);
	}

	public AuthorizeResponse authorize(AuthorizeRequest request, String forceResult) {
		applyForcedResult(request.orderId(), forceResult);
		applyLatency(request.orderId());
		return new AuthorizeResponse(
				request.orderId(),
				UUID.randomUUID(),
				AuthorizeResponse.STATUS_AUTHORIZED
		);
	}

	public CaptureResponse capture(CaptureRequest request, String forceResult) {
		applyForcedResult(request.orderId(), forceResult);
		applyLatency(request.orderId());
		return new CaptureResponse(
				request.orderId(),
				request.authorizationId(),
				CaptureResponse.STATUS_CAPTURED
		);
	}

	private void applyForcedResult(UUID orderId, String forceResult) {
		if (forceResult == null || forceResult.isBlank()) {
			if (random5xxProbability > 0 && ThreadLocalRandom.current().nextDouble() < random5xxProbability) {
				int status = ThreadLocalRandom.current().nextInt(3) == 0 ? 503
						: (ThreadLocalRandom.current().nextBoolean() ? 502 : 500);
				throw new MockPaymentException(status, "Mock: random 5xx");
			}
			return;
		}
		String lower = forceResult.trim().toLowerCase();
		switch (lower) {
			case "fail" -> throw new MockPaymentException(402, "Mock: payment declined");
			case "503" -> throw new MockPaymentException(503, "Mock: service unavailable");
			case "502" -> throw new MockPaymentException(502, "Mock: bad gateway");
			case "500" -> throw new MockPaymentException(500, "Mock: internal error");
			case "timeout" -> {
				int delay = simulatedTimeoutDelayMs > 0 ? simulatedTimeoutDelayMs : 30_000;
				log.info("Mock timeout: sleeping {} ms for order {}", delay, orderId);
				sleep(delay);
				throw new MockPaymentException(504, "Mock: gateway timeout");
			}
			default -> { /* success */ }
		}
	}

	private void applyLatency(UUID orderId) {
		int ms = latencyMinMs;
		if (latencyMaxMs > latencyMinMs) {
			ms = ThreadLocalRandom.current().nextInt(latencyMinMs, latencyMaxMs + 1);
		}
		if (ms > 0) {
			log.debug("Mock latency {} ms for order {}", ms, orderId);
			sleep(ms);
		}
	}

	private static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted during mock delay", e);
		}
	}

	public static class MockPaymentException extends RuntimeException {
		private final int httpStatus;

		public MockPaymentException(int httpStatus, String message) {
			super(message);
			this.httpStatus = httpStatus;
		}

		public int getHttpStatus() {
			return httpStatus;
		}
	}
}
