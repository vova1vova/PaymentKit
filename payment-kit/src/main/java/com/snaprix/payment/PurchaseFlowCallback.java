package com.snaprix.payment;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public interface PurchaseFlowCallback extends BillingCallback {
    // onPurchaseSuccess(sku :String)
    void onPurchaseSuccess(String sku);
    // onCancelByUser(sku :String)
    void onCancelByUser(String sku);
}