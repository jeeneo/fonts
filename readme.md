## One UI 8 rootless font installer

> [!IMPORTANT]  
> This app has been put under low-maintenance mode, I no longer own a Samsung device any longer to continue developing

<div align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" 
       style="width: 240px; max-width: 100%; height: auto; margin: 10px;" alt="">
  <p>
<p align="center">
    <a href="https://apt.izzysoft.de/packages/com.je.fontsmanager.samsung"><img src="fastlane/githubassets/IzzyOnDroid.png" width="220" alt="IzzyOnDroid"></a>
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/jeeneo/fonts"><img src="fastlane/githubassets/obtanium.png" width="220" alt="Obtanium"></a>
  <a href="https://github.com/jeeneo/fonts/releases/latest/download/fontsmanager.apk"><img src="fastlane/githubassets/badge_github.png" width="220" alt="Get it on GitHub"></a>
</p>
  </p>
</div>

my attempt to stay relevant and reverse engineer how one might add custom fonts on Samsung devices without root (OneUI 8)

zFont sucks with its ADS (it seems like its the only other app that can do this besides the one i based this off of, i just did it here for only for Samsung, other devices not supported)

its kinda thrown together (look its text replacement instead of apk decompiling)

basically an unsigned stub apk is built alongside then search-and-replacement is used to change the name and ID

apk in release

supports shizuku
