package com.survey.dto;

import lombok.Data;

@Data
public class SubmitResult {
    private boolean success;
    private String errorCode;
    private String errorMessage;

    public static SubmitResult success() {
        SubmitResult result = new SubmitResult();
        result.setSuccess(true);
        return result;
    }

    public static SubmitResult fail(String errorCode, String errorMessage) {
        SubmitResult result = new SubmitResult();
        result.setSuccess(false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }

    public static final String ERROR_NOT_FOUND = "NOT_FOUND";
    public static final String ERROR_NOT_ACTIVE = "NOT_ACTIVE";
    public static final String ERROR_EXPIRED = "EXPIRED";
    public static final String ERROR_RATE_LIMITED = "RATE_LIMITED";
    public static final String ERROR_ALREADY_SUBMITTED = "ALREADY_SUBMITTED";
    public static final String ERROR_PASSWORD_REQUIRED = "PASSWORD_REQUIRED";
    public static final String ERROR_PASSWORD_INVALID = "PASSWORD_INVALID";
    public static final String ERROR_QUOTA_FULL = "QUOTA_FULL";
}
