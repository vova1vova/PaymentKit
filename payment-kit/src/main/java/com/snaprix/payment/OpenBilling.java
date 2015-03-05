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
import org.onepf.oms.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by vladimirryabchikov on 7/10/14.
 */
public class OpenBilling {
    private static boolean SHOW_LOGS = false;
    public static void enableDebugLogging(boolean enabled) {
        SHOW_LOGS = enabled;
    }

    private static final String TAG = "OpenBilling";

    private static final int REQUEST_CODE_PURCHASE_FLOW = 100;

    private boolean mIsSetupInProgress;
    private boolean mIsSetupDone;

    @Nullable
    private QueryInventory mQueryInventory;
    @Nullable
    private Purchase mPurchase;

    private Context mContext;
    private OpenIabHelper mHelper;

    public OpenBilling(Context context) {
        mContext = context;
    }

    /**
     *
     * onCreate(storeKeys :Map<String, String>)
     *
     * @param storeKeys
     */
    public void onCreate(Map<String, String> storeKeys){
        if (SHOW_LOGS) Log.v(TAG, "onCreate");

        // set up in-app billing
        // compute your public key and store it in base64EncodedPublicKey

        Logger.setLoggable(SHOW_LOGS);

        OpenIabHelper.Options.Builder builder = new OpenIabHelper.Options.Builder();
        builder.setVerifyMode(OpenIabHelper.Options.VERIFY_EVERYTHING);
        builder.addStoreKeys(storeKeys);

        // this code force to consider, that the app was installed from Google Play
        // the other way to customise installation source is via adb:
        // ./adb install -i store_package_to_test /path/to/apk
//        if (SHOW_LOGS) {
//            builder.setStoreSearchStrategy(OpenIabHelper.Options.SEARCH_STRATEGY_BEST_FIT);
//            builder.addPreferredStoreName(OpenIabHelper.NAME_GOOGLE);
//        }

        mIsSetupInProgress = true;
        mIsSetupDone = false;

        mHelper = new OpenIabHelper(mContext, builder.build());
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener(){
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (SHOW_LOGS) Log.v(TAG, String.format("onIabSetupFinished result=%b hasQuery=%b hasPurchase=%b",
                        result.isSuccess(), mQueryInventory != null, mPurchase != null));

                mIsSetupInProgress = false;
                mIsSetupDone = result.isSuccess();

                // if it's called after onDestroy mQueryInventory and mPurchase should be set to null

                if (mQueryInventory != null) {
                    if (mIsSetupDone){
                        mQueryInventory.run();
                    } else {
                        mQueryInventory.failedToSetup();
                    }
                }

                if (mPurchase != null) {
                    if (mIsSetupDone) {
                        mPurchase.run();
                    } else {
                        mPurchase.failedToSetup();
                    }
                }
            }
        });
    }

    /**
     *
     * handleActivityResult(requestCode :int, resultCode :int, data :Intent)
     *
     * @param requestCode
     * @param resultCode
     * @param data
     * @return
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data){
        return mHelper.handleActivityResult(requestCode, resultCode, data);
    }

    /**
     * onDestroy()
     */
    public void onDestroy(){
        if (SHOW_LOGS) Log.v(TAG, "onDestroy");

        mQueryInventory = null;
        mPurchase = null;

        mIsSetupDone = false;

        if (mHelper != null) mHelper.dispose();
        mHelper = null;
    }

    /**
     * retrieve SKUs from Store
     *
     * checkSKU(sku :String, callback :QueryInventoryCallback)
     */
    public void checkSKU(String sku, QueryInventoryCallback callback) {
        // if it's called after onDestroy mIsSetupDone should be set to false

        if (mQueryInventory != null){
            /**
             * it's already in progress, wait for previous result,
             * and do not call callback methods
             */
            return;
        }

        mQueryInventory = new QueryInventory(sku, mHelper, callback, new Completion() {
            @Override
            public void onFinished() {
                mQueryInventory = null;
            }
        });

        if (mIsSetupDone) {
            mQueryInventory.run();
        } else {
            if (mIsSetupInProgress) {
                // wait for setup and try from setup callback
            } else {
                mQueryInventory.failedToSetup();
            }
        }
    }

    /**
     *
     * launchPurchaseFlow(activity :Activity, sku :String, callback :PurchaseFlowCallback)
     *
     * @param activity
     * @param sku
     * @param callback
     */
    public void launchPurchaseFlow(Activity activity, String sku, PurchaseFlowCallback callback) {
        // if it's called after onDestroy mIsSetupDone should be set to false
        
        if (mPurchase != null){
            /**
             * it's already in progress, wait for previous result,
             * and do not call callback methods
             */
            return;
        }

        mPurchase = new Purchase(sku, mHelper, activity, callback, new Completion() {
            @Override
            public void onFinished() {
                mPurchase = null;
            }
        });

        if (mIsSetupDone) {
            mPurchase.run();
        } else {
            if (mIsSetupInProgress) {
                // wait for setup and try from setup callback
            } else {
                mPurchase.failedToSetup();
            }
        }
    }



    private static interface Completion {
        void onFinished();
    }

    private static abstract class BillingRunnable implements Runnable{
        protected final String mSku;
        protected final OpenIabHelper mHelper;
        private final BillingCallback mCallback;
        private final Completion mCompletion;

        private BillingRunnable(String sku, OpenIabHelper helper, BillingCallback callback, Completion completion) {
            mSku = sku;
            mHelper = helper;
            mCallback = callback;
            mCompletion = completion;
        }

        public void failedToSetup(){
            failed(new BillingException("failed to setup"));
        }

        public void failed(Throwable e){
            mCallback.onFail(mSku, e);
            finished();
        }

        protected void finished(){
            mCompletion.onFinished();
        }
    }

    private static class QueryInventory extends BillingRunnable{
        /**
         * separate instance variable to check type right into constructor
         */
        private final QueryInventoryCallback mCallback;

        private QueryInventory(String sku, OpenIabHelper helper, QueryInventoryCallback callback, Completion completion) {
            super(sku, helper, callback, completion);

            mCallback = callback;
        }

        @Override
        public void run() {
            List<String> additionalSkuList = new ArrayList<>();
            additionalSkuList.add(mSku);

            mHelper.queryInventoryAsync(true, additionalSkuList, new IabHelper.QueryInventoryFinishedListener(){

                @Override
                public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                    if (result.isFailure()) {
                        failed(new BillingException(String.format("onQueryInventoryFinished result=%s", result)));
                        return;
                    }

                    boolean isPurchased = inv.hasPurchase(mSku);
                    if (SHOW_LOGS) Log.v(TAG, String.format("onQueryInventoryFinished isPurchased=%b", isPurchased));

                    mCallback.onFinishQuery(inv.getSkuDetails(mSku), isPurchased);
                    finished();
                }
            });
        }
    }

    private static class Purchase extends BillingRunnable {
        private final Activity mActivity;
        /**
         * separate instance variable to check type right into constructor
         */
        private final PurchaseFlowCallback mCallback;

        private Purchase(String sku, OpenIabHelper helper, Activity activity, PurchaseFlowCallback callback, Completion completion) {
            super(sku, helper, callback, completion);
            mActivity = activity;
            mCallback = callback;
        }

        @Override
        public void run() {
            mHelper.launchPurchaseFlow(mActivity, mSku, REQUEST_CODE_PURCHASE_FLOW, new IabHelper.OnIabPurchaseFinishedListener(){

                @Override
                public void onIabPurchaseFinished(IabResult result, org.onepf.oms.appstore.googleUtils.Purchase info) {
                    if (SHOW_LOGS) Log.v(TAG, String.format("onIabPurchaseFinished result=%s info=%s", result, info));

                    if (result.isFailure()) {
                        if (result.getResponse() == IabHelper.IABHELPER_USER_CANCELLED){
                            mCallback.onCancelByUser(mSku);
                            finished();
                        } else {
                            failed(new BillingException(String.format("onIabPurchaseFinished result=%s", result)));
                        }

                        return;
                    }

                    if (info.getSku().equals(mSku)) {
                        // give user access to premium content and update the UI
                        if (SHOW_LOGS) Log.d(TAG, "onIabPurchaseFinished " + mSku + " purchased!!!");

                        mCallback.onPurchaseSuccess(mSku);
                        finished();
                    } else {
                        failed(new BillingException(String.format("onIabPurchaseFinished not purchased")));
                    }
                }
            }, "extraData");
        }
    }
}