/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ads.interactivemedia.v3.samples.videoplayerapp;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdProgressInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.CompanionAd;
import com.google.ads.interactivemedia.v3.api.CuePoint;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.StreamDisplayContainer;
import com.google.ads.interactivemedia.v3.api.StreamManager;
import com.google.ads.interactivemedia.v3.api.StreamRequest;
import com.google.ads.interactivemedia.v3.api.StreamRequest.StreamFormat;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.ads.interactivemedia.v3.api.player.VideoStreamPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer;
import com.google.ads.interactivemedia.v3.samples.samplevideoplayer.SampleVideoPlayer.SampleVideoPlayerCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class adds ad-serving support to Sample HlsVideoPlayer
 */
public class SampleAdsWrapper implements AdEvent.AdEventListener, AdErrorEvent.AdErrorListener,
        AdsLoader.AdsLoadedListener {

    // Live stream asset key.
    private static final String TEST_ASSET_KEY = "sN_IYUG8STe1ZzhIIE_ksA";

    // VOD HLS content source and video IDs.
    private static final String TEST_HLS_CONTENT_SOURCE_ID = "2490667";
    private static final String TEST_HLS_VIDEO_ID = "googleio-highlights";

    // VOD DASH content source and video IDs.
    private static final String TEST_DASH_CONTENT_SOURCE_ID = "2474148";
    private static final String TEST_DASH_VIDEO_ID = "bbb-clear";

    private static final String PLAYER_TYPE = "DAISamplePlayer";

    private enum ContentType {
        LIVE_HLS,
        VOD_HLS,
        VOD_DASH,
    }

    // Select a LIVE HLS stream. To play a VOD HLS stream or a VOD DASH stream, set CONTENT_TYPE to
    // the associated enum.
    private static final ContentType CONTENT_TYPE = ContentType.VOD_HLS;

    /**
     * Log interface, so we can output the log commands to the UI or similar.
     */
    public interface Logger {
        void log(String logMessage);
    }

    private ImaSdkFactory mSdkFactory;
    private AdsLoader mAdsLoader;
    private StreamDisplayContainer mDisplayContainer;
    private StreamManager mStreamManager;
    private List<VideoStreamPlayer.VideoStreamPlayerCallback> mPlayerCallbacks;

    private SampleVideoPlayer mVideoPlayer;
    private Context mContext;
    private ViewGroup mAdUiContainer;

    private String mFallbackUrl;
    private Logger mLogger;
    private InnovidAdWrapper mInteractiveAd;
    private WebView mWebView;
    private String mAdvertisingId;


    /**
     * Creates a new SampleAdsWrapper that implements IMA direct-ad-insertion.
     *
     * @param context       the app's context.
     * @param videoPlayer   underlying HLS video player.
     * @param adUiContainer ViewGroup in which to display the ad's UI.
     */
    public SampleAdsWrapper(Context context, SampleVideoPlayer videoPlayer,
                            ViewGroup adUiContainer, WebView webView) {
        mVideoPlayer = videoPlayer;
        mContext = context;
        mAdUiContainer = adUiContainer;
        mSdkFactory = ImaSdkFactory.getInstance();
        mPlayerCallbacks = new ArrayList<>();
        mWebView = webView;
        createAdsLoader();
    }

    public void releaseInteractiveAd() {
        disposeCurrentInteractiveAd();
    }

    @TargetApi(19)
    private void enableWebViewDebugging() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
    }

    private void createAdsLoader() {
        ImaSdkSettings settings = ImaSdkFactory.getInstance().createImaSdkSettings();
        // Change any settings as necessary here.
        settings.setPlayerType(PLAYER_TYPE);
        enableWebViewDebugging();
        mDisplayContainer = mSdkFactory.createStreamDisplayContainer();
        VideoStreamPlayer videoStreamPlayer = createVideoStreamPlayer();
        mVideoPlayer.setSampleVideoPlayerCallback(
                new SampleVideoPlayerCallback() {
                    @Override
                    public void onUserTextReceived(String userText) {
                        for (VideoStreamPlayer.VideoStreamPlayerCallback callback :
                                mPlayerCallbacks) {
                            callback.onUserTextReceived(userText);
                        }
                    }

                    @Override
                    public void onSeek(int windowIndex, long positionMs) {
                        // See if we would seek past an ad, and if so, jump back to it.
                        long newSeekPositionMs = positionMs;
                        if (mStreamManager != null) {
                            CuePoint prevCuePoint = mStreamManager.getPreviousCuePointForStreamTime(
                                    positionMs / 1000);
                            if (prevCuePoint != null && !prevCuePoint.isPlayed()) {
                                newSeekPositionMs = (long) (prevCuePoint.getStartTime() * 1000);
                            }
                        }
                        mVideoPlayer.seekTo(windowIndex, newSeekPositionMs);
                    }
                });
        mDisplayContainer.setVideoStreamPlayer(videoStreamPlayer);
        mDisplayContainer.setAdContainer(mAdUiContainer);
        mAdsLoader = mSdkFactory.createAdsLoader(mContext, settings, mDisplayContainer);
    }

    public void requestAndPlayAds() {
        mAdsLoader.addAdErrorListener(this);
        mAdsLoader.addAdsLoadedListener(this);
        mAdsLoader.requestStream(buildStreamRequest());
    }

    private StreamRequest buildStreamRequest() {
        StreamRequest request;
        switch (CONTENT_TYPE) {
            case LIVE_HLS:
                // Live HLS stream request.
                return mSdkFactory.createLiveStreamRequest(TEST_ASSET_KEY, null);
            case VOD_HLS:
                // VOD HLS request.
                request = mSdkFactory.createVodStreamRequest(
                        TEST_HLS_CONTENT_SOURCE_ID,
                        TEST_HLS_VIDEO_ID,
                        null); // apiKey
                request.setFormat(StreamFormat.HLS);
                return request;
            case VOD_DASH:
                // VOD DASH request.
                request = mSdkFactory.createVodStreamRequest(
                        TEST_DASH_CONTENT_SOURCE_ID,
                        TEST_DASH_VIDEO_ID,
                        null); // apiKey
                request.setFormat(StreamFormat.DASH);
                return request;
            default:
                // Content type not selected.
                return null;
        }
    }

    private VideoStreamPlayer createVideoStreamPlayer() {
        return new VideoStreamPlayer() {
            @Override
            public void loadUrl(String url, List<HashMap<String, String>> subtitles) {
                mVideoPlayer.setStreamUrl(url);
                mVideoPlayer.play();
            }

            @Override
            public int getVolume() {
                // Make the video player play at the current device volume.
                return 100;
            }

            @Override
            public void addCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                mPlayerCallbacks.add(videoStreamPlayerCallback);
            }

            @Override
            public void removeCallback(VideoStreamPlayerCallback videoStreamPlayerCallback) {
                mPlayerCallbacks.remove(videoStreamPlayerCallback);
            }

            @Override
            public void onAdBreakStarted() {
                // Disable player controls.
                mVideoPlayer.enableControls(false);
                log("Ad Break Started\n");
            }

            @Override
            public void onAdBreakEnded() {
                // Re-enable player controls.
                mVideoPlayer.enableControls(true);
                log("Ad Break Ended\n");
            }

            @Override
            public void onAdPeriodStarted() {
                log("Ad Period Started\n");
            }

            @Override
            public void onAdPeriodEnded() {
                log("Ad Period Ended\n");
            }

            @Override
            public void seek(long timeMs) {
                // An ad was skipped. Skip to the content time.
                mVideoPlayer.seekTo(timeMs);
                log("seek");
            }

            @Override
            public VideoProgressUpdate getContentProgress() {
                return new VideoProgressUpdate(
                    mVideoPlayer.getCurrentPositionPeriod(), mVideoPlayer.getDuration()
                );
            }
        };
    }

    /**
     * AdErrorListener implementation
     **/
    @Override
    public void onAdError(AdErrorEvent event) {
        log(String.format("Error: %s\n", event.getError().getMessage()));
        // play fallback URL.
        log("Playing fallback Url\n");
        mVideoPlayer.setStreamUrl(mFallbackUrl);
        mVideoPlayer.enableControls(true);
        mVideoPlayer.play();
    }

    /**
     * AdEventListener implementation
     **/
    @Override
    public void onAdEvent(AdEvent event) {
        switch (event.getType()) {
            case AD_PROGRESS:
                checkAndInjectAdProgressInfo(mStreamManager.getAdProgressInfo());
                break;
            case STARTED:
                checkAndStartInteractiveAd(event.getAd());
                break;
            case COMPLETED:
                checkAndStopInteractiveAd();
                break;
            default:
                describeAd(event);
        }
    }

    private void describeAd(AdEvent event) {
        Ad ad = event.getAd();

        if (ad != null) {
            AdPodInfo pod = ad.getAdPodInfo();
            boolean hasCompanions = false;

            try {
                hasCompanions = (ad.getCompanionAds() != null && ad.getCompanionAds().size() > 0);
            } catch (Exception e) {}

            log(String.format("Event: %s, Pod %s, Ad(%s, %s)-- has companions: %s ", event.getType(), pod.getPodIndex(), pod.getAdPosition(), pod.getTotalAds(), hasCompanions));
        } else {
            log(String.format("Event: %s\n", event.getType()));
        }
    }

    private void describeAdProgress(AdProgressInfo info) {
        log(String.format("Ad(%s, %s) -- %s ____ %s", info.getAdPosition(), info.getTotalAds(), info.getCurrentTime(), info.getDuration()));
    }

    /**
     * AdsLoadedListener implementation
     **/
    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent event) {
        mStreamManager = event.getStreamManager();
        mStreamManager.addAdErrorListener(this);
        mStreamManager.addAdEventListener(this);
        mStreamManager.init();
    }

    /**
     * Sets fallback URL in case ads stream fails.
     **/
    void setFallbackUrl(String url) {
        mFallbackUrl = url;
    }

    /**
     * Sets logger for displaying events to screen. Optional.
     **/
    void setLogger(Logger logger) {
        mLogger = logger;
    }

    void setAdvertisingId(String value) {
        mAdvertisingId = value;
    }

    private void log(String message) {
        if (mLogger != null) {
            mLogger.log(message);
        }
    }

    private void checkAndStartInteractiveAd(Ad ad) {
        disposeCurrentInteractiveAd();

        CompanionAd iAd = findInteractiveAdInfo(ad.getCompanionAds());

        if (iAd == null) {
            return;
        }

        // remove default ad position indicator
        mAdUiContainer.setVisibility(View.GONE);

        // create and start innovid ad
        mInteractiveAd = new InnovidAdWrapper(mWebView, iAd, mAdvertisingId);
        mInteractiveAd.setAdEventListener(new InnovidAdWrapper.InnovidAdEventListener() {
            @Override
            public void onInnovidAdEvent(InnovidAdWrapper.InnovidAdEventType type) {
                mLogger.log(String.format("onInnovidAdEvent(%s)", type));
            }
        });

        mInteractiveAd.setAdPlaybackRequestListener(new InnovidAdWrapper.InnovidAdPlaybackRequestListener() {
            private boolean mRestartRequested = false;

            @Override
            public void onPauseRequest() {
                mLogger.log("onPauseRequest()");

                mVideoPlayer.pause();
            }

            @Override
            public void onResumeRequest() {
                mLogger.log("onResumeRequest()");

                if (mRestartRequested) {
//                    mVideoPlayer.setVisibility(View.VISIBLE);
                }

                mVideoPlayer.resume();
            }

            @Override
            public void onStopAndRestartOnNextResumeRequest() {
                mLogger.log("onStopAndRestartOnNextResumeRequest()");

                mRestartRequested = true;

//                mVideoPlayer.stop();
//                mVideoPlayer.setVisibility(View.GONE);
            }
        });
        mInteractiveAd.start();
    }

    private void checkAndStopInteractiveAd() {
        if (mInteractiveAd == null) {
            return;
        }

        mInteractiveAd.requestStop();
        mInteractiveAd = null;
    }

    private void checkAndInjectAdProgressInfo(AdProgressInfo info) {
        if (mInteractiveAd != null && mVideoPlayer.getPlaybackState() != SampleVideoPlayer.STOPPED) {
            final InnovidAdWrapper.SSAIPlaybackState playbackState = mVideoPlayer.getPlaybackState() == SampleVideoPlayer.PLAYING
                    ? InnovidAdWrapper.SSAIPlaybackState.PLAYING
                    : InnovidAdWrapper.SSAIPlaybackState.PAUSED
            ;

            mInteractiveAd.injectPlaybackProgressInfo(
                    playbackState, info.getCurrentTime(), info.getDuration()
            );
        }
    }

    private void disposeCurrentInteractiveAd() {
        if (mInteractiveAd == null) {
            return;
        }

        mInteractiveAd.enforceStop();
        mInteractiveAd = null;
    }

    private CompanionAd findInteractiveAdInfo(List<CompanionAd> companionAds) {
        if (companionAds == null) {
            return null;
        }

        CompanionAd result = null;

        for (CompanionAd companionAd : companionAds) {
            if (isCompanionAdSupported( companionAd )) {
                result = companionAd;
                break;
            }
        }

        return result;
    }

    private static Boolean isCompanionAdSupported(CompanionAd companionAd) {
        String apiFramework = companionAd.getApiFramework();
        String url = companionAd.getResourceValue();

        return apiFramework != null
                && url != null
                && apiFramework.toLowerCase().equals("innovid")
                && (url.contains(".html?") || url.contains("tag/get.php?tag="))
        ;
    }
}
