package com.example.hackchaton_2026_crm.ui.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.Badge
import com.example.hackchaton_2026_crm.data.LicenceRules
import com.example.hackchaton_2026_crm.data.LicenceType
import com.example.hackchaton_2026_crm.ocr.FormScanner
import com.example.hackchaton_2026_crm.ocr.IcInfo
import com.example.hackchaton_2026_crm.ocr.IcScanner
import com.example.hackchaton_2026_crm.ocr.LicenceInfo
import com.example.hackchaton_2026_crm.ocr.LicenceScanner
import com.example.hackchaton_2026_crm.ocr.MyKadParser
import com.example.hackchaton_2026_crm.ocr.ScanException
import com.example.hackchaton_2026_crm.ui.Format
import com.example.hackchaton_2026_crm.ui.components.LicencePanel
import com.example.hackchaton_2026_crm.ui.theme.LocalStatusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Camera or gallery for one kind of scan. */
class PhotoSource internal constructor(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

/**
 * Camera and gallery launchers that hand a photo to [onPhoto]. A camera photo is deleted as
 * soon as [onPhoto] returns, so no picture of an ID or form is left in app storage.
 */
@Composable
internal fun rememberPhotoSource(onPhoto: suspend (Uri) -> Unit): PhotoSource {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun handle(uri: Uri, ours: Boolean) {
        scope.launch {
            try {
                onPhoto(uri)
            } finally {
                if (ours) runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri ?: return@rememberLauncherForActivityResult
        cameraUri = null
        if (saved) handle(uri, ours = true) else runCatching { context.contentResolver.delete(uri, null, null) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) handle(uri, ours = false)
    }
    return PhotoSource(
        takePhoto = {
            val dir = File(context.cacheDir, "ic_scan").apply { mkdirs() }
            val file = File.createTempFile("scan_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        },
        pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/** Survives rotation and the camera app taking over the screen. */
private val IcInfoSaver = listSaver<IcInfo?, String>(
    save = { ic -> if (ic == null) emptyList() else listOf(ic.fullName, ic.icNumber, ic.address, ic.gender, ic.warnings.joinToString("\n")) },
    restore = { l ->
        if (l.isEmpty()) null
        else IcInfo(
            fullName = l[0], icNumber = l[1], address = l[2], gender = l[3],
            warnings = l[4].split("\n").filter { it.isNotEmpty() },
            birthDate = MyKadParser.birthDate(l[1].take(6), LocalDate.now()),
        )
    },
)

private val LicenceInfoSaver = listSaver<LicenceInfo?, String>(
    save = { l ->
        if (l == null) emptyList()
        else listOf(
            l.fullName, l.icNumber, l.address, l.type?.name.orEmpty(), l.classes.joinToString(" "),
            l.validFrom?.toString().orEmpty(), l.validTo?.toString().orEmpty(), l.warnings.joinToString("\n"),
            l.typeFromPhoto.toString(),
        )
    },
    restore = { s ->
        if (s.isEmpty()) null
        else LicenceInfo(
            fullName = s[0], icNumber = s[1], address = s[2], type = LicenceType.fromName(s[3]),
            classes = LicenceRules.parseClasses(s[4]),
            validFrom = s[5].takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
            validTo = s[6].takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
            warnings = s[7].split("\n").filter { it.isNotEmpty() },
            typeFromPhoto = s.getOrNull(8) == "true",
        )
    },
)

/** What the Capture screen needs from the scanners. Only one ID is kept: an IC or a driving licence. */
class ScanState internal constructor(
    val ic: IcInfo?,
    val licence: LicenceInfo?,
    val busy: Boolean,
    val message: String?,
    val isError: Boolean,
    internal val icSource: PhotoSource,
    internal val licenceSource: PhotoSource,
    internal val formSource: PhotoSource,
    val clearIc: () -> Unit,
    val clearLicence: () -> Unit,
    val setLicenceType: (LicenceType) -> Unit,
)

/**
 * IC and form scanning. The IC fills name, IC number, address and gender; a form is read into
 * "Label: value" lines that are appended to the notes through [onFormText], where they can be
 * edited before the AI turns them into fields.
 */
@Composable
fun rememberScan(onFormText: (String) -> Unit): ScanState {
    val context = LocalContext.current
    var ic by rememberSaveable(stateSaver = IcInfoSaver) { mutableStateOf<IcInfo?>(null) }
    var licence by rememberSaveable(stateSaver = LicenceInfoSaver) { mutableStateOf<LicenceInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    suspend fun run(block: suspend () -> String?) {
        busy = true
        message = null
        isError = false
        try {
            message = block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScanException) {
            message = e.message
            isError = true
        } finally {
            busy = false
        }
    }

    val icSource = rememberPhotoSource { uri ->
        run {
            val result = IcScanner.scan(context, uri)
            if (result.icNumber.isEmpty() && result.fullName.isEmpty()) {
                isError = true
                "Couldn't find an IC in that photo. Fill the card edge to edge and avoid glare."
            } else {
                ic = result
                licence = null
                null
            }
        }
    }
    // A licence carries the same name, IC number and address, plus what an IC can't show: whether
    // the person has passed (CDL / PDL / LDL). It replaces a scanned IC; one ID is enough.
    val licenceSource = rememberPhotoSource { uri ->
        run {
            val result = LicenceScanner.scan(context, uri)
            if (result.isEmpty) {
                isError = true
                "Couldn't read a driving licence in that photo. Hold the card upright, fill the frame and avoid glare."
            } else {
                licence = result
                ic = null
                null
            }
        }
    }
    val formSource = rememberPhotoSource { uri ->
        run {
            val result = FormScanner.scan(context, uri)
            if (result.rawText.isBlank()) {
                isError = true
                "Couldn't read any text on that photo. Try again in better light."
            } else {
                onFormText(result.toNotes())
                if (result.fields.isEmpty()) {
                    "No form labels found; the text was added to the notes. Check it before continuing."
                } else {
                    "Read ${result.fields.size} fields from the form. Check them in the notes below."
                }
            }
        }
    }
    return ScanState(
        ic, licence, busy, message, isError, icSource, licenceSource, formSource,
        clearIc = { ic = null },
        clearLicence = { licence = null },
        // A type read from the photo is locked; only an unreadable one may be picked by hand.
        setLicenceType = { t -> licence = licence?.takeIf { !it.typeFromPhoto }?.copy(type = t) ?: licence },
    )
}

@Composable
fun ScanSection(scan: ScanState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanButton(
                label = if (scan.ic == null) "Scan IC" else "Rescan IC",
                icon = Icons.Filled.CreditCard,
                source = scan.icSource,
                enabled = !scan.busy,
                modifier = Modifier.weight(1f),
            )
            ScanButton(
                label = if (scan.licence == null) "Scan licence" else "Rescan licence",
                icon = Icons.Filled.Badge,
                source = scan.licenceSource,
                enabled = !scan.busy,
                modifier = Modifier.weight(1f),
            )
        }
        ScanButton(
            label = "Scan form",
            icon = Icons.Filled.Description,
            source = scan.formSource,
            enabled = !scan.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        if (scan.ic == null && scan.licence == null) {
            Text(
                "One ID is enough: IC or driving licence. A CDL licence is needed later to book a test drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (scan.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading the photo on your phone…")
            }
        }
        scan.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (scan.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        scan.ic?.let { IcCard(it, onRemove = scan.clearIc) }
        scan.licence?.let { l ->
            LicencePanel(
                title = l.fullName.ifEmpty { "Name not read" },
                maskedIc = IcInfo.maskIc(l.icNumber),
                type = l.type,
                typeLocked = l.typeFromPhoto,
                classes = l.classes,
                validTo = l.validTo,
                warnings = l.warnings,
                onTypeChange = scan.setLicenceType,
                onRemove = scan.clearLicence,
            )
        }
    }
}

/** Opens a small menu: take a photo or pick one from the gallery. */
@Composable
internal fun ScanButton(label: String, icon: ImageVector, source: PhotoSource, enabled: Boolean, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Take photo") },
                leadingIcon = { Icon(Icons.Filled.CameraAlt, null) },
                onClick = { menu = false; source.takePhoto() },
            )
            DropdownMenuItem(
                text = { Text("From gallery") },
                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                onClick = { menu = false; source.pickPhoto() },
            )
        }
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
