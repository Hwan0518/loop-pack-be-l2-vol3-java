package com.loopers.queue.waitingqueue.infrastructure.auth;


import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.support.config.QueueTokenProperties;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;


/**
 * HMAC 기반 대기열 토큰 제공자
 * - 토큰 형식: {userId}:{expiryEpochMs}:{hmacHex}
 * - HMAC-SHA256 서명으로 무결성 보장
 * - I/O 없음 (CPU 연산만, 수 us)
 */
@Component
public class HmacQueueAuthProvider implements QueueAuthPort {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String TOKEN_DELIMITER = ":";
	private static final int TOKEN_PARTS_COUNT = 3;

	private final QueueTokenProperties properties;


	public HmacQueueAuthProvider(QueueTokenProperties properties) {
		this.properties = properties;
	}


	// 1. 서명 토큰 검증 + userId 추출 + 갱신된 토큰 발급 (단일 호출)
	@Override
	public ResolvedQueueToken resolveAndRefresh(String token) {
		Long userId = parseValidUserId(token);
		String refreshedToken = generateToken(userId);
		return new ResolvedQueueToken(userId, refreshedToken);
	}


	// 2. userId 기반 서명 토큰 생성
	@Override
	public String generateToken(Long userId) {
		long expiryEpochMs = System.currentTimeMillis() + (properties.tokenTtlSeconds() * 1000);
		String payload = userId + TOKEN_DELIMITER + expiryEpochMs;
		String hmacHex = computeHmac(payload);
		return payload + TOKEN_DELIMITER + hmacHex;
	}


	// 서명 토큰 파싱 + HMAC 검증 + 만료 확인 → userId 반환
	private Long parseValidUserId(String token) {
		// null/blank 검증
		if (token == null || token.isBlank()) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}

		// 토큰 파싱 (3파트: userId:expiryEpochMs:hmacHex)
		String[] parts = token.split(TOKEN_DELIMITER);
		if (parts.length != TOKEN_PARTS_COUNT) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}

		String userIdStr = parts[0];
		String expiryStr = parts[1];
		String hmacHex = parts[2];

		// HMAC 검증 (상수 시간 비교 — 타이밍 공격 방어)
		String expectedHmac = computeHmac(userIdStr + TOKEN_DELIMITER + expiryStr);
		if (!MessageDigest.isEqual(
			expectedHmac.getBytes(StandardCharsets.UTF_8),
			hmacHex.getBytes(StandardCharsets.UTF_8)
		)) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}

		// 숫자 필드 파싱 + 만료 확인
		try {
			long expiryEpochMs = Long.parseLong(expiryStr);
			if (System.currentTimeMillis() >= expiryEpochMs) {
				throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
			}
			return Long.parseLong(userIdStr);
		} catch (NumberFormatException e) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}
	}


	// 4. 진입 증명 생성 (enter 성공 시 발급)
	@Override
	public String generateEnterReceipt(Long userId) {
		long enterTimestamp = System.currentTimeMillis();
		String payload = "enter" + TOKEN_DELIMITER + userId + TOKEN_DELIMITER + enterTimestamp;
		String hmacHex = computeHmac(payload);
		return payload + TOKEN_DELIMITER + hmacHex;
	}


	// 5. 진입 증명 검증 (userId 반환, 무효/만료 시 null)
	@Override
	public Long resolveEnterReceipt(String receipt) {
		// null/blank 검증
		if (receipt == null || receipt.isBlank()) {
			return null;
		}

		// 파싱 (enter:{userId}:{enterTimestamp}:{hmacHex} = 4파트)
		String[] parts = receipt.split(TOKEN_DELIMITER);
		if (parts.length != 4 || !"enter".equals(parts[0])) {
			return null;
		}

		// HMAC 검증 (상수 시간 비교 — 타이밍 공격 방어)
		String payload = parts[0] + TOKEN_DELIMITER + parts[1] + TOKEN_DELIMITER + parts[2];
		String expectedHmac = computeHmac(payload);
		if (!MessageDigest.isEqual(
			expectedHmac.getBytes(StandardCharsets.UTF_8),
			parts[3].getBytes(StandardCharsets.UTF_8)
		)) {
			return null;
		}

		// 만료 확인 (enterTimestamp + tokenTtlSeconds > now)
		try {
			long enterTimestamp = Long.parseLong(parts[2]);
			long expiryMs = enterTimestamp + (properties.tokenTtlSeconds() * 1000);
			if (System.currentTimeMillis() >= expiryMs) {
				return null;
			}
			return Long.parseLong(parts[1]);
		} catch (NumberFormatException e) {
			return null;
		}
	}


	// HMAC-SHA256 계산
	private String computeHmac(String data) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			SecretKeySpec keySpec = new SecretKeySpec(
				properties.secretKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM
			);
			mac.init(keySpec);
			byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hmacBytes);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new CoreException(ErrorType.INTERNAL_ERROR, "HMAC 계산 실패");
		}
	}
}
