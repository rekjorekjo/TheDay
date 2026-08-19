package io.github.thedayapp.ui.screens

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thedayapp.R
import io.github.thedayapp.ui.documents.AppDocument
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    document: AppDocument,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val content = remember(document) {
        loadDocumentContent(context, document)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(stringResource(document.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        SelectionContainer {
            Text(
                text = content ?: stringResource(R.string.document_read_failed),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }
    }
}

private fun loadDocumentContent(context: Context, document: AppDocument): String? {
    return try {
        context.resources.openRawResource(document.resourceId)
            .bufferedReader()
            .use { it.readText() }
    } catch (exception: Resources.NotFoundException) {
        Log.w("DocumentViewer", "Document resource was not found", exception)
        null
    } catch (exception: IOException) {
        Log.w("DocumentViewer", "Failed to read document resource", exception)
        null
    }
}