package com.jilali.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.inject.Singleton;

@Singleton
public class PasswordHasher {

    private static final int COST = 12;

    public String hash(String plain) {
        return BCrypt.withDefaults().hashToString(COST, plain.toCharArray());
    }

    public boolean matches(String plain, String hashed) {
        return BCrypt.verifyer().verify(plain.toCharArray(), hashed).verified;
    }
}
