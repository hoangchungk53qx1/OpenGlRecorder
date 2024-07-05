package com.vn.chungha.openglrecoder

import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vn.chungha.openglrecoder.encode.ScreenRecordService
import com.vn.chungha.openglrecoder.ui.theme.OpenGlRecoderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenGlRecoderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreenRecord(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreenRecord(name: String, modifier: Modifier = Modifier) {

    val context = LocalContext.current
    val mediaProjectionManager =
        remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    val permissionResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGrant ->
            if (isGrant) {
                // Permission granted
            }
        }
    )

    val mediaProjectionResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == ComponentActivity.RESULT_OK && result.data != null) {
                ScreenRecordService.startService(context, result.resultCode, result.data!!)
            }
        }
    )

    LaunchedEffect(key1 = Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionResult.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        Button(
            onClick = {
                mediaProjectionResult.launch(mediaProjectionManager.createScreenCaptureIntent())
            },
        ) {
            Text(text = "Start", modifier = Modifier.padding(16.dp))
        }

        Button(onClick = {
            ScreenRecordService.stopService(context)
        }) {
            Text(text = "Stop")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OpenGlRecoderTheme {
        MainScreenRecord("Android")
    }
}