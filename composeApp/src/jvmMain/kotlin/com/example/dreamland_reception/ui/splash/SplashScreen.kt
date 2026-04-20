package com.example.dreamland_reception.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dreamland_reception.DreamlandForest
import com.example.dreamland_reception.DreamlandForestElevated
import com.example.dreamland_reception.DreamlandGold
import com.example.dreamland_reception.DreamlandGoldBright
import com.example.dreamland_reception.DreamlandOnDark
import dreamlandreception.composeapp.generated.resources.Res
import dreamlandreception.composeapp.generated.resources.dreamland_logo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** Left-to-right text reveal (from Gagan Jewellers `SplashScreen.kt`). */
private class LeftToRightRevealShape(private val revealFraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(
        Rect(
            left = 0.5f,
            top = 0.5f,
            right = size.width * revealFraction,
            bottom = size.height,
        ),
    )
}

@Composable
fun SplashScreenDesktop(onFinished: () -> Unit) {
    val mainScale = remember { Animatable(0.9f) }
    val mainAlpha = remember { Animatable(0f) }
    val taglineReveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                mainAlpha.animateTo(1f, animationSpec = tween(1000, easing = EaseOutQuad))
            }
            launch {
                mainScale.animateTo(1f, animationSpec = tween(1000, easing = EaseOutQuad))
            }
            launch {
                delay(700)
                taglineReveal.animateTo(1f, animationSpec = tween(900, easing = LinearEasing))
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(3000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DreamlandForestElevated, DreamlandForest),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .scale(mainScale.value)
                    .alpha(mainAlpha.value),
            ) {
//                Icon(
//                    imageVector = Icons.Filled.Hotel,
//                    contentDescription = "Dreamland",
//                    tint = DreamlandGoldBright,
//                    modifier = Modifier.size(50.dp),
//                )
                Image(
                    painter = painterResource(Res.drawable.dreamland_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .height(56.dp),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = "DREAMLAND",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp,
                        color = DreamlandGold,
                    )
                    Text(
                        text = "Reception",
                        style = MaterialTheme.typography.displaySmall,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(20.dp)
                        .width(280.dp),
                ) {
                    Text(
                        text = "WELCOME YOUR GUESTS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DreamlandOnDark.copy(alpha = 0f),
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "WELCOME YOUR GUESTS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DreamlandGold.copy(alpha = 0.92f),
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clip(LeftToRightRevealShape(taglineReveal.value)),
                    )
                }
            }
        }
    }
}
