# Keep rules for the vendored FitCloudPro SDK (implementation-plan.md §4, §10.1).
#
# sdk-base ships its own consumer rules covering the third-party libraries it bundles
# (Nordic DFU, Realtek, Ali agent, ...), and AGP applies those automatically. What it does
# NOT keep is com.topstep.** itself, so anything the SDK reaches reflectively rather than
# through a call site R8 can see would be stripped in release. These rules cover that gap.

# The SDK parses watch payloads into these model types and walks them reflectively.
-keep class com.topstep.fitcloud.sdk.v2.model.** { *; }
-keep class com.topstep.wearkit.base.connector.** { *; }

# @FcMessageType / @FcNotificationType / @FcDeviceInfo$Feature are annotation-based int
# constant holders; Signature is already kept by sdk-base, these keep the members.
-keepclassmembers class com.topstep.fitcloud.sdk.v2.model.message.** { public static final int *; }
-keepclassmembers class com.topstep.fitcloud.sdk.v2.model.config.FcDeviceInfo$Feature { public static final int *; }

# RxJava3 plugin hooks and the BLE stack the connector drives are resolved by name.
-dontwarn io.reactivex.rxjava3.**
-keep class io.reactivex.rxjava3.plugins.RxJavaPlugins { *; }
-keep class com.polidea.rxandroidble3.** { *; }
-dontwarn com.polidea.rxandroidble3.**

# The SDK is one AAR for every watch its vendor supports, so it compiles against optional
# extension libraries that NexWatch does not ship: WeChat Pay certification (mltcode), the
# AI-chat client (artillery.ctc), the Bluetrum dial packer, Realtek audio/DFU, the vendor's
# Opus codec, and OkHttp for the watchface downloader. None of those code paths are
# reachable from the features enabled in FitCloudSdk, so the references are dead weight —
# R8 only needs to be told not to fail on them. Nothing here keeps code; if one of these
# features is ever enabled, the real dependency has to be added instead.
-dontwarn com.android.mltcode.**
-dontwarn com.artillery.ctc.**
-dontwarn com.bluetrum.abpartool.**
-dontwarn com.realsil.sdk.**
-dontwarn com.topstep.opus.**
-dontwarn okhttp3.**
