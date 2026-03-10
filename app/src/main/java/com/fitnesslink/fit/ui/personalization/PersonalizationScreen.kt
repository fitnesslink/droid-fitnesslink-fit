package com.fitnesslink.fit.ui.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.model.PersonalizationItem
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.PrimaryButtonView
import com.fitnesslink.fit.ui.components.SecondaryButtonView
import com.fitnesslink.fit.ui.components.StepProgressView
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import com.fitnesslink.fit.viewmodel.PersonalizationViewModel

@Composable
fun PersonalizationScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit = {}
) {
    val viewModel: PersonalizationViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        StepProgressView(
            progress = viewModel.progress,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        )

        viewModel.currentPage?.let { page ->
            Text(
                text = page.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                page.options.forEach { item ->
                    PersonalizationItemView(
                        item = item,
                        onTap = { viewModel.toggleSelection(item) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 30.dp)
        ) {
            if (!viewModel.isFirstPage) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.previous() }
                ) {
                    SecondaryButtonView(text = "Previous")
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (viewModel.hasSelection) 1f else 0.5f)
                    .clickable(enabled = viewModel.hasSelection) {
                        viewModel.next()
                        if (viewModel.isComplete) {
                            onComplete()
                        }
                    }
            ) {
                PrimaryButtonView(
                    text = if (viewModel.isLastPage) "Finish" else "Continue"
                )
            }
        }
    }
}

@Composable
fun PersonalizationItemView(
    item: PersonalizationItem,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.selected) FLPrimary else TextSecondaryColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = item.text,
            fontSize = 16.sp
        )
    }
}
