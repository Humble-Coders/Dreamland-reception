@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)

package com.example.dreamland_reception.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.BillingScreen
import com.example.dreamland_reception.billing.StayBillingScreen
import com.example.dreamland_reception.ComplaintsScreen
import com.example.dreamland_reception.DashboardScreen
import com.example.dreamland_reception.DreamlandAppInitializer
import com.example.dreamland_reception.DreamlandForest
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandForestSurface
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandGoldBright
import com.example.dreamland_reception.DreamlandMuted
import com.example.dreamland_reception.DreamlandOnDark
import com.example.dreamland_reception.OrdersScreen
import com.example.dreamland_reception.ReportsScreen
import com.example.dreamland_reception.RoomsAndBookingsScreen
import com.example.dreamland_reception.SettingsScreen
import com.example.dreamland_reception.StaffScreen
import com.example.dreamland_reception.StaysScreen
import com.example.dreamland_reception.data.loading.DreamlandLoadCoordinator
import dreamlandreception.composeapp.generated.resources.Res
import dreamlandreception.composeapp.generated.resources.dreamland_logo
import org.jetbrains.compose.resources.painterResource

private val SidebarExpandedWidth = 280.dp
private val SidebarCollapsedWidth = 72.dp

internal enum class MainTab(val label: String) {
    Dashboard("Dashboard"),
    RoomsAndBookings("Rooms & Bookings"),
    Stays("Stays"),
    Billing("Billing"),
    Orders("Orders"),
    Complaints("Complaints"),
    Staff("Staff"),
    Reports("Reports"),
    Settings("Settings"),
}

private data class NavItem(
    val tab: MainTab,
    val icon: ImageVector,
    val title: String,
)

private val dreamlandNavItems: List<NavItem> = listOf(
    NavItem(MainTab.Dashboard, Icons.Filled.Dashboard, "Dashboard"),
    NavItem(MainTab.RoomsAndBookings, Icons.Filled.Hotel, "Rooms & Bookings"),
    NavItem(MainTab.Stays, Icons.Filled.Key, "Stays"),
    NavItem(MainTab.Billing, Icons.Filled.CreditCard, "Billing"),
    NavItem(MainTab.Orders, Icons.Filled.ShoppingBag, "Orders"),
    NavItem(MainTab.Complaints, Icons.Filled.Feedback, "Complaints"),
    NavItem(MainTab.Staff, Icons.Filled.Groups, "Staff"),
    NavItem(MainTab.Reports, Icons.Filled.BarChart, "Reports"),
    NavItem(MainTab.Settings, Icons.Filled.Settings, "Settings"),
)

/**
 * Root shell: jewellery-style sidebar (hover expand, content-hover collapse, animated width/header)
 * + top bar (menu when narrow, refresh) matching `navigation/JewelryApp.kt` behavior.
 */
@Composable
fun DreamlandApp() {
    var selectedTab by remember { mutableStateOf(MainTab.Dashboard) }
    var pendingBillingStayId by remember { mutableStateOf("") }
    var isSidebarExpanded by remember { mutableStateOf(true) }
    var isSidebarVisible by remember { mutableStateOf(true) }

    val globalLoading by DreamlandLoadCoordinator.loading.collectAsStateWithLifecycle()

    val contentInteractionSource = remember { MutableInteractionSource() }
    val isContentHovered by contentInteractionSource.collectIsHoveredAsState()

    LaunchedEffect(isContentHovered) {
        if (isContentHovered && isSidebarExpanded && isSidebarVisible) {
            isSidebarExpanded = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DreamlandEnhancedSidebar(
                isSidebarExpanded = isSidebarExpanded,
                isSidebarVisible = isSidebarVisible,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onSidebarToggle = { isSidebarExpanded = it },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .hoverable(contentInteractionSource),
            ) {
                DreamlandTopAppBar(
                    title = {
                        Text(
                            text = "Dreamland Reception",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    isSidebarExpanded = isSidebarExpanded,
                    isSidebarVisible = isSidebarVisible,
                    onToggleSidebar = {
                        isSidebarVisible = true
                        isSidebarExpanded = true
                    },
                    onRefresh = { DreamlandAppInitializer.refreshAllViewModels() },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    when (selectedTab) {
                        MainTab.Dashboard -> DashboardScreen(
                            onNewWalkIn = {
                                DreamlandAppInitializer.getStaysViewModel().openWalkIn()
                                selectedTab = MainTab.Stays
                            },
                            onNavigateToBookings = { selectedTab = MainTab.RoomsAndBookings },
                            onNavigateToOrders = { selectedTab = MainTab.Orders },
                            onNavigateToComplaints = { selectedTab = MainTab.Complaints },
                            onNavigateToStaff = { selectedTab = MainTab.Staff },
                        )
                        MainTab.RoomsAndBookings -> RoomsAndBookingsScreen(
                            onCheckIn = { booking ->
                                DreamlandAppInitializer.getStaysViewModel().prefillFromBooking(booking)
                                selectedTab = MainTab.Stays
                            },
                        )
                        MainTab.Stays -> StaysScreen(onNavigateToBilling = { stayId ->
                            pendingBillingStayId = stayId
                            selectedTab = MainTab.Billing
                        })
                        MainTab.Billing -> if (pendingBillingStayId.isNotBlank()) {
                            StayBillingScreen(
                                stayId = pendingBillingStayId,
                                onBack = { pendingBillingStayId = "" },
                            )
                        } else {
                            BillingScreen(onOpenStayBilling = { stayId ->
                                pendingBillingStayId = stayId
                            })
                        }
                        MainTab.Orders -> OrdersScreen()
                        MainTab.Complaints -> ComplaintsScreen()
                        MainTab.Staff -> StaffScreen()
                        MainTab.Reports -> ReportsScreen()
                        MainTab.Settings -> SettingsScreen()
                    }
                }
            }
        }

        if (globalLoading) {
            DreamlandGlobalLoadingOverlay()
        }
    }
}

@Composable
private fun DreamlandGlobalLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.size(120.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    color = DreamlandGold,
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DreamlandEnhancedSidebar(
    isSidebarExpanded: Boolean,
    isSidebarVisible: Boolean,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onSidebarToggle: (Boolean) -> Unit,
) {
    if (!isSidebarVisible) return

    val sidebarInteractionSource = remember { MutableInteractionSource() }
    val isSidebarHovered by sidebarInteractionSource.collectIsHoveredAsState()

    LaunchedEffect(isSidebarHovered) {
        if (isSidebarHovered && !isSidebarExpanded) {
            onSidebarToggle(true)
        }
    }

    val animatedWidth by animateDpAsState(
        targetValue = if (isSidebarExpanded) SidebarExpandedWidth else SidebarCollapsedWidth,
        animationSpec = tween(durationMillis = 300, easing = EaseInOutQuart),
        label = "dreamland_sidebar_width",
    )

    val headerHeight by animateDpAsState(
        targetValue = if (isSidebarExpanded) 150.dp else 64.dp,
        animationSpec = tween(durationMillis = 300, easing = EaseInOutQuart),
        label = "dreamland_sidebar_header_height",
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(animatedWidth)
            .background(
                Brush.verticalGradient(
                    colors = listOf(DreamlandForestSurface, DreamlandForest),
                ),
            )
            .border(BorderStroke(1.dp, DreamlandGold.copy(alpha = 0.35f)))
            .hoverable(sidebarInteractionSource),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DreamlandForestElevated, DreamlandForestSurface),
                        ),
                    ),
            ) {
                if (!isSidebarExpanded) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .align(Alignment.TopCenter)
                            .padding(top = 19.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Hotel,
                            contentDescription = "Dreamland",
                            tint = DreamlandGoldBright,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                ) {
                    val brandAlpha by animateFloatAsState(
                        targetValue = if (isSidebarExpanded) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 400,
                            delayMillis = 200,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "dreamland_brand_alpha",
                    )

                    if (isSidebarExpanded) {
                        Box(modifier = Modifier.alpha(brandAlpha)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(Res.drawable.dreamland_logo),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    text = "DREAMLAND",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = DreamlandGold,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                )
                                Text(
                                    text = "Reception",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DreamlandOnDark.copy(alpha = 0.92f),
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                dreamlandNavItems.forEach { item ->
                    DreamlandNavigationRow(
                        icon = item.icon,
                        title = item.title,
                        selected = selectedTab == item.tab,
                        onClick = { onTabSelected(item.tab) },
                        isExpanded = isSidebarExpanded,
                    )
                }
            }

            if (isSidebarExpanded) {
                val footerAlpha by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(300, delayMillis = 100),
                    label = "dreamland_footer_alpha",
                )
                Box(
                    modifier = Modifier
                        .alpha(footerAlpha)
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Version 1.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = DreamlandMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DreamlandNavigationRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    isExpanded: Boolean,
) {
    val backgroundColor = if (selected) DreamlandGold.copy(alpha = 0.18f) else Color.Transparent
    val textColor = if (selected) DreamlandGoldBright else DreamlandOnDark.copy(alpha = 0.92f)
    val iconColor = if (selected) DreamlandGoldBright else DreamlandGold.copy(alpha = 0.92f)

    val iconPadding by animateDpAsState(
        targetValue = if (isExpanded) 16.dp else 24.dp,
        animationSpec = tween(durationMillis = 300, easing = EaseInOutQuart),
        label = "dreamland_nav_icon_padding",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(backgroundColor)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .padding(start = iconPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(26.dp),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(300, delayMillis = 150)) +
                expandHorizontally(
                    animationSpec = tween(300, easing = EaseOutQuad),
                    expandFrom = Alignment.Start,
                ),
            exit = fadeOut(animationSpec = tween(200)) +
                shrinkHorizontally(
                    animationSpec = tween(200, easing = EaseInQuad),
                    shrinkTowards = Alignment.Start,
                ),
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 56.dp)
                .align(Alignment.CenterStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    color = textColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.25.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DreamlandTopAppBar(
    title: @Composable () -> Unit,
    isSidebarExpanded: Boolean,
    isSidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = DreamlandForestSurface,
    contentColor: Color = DreamlandOnDark,
    elevation: Dp = 4.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = backgroundColor,
        shadowElevation = elevation,
        modifier = modifier.border(1.dp, DreamlandGold.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isSidebarExpanded || !isSidebarVisible) {
                    IconButton(onClick = onToggleSidebar) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open sidebar",
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        title()
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = contentColor,
                        )
                    }
                }
            }
        }
    }
}
