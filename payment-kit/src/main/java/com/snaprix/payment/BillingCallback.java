package com.snaprix.payment;

/**
 * Created by vladimirryabchikov on 3/5/15.
 */
public interface BillingCallback {
    // onFail(sku :String, e :Throwable)
    void onFail(String sku, Throwable e);
}