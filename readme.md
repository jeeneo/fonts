my attempt to stay relevant and reverse engineer how one might add custom fonts on Samsung devices (OneUI 8) also zFont sucks with its ADS

i just made this in a few hours so expect bugs, and dont use for production unless you're comfortable with having your font packages have a public keyfile with a viewable password (WIP)

its kinda thrown together (look its text replacement instead of apk decompiling)

check out [template.apk](https://github.com/jeeneo/fonts/blob/main/android/app/src/main/assets/template.apk) to see how it works, its based off of the SamsungSans apk, its not obfuscated and youre free to decompile it, i just use search-and-replace patching is all. I attempted to embed aapt2 but that failed. you can create it yourself by extracting the SamsungSans apk, editing resources.arsc and AndroidManifest.xml with the placeholder values (app_name/package ID), deleting the assets folder and saving

debug apk in release
