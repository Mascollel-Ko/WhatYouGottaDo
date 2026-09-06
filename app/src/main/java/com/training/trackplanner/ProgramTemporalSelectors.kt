package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.training.trackplanner.localization.localizedWeekday
import java.time.DayOfWeek

/** Presentation only: typed week/day values, no schedule decisions or draft mutation. */
@Composable
internal fun ProgramTemporalSelector(
    values: List<Int>,
    selected: Set<Int>,
    tag: String,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    accessibilityLabel: @Composable (Int) -> String = label
) {
    Row(Modifier.fillMaxWidth().testTag("$tag-row"), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEach { value ->
            val text = label(value)
            val spokenLabel = accessibilityLabel(value)
            val active = value in selected
            val shape = RoundedCornerShape(8.dp)
            val colors = MaterialTheme.colorScheme
            Box(
                Modifier.weight(1f, fill = false).widthIn(max = 40.dp).fillMaxWidth()
                    .height(48.dp).testTag("$tag-$value")
                    .clip(shape)
                    .background(if (active) colors.primary else colors.surface)
                    .border(1.dp, if (active) colors.primary else colors.outline, shape)
                    .selectable(active, role = Role.Button, onClick = { onSelect(value) })
                    .semantics { contentDescription = spokenLabel },
                contentAlignment = Alignment.Center
            ) {
                MaterialText(text, Modifier.fillMaxWidth().testTag("$tag-$value-label"),
                    color = if (active) colors.onPrimary else colors.onSurface,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
internal fun programWeekLabel(week: Int): String = stringResource(R.string.program_week_number, week)

@Composable
internal fun programWeekChipLabel(week: Int): String = stringResource(R.string.program_week_compact, week)

/** Explicit typed, Program-only abbreviations; never truncate or translate atomic weekday tokens. */
@Composable
internal fun programWeekdayChipLabel(day: Int): String = stringResource(when (DayOfWeek.of(day)) {
    DayOfWeek.MONDAY -> R.string.program_monday_compact
    DayOfWeek.TUESDAY -> R.string.program_tuesday_compact
    DayOfWeek.WEDNESDAY -> R.string.program_wednesday_compact
    DayOfWeek.THURSDAY -> R.string.program_thursday_compact
    DayOfWeek.FRIDAY -> R.string.program_friday_compact
    DayOfWeek.SATURDAY -> R.string.program_saturday_compact
    DayOfWeek.SUNDAY -> R.string.program_sunday_compact
})

@Composable
internal fun programWeekdayLabel(day: Int): String = localizedWeekday(DayOfWeek.of(day))

/** Creation fields wrap as whole controls; their selected temporal values remain readable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramOptionRow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2, content = content)
}
