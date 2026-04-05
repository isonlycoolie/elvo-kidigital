package com.elvo.identity.security;

public interface SecurityHashingService {

    String hashPassword(String rawValue);

    boolean verifyPassword(String rawValue, String hashedValue);

    String hashEsp(String rawValue);

    boolean verifyEsp(String rawValue, String hashedValue);

    String hashOneTimeCode(String rawValue);

    boolean verifyOneTimeCode(String rawValue, String hashedValue);
}
