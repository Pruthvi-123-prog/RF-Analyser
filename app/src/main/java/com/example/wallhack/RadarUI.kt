package com.example.wallhack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

@Composable
fun RadarUI(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRadar(this)
    }
}

fun drawRadar(drawScope: DrawScope) {
    drawScope.apply {
        drawCircle(Color.Green, radius = 100f)
        // Add spikes or pulses based on sensor data
    }
}

@Composable
fun RadarScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Live Radar", modifier = Modifier.padding(16.dp))
        RadarUI(modifier = Modifier.fillMaxSize())
    }
}