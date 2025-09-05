package com.ssolstice.camera.manual.compose.widgets

import android.app.Activity
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.utils.UpdateState

@Composable
fun UpdateDialog(updateState: UpdateState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    when (updateState) {
        is UpdateState.Force -> {
            AlertDialog(
                onDismissRequest = {
                    (context as Activity).finish()
                },
                title = { Text("Update Required") },
                text = { Text("Please update to continue using the app.") },
                confirmButton = {
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW, updateState.url.toUri()
                            )
                        )
                        (context as Activity).finish()
                    }) {
                        Text(stringResource(R.string.update))
                    }
                })
        }

        is UpdateState.Recommended -> {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text("Update Recommended") },
                text = { Text("A new version is available. For the best experience, please update.") },
                confirmButton = {
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW, updateState.url.toUri()
                            )
                        )
                    }) {
                        Text(stringResource(R.string.update))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { onDismiss() }) {
                        Text(stringResource(R.string.later))
                    }
                })
        }

        is UpdateState.Optional -> {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                title = { Text("New Version Available") },
                text = { Text("You can update to the latest version.") },
                confirmButton = {
                    Button(onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW, updateState.url.toUri()
                            )
                        )
                    }) {
                        Text(stringResource(R.string.update))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { onDismiss() }) {
                        Text(stringResource(R.string.skip))
                    }
                })
        }

        UpdateState.None -> Unit
    }
}
