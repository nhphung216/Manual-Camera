package com.ssolstice.camera.manual.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.billingclient.api.*;
import com.ssolstice.camera.manual.MainActivity;
import com.ssolstice.camera.manual.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BillingManager implements PurchasesUpdatedListener {

    private static final String TAG = "BillingManager";

    public static final String PREF_BILLING_NAME = "billing_prefs";
    public static final String PREF_PREMIUM_KEY = "user_is_premium";

    private final BillingClient billingClient;
    private final Context context;
    private final Runnable onShowCongratulation;
    private final Runnable onConnectionSuccess;
    private final Runnable onFailed;

    private final Map<String, ProductDetails> productDetailsMap = new HashMap<>();

    public static final long ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000;
    public static final long ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000;
    public static final long FOREVER = 4102444800000L;

    public static class BillingProduct {
        public String productId;
        public String title;
        public String description;
        public String price;
        public String productType;
        public String offerToken;

        public BillingProduct(String productId, String title, String description, String price, String productType, String offerToken) {
            this.productId = productId;
            this.title = title;
            this.description = description;
            this.price = price;
            this.productType = productType;
            this.offerToken = offerToken;
        }
    }

    public static class ActiveSubscription {
        public String productId;
        public long expiryTime;

        public ActiveSubscription(String productId, long expiryTime) {
            this.productId = productId;
            this.expiryTime = expiryTime;
        }
    }

    public BillingManager(Context context, Runnable onShowCongratulation, Runnable onConnectionSuccess, Runnable onFailed) {
        this.context = context;
        this.onShowCongratulation = onShowCongratulation;
        this.onConnectionSuccess = onConnectionSuccess;
        this.onFailed = onFailed;
        this.billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build();
        startConnection();
    }

    private void startConnection() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingServiceDisconnected() {
                Log.e(TAG, "Billing disconnected");
            }

            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    onConnectionSuccess.run();
                }
            }
        });
    }

    private BillingProduct toBillingProduct(ProductDetails details) {
        String offerToken = details.getSubscriptionOfferDetails() != null && !details.getSubscriptionOfferDetails().isEmpty()
                ? details.getSubscriptionOfferDetails().get(0).getOfferToken()
                : null;

        String price = (details.getSubscriptionOfferDetails() != null && !details.getSubscriptionOfferDetails().isEmpty())
                ? details.getSubscriptionOfferDetails().get(0).getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice()
                : (details.getOneTimePurchaseOfferDetails() != null
                ? details.getOneTimePurchaseOfferDetails().getFormattedPrice()
                : "N/A");

        return new BillingProduct(
                details.getProductId(),
                details.getName(),
                details.getDescription(),
                price,
                details.getProductType(),
                offerToken
        );
    }

    public void queryProducts(OnProductsQueried listener) {
        List<BillingProduct> allProducts = new ArrayList<>();
        List<QueryProductDetailsParams.Product> inApps = new ArrayList<>();
        inApps.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId("one_time_purchase")
                .setProductType(BillingClient.ProductType.INAPP)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(inApps)
                .build();

        billingClient.queryProductDetailsAsync(params, (result, list) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (ProductDetails pd : list) {
                    allProducts.add(toBillingProduct(pd));
                    productDetailsMap.put(pd.getProductId(), pd);
                }
                listener.onResult(allProducts);
            } else {
                Log.e(TAG, "In-app query failed: " + result.getDebugMessage());
            }
        });
    }

    public void doUpgrade(MainActivity activity) {
        launchPurchaseFlow(activity, "one_time_purchase");
    }

    public void launchPurchaseFlow(Activity activity, String productId) {
        ProductDetails details = productDetailsMap.get(productId);
        if (details == null) {
            Log.e(TAG, "ProductDetails not found for: " + productId);
            Toast.makeText(activity, activity.getString(R.string.msg_upgrade_not_support), Toast.LENGTH_SHORT).show();
            return;
        }

        String offerToken = details.getSubscriptionOfferDetails() != null && !details.getSubscriptionOfferDetails().isEmpty()
                ? details.getSubscriptionOfferDetails().get(0).getOfferToken()
                : null;

        BillingFlowParams.ProductDetailsParams.Builder paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details);

        if (offerToken != null) {
            paramsBuilder.setOfferToken(offerToken);
        }

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(paramsBuilder.build()))
                .build();

        billingClient.launchBillingFlow(activity, flowParams);
    }

    public long getExpiryTime(Purchase purchase) {
        String productId = purchase.getProducts().isEmpty() ? "" : purchase.getProducts().get(0);
        return switch (productId) {
            case "sub_monthly" -> purchase.getPurchaseTime() + ONE_MONTH_MS;
            case "premium_yearly" -> purchase.getPurchaseTime() + ONE_YEAR_MS;
            case "one_time_purchase" -> FOREVER;
            default -> 0L;
        };
    }

    public void queryAllPurchases(OnActiveSubscriptionListener listener) {
        QueryPurchasesParams inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        QueryPurchasesParams subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClient.queryPurchasesAsync(inAppParams, (res1, list1) -> {
            billingClient.queryPurchasesAsync(subsParams, (res2, list2) -> {
                List<Purchase> all = new ArrayList<>();
                if (res1.getResponseCode() == BillingClient.BillingResponseCode.OK)
                    all.addAll(list1);
                if (res2.getResponseCode() == BillingClient.BillingResponseCode.OK)
                    all.addAll(list2);

                handlePurchases(all, listener);
            });
        });
    }

    private void handlePurchases(List<Purchase> purchases, OnActiveSubscriptionListener listener) {
        SharedPreferences billingPrefs = context.getSharedPreferences(PREF_BILLING_NAME, Context.MODE_PRIVATE);
        if (purchases.isEmpty()) {
            billingPrefs.edit().putBoolean(PREF_PREMIUM_KEY, false).apply();
            listener.onResult(null);
            return;
        }

        long now = System.currentTimeMillis();
        Purchase active = null;
        for (Purchase p : purchases) {
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED && now < getExpiryTime(p)) {
                if (active == null || getExpiryTime(p) > getExpiryTime(active)) {
                    active = p;
                }
            }
        }

        if (active != null) {
            billingPrefs.edit().putBoolean(PREF_PREMIUM_KEY, true).apply();
            listener.onResult(new ActiveSubscription(active.getProducts().get(0), getExpiryTime(active)));
        } else {
            billingPrefs.edit().putBoolean(PREF_PREMIUM_KEY, false).apply();
            listener.onResult(null);
        }

        for (Purchase p : purchases) {
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                if (!p.isAcknowledged()) {
                    billingClient.acknowledgePurchase(
                            AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(p.getPurchaseToken())
                                    .build(),
                            result -> {
                                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {

                                }
                            });
                }
            }
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        SharedPreferences billingPrefs = context.getSharedPreferences(PREF_BILLING_NAME, Context.MODE_PRIVATE);
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) {
                if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    if (!p.isAcknowledged()) {
                        billingClient.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(p.getPurchaseToken())
                                        .build(),
                                ackResult -> {
                                    if (ackResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                        billingPrefs.edit().putBoolean(PREF_PREMIUM_KEY, true).apply();
                                        onShowCongratulation.run();
                                    }
                                });
                    } else {
                        billingPrefs.edit().putBoolean(PREF_PREMIUM_KEY, true).apply();
                        onShowCongratulation.run();
                    }
                }
            }
        } else if (result.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.e(TAG, "Purchase failed: " + result.getDebugMessage());
            onFailed.run();
        } else {
            onFailed.run();
        }
    }

    public void endConnection() {
        Log.d(TAG, "endConnection");
        billingClient.endConnection();
    }

    public interface OnProductsQueried {
        void onResult(List<BillingProduct> products);
    }

    public interface OnActiveSubscriptionListener {
        void onResult(ActiveSubscription active);
    }
}