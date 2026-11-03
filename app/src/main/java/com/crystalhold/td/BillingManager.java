package com.crystalhold.td;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Google Play Billing (library 8) for one non-consumable product: "remove_ads".
 * Create a one-time product with exactly this ID in Play Console.
 * State is pushed to the game with window.__onStore(adFree, price, fresh).
 */
class BillingManager implements PurchasesUpdatedListener {
    static final String PRODUCT_ID = "remove_ads";

    private final Activity activity;
    private final MainActivity.JsSink sink;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BillingClient client;
    private volatile boolean adFree;
    private volatile String price = "";
    private ProductDetails product;
    private boolean connecting;

    BillingManager(Activity activity, MainActivity.JsSink sink) {
        this.activity = activity;
        this.sink = sink;
        this.prefs = activity.getSharedPreferences("store", Activity.MODE_PRIVATE);
        this.adFree = prefs.getBoolean("ad_free", false);
        this.client = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        connect();
        handler.postDelayed(() -> notifyGame(false), 1500);
    }

    boolean isAdFree() {
        return adFree;
    }

    String price() {
        return price;
    }

    private void connect() {
        if (connecting || client.isReady()) return;
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                connecting = false;
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProduct();
                    queryOwned();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connecting = false;
                handler.postDelayed(BillingManager.this::connect, 10_000);
            }
        });
    }

    void refresh() {
        if (client.isReady()) queryOwned();
        else connect();
    }

    private void queryProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()))
                .build();
        client.queryProductDetailsAsync(params, (result, details) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            List<ProductDetails> list = details.getProductDetailsList();
            if (list == null || list.isEmpty()) return;
            product = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offer = product.getOneTimePurchaseOfferDetails();
            if (offer != null) price = offer.getFormattedPrice();
            notifyGame(false);
        });
    }

    private void queryOwned() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (result, purchases) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    boolean owned = false;
                    for (Purchase p : purchases) {
                        if (p.getProducts().contains(PRODUCT_ID) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            owned = true;
                            acknowledge(p);
                        }
                    }
                    setAdFree(owned, false);
                });
    }

    void buyRemoveAds() {
        if (adFree) {
            notifyGame(false);
            return;
        }
        if (product == null) {
            connect();
            return;
        }
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
                .build();
        client.launchBillingFlow(activity, params);
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, @Nullable List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) {
                if (p.getProducts().contains(PRODUCT_ID) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    acknowledge(p);
                    setAdFree(true, true);
                }
            }
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryOwned();
        }
    }

    private void acknowledge(Purchase p) {
        if (p.isAcknowledged()) return;
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), r -> { });
    }

    private void setAdFree(boolean value, boolean fresh) {
        adFree = value;
        prefs.edit().putBoolean("ad_free", value).apply();
        notifyGame(fresh);
    }

    private void notifyGame(boolean fresh) {
        sink.js("window.__onStore&&__onStore(" + adFree + "," + MainActivity.quote(price) + "," + fresh + ")");
    }

    void destroy() {
        handler.removeCallbacksAndMessages(null);
        if (client.isReady()) client.endConnection();
    }
}
