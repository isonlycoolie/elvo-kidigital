package com.elvo.identity.service.impl;

import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.elvo.identity.entity.VerificationOtp;
import com.elvo.identity.service.OtpRateLimitService;

@Service
public class RedisOtpRateLimitService implements OtpRateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final int verifyIpMax;
    private final Duration verifyIpWindow;
    private final int sendIpMax;
    private final Duration sendIpWindow;
    private final int sendDeviceMax;
    private final Duration sendDeviceWindow;
    private final String keyPrefix;

    public RedisOtpRateLimitService(
            StringRedisTemplate redisTemplate,
            @Value("${elvo.security.otp.rate-limit.verify-ip-max:10}") int verifyIpMax,
            @Value("${elvo.security.otp.rate-limit.verify-ip-window-minutes:15}") long verifyIpWindowMinutes,
            @Value("${elvo.security.otp.rate-limit.send-ip-max:20}") int sendIpMax,
            @Value("${elvo.security.otp.rate-limit.send-ip-window-minutes:60}") long sendIpWindowMinutes,
            @Value("${elvo.security.otp.rate-limit.send-device-max:10}") int sendDeviceMax,
            @Value("${elvo.security.otp.rate-limit.send-device-window-minutes:60}") long sendDeviceWindowMinutes,
            @Value("${elvo.security.otp.rate-limit.key-prefix:elvo:otp:rate:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.verifyIpMax = Math.max(1, verifyIpMax);
        this.verifyIpWindow = Duration.ofMinutes(Math.max(1, verifyIpWindowMinutes));
        this.sendIpMax = Math.max(1, sendIpMax);
        this.sendIpWindow = Duration.ofMinutes(Math.max(1, sendIpWindowMinutes));
        this.sendDeviceMax = Math.max(1, sendDeviceMax);
        this.sendDeviceWindow = Duration.ofMinutes(Math.max(1, sendDeviceWindowMinutes));
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void enforceSendLimit(VerificationOtp.Purpose purpose, String sourceIp, String deviceId) {
        enforce(purpose, "send", "ip", sourceIp, sendIpMax, sendIpWindow);
        enforce(purpose, "send", "device", deviceId, sendDeviceMax, sendDeviceWindow);
    }

    @Override
    public void enforceVerifyLimit(VerificationOtp.Purpose purpose, String sourceIp, String deviceId) {
        enforce(purpose, "verify", "ip", sourceIp, verifyIpMax, verifyIpWindow);
        enforce(purpose, "verify", "device", deviceId, sendDeviceMax, sendDeviceWindow);
    }

    private void enforce(VerificationOtp.Purpose purpose,
                         String flow,
                         String dimension,
                         String value,
                         int limit,
                         Duration window) {
        if (value == null || value.isBlank()) {
            return;
        }

        String key = keyPrefix + purpose.name().toLowerCase(Locale.ROOT) + ":" + flow + ":" + dimension + ":" + value.trim().toLowerCase(Locale.ROOT);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }

        if (count != null && count > limit) {
            throw new IllegalStateException("OTP rate limit exceeded");
        }
    }
}
