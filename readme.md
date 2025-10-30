## One UI 8 rootless font changer

<div align="center">
  <img src="https://github.com/user-attachments/assets/6d1e6fde-58b6-4991-9bb3-57b64627fbcf" height="140" alt="">
  <br>
  An open source app for removing noise and compression from photos
  <h2></h2>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <p>
<p align="center">
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/jeeneo/fonts"><img src="fastlane/githubassets/obtanium.png" width="220" alt="Obtanium"></a>
  <a href="https://github.com/jeeneo/fonts/releases/latest"><img src="fastlane/githubassets/badge_github.png" width="220" alt="Get it on GitHub"></a>
</p>
  </p>
</div>

my attempt to stay relevant and reverse engineer how one might add custom fonts on Samsung devices without root (OneUI 8)

zFont sucks with its ADS (it seems like its the only other app that can do this besides the one i based this off of, i just did it here for only for Samsung, other devices not supported)

its kinda thrown together (look its text replacement instead of apk decompiling)

basically an unsigned stub apk is built alongside then search-and-replacement is used to change the name and ID

apk in release

supports shizuku