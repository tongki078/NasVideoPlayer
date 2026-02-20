package org.nas.videoplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nas.videoplayer.domain.model.Category
import org.nas.videoplayer.cleanTitle

@Composable
fun HeroSection(category: Category, onInfoClick: () -> Unit, onPlayClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
            .clickable { onInfoClick() }
            .background(Color.Black)
    ) {
        TmdbAsyncImage(
            title = category.name,
            posterPath = category.posterPath,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            isLarge = true
        )
        
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black))))

        Card(
            modifier = Modifier.width(280.dp).height(400.dp).align(Alignment.TopCenter).padding(top = 40.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            TmdbAsyncImage(
                title = category.name, 
                posterPath = category.posterPath,
                modifier = Modifier.fillMaxSize(), 
                isLarge = true
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = category.name.cleanTitle(includeYear = false),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, shadow = Shadow(color = Color.Black, blurRadius = 8f)),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(45.dp).weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("재생", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onInfoClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(45.dp).weight(1f)
                ) {
                    Icon(Icons.Default.Info, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("정보", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
