package com.elvo.accountmanagement.util;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class EanGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        int[] digits = new int[12];
        StringBuilder builder = new StringBuilder(13);
        for (int index = 0; index < digits.length; index++) {
            digits[index] = RANDOM.nextInt(10);
            builder.append(digits[index]);
        }
        builder.append(calculateCheckDigit(digits));
        return builder.toString();
    }

    private int calculateCheckDigit(int[] digits) {
        int sum = 0;
        for (int index = 0; index < digits.length; index++) {
            int digit = digits[index];
            sum += (index % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }
}
