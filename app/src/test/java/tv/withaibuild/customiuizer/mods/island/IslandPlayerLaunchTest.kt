package tv.withaibuild.customiuizer.mods.island

import android.app.PendingIntent
import android.content.IntentSender
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandPlayerLaunchTest {
    @Test fun validPendingIntentCommitsOnceAndNeverResolvesAPackageFallback() {
        var starts = 0
        val result = IslandPlayerLaunch.open({ true }, { IslandPlayerLaunch.Entry { starts++ } },
            { error("A successful PendingIntent must finish the transaction") })
        assertEquals(IslandPlayerLaunch.Result.STARTED, result)
        assertEquals(1, starts)
    }

    @Test fun missingAndCanceledSessionActivitiesUseOneCurrentPackageEntry() {
        for (canceled in listOf(false, true)) {
            var starts = 0
            val result = IslandPlayerLaunch.open({ true }, {
                if (canceled) IslandPlayerLaunch.Entry { throw PendingIntent.CanceledException() } else null
            }, { IslandPlayerLaunch.Entry { starts++ } })
            assertEquals(IslandPlayerLaunch.Result.STARTED, result)
            assertEquals(1, starts)
        }
    }

    @Test fun replacingAPlayerInvalidatesBothResolvedAndPendingFallbacks() {
        var current = true; var starts = 0
        val result = IslandPlayerLaunch.open({ current }, { current = false; IslandPlayerLaunch.Entry { starts++ } },
            { error("Stale player cannot resolve a fallback") })
        assertEquals(IslandPlayerLaunch.Result.STALE, result)
        current = true
        assertEquals(IslandPlayerLaunch.Result.STALE, IslandPlayerLaunch.open({ current }, { null }, {
            current = false; IslandPlayerLaunch.Entry { starts++ }
        }))
        assertEquals(0, starts)
    }

    @Test fun absentApplicationInvalidSenderAndPrivacyRestrictionFailWithoutAnotherLaunch() {
        assertEquals(IslandPlayerLaunch.Result.UNAVAILABLE, IslandPlayerLaunch.open({ true }, { null }, { null }))
        assertEquals(IslandPlayerLaunch.Result.UNAVAILABLE, IslandPlayerLaunch.open({ true }, { null }, {
            throw PackageManager.NameNotFoundException()
        }))
        assertEquals(IslandPlayerLaunch.Result.UNAVAILABLE, IslandPlayerLaunch.open({ true }, { null }, {
            IslandPlayerLaunch.Entry { throw IntentSender.SendIntentException() }
        }))
        assertEquals(IslandPlayerLaunch.Result.BLOCKED, IslandPlayerLaunch.open({ true }, {
            IslandPlayerLaunch.Entry { throw SecurityException() }
        }, { error("Privacy restrictions must not be bypassed") }))
    }
}
