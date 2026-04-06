package com.elvo.identity.security;

public interface TokenRevocationChecker {

    boolean isRevoked(String jti);
}
