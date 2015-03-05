package com.snaprix.payment;


import org.onepf.oms.appstore.googleUtils.SkuDetails;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public interface QueryInventoryCallback extends BillingCallback {
    // onQueryFinished(sku :SkuDetails, isPurchased :boolean)
    void onQueryFinished(SkuDetails sku, boolean isPurchased);
}