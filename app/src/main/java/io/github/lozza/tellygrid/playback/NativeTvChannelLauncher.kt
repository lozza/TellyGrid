package io.github.lozza.tellygrid.playback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri

data class NativeTvChannel(
    val lcn: Int,
    val inputId: String,
    val channelUri: Uri,
)

/**
 * Resolves a Freeview logical channel number to a channel installed in the TV's
 * local tuner database and identifies the Android TV tuner input for in-app TvView.
 *
 * The Android TV channel database cannot normally be enumerated by third-party
 * apps. Philips Freeview Play sets expose a read-only EPG index containing the
 * corresponding TvContract channel IDs, so that is used when available. The
 * external launcher remains a fallback if a TvView session cannot be created.
 */
class NativeTvChannelLauncher(private val context: Context) {
    fun resolve(lcn: Int): NativeTvChannel? {
        val channelId = findPhilipsFreeviewChannelId(lcn) ?: return null
        val inputId = findTunerInputId() ?: return null
        return NativeTvChannel(
            lcn = lcn,
            inputId = inputId,
            channelUri = TvContract.buildChannelUri(channelId),
        )
    }

    fun launchExternal(channel: NativeTvChannel): LaunchResult? {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(channel.channelUri, CHANNEL_MIME_TYPE)
            putExtra("channel_id", channel.channelUri.lastPathSegment?.toIntOrNull())
            putExtra("zap_method", 2)
            putExtra("method_id", 9)
            putExtra("source_id", 0)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            LaunchResult.Opened("Freeview channel ${channel.lcn}")
        } catch (_: ActivityNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun findTunerInputId(): String? {
        val manager = context.getSystemService(TvInputManager::class.java) ?: return null
        return manager.tvInputList
            .filter { it.type == TvInputInfo.TYPE_TUNER }
            .sortedByDescending { it.id.contains("TunerInputService/HW0", ignoreCase = true) }
            .firstOrNull()
            ?.id
    }

    private fun findPhilipsFreeviewChannelId(lcn: Int): Long? {
        val candidates = mutableListOf<PhilipsChannel>()
        val selection = "presetnumber = ? AND browsable = 1"
        val selectionArgs = arrayOf((lcn * PHILIPS_LCN_SCALE).toString())

        val cursor = try {
            context.contentResolver.query(
                PHILIPS_CHANNEL_INDEX,
                PHILIPS_PROJECTION,
                selection,
                selectionArgs,
                null,
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val id = it.longOrNull("presetid") ?: continue
                candidates += PhilipsChannel(
                    id = id,
                    originalNetworkId = it.intOrNull("onid"),
                    serviceId = it.intOrNull("sid"),
                    serviceListId = it.intOrNull("svlid"),
                )
            }
        }

        return chooseFreeviewCandidate(candidates)?.id
    }

    private fun Cursor.intOrNull(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private data class PhilipsChannel(
        val id: Long,
        val originalNetworkId: Int?,
        val serviceId: Int?,
        val serviceListId: Int?,
    )

    private fun chooseFreeviewCandidate(candidates: List<PhilipsChannel>): PhilipsChannel? =
        candidates
            .filter { (it.serviceId ?: 0) > 0 }
            .maxWithOrNull(
                compareBy<PhilipsChannel> { it.originalNetworkId == UK_DTT_ORIGINAL_NETWORK_ID }
                    .thenBy { it.serviceListId == PHILIPS_TERRESTRIAL_SERVICE_LIST }
                    .thenBy { it.id },
            )

    private companion object {
        val PHILIPS_CHANNEL_INDEX: Uri =
            Uri.parse("content://org.droidtv.epgdvb/antenna/indextable")
        val PHILIPS_PROJECTION = arrayOf("presetid", "onid", "sid", "svlid", "browsable")
        const val CHANNEL_MIME_TYPE = "vnd.android.cursor.item/channel"
        const val PHILIPS_LCN_SCALE = 100
        const val UK_DTT_ORIGINAL_NETWORK_ID = 9018
        const val PHILIPS_TERRESTRIAL_SERVICE_LIST = 1
    }
}
