package com.snaprix.payment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.jetbrains.annotations.Nullable;
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

    private boolean mIsSetupInProgress;
    private boolean mIsSetupDone;

    @Nullable
    private QueryInventoryData mQueryInventoryData;
    @Nullable
    private PurchaseData mPurchaseData;

    private Context mContext;
    private OpenIabHelper mHelper;

    public OpenBilling(Context context) {
        mContext = context;
    }

    public void onCreate(Map<String, String> storeKeys){
        if (DEBUG) Log.v(TAG, "onCreate");

        // set up in-app billing
        // compute your public key and store it in base64EncodedPublicKey

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

        mIsSetupInProgress = true;
        mIsSetupDone = false;

        mHelper = new OpenIabHelper(mContext, builder.build());
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener(){
            @Override
            public void onIabSetupFinished(IabResult result) {
                mIsSetupInProgress = false;
                mIsSetupDone = result.isSuccess();

                if (mQueryInventoryData != null) {
                    if (mIsSetupDone){
                        checkSKU(mQueryInventoryData.mSku, mQueryInventoryData.mCallback);
                    } else {
                        checkSkuFailedOnSetup(mQueryInventoryData.mSku, mQueryInventoryData.mCallback);
                    }
                }

                if (mPurchaseData != null) {
                    if (mIsSetupDone) {
                        launchPurchaseFlow(mPurchaseData.mActivity, mPurchaseData.mSku, mPurchaseData.mCallback);
                    } else {
                        launchPurchaseFailedOnSetup(mPurchaseData.mSku, mPurchaseData.mCallback);
                    }
                }
            }
        });
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data){
        return mHelper.handleActivityResult(requestCode, resultCode, data);
    }

    public void onDestroy(){
        if (DEBUG) Log.v(TAG, "onDestroy");

        if (mHelper != null) mHelper.dispose();
        mHelper = null;

        mQueryInventoryData = null;
        mPurchaseData = null;
    }

    /**
     * retrieve SKUs from Store;
     */
    public void checkSKU(String sku, QueryInventoryCallback callback) {
        if (mHelper == null) {
            callback.onFailure(sku, new BillingException("checkSKU mHelper == null"));
            return;
        }

        if (mQueryInventoryData != null){
            /**
             * it's already in progress, wait for previous result,
             * and do not call callback methods
             */
            return;
        }

        mQueryInventoryData = new QueryInventoryData(sku, callback);

        if (mIsSetupDone) {
            List<String> additionalSkuList = new ArrayList<>();
            additionalSkuList.add(sku);
            mHelper.queryInventoryAsync(true, additionalSkuList, new QueryInventoryFinishedListener());
        } else {
            if (mIsSetupInProgress) {
                // wait for setup and try from setup callback
            } else {
                checkSkuFailedOnSetup(sku, callback);
            }
        }
    }

    public void launchPurchaseFlow(Activity activity, String sku, PurchaseFlowCallback callback) {
        if (mHelper == null) {
            callback.onFailure(sku, new BillingException("launchPurchaseFlow mHelper == null"));
            return;
        }

        if (mPurchaseData != null){
            /**
             * it's already in progress, wait for previous result,
             * and do not call callback methods
             */
            return;
        }

        mPurchaseData = new PurchaseData(activity, sku, callback);

        if (mIsSetupDone) {
            mHelper.launchPurchaseFlow(activity, sku, REQUEST_CODE_PURCHASE_FLOW, new IabPurchaseFinishedListener(), "extraData");
        } else {
            if (mIsSetupInProgress) {
                // wait for setup and try from setup callback
            } else {
                launchPurchaseFailedOnSetup(sku, callback);
            }
        }
    }

    private void checkSkuFailedOnSetup(String sku, QueryInventoryCallback callback){
        callback.onFailure(sku, new BillingException("failed to setup"));
        mQueryInventoryData = null;
    }

    private void launchPurchaseFailedOnSetup(String sku, PurchaseFlowCallback callback){
        callback.onFailure(sku, new BillingException("failed to setup"));
        mPurchaseData = null;
    }



    private static class QueryInventoryData{
        private final String mSku;
        private final QueryInventoryCallback mCallback;

        private QueryInventoryData(String sku, QueryInventoryCallback callback) {
            mSku = sku;
            mCallback = callback;
        }
    }

    private static class PurchaseData {
        private final Activity mActivity;
        private final String mSku;
        private final PurchaseFlowCallback mCallback;

        private PurchaseData(Activity activity, String sku, PurchaseFlowCallback callback) {
            mActivity = activity;
            mSku = sku;
            mCallback = callback;
        }
    }

    private class QueryInventoryFinishedListener implements IabHelper.QueryInventoryFinishedListener {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
            final String sku = mQueryInventoryData.mSku;
            final QueryInventoryCallback callback = mQueryInventoryData.mCallback;
            mQueryInventoryData = null;

            if (result.isFailure()) {
                callback.onFailure(sku, new BillingException(String.format("onQueryInventoryFinished result=%s", result)));
                return;
            }

            boolean isPurchased = inv.hasPurchase(sku);
            if (DEBUG) Log.v(TAG, String.format("onQueryInventoryFinished isPurchased=%b", isPurchased));

            callback.onQueryFinished(inv.getSkuDetails(sku), isPurchased);
        }
    }

    private class IabPurchaseFinishedListener implements IabHelper.OnIabPurchaseFinishedListener {
        @Override
        public void onIabPurchaseFinished(IabResult result, Purchase info) {
            final String sku = mPurchaseData.mSku;
            final PurchaseFlowCallback callback = mPurchaseData.mCallback;
            mPurchaseData = null;

            if (result.isFailure()) {
                callback.onFailure(sku, new BillingException(String.format("onIabPurchaseFinished result=%s", result)));
                return;
            }

            if (DEBUG) Log.d(TAG, "onIabPurchaseFinished " + "purchase " + info.toString());
            if (info.getSku().equals(sku)) {
                // give user access to premium content and update the UI
                if (DEBUG) Log.d(TAG, "onIabPurchaseFinished " + sku + " purchased!!!");

                callback.onPurchaseFinished(sku);
            } else {
                callback.onFailure(sku, new BillingException(String.format("onIabPurchaseFinished not purchased")));
            }
        }
    }
}