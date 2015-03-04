package com.snaprix.payment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.onepf.oms.OpenIabHelper;
import org.onepf.oms.appstore.googleUtils.IabHelper;
import org.onepf.oms.appstore.googleUtils.IabResult;
import org.onepf.oms.appstore.googleUtils.Inventory;
import org.onepf.oms.appstore.googleUtils.Purchase;
import org.onepf.oms.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public class OpenBilling {
    private static boolean DEBUG = false;
    public static void enableDebugLogging(boolean enabled) {
        DEBUG = enabled;
    }

    private static final String TAG = "OpenBilling";

    private static final int REQUEST_CODE_PURCHASE_FLOW = 100;

    private boolean mIsSetupDone;
    private boolean mQueryInProgress;

    private Context mContext;
    private OpenIabHelper mHelper;

    public OpenBilling(Context context) {
        mContext = context;
    }

    public void onCreate(Map<String, String> storeKeys){
        if (DEBUG) Log.v(TAG, "onCreate");

        // set up in-app billing
        // compute your public key and store it in base64EncodedPublicKey

        mIsSetupDone = false;
        mQueryInProgress = false;

        Logger.setLoggable(DEBUG);

        OpenIabHelper.Options.Builder builder = new OpenIabHelper.Options.Builder();
        builder.setVerifyMode(OpenIabHelper.Options.VERIFY_EVERYTHING);
        builder.addStoreKeys(storeKeys);

        // this code force to consider, that the app was installed from Google Play
        // the other way to customise installation source is via adb:
        // ./adb install -i store_package_to_test /path/to/apk
//        if (DEBUG) {
//            builder.setStoreSearchStrategy(OpenIabHelper.Options.SEARCH_STRATEGY_BEST_FIT);
//            builder.addPreferredStoreName(OpenIabHelper.NAME_GOOGLE);
//        }

        mHelper = new OpenIabHelper(mContext, builder.build());
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data){
        return mHelper.handleActivityResult(requestCode, resultCode, data);
    }

    public void onDestroy(){
        if (DEBUG) Log.v(TAG, "onDestroy");

        if (mHelper != null) mHelper.dispose();
        mHelper = null;

    }

    /**
     * retrieve SKUs from Store;
     */
    public void checkSKU(String sku, QueryInventoryCallback callback) {
        if (mHelper == null) {
            if (DEBUG) Log.w(TAG, "checkSKU mHelper == null");
            return;
        }

        if (DEBUG) Log.v(TAG, String.format("checkSKU sku=%s", sku));

        if (mIsSetupDone && !mQueryInProgress) {
            mQueryInProgress = true;

            List<String> additionalSkuList = new ArrayList<String>();
            additionalSkuList.add(sku);
            mHelper.queryInventoryAsync(true, additionalSkuList, new QueryInventoryFinishedListener(sku, callback));
        } else {
            if (!mIsSetupDone) {
                if (DEBUG) Log.d(TAG, "checkSKU " + "mHelper has not been setup");
                mHelper.startSetup(new IabSetupFinishedListener(sku, callback));
            }

            if (mQueryInProgress) {
                if (DEBUG) Log.w(TAG, "checkSKU " + "mHelper async in progress");
            }
        }
    }

    public void launchPurchaseFlow(Activity activity, String sku, PurchaseFlowCallback callback) {
        mHelper.launchPurchaseFlow(activity, sku, REQUEST_CODE_PURCHASE_FLOW, new IabPurchaseFinishedListener(sku, callback), "extraData");
    }




    private class IabSetupFinishedListener implements IabHelper.OnIabSetupFinishedListener {
        private final String mSku;
        private final QueryInventoryCallback mCallback;

        private IabSetupFinishedListener(String sku, QueryInventoryCallback callback) {
            mSku = sku;
            mCallback = callback;
        }

        @Override
        public void onIabSetupFinished(IabResult result) {
            if (result.isSuccess()) {
                mIsSetupDone = true;
                // Hooray, IAB is fully set up! try to check SKUs again
                checkSKU(mSku, mCallback);
            } else {
                // Oh noes, there was a problem.
                Log.e(TAG, "onIabSetupFinished Problem setting up In-app Billing: " + result);
                mCallback.onFailure(mSku);
            }
        }
    }

    private class QueryInventoryFinishedListener implements IabHelper.QueryInventoryFinishedListener {
        private final String mSku;
        private final QueryInventoryCallback mCallback;

        private QueryInventoryFinishedListener(String sku, QueryInventoryCallback callback) {
            mSku = sku;
            mCallback = callback;
        }

        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
            mQueryInProgress = false;

            if (result.isFailure()) {
                mCallback.onFailure(mSku);
                return;
            }

            boolean isPurchased = inv.hasPurchase(mSku);
            if (DEBUG) Log.v(TAG, String.format("onQueryInventoryFinished isPurchased=%b", isPurchased));

            mCallback.onQueryFinished(inv.getSkuDetails(mSku), isPurchased);
        }
    }

    private static class IabPurchaseFinishedListener implements IabHelper.OnIabPurchaseFinishedListener {
        private final String mSku;
        private final PurchaseFlowCallback mCallback;

        private IabPurchaseFinishedListener(String sku, PurchaseFlowCallback callback) {
            mSku = sku;
            mCallback = callback;
        }

        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase info) {
            if (result.isFailure()) {
                mCallback.onFailure(mSku);
                return;
            }

            if (DEBUG) Log.d(TAG, "onIabPurchaseFinished " + "purchase " + info.toString());
            if (info.getSku().equals(mSku)) {
                // give user access to premium content and update the UI
                if (DEBUG) Log.d(TAG, "onIabPurchaseFinished " + mSku + " purchased!!!");

                mCallback.onPurchaseFinished(mSku);
            }
        }
    }
}
