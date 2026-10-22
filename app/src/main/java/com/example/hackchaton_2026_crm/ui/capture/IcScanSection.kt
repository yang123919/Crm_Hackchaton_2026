package com.example.hackchaton_2026_crm.ui.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.hackchaton_2026_crm.ocr.IcInfo
import com.example.hackchaton_2026_crm.ocr.IcScanException
import com.example.hackchaton_2026_crm.ocr.IcScanner
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Survives rotation and the camera app taking over the screen. */
private val IcInfoSaver = androidx.compose.runtime.saveable.listSaver<IcInfo?, String>(
    save = { ic -> if (ic == null) emptyList() else listOf(ic.fullName, ic.icNumber, ic.address, ic.gender, ic.warnings.joinToString("\n")) },
    restore = { l ->
        if (l.isEmpty()) null
        else IcInfo(
            fullName = l[0], icNumber = l[1], address = l[2], gender = l[3],
            warnings = l[4].split("\n").filter { it.isNotEmpty() },
            birthDate = com.example.hackchaton_2026_crm.ocr.MyKadParser.birthDate(l[1].take(6), java.time.LocalDate.now()),
        )
    },
)

/** Holds the scan result for the Capture screen. */
class IcScanState internal constructor(
    val info: IcInfo?,
    val scanning: Boolean,
    val error: String?,
    val takePhoto: () -> Unit,
    val pickPhoto: () -> Unit,
    val clear: () -> Unit,
)

/**
 * "Scan IC" with the camera or the gallery. The photo is read on the phone by ML Kit and
 * the camera photo is deleted as soon as it has been read; only the text is kept.
 */
@Composable
fun rememberIcScan(): IcScanState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by rememberSaveable(stateSaver = IcInfoSaver) { mutableStateOf<IcInfo?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun scan(uri: Uri, ours: Boolean) {
        scanning = true
        error = null
        scope.launch {
            try {
                val result = IcScanner.scan(context, uri)
                if (result.icNumber.isEmpty() && result.fullName.isEmpty()) {
                    error = "Couldn't find an IC in that photo. Fill the card edge to edge and avoid glare."
                } else info = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: IcScanException) {
                error = e.message
            } finally {
                scanning = false
                // The photo has done its job: don't leave an IC picture lying around in app storage.
                if (ours) runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri ?: return@rememberLauncherForActivityResult
        cameraUri = null
        if (saved) scan(uri, ours = true) else runCatching { context.contentResolver.delete(uri, null, null) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scan(uri, ours = false)
    }

    return IcScanState(
        info = info,
        scanning = scanning,
        error = error,
        takePhoto = {
            val dir = File(context.cacheDir, "ic_scan").apply { mkdirs() }
            val file = File.createTempFile("ic_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        },
        pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        clear = { info = null; error = null },
    )
}

@Composable
fun IcScanSection(scan: IcScanState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = scan.takePhoto, enabled = !scan.scanning, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CreditCard, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (scan.info == null) "Scan IC" else "Rescan IC", maxLines = 1)
            }
            OutlinedButton(onClick = scan.pickPhoto, enabled = !scan.scanning, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("From gallery", maxLines = 1)
            }
        }
        if (scan.scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading the IC on your phone…")
            }
        }
        scan.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        scan.info?.let { IcCard(it, onRemove = scan.clear) }
    }
}

@Composable
private fun IcCard(ic: IcInfo, onRemove: () -> Unit) {
    val status = LocalStatusColors.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CreditCard, null, tint = status.ok)
                Spacer(Modifier.width(8.dp))
                Text(
                    ic.fullName.ifEmpty { "Name not read" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove scanned IC") }
            }
            val line = listOfNotNull(
                ic.maskedIc.takeIf { it.isNotEmpty() },
                ic.gender.takeIf { it.isNotEmpty() },
                ic.birthDate?.let(Format::longDate),
            ).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium)
            if (ic.address.isNotEmpty()) {
                Text(
                    ic.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                )
            }
            ic.warnings.forEach {
                Text("! $it", style = MaterialTheme.typography.bodySmall, color = status.dueToday,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
