## One UI 8 rootless font changer

my attempt to stay relevant and reverse engineer how one might add custom fonts on Samsung devices without root (OneUI 8)

zFont sucks with its ADS (it seems like its the only other app that can do this besides the one i based this off of, i just did it here for only for Samsung, other devices not supported)

its kinda thrown together (look its text replacement instead of apk decompiling)

check out [template.apk](https://github.com/jeeneo/fonts/blob/main/android/app/src/main/assets/template.apk) to see how it works, its based off of the SamsungSans apk, its not obfuscated and youre free to decompile it, i just use search-and-replace patching is all. I attempted to embed aapt2 but that failed. you can create it yourself by extracting the SamsungSans apk, editing resources.arsc and AndroidManifest.xml with the placeholder values (app_name/package ID), deleting the assets folder and saving. It's using a generic keyfile to sign apks so be warned.

apk in release

supports shizuku