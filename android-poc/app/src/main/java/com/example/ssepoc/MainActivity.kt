package com.example.ssepoc

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.ssepoc.ui.compose.SseComposeScreen

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Compose", "XML Fragment")

            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                // Ambas as telas ficam sempre na árvore — só alternam tamanho.
                // Isso preserva o Fragment e o ViewModel ao trocar de aba.
                Box(modifier = Modifier.fillMaxSize()) {
                    SseComposeScreen(
                        modifier = if (selectedTab == 0) Modifier.fillMaxSize()
                                   else Modifier.size(0.dp)
                    )
                    XmlFragmentContainer(
                        modifier = if (selectedTab == 1) Modifier.fillMaxSize()
                                   else Modifier.size(0.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun XmlFragmentContainer(modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.widget.FrameLayout(context).apply {
                id = R.id.xml_fragment_container
                post {
                    val fm = (context as FragmentActivity).supportFragmentManager
                    if (fm.findFragmentById(R.id.xml_fragment_container) == null) {
                        fm.beginTransaction()
                            .replace(R.id.xml_fragment_container, com.example.ssepoc.ui.xml.SseFragment())
                            .commit()
                    }
                }
            }
        },
        modifier = modifier,
    )
}
