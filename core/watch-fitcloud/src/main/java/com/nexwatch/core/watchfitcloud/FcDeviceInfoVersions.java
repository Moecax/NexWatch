package com.nexwatch.core.watchfitcloud;

import androidx.annotation.Nullable;

import com.topstep.fitcloud.sdk.v2.model.config.FcDeviceInfo;

/**
 * Reads the firmware version strings off {@link FcDeviceInfo}.
 *
 * <p>Java, on purpose. {@code app}, {@code project}, {@code flash} and {@code patch} are
 * declared {@code internal} in the SDK's own Kotlin module, so Kotlin in this module cannot
 * call them — but {@code internal} is a Kotlin-compiler rule with no JVM equivalent, and the
 * accessors are plain public methods in the AAR. Java therefore reaches them without
 * reflection, which keeps the call compile-time checked. §4.5 needs a firmware version and
 * this is the only source the SDK exposes for one (see {@code docs/recon.md} §1).
 *
 * <p>R8 safety comes from {@code consumer-rules.pro}, which keeps all of
 * {@code com.topstep.fitcloud.sdk.v2.model.**} including these members.
 */
final class FcDeviceInfoVersions {

    private FcDeviceInfoVersions() {
    }

    @Nullable
    static String app(FcDeviceInfo info) {
        return info.getApp();
    }

    @Nullable
    static String project(FcDeviceInfo info) {
        return info.getProject();
    }
}
