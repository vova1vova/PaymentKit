package com.snaprix.payment;

/**
 * general exception
 *
 * @author vladimirryabchikov
 */
public class BillingException extends Exception {
    /**
     *
     */
    private static final long serialVersionUID = -7352515053643564485L;

    public BillingException(String detailedMessage) {
        super(detailedMessage);
    }

    public BillingException(String detailedMessage, Throwable throwable) {
        super(detailedMessage, throwable);
    }
}
