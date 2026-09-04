/*
 * Copyright (c) 2025-2026 Nishant Mishra
 *
 * This file is part of Tomato - a minimalist pomodoro timer for Android.
 *
 * Tomato is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tomato is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tomato.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package org.nsh07.pomodoro.ui.settingsScreen.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.nsh07.pomodoro.di.AppInfo
import org.nsh07.pomodoro.di.FlavorUI
import org.nsh07.pomodoro.ui.mergePaddingValues
import org.nsh07.pomodoro.ui.settingsScreen.components.LicenseBottomSheet
import org.nsh07.pomodoro.ui.theme.CustomColors.detailPaneTopBarColors
import org.nsh07.pomodoro.ui.theme.CustomColors.listItemColors
import org.nsh07.pomodoro.ui.theme.CustomColors.topBarColors
import org.nsh07.pomodoro.ui.theme.LocalAppFonts
import org.nsh07.pomodoro.ui.theme.TomatoShapeDefaults.PANE_MAX_WIDTH
import org.nsh07.pomodoro.ui.theme.TomatoShapeDefaults.segmentedListItemShapes
import org.nsh07.pomodoro.ui.theme.TomatoTheme
import tomato.shared.generated.resources.Res
import tomato.shared.generated.resources.about
import tomato.shared.generated.resources.app_name
import tomato.shared.generated.resources.app_name_plus
import tomato.shared.generated.resources.arrow_back
import tomato.shared.generated.resources.back
import tomato.shared.generated.resources.gavel
import tomato.shared.generated.resources.ic_launcher_monochrome
import tomato.shared.generated.resources.license

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    isPlus: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    flavorUI: FlavorUI = koinInject(),
    appInfo: AppInfo = koinInject()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val widthExpanded = currentWindowAdaptiveInfo()
        .windowSizeClass
        .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    var showLicense by rememberSaveable { mutableStateOf(false) }

    val barColors = if (widthExpanded) detailPaneTopBarColors
    else topBarColors

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(barColors.containerColor)
        ) {
            Scaffold(
                topBar = {
                    LargeFlexibleTopAppBar(
                        title = {
                            Text(
                                stringResource(Res.string.about),
                                fontFamily = LocalAppFonts.current.topBarTitle
                            )
                        },
                        subtitle = {
                            Text(stringResource(Res.string.app_name))
                        },
                        navigationIcon = {
                            if (!widthExpanded)
                                FilledTonalIconButton(
                                    onClick = onBack,
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = listItemColors.containerColor
                                    )
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.arrow_back),
                                        stringResource(Res.string.back)
                                    )
                                }
                        },
                        colors = barColors,
                        scrollBehavior = scrollBehavior
                    )
                },
                containerColor = barColors.containerColor,
                modifier = modifier
                    .widthIn(max = PANE_MAX_WIDTH)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) { innerPadding ->
                val insets = mergePaddingValues(innerPadding, contentPadding)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = insets,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Box(Modifier.background(listItemColors.containerColor, segmentedListItemShapes(0, 1))) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    painterResource(Res.drawable.ic_launcher_monochrome),
                                    tint = colorScheme.onPrimaryContainer,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            colorScheme.primaryContainer,
                                            MaterialShapes.Cookie12Sided.toShape()
                                        )
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        if (!isPlus) stringResource(Res.string.app_name)
                                        else stringResource(Res.string.app_name_plus),
                                        color = colorScheme.onSurface,
                                        style = typography.titleLarge,
                                        fontFamily = typography.bodyLarge.fontFamily
                                    )
                                    Text(
                                        text = "${appInfo.versionName} (${appInfo.versionCode})",
                                        style = typography.labelLarge,
                                        color = colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }

                item {
                    SegmentedListItem(
                        onClick = { showLicense = true },
                        leadingContent = { Icon(painterResource(Res.drawable.gavel), null) },
                        content = { Text(stringResource(Res.string.license)) },
                        supportingContent = { Text("GNU General Public License Version 3") },
                        selected = showLicense,
                        shapes = segmentedListItemShapes(0, 1),
                        colors = listItemColors
                    )
                }
            }
        }
    }

    if (showLicense) {
        LicenseBottomSheet({ showLicense = false })
    }
}

@Preview
@Composable
private fun AboutScreenPreview() {
    TomatoTheme(dynamicColor = false) {
        AboutScreen(
            contentPadding = PaddingValues(),
            isPlus = true,
            onBack = {}
        )
    }
}

