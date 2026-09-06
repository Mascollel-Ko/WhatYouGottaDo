package com.training.trackplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.training.trackplanner.localization.localizedWeekday
import java.time.DayOfWeek

/** Presentation only: typed week/day values, no schedule decisions or draft mutation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramTemporalSelector(
    values: List<Int>,
    selected: Set<Int>,
    tag: String,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            val text = label(value)
            val modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-$value")
            val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                MaterialText(text, Modifier.testTag("$tag-$value-label"), maxLines = 1, softWrap = false)
            }
            if (value in selected) Button(onClick = { onSelect(value) }, modifier = modifier, content = content)
            else OutlinedButton(onClick = { onSelect(value) }, modifier = modifier, content = content)
        }
    }
}

@Composable
internal fun programWeekLabel(week: Int): String = stringResource(R.string.program_week_number, week)

@Composable
internal fun programWeekdayLabel(day: Int): String = localizedWeekday(DayOfWeek.of(day))

/** Creation fields wrap as whole controls; their selected temporal values remain readable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramOptionRow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2, content = content)
}
