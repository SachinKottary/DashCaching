# exo-player-pre-cache-sample
Sample App for pre-caching Dash video streams:

The idea is to reduce the video loading time for dash along with adaptive streaming capability.

We'll be trying to download video only at the lowest resolution (stream keys is limited when passing to cache files) and depending on situation (we could either wait till cache complete, or cache via percentage and check current video duration) try to be adaptive again. At the moment only provided manual button click for testing this.

Special thanks to: https://github.com/thanductaimgt/exo-player-pre-cache-sample !

Medium post for HLS:

https://medium.com/@thanductaimgt/pre-caching-adaptive-video-stream-in-a-playlist-with-exoplayer-api-5ef92097d94e
