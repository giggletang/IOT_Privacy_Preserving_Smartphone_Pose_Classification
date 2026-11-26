
package com.csc8223.posefl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PoseFLScreen(
    vm: AppVM,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Collect", "Train", "Infer")
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val serverFieldRequester = remember { BringIntoViewRequester() }
    val clientFieldRequester = remember { BringIntoViewRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "PoseFL",
            style = MaterialTheme.typography.headlineMedium
        )

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    Text("Collect Sensor Data")

                    POSES.forEachIndexed { index, pose ->
                        Row {
                            RadioButton(
                                selected = vm.label == index,
                                onClick = { vm.label = index }
                            )
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(pose)

                                Text(
                                    POSE_INSTRUCTIONS[index],
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = vm.session,
                        onValueChange = { vm.session = it },
                        label = { Text("Session ID") }
                    )

                    Text("Dataset split")

                    // Includes calibration as a third option.
                    Row {
                        listOf("train", "test", "calib").forEach { option ->
                            TextButton(
                                onClick = { vm.split = option }
                            ) {
                                Text(
                                    if (vm.split == option) {
                                        "✓ $option"
                                    } else {
                                        option
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { vm.startRecord() },
                        enabled = vm.mode == "idle"
                    ) {
                        Text("Start Recording")
                    }

                    Button(
                        onClick = { vm.stop() },
                        enabled = vm.mode != "idle"
                    ) {
                        Text("Stop")
                    }

                    Text("Recorded: ${vm.count} windows")
                    Text("Gravity: ${vm.gravity}")
                    Text(vm.summary)

                    OutlinedButton(
                        onClick = {
                            vm.store.export(vm.getApplication())
                        }
                    ) {
                        Text("Export CSV")
                    }
                }

                1 -> {
                    Text("Train Models")

                    Text("Feature configuration")

                    FeatureConfig.entries.forEach { option ->
                        Row {
                            RadioButton(
                                selected = vm.cfg == option,
                                onClick = { vm.cfg = option }
                            )
                            Text(
                                option.name,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    Button(onClick = { vm.trainLocal() }) {
                        Text("Train Local Model")
                    }

                    OutlinedTextField(
                        value = vm.serverUrl,
                        onValueChange = { vm.serverUrl = it },
                        label = { Text("FL Server URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(serverFieldRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    scope.launch {
                                        delay(250)
                                        serverFieldRequester.bringIntoView()
                                    }
                                }
                            }
                    )

                    OutlinedTextField(
                        value = vm.clientId,
                        onValueChange = { vm.clientId = it },
                        label = { Text("Client ID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(clientFieldRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    scope.launch {
                                        delay(250)
                                        clientFieldRequester.bringIntoView()
                                    }
                                }
                            }
                    )

                    TextButton(onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }) {
                        Text("Hide Keyboard")
                    }

                    Button(onClick = { vm.runRound() }) {
                        Text("Run FL Round")
                    }

                    Button(onClick = { vm.refreshGlobal() }) {
                        Text("Refresh Global Model")
                    }

                    Text("Activity log")

                    vm.log.forEach { message ->
                        Text(message)
                    }
                }

                2 -> {
                    Text("Live Pose Prediction")

                    listOf("local", "global").forEach { option ->
                        Row {
                            RadioButton(
                                selected = vm.inferModel == option,
                                onClick = { vm.inferModel = option }
                            )
                            Text(
                                option,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { vm.startInfer() },
                        enabled = vm.mode == "idle"
                    ) {
                        Text("Start Inference")
                    }

                    Button(
                        onClick = { vm.stop() },
                        enabled = vm.mode != "idle"
                    ) {
                        Text("Stop")
                    }

                    Text(
                        if (vm.pred in POSES.indices) {
                            "Prediction: ${POSES[vm.pred]}"
                        } else {
                            "No prediction yet"
                        }
                    )

                    POSES.forEachIndexed { index, pose ->
                        Text(pose)
                        LinearProgressIndicator(
                            progress = {
                                vm.probs.getOrElse(index) { 0f }
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(onClick = { vm.evaluate("local") }) {
                        Text("Evaluate Local")
                    }

                    Button(onClick = { vm.evaluate("global") }) {
                        Text("Evaluate Global")
                    }

                    vm.log.forEach { message ->
                        Text(message)
                    }
                }
            }
        }
    }
}
