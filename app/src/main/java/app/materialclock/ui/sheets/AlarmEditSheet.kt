package app.materialclock.ui.sheets

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePickerSelectionMode
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.materialclock.core.Alarm
import app.materialclock.data.WeekStart
import app.materialclock.data.order
import app.materialclock.ui.theme.Numerals
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Add and edit are the same sheet.
 *
 * The only difference is whether Delete is offered, so making them two screens would mean
 * maintaining two copies of a time picker, a day row and a ringtone picker to save one boolean.
 * A new alarm arrives as an [Alarm] with `id == 0`, which the view model reads as "insert".
 *
 * ## It fits, and it does not scroll
 *
 * Everything is on one screen. A form that scrolls is a form where you cannot see what you are
 * about to save, and inside a sheet the scroll also fights the drag gesture that closes it.
 * Fitting cost three things: the dial is scaled (see [Scaled]); the section headers are gone,
 * since a row of weekday letters and a ringtone name do not need to be told what they are; and
 * there is no sheet title, because the sheet *is* the thing you tapped, so naming it is redundant.
 *
 * ## Saving
 *
 * Editing is live. Anything but Cancel commits: the Save button, dragging the sheet down, tapping
 * the scrim. That is how every settings screen on the phone behaves. A modal form that throws
 * away work because you dismissed it the wrong way is a trap. Cancel is the explicit discard, and
 * it is the only one.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheet(
    initial: Alarm,
    weekStart: WeekStart,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: ((Long) -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var label by rememberSaveable(initial.id) { mutableStateOf(initial.label) }
    var days by remember(initial.id) { mutableStateOf(initial.days) }
    var vibrate by rememberSaveable(initial.id) { mutableStateOf(initial.vibrate) }
    var soundUri by rememberSaveable(initial.id) { mutableStateOf(initial.soundUri) }
    var discard by remember(initial.id) { mutableStateOf(false) }

    val timeState = rememberTimePickerState(
        initialHour = initial.time.hour,
        initialMinute = initial.time.minute,
        // The system's own 12/24 preference, not the app's world-clock one: that setting belongs to
        // another tab, and a picker disagreeing with every other picker on the phone is a bug.
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )

    /** Every exit but Cancel runs this. */
    fun commitAndClose() {
        if (!discard) {
            onSave(
                initial.copy(
                    time = LocalTime.of(timeState.hour, timeState.minute),
                    label = label.trim(),
                    days = days,
                    enabled = true,
                    vibrate = vibrate,
                    soundUri = soundUri,
                )
            )
        }
        onDismiss()
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            // A null pick is "Silent", which is a legitimate choice and must be distinguishable
            // from "unset". Storing the literal string keeps that difference in one nullable field.
            soundUri = picked?.toString() ?: SILENT
        }
    }

    ModalBottomSheet(onDismissRequest = { commitAndClose() }, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 20.dp)) {

            // The field and the dial are composed separately, rather than letting `TimePicker`
            // draw both.
            //
            // Two reasons. `TimePicker` puts a fixed spacer between its display and its dial that
            // no parameter reaches, and its selector boxes are a hard 96 x 80 dp from
            // `TimeSelectorContainerWidth`/`Height`, which is the wrong shape entirely once the
            // digits inside are half again as wide as they are tall. Building the field means the
            // pills can be sized *by the numerals*, and means there is no gap to remove.
            //
            // The dial is still the library's own `ClockFace`, driven by the same `TimePickerState`
            // through an `AnalogTimePickerState`, so dragging, snapping and the auto-advance from
            // hour to minute are all unchanged.
            WideTimeField(state = timeState)

            // Small, but not nothing: the field's pills and the dial's pill are the same shape in
            // the same colour family, and with them touching they read as one container with a
            // notch in it rather than as a readout above a picker.
            Spacer(Modifier.height(8.dp))
            PillDial(
                state = timeState,
                modifier = Modifier.padding(horizontal = EDGE),
                // Same courtesy the library's dial does: picking an hour moves you on to minutes,
                // because nobody sets an alarm for exactly o'clock and then stops.
                onHourPicked = { timeState.selection = TimePickerSelectionMode.Minute },
            )

            Spacer(Modifier.height(4.dp))
            DayToggles(
                order = weekStart.order(systemFirstDay()),
                selected = days,
                onToggle = { d -> days = if (d in days) days - d else days + d },
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE),
            )

            Spacer(Modifier.height(10.dp))
            // Sound and vibrate are one decision (how this alarm gets your attention), so they
            // are one line. Both sit on the same [EDGE] as the field above; the previous version
            // used a ListItem for the sound, whose own 16 dp inset put it 8 dp out of line with
            // everything else on the sheet.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EDGE),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        ringtonePicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                .putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    soundUri?.takeIf { it != SILENT }?.let(Uri::parse),
                                )
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier.weight(1f).height(ROW_H),
                ) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        ringtoneName(soundUri),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
                // State as shape, not only fill: the expressive toggle goes round → squircle when
                // it is on, so it reads without relying on colour.
                FilledIconToggleButton(
                    checked = vibrate,
                    onCheckedChange = { vibrate = it },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(ROW_H),
                ) {
                    Icon(
                        Icons.Outlined.Vibration,
                        contentDescription = if (vibrate) "Vibrate on" else "Vibrate off",
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            // Same shape, different colour and width, which is the spec's "do" for a group of
            // buttons, but the colour now says what each one *does*. An earlier version used
            // `ButtonGroup`'s `clickableItem`, which renders every item identically and offers no
            // colour parameter, so Delete and Save looked like the same decision.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = EDGE),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    FilledTonalButton(
                        onClick = { onDelete(initial.id); onDismiss() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.weight(1f).height(ROW_H),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, Modifier.size(20.dp))
                    }
                }
                // Outlined, because discarding is a retreat and should not compete for the eye.
                OutlinedButton(
                    onClick = { discard = true; onDismiss() },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1.2f).height(ROW_H),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLargeEmphasized)
                }
                Button(
                    onClick = { commitAndClose() },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(if (onDelete != null) 1.8f else 2.4f).height(ROW_H),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save", style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

/**
 * Hour and minute as two pills that **hug their digits**.
 *
 * The library's selector is a fixed 96 x 80 dp box, which is portrait, and portrait is the wrong
 * proportion for numerals that are half again as wide as they are tall. These are built from
 * [Numerals], whose box *is* its ink, so the pill is exactly the digits plus a constant padding:
 * short because the cap height is 30 dp, wide because `wdth` 151 makes each digit 1.55 x that.
 * Nothing is guessed and nothing is left over.
 *
 * Tapping a pill sets `TimePickerState.selection`, which is the same public property the library's
 * own display writes, so the dial follows without any wiring between them.
 */
@Composable
private fun WideTimeField(state: TimePickerState) {
    val hour = if (state.is24hour) state.hour else ((state.hour % 12).takeIf { it != 0 } ?: 12)
    val onHour = state.selection == TimePickerSelectionMode.Hour

    Row(
        Modifier.fillMaxWidth().padding(horizontal = EDGE),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldPill(
            text = "%02d".format(hour),
            selected = onHour,
            onClick = { state.selection = TimePickerSelectionMode.Hour },
        )
        Text(
            ":",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        FieldPill(
            text = "%02d".format(state.minute),
            selected = !onHour,
            onClick = { state.selection = TimePickerSelectionMode.Minute },
        )
        if (!state.is24hour) {
            Spacer(Modifier.width(10.dp))
            // AM over PM has to come to exactly the pill's height, gap included, because the two
            // columns sit side by side and any surplus reads as the meridiem being misaligned
            // rather than as a generous gap. Both chip heights are therefore derived from the pill
            // rather than set: whatever the digits' cap height becomes, this follows it.
            Column(verticalArrangement = Arrangement.spacedBy(MERIDIEM_GAP)) {
                MeridiemChip("AM", state.hour < 12) { if (state.hour >= 12) state.hour -= 12 }
                MeridiemChip("PM", state.hour >= 12) { if (state.hour < 12) state.hour += 12 }
            }
        }
    }
}

@Composable
private fun FieldPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        shape = CircleShape,
        onClick = onClick,
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = FIELD_PAD_V)) {
            Numerals(
                text = text,
                capHeight = FIELD_CAP,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                width = 151f,
                weight = 460,
            )
        }
    }
}

@Composable
private fun MeridiemChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(MERIDIEM_CHIP_H),
        color = if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = CircleShape,
        onClick = onClick,
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** One inset for every row in the sheet, so nothing is a pixel out of line with anything else. */
private val EDGE = 24.dp
private val ROW_H = 56.dp
private val FIELD_CAP = 30.dp
private val FIELD_PAD_V = 14.dp
/** The digits' ink plus its padding: [FieldPill]'s height, and the one the meridiem must match. */
private val FIELD_PILL_H = FIELD_CAP + FIELD_PAD_V * 2
private val MERIDIEM_GAP = 4.dp
private val MERIDIEM_CHIP_H = (FIELD_PILL_H - MERIDIEM_GAP) / 2


/**
 * Digits wider than they are tall.
 *
 * `wdth` 151 is the top of Google Sans Flex's width axis. Paired with a cap height well under the
 * advance it produces numerals that read as *set into* a wide pill rather than dropped into one.
 * That is the opposite of the ultra-condensed 25 the alarm grid uses, and deliberately so: the
 * grid is a wall of numerals and this is one field.
 */


/** The literal stored for a deliberately silent alarm, as distinct from "never chose one". */
const val SILENT = "silent"

/**
 * How much of the screen a sheet may take.
 *
 * The remainder is what makes it legible as a sheet rather than a screen: you can see what you came
 * from, and dragging down is obviously the way back.
 */
const val SHEET_MAX_FRACTION = 0.88f

@Composable
private fun ringtoneName(uri: String?): String {
    val context = LocalContext.current
    return remember(uri) {
        when {
            uri == null -> "Default sound"
            uri == SILENT -> "Silent"
            else -> runCatching {
                RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context)
            }.getOrNull() ?: "Default sound"
        }
    }
}

/**
 * Seven day toggles.
 *
 * [ToggleButton] is the M3 Expressive control for exactly this case, a persistent on/off whose
 * *shape* changes with state, not only its fill. Its default shape set is the round-to-squircle
 * pair, which is the tactic that makes a selected day readable without relying on colour alone.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayToggles(order: List<DayOfWeek>, selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = EDGE - 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        order.forEach { day ->
            ToggleButton(
                checked = day in selected,
                onCheckedChange = { onToggle(day) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The locale's own first day, used when the setting says "system default". */
fun systemFirstDay(): DayOfWeek =
    java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek
