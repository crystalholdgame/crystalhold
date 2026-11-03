package com.crystalhold.td;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consent (UMP, required in the EEA incl. Norway) + AdMob rewarded and interstitial ads.
 * Results are reported back to the game with window.__onRewarded(tag, ok) and window.__onInterDone().
 */
class AdsManager {
    private final Activity activity;
    private final MainActivity.JsSink sink;
    private final MainActivity.AdFreeCheck adFree;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ConsentInformation consent;
    private RewardedAd rewarded;
    private InterstitialAd interstitial;
    private boolean loadingRewarded, loadingInterstitial;

    AdsManager(Activity activity, MainActivity.JsSink sink, MainActivity.AdFreeCheck adFree) {
        this.activity = activity;
        this.sink = sink;
        this.adFree = adFree;
    }

    void start() {
        consent = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    if (consent.canRequestAds()) initAds();
                    sink.js("window.renderStoreBtns&&renderStoreBtns()");
                }),
                requestError -> {
                    if (consent.canRequestAds()) initAds();
                });
        if (consent.canRequestAds()) initAds();
    }

    private void initAds() {
        if (started.getAndSet(true)) return;
        new Thread(() -> MobileAds.initialize(activity, status -> handler.post(() -> {
            loadRewarded();
            loadInterstitial();
        }))).start();
    }

    boolean privacyOptionsRequired() {
        return consent != null && consent.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    void showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (consent.canRequestAds()) initAds();
        });
    }

    private void loadRewarded() {
        if (!started.get() || loadingRewarded || rewarded != null) return;
        loadingRewarded = true;
        RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED, new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                rewarded = ad;
                loadingRewarded = false;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                rewarded = null;
                loadingRewarded = false;
                handler.postDelayed(AdsManager.this::loadRewarded, 30_000);
            }
        });
    }

    private void loadInterstitial() {
        if (!started.get() || adFree.isAdFree() || loadingInterstitial || interstitial != null) return;
        loadingInterstitial = true;
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd ad) {
                interstitial = ad;
                loadingInterstitial = false;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                interstitial = null;
                loadingInterstitial = false;
                handler.postDelayed(AdsManager.this::loadInterstitial, 60_000);
            }
        });
    }

    void showRewarded(String rawTag) {
        final String tag = rawTag == null ? "" : rawTag.replaceAll("[^a-z]", "");
        if (rewarded == null) {
            reportReward(tag, false);
            loadRewarded();
            return;
        }
        final boolean[] earned = {false};
        RewardedAd ad = rewarded;
        rewarded = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                reportReward(tag, earned[0]);
                loadRewarded();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                reportReward(tag, false);
                loadRewarded();
            }
        });
        ad.show(activity, rewardItem -> earned[0] = true);
    }

    void showInterstitial() {
        if (adFree.isAdFree() || interstitial == null) {
            sink.js("window.__onInterDone&&__onInterDone()");
            loadInterstitial();
            return;
        }
        InterstitialAd ad = interstitial;
        interstitial = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                sink.js("window.__onInterDone&&__onInterDone()");
                loadInterstitial();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                sink.js("window.__onInterDone&&__onInterDone()");
                loadInterstitial();
            }
        });
        ad.show(activity);
    }

    private void reportReward(String tag, boolean ok) {
        sink.js("window.__onRewarded&&__onRewarded('" + tag + "'," + ok + ")");
    }
}
