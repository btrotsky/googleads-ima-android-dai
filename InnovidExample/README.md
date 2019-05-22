# Overview

This project contains sample source code that demonstrates how to integrate the Innovid iRoll 
with the Google IMA DAI SDK in Android. This document will step through the various pieces of code 
that make the integration work, so that the same basic ideas can be replicated in a real production app.

# Flow

## 1. Listen to IMA SDK `AdEvent.STARTED` event
    [Example][listen_ima_events link]
    
## 2. Look for Innovid companions for a given ad
    In the handler method for IMA `AdEvent.STARTED` event, we inspect `Ad#companions` property. 
    If any companion has an `apiFramework` value matching `innovid`, then we ignore all other companions and begin Innovid iRoll™.

    [Example][parse_ad_info link]

## 3. Initialize and start the renderer
    Once we have the **tag url**, we can initialize the innovid ads wrapper [Example][instantiate_iroll link],
    and set playback listener and ad events listener.
    

## 4. Respond to renderer `playback-request` events
    [Example][handle_playback_request link]


[listen_ima_events link]: app/main/java/com.google.ads.interactivemedia.v3.samples/videoplayer/SampleAdsWrapper#onAdEvent
[parse_ad_info link]: app/main/java/com.google.ads.interactivemedia.v3.samples/videoplayer/SampleAdsWrapper#findInteractiveAdInfo       
[parse_ad_info link]: ads.interactivemedia.v3.samples/videoplayer/SampleAdsWrapper#findInteractiveAdInfo       
[instantiate_iroll link]: app/main/java/com.google.ads.interactivemedia.v3.samples/videoplayer/SampleAdsWrapper#checkAndStartInteractiveAd
[listen_ima_events link]: app/main/java/com.google.ads.interactivemedia.v3.samples/videoplayer/SampleAdsWrapper#checkAndStartInteractiveAd