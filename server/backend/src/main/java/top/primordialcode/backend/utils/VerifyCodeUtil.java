package top.primordialcode.backend.utils;

import java.security.SecureRandom;


public class VerifyCodeUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCode() {
        int code = RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

}