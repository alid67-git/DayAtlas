package com.dayatlas.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.dayatlas.app.prefs.AppPrefs

/**
 * Google Play Billing wrapper for the single subscription ([PRODUCT_ID])
 * that gates the Play build of DayAtlas (see BuildConfig.PAYWALL_ENABLED).
 *
 * The 7-day free trial is configured entirely on the Play Console side (as
 * the subscription's trial phase) - Play reports a trialing purchase as
 * PURCHASED just like a paid one, so there is no local trial-countdown
 * logic here: "entitled" simply means "Play currently reports an active
 * purchase of this product". That also means reinstalling the app, or
 * installing on a second device with the same Play account, correctly
 * restores access without any server of our own.
 */
object SubscriptionManager : PurchasesUpdatedListener {
    /** Must match the subscription product id created in Play Console. */
    const val PRODUCT_ID = "dayatlas_pro_monthly"

    /** Set by whichever screen is currently showing purchase UI. */
    var purchaseListener: ((entitled: Boolean, errorMessage: String?) -> Unit)? = null

    private var client: BillingClient? = null
    private var appContext: Context? = null

    private fun clientFor(context: Context): BillingClient {
        appContext = context.applicationContext
        val existing = client
        if (existing != null) return existing
        val created = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().build())
            .build()
        client = created
        return created
    }

    private fun ensureConnected(context: Context, onReady: (BillingClient) -> Unit) {
        val billingClient = clientFor(context)
        if (billingClient.isReady) {
            onReady(billingClient)
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onReady(billingClient)
                }
            }

            override fun onBillingServiceDisconnected() {
                // No-op: the next call reconnects lazily via ensureConnected.
            }
        })
    }

    /**
     * Re-checks Play for an active/trialing purchase of [PRODUCT_ID] and
     * updates the cached flag in [AppPrefs]. Safe to call often (app start,
     * resume) - this is a local Play Store query, not a network call to our
     * own server. On a connection error, the previously cached value is
     * kept rather than forced to false, so a brief Play Store hiccup never
     * locks out an already-paying subscriber.
     */
    fun refreshEntitlement(context: Context, onResult: (Boolean) -> Unit) {
        val prefs = AppPrefs(context)
        ensureConnected(context) { billingClient ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onResult(prefs.subscriptionActive)
                    return@queryPurchasesAsync
                }
                val active = purchases.filter { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                active.forEach { acknowledgeIfNeeded(billingClient, it) }
                prefs.subscriptionActive = active.isNotEmpty()
                prefs.subscriptionLastVerifiedMillis = System.currentTimeMillis()
                onResult(prefs.subscriptionActive)
            }
        }
    }

    /** Looks up the product's live price/trial details for the paywall UI. */
    fun queryOffer(context: Context, onResult: (ProductDetails?) -> Unit) {
        ensureConnected(context) { billingClient ->
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()
            // Billing Library 8.x wraps the result in QueryProductDetailsResult
            // (fetched + unfetched product lists) instead of returning a bare
            // List<ProductDetails> as 7.x did.
            billingClient.queryProductDetailsAsync(params) { result, queryProductDetailsResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onResult(queryProductDetailsResult.productDetailsList.firstOrNull())
                } else {
                    onResult(null)
                }
            }
        }
    }

    /** Launches Play's purchase sheet for the subscription's first (only) offer. */
    fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        ensureConnected(activity) { billingClient ->
            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    private fun acknowledgeIfNeeded(billingClient: BillingClient, purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { /* best-effort */ }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val context = appContext
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull { it.products.contains(PRODUCT_ID) }
                val entitled = purchase != null &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                if (entitled && purchase != null) {
                    client?.let { acknowledgeIfNeeded(it, purchase) }
                }
                if (context != null) {
                    val prefs = AppPrefs(context)
                    prefs.subscriptionActive = entitled
                    prefs.subscriptionLastVerifiedMillis = System.currentTimeMillis()
                }
                purchaseListener?.invoke(entitled, null)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseListener?.invoke(false, null)
            }
            else -> {
                purchaseListener?.invoke(false, result.debugMessage)
            }
        }
    }
}
