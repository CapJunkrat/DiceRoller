package com.johnz.diceroller

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdMobHelper(private val context: Context) {
    private var rewardedAd: RewardedAd? = null
    // Ad Unit ID for Rewarded Ads
    private val adUnitId = "ca-app-pub-3993081078975241/8590330013"

    fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdMobHelper", adError.toString())
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d("AdMobHelper", "Ad was loaded.")
                rewardedAd = ad
                
                rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdClicked() {
                        // Called when a click is recorded for an ad.
                        Log.d("AdMobHelper", "Ad was clicked.")
                    }

                    override fun onAdDismissedFullScreenContent() {
                        // Called when ad is dismissed.
                        // Set the ad reference to null so you don't show the ad a second time.
                        Log.d("AdMobHelper", "Ad dismissed fullscreen content.")
                        rewardedAd = null
                        // Preload the next ad
                        loadRewardedAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                        // Called when ad fails to show.
                        Log.e("AdMobHelper", "Ad failed to show fullscreen content.")
                        rewardedAd = null
                    }

                    override fun onAdImpression() {
                        // Called when an impression is recorded for an ad.
                        Log.d("AdMobHelper", "Ad recorded an impression.")
                    }

                    override fun onAdShowedFullScreenContent() {
                        // Called when ad is shown.
                        Log.d("AdMobHelper", "Ad showed fullscreen content.")
                    }
                }
            }
        })
    }

    fun showRewardedAd(activity: Activity, onRewardEarned: (Int, String) -> Unit) {
        rewardedAd?.let { ad ->
            ad.show(activity, OnUserEarnedRewardListener { rewardItem ->
                // Handle the reward.
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d("AdMobHelper", "User earned the reward.")
                onRewardEarned(rewardAmount, rewardType)
            })
        } ?: run {
            Log.d("AdMobHelper", "The rewarded ad wasn't ready yet.")
            // Ideally, tell the user the ad isn't ready or try to load again
            loadRewardedAd()
        }
    }
    
    fun isAdReady(): Boolean {
        return rewardedAd != null
    }
}
