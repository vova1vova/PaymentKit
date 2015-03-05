package com.snaprix.payment;


import org.onepf.oms.appstore.googleUtils.SkuDetails;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public interface QueryInventoryCallback extends BillingCallback {
    // onFinishQuery(sku :SkuDetails, isPurchased :boolean)
    void onFinishQuery(SkuDetails sku, boolean isPurchased);
}