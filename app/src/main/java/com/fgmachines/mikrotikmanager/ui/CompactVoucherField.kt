package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

/** External labels reserve their own space; input height can grow with accessibility fonts. */
@Composable
fun CompactVoucherField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    Column(modifier) {
        label()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textStyle = textStyle.copy(color = FgWhite, textDirection = TextDirection.ContentOrLtr),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            cursorBrush = SolidColor(FgBlue),
            decorationBox = { input ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth()
                        .border(1.dp, FgBlue, RoundedCornerShape(12.dp))
                        .background(FgDeepNavy, RoundedCornerShape(12.dp))
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = androidx.compose.ui.Alignment.CenterStart
                ) { input() }
            }
        )
    }
}
