package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White

/**
 * Pill-shaped search field (iOS `SearchView`). State is hoisted: the caller
 * owns [text] and filters on it. While there is text a clear (x) button
 * replaces the trailing filter glyph; when the field is empty the filter
 * glyph shows if [showFilterIcon] (tappable when [onFilterTap] is set).
 */
@Composable
fun SearchView(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onFilterTap: (() -> Unit)? = null,
    showFilterIcon: Boolean = onFilterTap != null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(White, RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = TextPrimaryColor),
            cursorBrush = SolidColor(FLPrimary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(placeholder, fontSize = 16.sp, color = TextSecondaryColor, maxLines = 1)
                    }
                    inner()
                }
            }
        )
        when {
            text.isNotEmpty() -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = TextSecondaryColor,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onTextChange("") }
                )
            }
            showFilterIcon -> {
                Image(
                    painter = painterResource(R.drawable.filter),
                    contentDescription = "Filter",
                    modifier = Modifier
                        .width(18.dp)
                        .height(12.dp)
                        .then(if (onFilterTap != null) Modifier.clickable(onClick = onFilterTap) else Modifier)
                )
            }
        }
    }
}
