package com.elvo.wallet.security;

public interface UserTokenRevocationChecker {

    boolean isRevoked(String jti);
}
