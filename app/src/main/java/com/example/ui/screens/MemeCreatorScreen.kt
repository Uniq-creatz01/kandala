package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.viewmodel.KandalaViewModel
import com.example.utils.MemeBitmapGenerator
import com.example.utils.MemeSticker
import com.example.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PhotoSource {
    data class Resource(val resId: Int, val title: String) : PhotoSource()
    data class Gallery(val uri: Uri) : PhotoSource()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemeCreatorScreen(
    viewModel: KandalaViewModel,
    onMemePosted: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State for image source
    var selectedSource by remember {
        mutableStateOf<PhotoSource>(PhotoSource.Resource(R.drawable.img_template_cat, "Surprised Cat"))
    }

    // Text states
    var topText by remember { mutableStateOf("WHEN YOU CLICK") }
    var bottomText by remember { mutableStateOf("CREATE MEME ON KANDALA HUB") }
    var caption by remember { mutableStateOf("Freshly baked meme from Kandala Hub! 🔥") }

    // Text styling states
    var fontSizeRatio by remember { mutableFloatStateOf(0.08f) }
    var isUppercase by remember { mutableStateOf(true) }
    var selectedTextColor by remember { mutableStateOf(AndroidColor.WHITE) }

    // Stickers state
    val stickers = remember { mutableStateListOf<MemeSticker>() }

    var isProcessing by remember { mutableStateOf(false) }

    // Gallery Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedSource = PhotoSource.Gallery(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Meme Creator Studio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        topText = ""
                        bottomText = ""
                        caption = ""
                        stickers.clear()
                    },
                    modifier = Modifier.testTag("reset_meme_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset fields")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Scrollable Editor Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Preview Canvas
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .testTag("meme_live_preview")
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Base Image
                    when (val src = selectedSource) {
                        is PhotoSource.Resource -> {
                            AsyncImage(
                                model = src.resId,
                                contentDescription = "Template photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        is PhotoSource.Gallery -> {
                            AsyncImage(
                                model = src.uri,
                                contentDescription = "Gallery photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Live Top Text
                    val displayTop = if (isUppercase) topText.uppercase() else topText
                    if (displayTop.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayTop,
                                color = Color(selectedTextColor),
                                fontWeight = FontWeight.Black,
                                fontSize = (18 * (fontSizeRatio / 0.08f)).sp,
                                textAlign = TextAlign.Center,
                                lineHeight = (22 * (fontSizeRatio / 0.08f)).sp
                            )
                        }
                    }

                    // Live Bottom Text
                    val displayBottom = if (isUppercase) bottomText.uppercase() else bottomText
                    if (displayBottom.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = displayBottom,
                                color = Color(selectedTextColor),
                                fontWeight = FontWeight.Black,
                                fontSize = (18 * (fontSizeRatio / 0.08f)).sp,
                                textAlign = TextAlign.Center,
                                lineHeight = (22 * (fontSizeRatio / 0.08f)).sp
                            )
                        }
                    }

                    // Live Stickers
                    stickers.forEach { sticker ->
                        Text(
                            text = sticker.emoji,
                            fontSize = 32.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(
                                    start = (sticker.relX * 100).dp,
                                    top = (sticker.relY * 100).dp
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 1: Upload Photo from Gallery or Select Template
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "1. Choose Meme Photo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Prominent Gallery Upload Button
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("upload_gallery_photo_button")
                    ) {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Upload from gallery",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upload Photo from Gallery",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Or pick a popular template:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Templates Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TemplateChip(
                            title = "Surprised Cat",
                            resId = R.drawable.img_template_cat,
                            isSelected = selectedSource is PhotoSource.Resource && (selectedSource as PhotoSource.Resource).resId == R.drawable.img_template_cat,
                            onClick = {
                                selectedSource = PhotoSource.Resource(R.drawable.img_template_cat, "Surprised Cat")
                            }
                        )

                        TemplateChip(
                            title = "Doge Side-Eye",
                            resId = R.drawable.img_template_doge,
                            isSelected = selectedSource is PhotoSource.Resource && (selectedSource as PhotoSource.Resource).resId == R.drawable.img_template_doge,
                            onClick = {
                                selectedSource = PhotoSource.Resource(R.drawable.img_template_doge, "Doge Side-Eye")
                            }
                        )

                        TemplateChip(
                            title = "Kandala Banner",
                            resId = R.drawable.img_kandala_banner,
                            isSelected = selectedSource is PhotoSource.Resource && (selectedSource as PhotoSource.Resource).resId == R.drawable.img_kandala_banner,
                            onClick = {
                                selectedSource = PhotoSource.Resource(R.drawable.img_kandala_banner, "Kandala Banner")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Meme Text Inputs
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "2. Customize Meme Text",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = topText,
                        onValueChange = { topText = it },
                        label = { Text("Top Text") },
                        placeholder = { Text("e.g. WHEN YOU CLICK") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_top_text"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = bottomText,
                        onValueChange = { bottomText = it },
                        label = { Text("Bottom Text") },
                        placeholder = { Text("e.g. CREATE MEME") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_bottom_text"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text Color Selection
                    Text(
                        text = "Text Color:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val colors = listOf(
                            AndroidColor.WHITE to "White",
                            AndroidColor.YELLOW to "Yellow",
                            AndroidColor.CYAN to "Cyan",
                            AndroidColor.parseColor("#EC4899") to "Pink",
                            AndroidColor.GREEN to "Green",
                            AndroidColor.BLACK to "Black"
                        )
                        colors.forEach { (colorInt, _) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorInt))
                                    .border(
                                        width = if (selectedTextColor == colorInt) 3.dp else 1.dp,
                                        color = if (selectedTextColor == colorInt) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedTextColor = colorInt },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedTextColor == colorInt) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = if (colorInt == AndroidColor.WHITE || colorInt == AndroidColor.YELLOW || colorInt == AndroidColor.CYAN) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Text Controls: Uppercase and Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ALL CAPS (Classic Meme)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = isUppercase,
                            onCheckedChange = { isUppercase = it },
                            modifier = Modifier.testTag("uppercase_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Font Size",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = fontSizeRatio,
                        onValueChange = { fontSizeRatio = it },
                        valueRange = 0.05f..0.14f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3: Reaction Stickers
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. Add Reaction Stickers",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (stickers.isNotEmpty()) {
                            Text(
                                text = "Clear (${stickers.size})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { stickers.clear() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val emojiList = listOf("🔥", "💀", "😂", "💯", "🕶️", "🤡", "🚀", "👑")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojiList.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        // Place sticker randomly around bottom-right or center
                                        val rx = 0.7f + (Math.random().toFloat() * 0.15f)
                                        val ry = 0.5f + (Math.random().toFloat() * 0.3f)
                                        stickers.add(MemeSticker(emoji, rx, ry))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 4: Caption
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Post Caption") },
                placeholder = { Text("Add something funny or witty...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_caption"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (isProcessing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rendering & saving high-quality meme...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Post & Save to Device Button
                Button(
                    onClick = {
                        isProcessing = true
                        coroutineScope.launch {
                            val baseBitmap = loadBaseBitmap(context, selectedSource)
                            if (baseBitmap != null) {
                                val finishedMemeBitmap = MemeBitmapGenerator.createMemeBitmap(
                                    baseBitmap = baseBitmap,
                                    topText = topText,
                                    bottomText = bottomText,
                                    textColor = selectedTextColor,
                                    fontSizeRatio = fontSizeRatio,
                                    isUppercase = isUppercase,
                                    stickers = stickers
                                )
                                viewModel.postCreatedMeme(
                                    bitmap = finishedMemeBitmap,
                                    topText = topText,
                                    bottomText = bottomText,
                                    caption = caption,
                                    saveToDeviceImmediately = true,
                                    onComplete = {
                                        isProcessing = false
                                        onMemePosted()
                                    }
                                )
                            } else {
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("post_and_save_meme_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Post to Kandala Hub & Save to Device",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Save to Storage Only Button
                OutlinedButton(
                    onClick = {
                        isProcessing = true
                        coroutineScope.launch {
                            val baseBitmap = loadBaseBitmap(context, selectedSource)
                            if (baseBitmap != null) {
                                val finishedMemeBitmap = MemeBitmapGenerator.createMemeBitmap(
                                    baseBitmap = baseBitmap,
                                    topText = topText,
                                    bottomText = bottomText,
                                    textColor = selectedTextColor,
                                    fontSizeRatio = fontSizeRatio,
                                    isUppercase = isUppercase,
                                    stickers = stickers
                                )
                                StorageUtils.saveMemeToDeviceStorage(context, finishedMemeBitmap, "Kandala_Created")
                            }
                            isProcessing = false
                            onMemePosted()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_device_only_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save to Device Storage Only",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TemplateChip(
    title: String,
    resId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resId,
                contentDescription = title,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun loadBaseBitmap(context: android.content.Context, source: PhotoSource): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            when (source) {
                is PhotoSource.Resource -> {
                    BitmapFactory.decodeResource(context.resources, source.resId)
                }
                is PhotoSource.Gallery -> {
                    context.contentResolver.openInputStream(source.uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
