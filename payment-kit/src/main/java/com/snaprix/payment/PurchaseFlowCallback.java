package com.snaprix.payment;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public interface PurchaseFlowCallback {
    void onPurchaseFinished(String sku);
    void onFailure(String sku, Throwable e);
}