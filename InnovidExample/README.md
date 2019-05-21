# Overview

# Flow

## 1. Listen to IMA SDK `AdEvent.STARTED`
    
    
## 2. Look for Innovid companions for a given ad
    In the handler method for IMA `AdEvent.STARTED` event, we inspect `Ad#companions` property. 
    If any companion has an `apiFramework` value matching `innovid`, then we ignore all other companions 
    and i
## 3. Prepare WebView 
## 4. Initialize and start the renderer
