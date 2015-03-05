package com.snaprix.payment;

/**
 * Created by vladimirryabchikov on 3/5/15.
 */
public interface BillingCallback {
    // onFailure(sku :String, e :Throwable)
    void onFailure(String sku, Throwable e);
}
