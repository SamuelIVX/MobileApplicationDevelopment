package com.example.slideshow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.slideshow.ui.theme.SlideshowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlideshowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ImageWithButton(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun ImageWithButton(modifier: Modifier = Modifier) {
    var res by remember { mutableStateOf(1) }
    var targetText by remember {mutableStateOf("")}

    val image = when(res) {
        1 -> R.drawable.spiderman1
        2 -> R.drawable.spiderman2
        3 -> R.drawable.spiderman3
        4 -> R.drawable.spiderman4
        5 -> R.drawable.spiderman5
        else -> R.drawable.spiderman1
    }

    val caption = when(res){
        1 -> R.string.caption_1
        2 -> R.string.caption_2
        3 -> R.string.caption_3
        4 -> R.string.caption_4
        5 -> R.string.caption_5
        else -> R.string.caption_1
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ){
        Image(
            painter = painterResource(image),
            contentDescription = stringResource(caption),
            modifier = Modifier.size(300.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(caption),
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Button(onClick = {
                if (res <= 1)
                    res = 5
                else
                    res -= 1
            }){
                Text(text = stringResource(R.string.back_picture))
            }

            Button(onClick = {
                if (res >= 5)
                    res = 1
                else
                    res += 1
            }){
                Text(text = stringResource(R.string.next_picture))
            }

        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ){
            TextField(
                value = targetText,
                onValueChange = {targetText = it},
                label = {Text(stringResource(R.string.enter_picture_number))},
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Button(onClick = {
                val target = targetText.toIntOrNull()
                if (target != null && target in 1..5){
                    res = target
                    targetText = ""
                }
            }){
                Text(text = stringResource(R.string.go))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SlideshowTheme {
        ImageWithButton()
    }
}