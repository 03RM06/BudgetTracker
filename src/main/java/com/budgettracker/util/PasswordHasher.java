package com.budgettracker.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordHasher {

    private PasswordHasher() {}

    public static String hash(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt());
    }

    public static boolean verify(String plaintext, String hashed) {
        return BCrypt.checkpw(plaintext, hashed);
    }
}
