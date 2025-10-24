package com.ssolstice.camera.manual.ad

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardedAdManager(private val activity: Activity) {
    private var rewardedAd: RewardedAd? = null

    fun load(adUnitId: String) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(activity, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
            }
        })
    }

    fun show(onReward: (RewardItem) -> Unit) {
        rewardedAd?.show(activity) { reward ->
            onReward(reward)
        }
    }

    fun isReady(): Boolean = rewardedAd != null
}
