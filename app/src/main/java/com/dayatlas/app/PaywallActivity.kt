package com.dayatlas.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.android.billingclient.api.ProductDetails
import com.dayatlas.app.billing.PromoCode
import com.dayatlas.app.billing.SubscriptionManager
import com.dayatlas.app.databinding.ActivityPaywallBinding

/**
 * Blocking gate shown instead of [MainActivity] whenever the Play build
 * (see BuildConfig.PAYWALL_ENABLED) has no active subscription. Only ever
 * launched from MainActivity.onCreate/onStart - never the app's launcher
 * activity itself, so plain Back behaves like leaving the app.
 */
class PaywallActivity : DayAtlasActivity() {
    private lateinit var binding: ActivityPaywallBinding
    private var offer: ProductDetails? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (PromoCode.isActive(this)) {
            goToMain()
            return
        }

        binding.promoRedeemButton.setOnClickListener {
            val input = binding.promoCodeInput.text?.toString().orEmpty()
            if (PromoCode.redeem(this, input)) {
                Toast.makeText(this, R.string.paywall_promo_success, Toast.LENGTH_LONG).show()
                goToMain()
            } else {
                Toast.makeText(this, R.string.paywall_promo_invalid, Toast.LENGTH_SHORT).show()
            }
        }

        binding.subscribeButton.isEnabled = false
        binding.subscribeButton.setOnClickListener {
            offer?.let { SubscriptionManager.launchPurchase(this, it) }
        }
        binding.restoreButton.setOnClickListener { checkEntitlement(announceIfNotEntitled = true) }

        SubscriptionManager.purchaseListener = { entitled, errorMessage ->
            runOnUiThread {
                if (entitled) {
                    goToMain()
                } else if (errorMessage != null) {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        checkEntitlement(announceIfNotEntitled = false)
        loadOffer()
    }

    override fun onDestroy() {
        SubscriptionManager.purchaseListener = null
        super.onDestroy()
    }

    private fun checkEntitlement(announceIfNotEntitled: Boolean) {
        binding.progress.visibility = View.VISIBLE
        SubscriptionManager.refreshEntitlement(this) { entitled ->
            runOnUiThread {
                binding.progress.visibility = View.GONE
                if (entitled) {
                    goToMain()
                } else if (announceIfNotEntitled) {
                    Toast.makeText(this, R.string.paywall_no_active_subscription, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadOffer() {
        SubscriptionManager.queryOffer(this) { details ->
            runOnUiThread {
                offer = details
                binding.subscribeButton.isEnabled = details != null
                val price = details?.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.lastOrNull { it.priceAmountMicros > 0 }
                    ?.formattedPrice
                binding.description.text = if (price != null) {
                    getString(R.string.paywall_description, price)
                } else {
                    getString(R.string.paywall_description_fallback)
                }
            }
        }
    }

    private fun goToMain() {
        if (isFinishing) return
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
