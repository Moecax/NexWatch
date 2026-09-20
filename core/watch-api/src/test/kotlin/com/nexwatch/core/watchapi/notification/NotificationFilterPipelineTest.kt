package com.nexwatch.core.watchapi.notification

import com.nexwatch.core.watchapi.OutgoingNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFilterPipelineTest {

    private val settings = NotificationForwardingSettings(
        enabled = true,
        allowedPackages = setOf("com.whatsapp"),
    )
    private val ownPackage = "com.nexwatch"

    private fun notif(
        pkg: String = "com.whatsapp",
        title: String? = "Alice",
        text: String? = "Hello",
        category: String? = null,
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
    ) = IncomingNotification(pkg, title, text, category, ongoing, groupSummary)

    @Test
    fun `master switch off drops everything`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(), settings.copy(enabled = false), ownPackage, nowMs = 0)
        assertNull(result)
    }

    @Test
    fun `own package is always dropped`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(pkg = ownPackage), settings, ownPackage, nowMs = 0)
        assertNull(result)
    }

    @Test
    fun `package not on the allowlist is dropped`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(pkg = "com.random.app"), settings, ownPackage, nowMs = 0)
        assertNull(result)
    }

    @Test
    fun `ongoing and group-summary notifications are dropped`() {
        val pipeline = NotificationFilterPipeline()
        assertNull(pipeline.evaluate(notif(ongoing = true), settings, ownPackage, nowMs = 0))
        assertNull(pipeline.evaluate(notif(groupSummary = true), settings, ownPackage, nowMs = 0))
    }

    @Test
    fun `progress and service categories are dropped`() {
        val pipeline = NotificationFilterPipeline()
        for (category in listOf("progress", "transport", "service", "status")) {
            assertNull(pipeline.evaluate(notif(category = category), settings, ownPackage, nowMs = 0))
        }
    }

    @Test
    fun `an allowed notification maps to WHATSAPP by package`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 0)
        assertEquals(OutgoingNotification.NotificationType.WHATSAPP, result?.type)
        assertEquals("Alice", result?.title)
        assertEquals("Hello", result?.content)
    }

    @Test
    fun `unmapped allowed package falls back to OTHERS_APP`() {
        val pipeline = NotificationFilterPipeline()
        val settingsWithOther = settings.copy(allowedPackages = setOf("com.example.other"))
        val result = pipeline.evaluate(notif(pkg = "com.example.other"), settingsWithOther, ownPackage, nowMs = 0)
        assertEquals(OutgoingNotification.NotificationType.OTHERS_APP, result?.type)
    }

    @Test
    fun `identical content within 60 seconds is deduped`() {
        val pipeline = NotificationFilterPipeline()
        val first = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 0)
        val second = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 30_000)
        assertEquals("Alice", first?.title)
        assertNull(second)
    }

    @Test
    fun `identical content after the 60 second window is forwarded again`() {
        val pipeline = NotificationFilterPipeline()
        pipeline.evaluate(notif(), settings, ownPackage, nowMs = 0)
        val afterWindow = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 60_001)
        assertEquals("Alice", afterWindow?.title)
    }

    @Test
    fun `a second distinct notification from the same app within 5 seconds is throttled`() {
        val pipeline = NotificationFilterPipeline()
        pipeline.evaluate(notif(text = "first"), settings, ownPackage, nowMs = 0)
        val throttled = pipeline.evaluate(notif(text = "second"), settings, ownPackage, nowMs = 1_000)
        assertNull(throttled)
    }

    @Test
    fun `a notification from the same app after 5 seconds is not throttled`() {
        val pipeline = NotificationFilterPipeline()
        pipeline.evaluate(notif(text = "first"), settings, ownPackage, nowMs = 0)
        val later = pipeline.evaluate(notif(text = "second"), settings, ownPackage, nowMs = 5_001)
        assertEquals("second", later?.content)
    }

    @Test
    fun `title falls back to package name when EXTRA_TITLE is absent`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(title = null), settings, ownPackage, nowMs = 0)
        assertEquals("com.whatsapp", result?.title)
    }
}
