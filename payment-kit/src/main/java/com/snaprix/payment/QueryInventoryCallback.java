package com.snaprix.payment;


import org.onepf.oms.appstore.googleUtils.SkuDetails;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public interface QueryInventoryCallback {
    void onQueryFinished(SkuDetails sku, boolean isPurchased);
    void onFailure(String sku, Throwable e);
}