package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.BrandPurpleDark
import com.hearopilot.app.ui.ui.theme.ModeEmeraldTint
import com.hearopilot.app.ui.ui.theme.SpaceGroteskFont

/**
 * Offline phrasebook. All entries ship with the APK, so it works before any
 * model is downloaded and with no network at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhrasebookScreen(onNavigateBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPhrase by remember { mutableStateOf<PhraseEntry?>(null) }

    val filtered = remember(query, selectedCategory) {
        Phrasebook.entries.filter { entry ->
            val matchesQuery = query.isBlank() ||
                entry.zh.contains(query, ignoreCase = true) ||
                entry.en.contains(query, ignoreCase = true)
            val matchesCategory = query.isNotBlank() || selectedCategory == null ||
                entry.category.name == selectedCategory
            matchesQuery && matchesCategory
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.foundation.layout.Box(modifier = Modifier.background(BrandPurpleDark)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.phrasebook_title),
                                fontSize = 20.sp,
                                fontFamily = SpaceGroteskFont,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.phrasebook_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                AppIcons.Back,
                                contentDescription = stringResource(R.string.back_to_sessions),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text(stringResource(R.string.phrasebook_search_hint)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.dismiss))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.phrasebook_all)) }
                    )
                }
                items(PhraseCategory.entries) { category ->
                    FilterChip(
                        selected = selectedCategory == category.name,
                        onClick = {
                            selectedCategory = if (selectedCategory == category.name) null
                                              else category.name
                        },
                        label = { Text(stringResource(category.labelRes)) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.phrasebook_show_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.phrasebook_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { "${it.category}-${it.zh}" }) { phrase ->
                        PhraseCard(phrase = phrase, onClick = { selectedPhrase = phrase })
                    }
                }
            }
        }
    }

    selectedPhrase?.let { phrase ->
        PhrasebookDetailDialog(phrase = phrase, onDismiss = { selectedPhrase = null })
    }
}

@Composable
private fun PhraseCard(phrase: PhraseEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = phrase.zh,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = phrase.en,
                    fontSize = 14.sp,
                    color = ModeEmeraldTint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
