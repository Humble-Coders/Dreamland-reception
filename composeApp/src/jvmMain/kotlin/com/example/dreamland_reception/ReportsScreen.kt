package com.example.dreamland_reception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dreamland_reception.ui.viewmodel.ReportsUiState
import com.example.dreamland_reception.ui.viewmodel.ReportsViewModel

@Composable
fun ReportsScreen(vm: ReportsViewModel = DreamlandAppInitializer.getReportsViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ANALYTICS", style = MaterialTheme.typography.labelLarge, color = DreamlandGold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text("Reports", style = MaterialTheme.typography.headlineLarge, color = DreamlandOnDark)
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            is ReportsUiState.Loading -> CircularProgressIndicator(color = DreamlandGold)
            is ReportsUiState.Error -> ErrorText(s.message)
            is ReportsUiState.Success -> {
                val r = s.summary
                ComingSoonContent(
                    "Revenue: ₹${r.totalRevenue.toLong()} · " +
                    "Bookings: ${r.totalBookings} · " +
                    "Open complaints: ${r.openComplaints}"
                )
            }
        }
    }
}
