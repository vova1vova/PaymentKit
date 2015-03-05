package com.snaprix.payment;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public interface PurchaseFlowCallback extends BillingCallback {
    // onPurchaseFinished(sku :String)
    void onPurchaseFinished(String sku);
}