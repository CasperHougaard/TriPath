package com.tripath.ui.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Reads and writes backup JSON through the Storage Access Framework, so exports land wherever the
 * user chooses (Google Drive, Files, a memory card) without the app needing storage permissions.
 */
internal suspend fun writeTextToUri(context: Context, uri: Uri, text: String) {
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write(text)
            }
        } ?: throw IOException("Could not open the selected file for writing")
    }
}

internal suspend fun readTextFromUri(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        } ?: throw IOException("Could not open the selected file for reading")
    }
}
