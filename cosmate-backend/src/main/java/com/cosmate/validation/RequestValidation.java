package com.cosmate.validation;

public final class RequestValidation {

    private RequestValidation() {
    }

    public static final String PHONE_REGEX = "^(?:\\+84|0)[0-9]{9,10}$";
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{6,128}$";
    public static final String USERNAME_REGEX = "^[A-Za-z0-9._-]{3,100}$";
    public static final String PAYMENT_METHOD_REGEX = "^(?i)(VNPay|MOMO|WALLET)$";
}
