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
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Google Play Billing (library 8).
 * Non-consumable: "remove_ads" (and later hero unlocks).
 * Consumable gem packs: "gems_s", "gems_m", "gems_l", "gems_xl" - consumed right after purchase; the gems are kept
 * in SharedPreferences until the game collects them with takeGems(), so nothing is lost if the page reloads.
 * The game is told with window.__onStore(adFree, price, fresh) and window.__onGems().
 */
class BillingManager implements PurchasesUpdatedListener {
    static final String PRODUCT_ID = "remove_ads";
    static final String[] NON_CONSUMABLE = {"remove_ads"};
    static final Map<String, Integer> GEMS = new HashMap<>();
    static {
        GEMS.put("gems_s", 100);
        GEMS.put("gems_m", 300);
        GEMS.put("gems_l", 700);
        GEMS.put("gems_xl", 1600);
    }

    private final Activity activity;
    private final MainActivity.JsSink sink;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BillingClient client;
    private volatile boolean adFree;
    private final Map<String, ProductDetails> details = new HashMap<>();
    private final Map<String, String> prices = new HashMap<>();
    private final Set<String> consuming = new HashSet<>();
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
        String p = prices.get(PRODUCT_ID);
        return p == null ? "" : p;
    }

    synchronized String pricesJson() {
        return new JSONObject(prices).toString();
    }

    /** Gems bought but not yet given to the game. Returns them once and resets the counter. */
    synchronized int takeGems() {
        int n = prefs.getInt("pending_gems", 0);
        if (n > 0) prefs.edit().putInt("pending_gems", 0).apply();
        return n;
    }

    private synchronized void addPendingGems(int n) {
        prefs.edit().putInt("pending_gems", prefs.getInt("pending_gems", 0) + n).apply();
    }

    private void connect() {
        if (connecting || client.isReady()) return;
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                connecting = false;
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProducts();
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

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> list = new ArrayList<>();
        for (String id : NON_CONSUMABLE) list.add(product(id));
        for (String id : GEMS.keySet()) list.add(product(id));
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build(), (result, res) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            List<ProductDetails> got = res.getProductDetailsList();
            if (got == null) return;
            synchronized (this) {
                for (ProductDetails d : got) {
                    details.put(d.getProductId(), d);
                    ProductDetails.OneTimePurchaseOfferDetails offer = d.getOneTimePurchaseOfferDetails();
                    if (offer != null) prices.put(d.getProductId(), offer.getFormattedPrice());
                }
            }
            notifyGame(false);
        });
    }

    private static QueryProductDetailsParams.Product product(String id) {
        return QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.INAPP).build();
    }

    private void queryOwned() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (result, purchases) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    boolean owned = false;
                    for (Purchase p : purchases) {
                        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                        if (p.getProducts().contains(PRODUCT_ID)) {
                            owned = true;
                            acknowledge(p);
                        }
                        grantGems(p); // a gem pack that was paid but never consumed (e.g. app closed)
                    }
                    setAdFree(owned, false);
                });
    }

    void buyRemoveAds() {
        buy(PRODUCT_ID);
    }

    void buy(String id) {
        if (PRODUCT_ID.equals(id) && adFree) {
            notifyGame(false);
            return;
        }
        ProductDetails d;
        synchronized (this) {
            d = details.get(id);
        }
        if (d == null) {
            connect();
            return;
        }
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
                .build();
        client.launchBillingFlow(activity, params);
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, @Nullable List<Purchase> purchases) {
        int code = result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) {
                if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                if (p.getProducts().contains(PRODUCT_ID)) {
                    acknowledge(p);
                    setAdFree(true, true);
                }
                grantGems(p);
            }
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryOwned();
        }
    }

    /** Consume a gem pack, then store its gems for the game. */
    private void grantGems(Purchase p) {
        int n = 0;
        for (String id : p.getProducts()) {
            Integer g = GEMS.get(id);
            if (g != null) n += g * Math.max(1, p.getQuantity());
        }
        if (n == 0) return;
        final String token = p.getPurchaseToken();
        synchronized (this) {
            if (!consuming.add(token)) return;
        }
        final int gems = n;
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build(), (r, t) -> {
            if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                addPendingGems(gems);
                sink.js("window.__onGems&&__onGems()");
            } else {
                synchronized (BillingManager.this) {
                    consuming.remove(token);
                }
            }
        });
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
        sink.js("window.__onStore&&__onStore(" + adFree + "," + MainActivity.quote(price()) + "," + fresh + ")");
    }

    void destroy() {
        handler.removeCallbacksAndMessages(null);
        if (client.isReady()) client.endConnection();
    }
}
