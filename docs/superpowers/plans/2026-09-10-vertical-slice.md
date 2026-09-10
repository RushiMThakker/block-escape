# Unblock Vertical Slice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A genuinely playable single Rush Hour level — Compose UI, real drag-to-slide, win detection, undo — backed by a pure-Kotlin domain model and a BFS solver, in a new Gradle project at `D:\BlockEscape`.

**Architecture:** Single Gradle module (`app`) split into `domain` (pure Kotlin board/vehicle logic, no Android deps), `solver` (pure Kotlin BFS), `level` (kotlinx.serialization JSON parsing + Android asset loading), and `ui` (Compose screen + ViewModel). Domain/solver/level-parsing are unit-tested with plain JUnit; Compose rendering and the Android-asset-reading edge are verified by building and running the app (no emulator/instrumentation tests this session — see spec's environment notes).

**Tech Stack:** Kotlin, Jetpack Compose (BOM), kotlinx.serialization, JUnit4, Gradle (Kotlin DSL) with a version catalog, minSdk 24 / target+compileSdk 35.

**Package:** `com.rushi.unblock`. **App name:** Unblock.

**Spec:** `docs/superpowers/specs/2026-09-10-vertical-slice-design.md`

> **Known environment issue — read before running any `.\gradlew.bat` command in this
> session's sandbox:** if a Gradle invocation fails with
> `java.io.IOException: Unable to establish loopback connection`, this is **not** a
> Gradle/JDK/AGP version problem — it's already been root-caused (during Task 1) to the
> sandboxed shell's default `%TEMP%` directory blocking the loopback socket the Gradle
> wrapper's client JVM uses to talk to its daemon. **Do not re-diagnose this as a
> version mismatch.** Fix: before running `gradlew`, set
> `$env:TEMP='C:\jtmp'; $env:TMP='C:\jtmp'` (creating the directory first if needed:
> `New-Item -ItemType Directory -Force C:\jtmp`). This is a per-invocation/per-session
> workaround, not something baked into the repo, since it's specific to this sandboxed
> environment and would be wrong on an unaffected machine.

---

## Task 1: Toolchain — JDK, Android SDK, Gradle wrapper

No app code yet. This task gets a working `./gradlew` that can compile and run Android
builds from the command line, independent of the Android Studio GUI.

**Files:**
- Create: `D:\BlockEscape\gradle.properties`
- Create: `D:\BlockEscape\local.properties` (gitignored — machine-specific SDK path)
- Create: `D:\BlockEscape\.gitignore`

- [ ] **Step 1: Confirm Android Studio finished installing, find its bundled JBR (JDK)**

Run: `powershell -Command "Get-Process 'Android Studio*' -ErrorAction SilentlyContinue; Test-Path 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'"`

Expected: `True` (installer finished; JBR present). If `False`, wait and re-check — do not
proceed until it's there. If the winget install is still the background task from the
brainstorming session, check its output file instead of re-running winget.

- [ ] **Step 2: Ask the user before downloading the Android SDK command-line tools and components**

This step downloads several files (cmdline-tools zip ~150MB from `dl.google.com`,
plus platform-tools, platform 35, build-tools, emulator, and an x86_64 system image —
combined roughly 2-3GB). Per the standing rule on downloads, pause here and ask the user
to confirm before running Step 3, even though the earlier "install Android Studio to D:"
approval covered the IDE — the SDK components are separate downloads. State the exact
URLs/packages and total approximate size when asking.

- [ ] **Step 3: Download and extract Android cmdline-tools to D:\Android\Sdk**

Run:
```
powershell -Command "New-Item -ItemType Directory -Force -Path D:\Android\Sdk\cmdline-tools | Out-Null; Invoke-WebRequest -Uri https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip -OutFile D:\Android\cmdline-tools.zip"
powershell -Command "Expand-Archive -Path D:\Android\cmdline-tools.zip -DestinationPath D:\Android\Sdk\cmdline-tools -Force"
powershell -Command "Move-Item D:\Android\Sdk\cmdline-tools\cmdline-tools D:\Android\Sdk\cmdline-tools\latest"
```
Expected: `D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat` exists.

If that exact versioned URL 404s (Google rotates build numbers), fetch the current one
from https://developer.android.com/studio#command-tools first — don't guess a version.

- [ ] **Step 4: Install SDK components headlessly**

Run (using the Studio JBR as the JDK, since system JDK 24 is too new for `sdkmanager`):
```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=D:\Android\Sdk --licenses
D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=D:\Android\Sdk "platform-tools" "platforms;android-35" "build-tools;35.0.0" "emulator" "system-images;android-35;google_apis;x86_64"
```
Accept all licenses when prompted (`y` for each). Expected: commands complete without
error; `D:\Android\Sdk\platform-tools\adb.exe` exists.

- [ ] **Step 5: Create an AVD**

Run:
```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
D:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat create avd -n unblock_test -k "system-images;android-35;google_apis;x86_64" -d pixel_6 --force
```
Expected: "Created AVD" confirmation, no error.

- [ ] **Step 6: Write `.gitignore`**

```gitignore
*.iml
.gradle/
/local.properties
.idea/
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
D:/Android
```

- [ ] **Step 7: Write `local.properties`**

```properties
sdk.dir=D\:\\Android\\Sdk
```

- [ ] **Step 8: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.java.home=C\:\\Program Files\\Android Studio\\jbr
android.useAndroidX=true
kotlin.code.style=official
```

Note: `org.gradle.java.home` must point at the actual Studio install path found in Step
1 (it's usually `C:\Program Files\Android\Android Studio\jbr` — verify and correct the
path here if Step 1 found it somewhere else).

- [ ] **Step 9: Generate the Gradle wrapper using Studio's bundled Gradle**

Android Studio ships Gradle under its own install; the simplest reliable way to bootstrap
`gradlew` without a separate global Gradle install is to invoke Studio's bundled Gradle
distribution directly once. Find it and run `gradle wrapper`:

Run:
```
powershell -Command "Get-ChildItem 'C:\Program Files\Android\Android Studio\plugins\gradle\lib' -Filter 'gradle-launcher-*.jar' -ErrorAction SilentlyContinue"
```

If that doesn't turn up a usable standalone `gradle` binary (Studio's bundled Gradle
isn't meant to be invoked standalone this way in newer versions), fall back to
downloading the wrapper files directly instead — this needs the same one-time download
confirmation as Step 2 (small files, ~60KB total, from `services.gradle.org` /
`raw.githubusercontent.com/gradle/gradle`):

```
powershell -Command "New-Item -ItemType Directory -Force -Path D:\BlockEscape\gradle\wrapper | Out-Null; Invoke-WebRequest -Uri https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar -OutFile D:\BlockEscape\gradle\wrapper\gradle-wrapper.jar"
```

Then hand-write `D:\BlockEscape\gradle\wrapper\gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

And `D:\BlockEscape\gradlew.bat`:
```bat
@rem Standard Gradle wrapper launcher for Windows
@if "%DEBUG%"=="" @echo off
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
goto execute
:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
exit /b 1
:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
```

Expected end state either way: `D:\BlockEscape\gradlew.bat` and
`D:\BlockEscape\gradle\wrapper\gradle-wrapper.jar` + `gradle-wrapper.properties` exist,
and running `.\gradlew.bat --version` from `D:\BlockEscape` downloads Gradle 8.9 on
first run and prints its version banner without error.

- [ ] **Step 10: Commit**

```bash
git add .gitignore gradle.properties gradlew.bat gradle/wrapper
git commit -m "chore: bootstrap gradle wrapper and toolchain config"
```

(`local.properties` is gitignored — do not add it.)

---

## Task 2: Gradle project scaffold (empty app that builds and launches)

**Files:**
- Create: `D:\BlockEscape\settings.gradle.kts`
- Create: `D:\BlockEscape\build.gradle.kts`
- Create: `D:\BlockEscape\gradle\libs.versions.toml`
- Create: `D:\BlockEscape\app\build.gradle.kts`
- Create: `D:\BlockEscape\app\src\main\AndroidManifest.xml`
- Create: `D:\BlockEscape\app\src\main\java\com\rushi\unblock\MainActivity.kt`
- Create: `D:\BlockEscape\app\src\main\java\com\rushi\unblock\ui\theme\Theme.kt`
- Create: `D:\BlockEscape\app\src\main\res\values\strings.xml`

- [ ] **Step 1: Write the version catalog**

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
composeBom = "2024.12.01"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
kotlinxSerializationJson = "1.7.3"
junit = "4.13.2"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Write root `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Unblock"
include(":app")
```

- [ ] **Step 3: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 4: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rushi.unblock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rushi.unblock"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
```

- [ ] **Step 5: Write `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.Material.Light.NoActionBar"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 6: Write `strings.xml`**

```xml
<resources>
    <string name="app_name">Unblock</string>
</resources>
```

- [ ] **Step 7: Write a minimal `Theme.kt`**

```kotlin
package com.rushi.unblock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun UnblockTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
```

- [ ] **Step 8: Write a placeholder `MainActivity.kt`**

```kotlin
package com.rushi.unblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.rushi.unblock.ui.theme.UnblockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnblockTheme {
                Surface(modifier = Modifier) {
                    Text("Unblock — scaffold OK")
                }
            }
        }
    }
}
```

- [ ] **Step 9: Build**

Run: `cd D:\BlockEscape; .\gradlew.bat assembleDebug`

Expected: `BUILD SUCCESSFUL`, and `app\build\outputs\apk\debug\app-debug.apk` exists. If
this is the very first Gradle invocation it will also download the Gradle 8.9
distribution and all dependencies — expect this to take several minutes and require
network access.

- [ ] **Step 10: Install and launch on the AVD, confirm it runs**

Run:
```
D:\Android\Sdk\emulator\emulator.exe -avd unblock_test -no-snapshot &
D:\Android\Sdk\platform-tools\adb.exe wait-for-device
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
D:\Android\Sdk\platform-tools\adb.exe shell am start -n com.rushi.unblock/.MainActivity
```
Expected: emulator boots, app installs, launches, screen shows "Unblock — scaffold OK".

- [ ] **Step 11: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app/
git commit -m "feat: scaffold Compose app project, builds and launches"
```

---

## Task 3: Domain model — `Vehicle`, `Board`, legal moves, win condition

**Files:**
- Create: `app/src/main/java/com/rushi/unblock/domain/Orientation.kt`
- Create: `app/src/main/java/com/rushi/unblock/domain/Vehicle.kt`
- Create: `app/src/main/java/com/rushi/unblock/domain/Board.kt`
- Test: `app/src/test/java/com/rushi/unblock/domain/BoardTest.kt`

- [ ] **Step 1: Write `Orientation.kt` and `Vehicle.kt`**

```kotlin
package com.rushi.unblock.domain

enum class Orientation { HORIZONTAL, VERTICAL }
```

```kotlin
package com.rushi.unblock.domain

data class Vehicle(
    val id: String,
    val orientation: Orientation,
    val length: Int,
    val row: Int,
    val col: Int,
    val isPrimary: Boolean = false
)
```

- [ ] **Step 2: Write the failing test for `Board`**

`app/src/test/java/com/rushi/unblock/domain/BoardTest.kt`:
```kotlin
package com.rushi.unblock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    private fun twoCarBoard(): Board = Board(
        width = 6,
        height = 6,
        exitRow = 2,
        vehicles = listOf(
            Vehicle(id = "primary", orientation = Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
            Vehicle(id = "blocker", orientation = Orientation.VERTICAL, length = 3, row = 1, col = 4)
        )
    )

    @Test
    fun `horizontal vehicle can slide right to the edge when unblocked`() {
        val board = twoCarBoard()
        assertEquals(0..3, board.legalMoves("primary"))
    }

    @Test
    fun `vertical blocker can slide down and clear the exit row`() {
        val board = twoCarBoard()
        assertEquals(-1..2, board.legalMoves("blocker"))
    }

    @Test
    fun `vehicle at the left wall cannot move further left`() {
        val board = twoCarBoard()
        assertEquals(0, board.legalMoves("primary").first)
    }

    @Test
    fun `move returns a new board with the vehicle shifted`() {
        val board = twoCarBoard()
        val moved = board.move("primary", 2)
        assertEquals(2, moved.vehicle("primary").col)
        assertEquals(0, board.vehicle("primary").col)
    }

    @Test
    fun `move throws for an illegal delta`() {
        val board = twoCarBoard()
        try {
            board.move("primary", 5)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `board is not solved before the primary vehicle reaches the right edge`() {
        assertFalse(twoCarBoard().isSolved)
    }

    @Test
    fun `board is solved once the primary vehicle's front reaches the right edge`() {
        val solved = twoCarBoard()
            .move("blocker", 2)
            .move("primary", 4)
        assertTrue(solved.isSolved)
    }
}
```

- [ ] **Step 3: Run the test, verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.domain.BoardTest"`
Expected: FAIL — `Board` class doesn't exist yet (compile error).

- [ ] **Step 4: Write `Board.kt`**

```kotlin
package com.rushi.unblock.domain

data class Board(
    val width: Int = 6,
    val height: Int = 6,
    val exitRow: Int = 2,
    val vehicles: List<Vehicle>
) {
    fun vehicle(id: String): Vehicle = vehicles.first { it.id == id }

    private fun occupiedCells(vehicle: Vehicle): List<Pair<Int, Int>> =
        when (vehicle.orientation) {
            Orientation.HORIZONTAL -> (vehicle.col until vehicle.col + vehicle.length).map { vehicle.row to it }
            Orientation.VERTICAL -> (vehicle.row until vehicle.row + vehicle.length).map { it to vehicle.col }
        }

    private fun occupancy(excludeId: String): Set<Pair<Int, Int>> =
        vehicles.filter { it.id != excludeId }.flatMap { occupiedCells(it) }.toSet()

    fun legalMoves(vehicleId: String): IntRange {
        val v = vehicle(vehicleId)
        val occupied = occupancy(excludeId = vehicleId)

        var minDelta = 0
        var maxDelta = 0

        if (v.orientation == Orientation.HORIZONTAL) {
            var c = v.col - 1
            while (c >= 0 && (v.row to c) !in occupied) {
                minDelta = c - v.col
                c--
            }
            val frontCol = v.col + v.length - 1
            var nc = frontCol + 1
            while (nc < width && (v.row to nc) !in occupied) {
                maxDelta = nc - frontCol
                nc++
            }
        } else {
            var r = v.row - 1
            while (r >= 0 && (r to v.col) !in occupied) {
                minDelta = r - v.row
                r--
            }
            val frontRow = v.row + v.length - 1
            var nr = frontRow + 1
            while (nr < height && (nr to v.col) !in occupied) {
                maxDelta = nr - frontRow
                nr++
            }
        }

        return minDelta..maxDelta
    }

    fun move(vehicleId: String, delta: Int): Board {
        if (delta == 0) return this
        val range = legalMoves(vehicleId)
        require(delta in range) { "Illegal move: delta=$delta not in $range for vehicle $vehicleId" }
        val v = vehicle(vehicleId)
        val moved = when (v.orientation) {
            Orientation.HORIZONTAL -> v.copy(col = v.col + delta)
            Orientation.VERTICAL -> v.copy(row = v.row + delta)
        }
        return copy(vehicles = vehicles.map { if (it.id == vehicleId) moved else it })
    }

    val isSolved: Boolean
        get() {
            val primary = vehicles.first { it.isPrimary }
            val frontCol = primary.col + primary.length - 1
            return primary.row == exitRow && frontCol == width - 1
        }
}
```

Win condition note: the game ends the moment the primary vehicle's front edge reaches
the rightmost column (touches the exit wall) — it doesn't need to slide fully off-board.
This matches how most Rush Hour implementations actually trigger the win, keeps
`legalMoves` free of exit-specific special-casing, and avoids modeling an off-board
position. If it ever feels anticlimactic in playtesting, revisit — the fix would only be
an animation flourish after `isSolved` fires, not a change to this logic.

- [ ] **Step 5: Run the test, verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.domain.BoardTest"`
Expected: `BUILD SUCCESSFUL`, all 7 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/rushi/unblock/domain app/src/test/java/com/rushi/unblock/domain
git commit -m "feat: add Board/Vehicle domain model with legal-move logic"
```

---

## Task 4: BFS solver

**Files:**
- Create: `app/src/main/java/com/rushi/unblock/solver/Solver.kt`
- Test: `app/src/test/java/com/rushi/unblock/solver/SolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.rushi.unblock.solver

import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {

    @Test
    fun `already-solved board needs zero moves`() {
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 4, isPrimary = true)
            )
        )
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(0, result.minMoves)
    }

    @Test
    fun `two-move puzzle is solved in two moves`() {
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
                Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
            )
        )
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(2, result.minMoves)
    }

    @Test
    fun `an impossible board is reported unsolvable`() {
        // Primary boxed in on both sides by vehicles that cannot move out of row 2.
        val board = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 2, isPrimary = true),
                Vehicle("left", Orientation.VERTICAL, length = 6, row = 0, col = 1),
                Vehicle("right", Orientation.VERTICAL, length = 6, row = 0, col = 4)
            )
        )
        val result = Solver.solve(board)
        assertTrue(!result.solvable)
        assertEquals(-1, result.minMoves)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.solver.SolverTest"`
Expected: FAIL — `Solver` doesn't exist yet.

- [ ] **Step 3: Write `Solver.kt`**

```kotlin
package com.rushi.unblock.solver

import com.rushi.unblock.domain.Board

object Solver {

    data class Result(val solvable: Boolean, val minMoves: Int)

    fun solve(initial: Board): Result {
        if (initial.isSolved) return Result(true, 0)

        val visited = mutableSetOf(stateKey(initial))
        var frontier = listOf(initial)
        var depth = 0

        while (frontier.isNotEmpty()) {
            depth++
            val next = mutableListOf<Board>()
            for (board in frontier) {
                for (vehicle in board.vehicles) {
                    val range = board.legalMoves(vehicle.id)
                    for (delta in range) {
                        if (delta == 0) continue
                        val moved = board.move(vehicle.id, delta)
                        if (moved.isSolved) return Result(true, depth)
                        val key = stateKey(moved)
                        if (visited.add(key)) next.add(moved)
                    }
                }
            }
            frontier = next
        }
        return Result(false, -1)
    }

    private fun stateKey(board: Board): String =
        board.vehicles.sortedBy { it.id }.joinToString(";") { "${it.id}:${it.row},${it.col}" }
}
```

Each BFS "move" is one full slide of one vehicle to a reachable cell (matching how Rush
Hour scores moves — sliding a car 3 cells in one drag is 1 move, not 3), so `minMoves`
here means the same thing a player's move counter will mean in Task 6.

- [ ] **Step 4: Run the test, verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.solver.SolverTest"`
Expected: `BUILD SUCCESSFUL`, all 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rushi/unblock/solver app/src/test/java/com/rushi/unblock/solver
git commit -m "feat: add BFS solver for shortest-solution length"
```

---

## Task 5: Level format — JSON parsing + starter level

**Files:**
- Create: `app/src/main/java/com/rushi/unblock/level/Level.kt`
- Create: `app/src/main/java/com/rushi/unblock/level/LevelRepository.kt`
- Create: `app/src/main/assets/levels/level_001.json`
- Test: `app/src/test/java/com/rushi/unblock/level/LevelParserTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.rushi.unblock.level

import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelParserTest {

    private val sampleJson = """
        {
          "id": "level_001",
          "gridSize": 6,
          "exitRow": 2,
          "vehicles": [
            {"id": "primary", "orientation": "horizontal", "length": 2, "row": 2, "col": 0, "isPrimary": true},
            {"id": "blocker", "orientation": "vertical", "length": 3, "row": 1, "col": 4, "isPrimary": false}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses vehicle fields and orientation correctly`() {
        val level = LevelParser.parse(sampleJson)
        assertEquals("level_001", level.id)
        assertEquals(6, level.gridSize)
        assertEquals(2, level.vehicles.size)
        assertEquals(Orientation.HORIZONTAL, level.vehicles[0].orientation)
        assertTrue(level.vehicles[0].isPrimary)
    }

    @Test
    fun `converts to a solvable Board`() {
        val board = LevelParser.parse(sampleJson).toBoard()
        val result = Solver.solve(board)
        assertTrue(result.solvable)
        assertEquals(2, result.minMoves)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.level.LevelParserTest"`
Expected: FAIL — nothing in `level` package exists yet.

- [ ] **Step 3: Write `Level.kt`**

```kotlin
package com.rushi.unblock.level

import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class OrientationDto { horizontal, vertical }

@Serializable
data class VehicleDto(
    val id: String,
    val orientation: OrientationDto,
    val length: Int,
    val row: Int,
    val col: Int,
    val isPrimary: Boolean = false
)

@Serializable
data class LevelDto(
    val id: String,
    val gridSize: Int,
    val exitRow: Int,
    val vehicles: List<VehicleDto>
)

fun LevelDto.toBoard(): Board = Board(
    width = gridSize,
    height = gridSize,
    exitRow = exitRow,
    vehicles = vehicles.map {
        Vehicle(
            id = it.id,
            orientation = if (it.orientation == OrientationDto.horizontal) Orientation.HORIZONTAL else Orientation.VERTICAL,
            length = it.length,
            row = it.row,
            col = it.col,
            isPrimary = it.isPrimary
        )
    }
)

object LevelParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(jsonText: String): LevelDto = json.decodeFromString(jsonText)
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.level.LevelParserTest"`
Expected: `BUILD SUCCESSFUL`, both tests pass.

- [ ] **Step 5: Write the starter level asset**

`app/src/main/assets/levels/level_001.json`:
```json
{
  "id": "level_001",
  "gridSize": 6,
  "exitRow": 2,
  "vehicles": [
    {"id": "primary", "orientation": "horizontal", "length": 2, "row": 2, "col": 0, "isPrimary": true},
    {"id": "blocker", "orientation": "vertical", "length": 3, "row": 1, "col": 4, "isPrimary": false}
  ]
}
```

- [ ] **Step 6: Write `LevelRepository.kt` (Android asset reading — not unit-tested, exercised at runtime in Task 9)**

```kotlin
package com.rushi.unblock.level

import android.content.Context
import com.rushi.unblock.domain.Board

class LevelRepository(private val context: Context) {
    fun loadLevel(fileName: String): Board {
        val text = context.assets.open("levels/$fileName").bufferedReader().use { it.readText() }
        return LevelParser.parse(text).toBoard()
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/rushi/unblock/level app/src/main/assets app/src/test/java/com/rushi/unblock/level
git commit -m "feat: add JSON level format, parser, and starter level"
```

---

## Task 6: GameViewModel — undo, move counter, restart, win state

**Files:**
- Create: `app/src/main/java/com/rushi/unblock/ui/GameViewModel.kt`
- Test: `app/src/test/java/com/rushi/unblock/ui/GameViewModelTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.rushi.unblock.ui

import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {

    private fun startBoard(): Board = Board(
        vehicles = listOf(
            Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
            Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
        )
    )

    @Test
    fun `starts with move count zero and not won`() {
        val vm = GameViewModel(startBoard())
        assertEquals(0, vm.moveCount.value)
        assertFalse(vm.isWon.value)
    }

    @Test
    fun `a legal move updates the board and increments the counter`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        assertEquals(1, vm.moveCount.value)
        assertEquals(3, vm.board.value.vehicle("blocker").row)
    }

    @Test
    fun `an illegal move is a no-op`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("primary", 99)
        assertEquals(0, vm.moveCount.value)
        assertEquals(0, vm.board.value.vehicle("primary").col)
    }

    @Test
    fun `undo reverts the last move and decrements the counter`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.undo()
        assertEquals(0, vm.moveCount.value)
        assertEquals(1, vm.board.value.vehicle("blocker").row)
    }

    @Test
    fun `undo on an empty history is a no-op`() {
        val vm = GameViewModel(startBoard())
        vm.undo()
        assertEquals(0, vm.moveCount.value)
    }

    @Test
    fun `restart resets to the initial board and clears the move count`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.attemptMove("primary", 4)
        vm.restart()
        assertEquals(0, vm.moveCount.value)
        assertEquals(0, vm.board.value.vehicle("primary").col)
        assertFalse(vm.isWon.value)
    }

    @Test
    fun `winning move sets isWon`() {
        val vm = GameViewModel(startBoard())
        vm.attemptMove("blocker", 2)
        vm.attemptMove("primary", 4)
        assertTrue(vm.isWon.value)
    }
}
```

- [ ] **Step 2: Run the test, verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.ui.GameViewModelTest"`
Expected: FAIL — `GameViewModel` doesn't exist yet.

- [ ] **Step 3: Write `GameViewModel.kt`**

```kotlin
package com.rushi.unblock.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.rushi.unblock.domain.Board

class GameViewModel(private val initialBoard: Board) : ViewModel() {

    private val history = ArrayDeque<Board>()

    private val _board = mutableStateOf(initialBoard)
    val board: State<Board> = _board

    private val _moveCount = mutableStateOf(0)
    val moveCount: State<Int> = _moveCount

    private val _isWon = mutableStateOf(initialBoard.isSolved)
    val isWon: State<Boolean> = _isWon

    fun attemptMove(vehicleId: String, delta: Int) {
        if (delta == 0) return
        val range = _board.value.legalMoves(vehicleId)
        if (delta !in range) return

        history.addLast(_board.value)
        _board.value = _board.value.move(vehicleId, delta)
        _moveCount.value += 1
        _isWon.value = _board.value.isSolved
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        _board.value = previous
        _moveCount.value -= 1
        _isWon.value = _board.value.isSolved
    }

    fun restart() {
        history.clear()
        _board.value = initialBoard
        _moveCount.value = 0
        _isWon.value = initialBoard.isSolved
    }
}
```

- [ ] **Step 4: Run the test, verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.rushi.unblock.ui.GameViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rushi/unblock/ui/GameViewModel.kt app/src/test/java/com/rushi/unblock/ui/GameViewModelTest.kt
git commit -m "feat: add GameViewModel with undo, move count, and win state"
```

---

> **Follow-up from Task 2's code review:** `composeBom` in `gradle/libs.versions.toml` is
> still the plan's original draft value (`2024.12.01`), unchanged despite Task 2 bumping
> AGP to 9.4.0 and Kotlin to 2.2.10 for Gradle 9.7.1 compatibility. That pairing didn't
> break Task 2's placeholder screen, but this task and Task 8 lean on
> `compose-foundation` gesture/animation APIs (`detectDragGestures`, `Animatable`,
> `spring`) that may need a newer BOM. Check for a current `composeBom` version
> compatible with Kotlin 2.2.10 and bump it before starting this task if the APIs below
> don't resolve.

## Task 7: Compose board rendering — procedural wood-grain board, no interaction yet

No unit tests this task — pure visual rendering, verified by building and eyeballing on
the emulator (Step 4).

**Files:**
- Create: `app/src/main/java/com/rushi/unblock/ui/BoardColors.kt`
- Create: `app/src/main/java/com/rushi/unblock/ui/GameBoardScreen.kt` (board/background part only — vehicles come in Task 8)

- [ ] **Step 1: Write `BoardColors.kt`**

```kotlin
package com.rushi.unblock.ui

import androidx.compose.ui.graphics.Color

object BoardColors {
    val woodBase = Color(0xFF8B5A2B)
    val woodDark = Color(0xFF6B4423)
    val woodLight = Color(0xFFA6714A)
    val gridLine = Color(0x33000000)
    val exitGlow = Color(0xFFFFD54F)

    val vehiclePrimary = Color(0xFFE53935)
    val vehiclePalette = listOf(
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFFB8C00),
        Color(0xFF8E24AA),
        Color(0xFF00897B),
        Color(0xFFFDD835)
    )
}
```

- [ ] **Step 2: Write `GameBoardScreen.kt` with the wood-grain board background**

```kotlin
package com.rushi.unblock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.rushi.unblock.domain.Board
import kotlin.math.sin
import kotlin.random.Random

private const val GRID_SIZE = 6

@Composable
fun GameBoardScreen(board: Board) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(1f)
    ) {
        drawWoodBoard(board)
    }
}

private fun DrawScope.drawWoodBoard(board: Board) {
    val cell = size.width / GRID_SIZE

    // Base wood gradient.
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(BoardColors.woodLight, BoardColors.woodBase, BoardColors.woodDark),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        size = size
    )

    // Procedural grain: faint sine-wavy horizontal streaks, seeded for stability.
    val rng = Random(seed = 42)
    repeat(28) {
        val y = rng.nextFloat() * size.height
        val amplitude = 2f + rng.nextFloat() * 4f
        val alpha = 0.03f + rng.nextFloat() * 0.05f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x < size.width) {
                lineTo(x, y + sin(x / 30f) * amplitude)
                x += 8f
            }
        }
        drawPath(
            path = path,
            color = BoardColors.woodDark.copy(alpha = alpha),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
    }

    // Grid lines.
    for (i in 0..GRID_SIZE) {
        drawLine(
            color = BoardColors.gridLine,
            start = Offset(i * cell, 0f),
            end = Offset(i * cell, size.height),
            strokeWidth = 1.5f
        )
        drawLine(
            color = BoardColors.gridLine,
            start = Offset(0f, i * cell),
            end = Offset(size.width, i * cell),
            strokeWidth = 1.5f
        )
    }

    // Exit glow on the right wall, at the exit row.
    val exitY = board.exitRow * cell
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(BoardColors.exitGlow.copy(alpha = 0f), BoardColors.exitGlow.copy(alpha = 0.6f)),
            startX = size.width - cell,
            endX = size.width
        ),
        topLeft = Offset(size.width - cell, exitY),
        size = Size(cell, cell)
    )
}
```

- [ ] **Step 3: Wire it into `MainActivity` temporarily to preview**

Modify `app/src/main/java/com/rushi/unblock/MainActivity.kt` — replace the placeholder
`Text` with a call to `GameBoardScreen` using a hardcoded board so it's visible before
`LevelRepository` is wired up in Task 9:

```kotlin
package com.rushi.unblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import com.rushi.unblock.ui.GameBoardScreen
import com.rushi.unblock.ui.theme.UnblockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previewBoard = Board(
            vehicles = listOf(
                Vehicle("primary", Orientation.HORIZONTAL, length = 2, row = 2, col = 0, isPrimary = true),
                Vehicle("blocker", Orientation.VERTICAL, length = 3, row = 1, col = 4)
            )
        )
        setContent {
            UnblockTheme {
                Surface(modifier = Modifier) {
                    GameBoardScreen(board = previewBoard)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Build, install, run, visually verify**

Run:
```
.\gradlew.bat installDebug
D:\Android\Sdk\platform-tools\adb.exe shell am start -n com.rushi.unblock/.MainActivity
```
Expected: emulator shows a 6x6 grid with a warm wood-toned gradient board, faint grain
streaks, and a yellow glow on the right wall at the exit row. Take a screenshot
(`D:\Android\Sdk\platform-tools\adb.exe exec-out screencap -p > board_preview.png`) and
look at it — if the grain reads as noise rather than wood, or the board looks flat,
iterate on the gradient/streak parameters before moving on. This is the "does it look
intentional, not placeholder" check the spec calls for.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rushi/unblock/ui/BoardColors.kt app/src/main/java/com/rushi/unblock/ui/GameBoardScreen.kt app/src/main/java/com/rushi/unblock/MainActivity.kt
git commit -m "feat: render procedural wood-grain board background"
```

---

## Task 8: Vehicles, drag-to-slide, snap animation, blocked feedback

> **Follow-up from Task 7's code review — do this FIRST, before adding vehicles/gestures:**
> `GameBoardScreen.kt`'s static wood-grain background (`drawBaseGradient`, `drawWoodGrain`,
> `drawKnots`, `drawVignette`, `drawGridLines`, `drawFrameWithExitGap`) does ~330 draw
> calls (70 `Path` builds with `sin()` evaluation + 260 line segments) every time the
> `Canvas` draw lambda runs, with no caching. That's fine for a static screen, but this
> task adds drag-gesture state that will cause the same `Canvas` to recompose on every
> drag frame — redoing all 330 static draw calls every frame alongside the actual
> per-frame vehicle draws, which risks real jank during dragging (the thing this task
> most needs to feel good). Before wiring up `detectDragGestures`, cache the static
> background layer — e.g. `Modifier.drawWithCache { ... }` producing a cached draw
> command, or render it once to an `ImageBitmap` and blit it each frame — so drag frames
> only redo the cheap vehicle-position draws, not the wood-grain background.

> **Follow-up from Task 6's code review:** `GameViewModel.attemptMove` delegates id
> lookup to `Board.vehicle(id)`, which throws `NoSuchElementException` for an unknown
> vehicle id rather than silently no-op'ing (unlike the illegal-delta branch, which is a
> silent no-op). `vehicleAt(board, offset, cellPx)` below always derives the id from a
> real hit-test against the current board's vehicles, so in the normal case this can't
> happen — but consider whether a drag that starts, then a `restart()`/level change
> happens mid-drag (unlikely in this single-level slice, but worth a thought), could hand
> `onMove` a stale id. If it's a real path, either guard it here or add the no-op guard
> to `GameViewModel` instead.

No unit tests — gesture/animation code, verified by playing it on the emulator.

**Files:**
- Modify: `app/src/main/java/com/rushi/unblock/ui/GameBoardScreen.kt`

- [ ] **Step 1: Replace `GameBoardScreen.kt` with the interactive version**

```kotlin
package com.rushi.unblock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rushi.unblock.domain.Board
import com.rushi.unblock.domain.Orientation
import com.rushi.unblock.domain.Vehicle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val GRID_SIZE = 6

@Composable
fun GameBoardScreen(board: Board, onMove: (String, Int) -> Unit = { _, _ -> }) {
    var cellPx by remember { mutableFloatStateOf(0f) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetCells by remember { mutableFloatStateOf(0f) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScopeCompat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .aspectRatio(1f)
            .pointerInput(board) {
                cellPx = size.width / GRID_SIZE.toFloat()
                detectDragGestures(
                    onDragStart = { offset ->
                        draggingId = vehicleAt(board, offset, cellPx)
                        dragOffsetCells = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val id = draggingId ?: return@detectDragGestures
                        val vehicle = board.vehicle(id)
                        val range = board.legalMoves(id)
                        val deltaCells = if (vehicle.orientation == Orientation.HORIZONTAL) {
                            dragAmount.x / cellPx
                        } else {
                            dragAmount.y / cellPx
                        }
                        val proposed = dragOffsetCells + deltaCells
                        val clamped = proposed.coerceIn(range.first.toFloat(), range.last.toFloat())
                        if (proposed != clamped) {
                            scope.launch {
                                shake.snapTo(0f)
                                shake.animateTo(1f, tween(60))
                                shake.animateTo(-1f, tween(60))
                                shake.animateTo(0f, tween(60))
                            }
                        }
                        dragOffsetCells = clamped
                    },
                    onDragEnd = {
                        val id = draggingId
                        if (id != null) {
                            onMove(id, dragOffsetCells.roundToInt())
                        }
                        draggingId = null
                        dragOffsetCells = 0f
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffsetCells = 0f
                    }
                )
            }
    ) {
        cellPx = size.width / GRID_SIZE.toFloat()
        drawWoodBoard(board, cellPx)
        board.vehicles.forEach { vehicle ->
            val offsetCells = if (vehicle.id == draggingId) dragOffsetCells else 0f
            val shakePx = if (vehicle.id == draggingId) shake.value * 6f else 0f
            drawVehicle(vehicle, cellPx, offsetCells, shakePx, isPrimary = vehicle.isPrimary)
        }
    }
}

private fun vehicleAt(board: Board, offset: Offset, cellPx: Float): String? {
    val col = (offset.x / cellPx).toInt()
    val row = (offset.y / cellPx).toInt()
    return board.vehicles.firstOrNull { v ->
        when (v.orientation) {
            Orientation.HORIZONTAL -> row == v.row && col in v.col until (v.col + v.length)
            Orientation.VERTICAL -> col == v.col && row in v.row until (v.row + v.length)
        }
    }?.id
}

private fun DrawScope.drawVehicle(
    vehicle: Vehicle,
    cellPx: Float,
    offsetCells: Float,
    shakePx: Float,
    isPrimary: Boolean
) {
    val margin = cellPx * 0.08f
    val baseX = vehicle.col * cellPx
    val baseY = vehicle.row * cellPx
    val (x, y) = when (vehicle.orientation) {
        Orientation.HORIZONTAL -> Offset(baseX + offsetCells * cellPx + shakePx, baseY)
        Orientation.VERTICAL -> Offset(baseX + shakePx, baseY + offsetCells * cellPx)
    }
    val w = if (vehicle.orientation == Orientation.HORIZONTAL) vehicle.length * cellPx else cellPx
    val h = if (vehicle.orientation == Orientation.VERTICAL) vehicle.length * cellPx else cellPx
    val color = if (isPrimary) BoardColors.vehiclePrimary
    else BoardColors.vehiclePalette[vehicle.id.hashCode().mod(BoardColors.vehiclePalette.size)]

    val topLeft = Offset(x + margin, y + margin)
    val bodySize = androidx.compose.ui.geometry.Size(w - margin * 2, h - margin * 2)

    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellPx * 0.25f, cellPx * 0.25f)
    )
    // Glossy highlight overlay.
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0f)),
            center = Offset(topLeft.x + bodySize.width * 0.3f, topLeft.y + bodySize.height * 0.25f),
            radius = maxOf(bodySize.width, bodySize.height) * 0.6f
        ),
        topLeft = topLeft,
        size = bodySize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellPx * 0.25f, cellPx * 0.25f)
    )
}

private fun DrawScope.drawWoodBoard(board: Board, cellPx: Float) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(BoardColors.woodLight, BoardColors.woodBase, BoardColors.woodDark),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        size = size
    )

    val rng = Random(seed = 42)
    repeat(28) {
        val y = rng.nextFloat() * size.height
        val amplitude = 2f + rng.nextFloat() * 4f
        val alpha = 0.03f + rng.nextFloat() * 0.05f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x < size.width) {
                lineTo(x, y + sin(x / 30f) * amplitude)
                x += 8f
            }
        }
        drawPath(
            path = path,
            color = BoardColors.woodDark.copy(alpha = alpha),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
    }

    for (i in 0..GRID_SIZE) {
        drawLine(BoardColors.gridLine, Offset(i * cellPx, 0f), Offset(i * cellPx, size.height), strokeWidth = 1.5f)
        drawLine(BoardColors.gridLine, Offset(0f, i * cellPx), Offset(size.width, i * cellPx), strokeWidth = 1.5f)
    }

    val exitY = board.exitRow * cellPx
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(BoardColors.exitGlow.copy(alpha = 0f), BoardColors.exitGlow.copy(alpha = 0.6f)),
            startX = size.width - cellPx,
            endX = size.width
        ),
        topLeft = Offset(size.width - cellPx, exitY),
        size = androidx.compose.ui.geometry.Size(cellPx, cellPx)
    )
}
```

Note: `rememberCoroutineScopeCompat()` is a stand-in name — actually just use Compose's
built-in `rememberCoroutineScope()` from `androidx.compose.runtime.rememberCoroutineScope`.
Replace that line with:
```kotlin
val scope = androidx.compose.runtime.rememberCoroutineScope()
```
and drop the fake `rememberCoroutineScopeCompat` reference entirely — it was a
placeholder slip while drafting this plan and must not appear in the real file.

The snap-back-to-grid *spring* animation (as opposed to the in-progress drag tracking
above, which follows the finger 1:1) happens implicitly: once `onMove` triggers
`attemptMove` in the ViewModel and the ViewModel's `board` state updates, `vehicle.col`/
`vehicle.row` change to the new grid-aligned position and `offsetCells` resets to `0f`
for that vehicle (since `draggingId` is cleared). To make that snap feel springy instead
of an instant jump, animate `offsetCells` back to `0` with a spring instead of resetting
it synchronously — replace the `onDragEnd` block's tail with:

```kotlin
                    onDragEnd = {
                        val id = draggingId
                        val finalDelta = dragOffsetCells.roundToInt()
                        if (id != null) {
                            onMove(id, finalDelta)
                        }
                        scope.launch {
                            val settleFrom = dragOffsetCells - finalDelta
                            val anim = Animatable(settleFrom)
                            anim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            // value only used for the transient visual settle; board state
                            // is already authoritative via onMove above
                        }
                        draggingId = null
                        dragOffsetCells = 0f
                    },
```

This is a reasonable v1: the drag-end snap is functionally instant (board state updates
immediately) with the spring as a brief visual flourish on top. If it doesn't feel
smooth enough once you play it, the fix is to keep `draggingId` set through the spring's
duration and only clear it in the animation's completion callback — flag this as the
first thing to tune once you're playing the real thing on the emulator, don't
over-engineer it blind before seeing it move.

- [ ] **Step 2: Update `MainActivity` to pass a real `onMove` (still hardcoded board, ViewModel wiring is Task 9)**

Leave `MainActivity` as-is from Task 7 for now — `onMove` defaults to a no-op, which is
fine for this task's purpose (verifying drag tracking and rendering). Task 9 does the
real wiring.

- [ ] **Step 3: Build, install, run, manually verify drag behavior**

Run:
```
.\gradlew.bat installDebug
D:\Android\Sdk\platform-tools\adb.exe shell am start -n com.rushi.unblock/.MainActivity
```

Manually check on the emulator (mouse-drag simulates touch):
- Dragging the red car right follows the finger smoothly, stops at the wood-grain edge
  near the blocker (it can't pass through it) — since `onMove` is a no-op here, releasing
  will snap it back to its original cell (expected, since there's no ViewModel yet).
- Dragging the vertical blocker up/down follows the finger, stops at the grid edges.
- Dragging a vehicle in its *wrong* axis (e.g. dragging the horizontal red car
  vertically) produces no movement, since `dragOffsetCells` only reads the axis-matching
  component of `dragAmount`.
- Dragging past a legal boundary produces a visible shake.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/rushi/unblock/ui/GameBoardScreen.kt
git commit -m "feat: add drag-to-slide gesture, snap animation, and blocked-move shake"
```

---

## Task 9: Wire it together — real level, ViewModel, HUD, win overlay

**Files:**
- Modify: `app/src/main/java/com/rushi/unblock/MainActivity.kt`
- Create: `app/src/main/java/com/rushi/unblock/ui/GameScreen.kt`

- [ ] **Step 1: Write `GameScreen.kt` — the HUD (move counter, undo, restart) + win overlay wrapping `GameBoardScreen`**

```kotlin
package com.rushi.unblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val board = viewModel.board.value
    val moveCount = viewModel.moveCount.value
    val isWon = viewModel.isWon.value

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Moves: $moveCount", style = MaterialTheme.typography.titleMedium)
                Row {
                    Button(onClick = { viewModel.undo() }) { Text("Undo") }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
                    Button(onClick = { viewModel.restart() }) { Text("Restart") }
                }
            }
            GameBoardScreen(board = board, onMove = viewModel::attemptMove)
        }

        if (isWon) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Solved in $moveCount moves!", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Button(onClick = { viewModel.restart() }) { Text("Play Again") }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Wire real level loading + ViewModel into `MainActivity.kt`**

```kotlin
package com.rushi.unblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rushi.unblock.level.LevelRepository
import com.rushi.unblock.ui.GameScreen
import com.rushi.unblock.ui.GameViewModel
import com.rushi.unblock.ui.theme.UnblockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val board = LevelRepository(applicationContext).loadLevel("level_001.json")
        val viewModel = GameViewModel(board)
        setContent {
            UnblockTheme {
                Surface(modifier = Modifier) {
                    GameScreen(viewModel = viewModel)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build, install, run, full manual playtest**

Run:
```
.\gradlew.bat installDebug
D:\Android\Sdk\platform-tools\adb.exe shell am start -n com.rushi.unblock/.MainActivity
```

Playtest checklist (do all of these on the emulator):
- App launches straight into level_001, move counter reads "Moves: 0".
- Drag the vertical blocker down out of the exit row — it slides, snaps to grid, move
  counter becomes 1.
- Drag the red car right — it slides past where the blocker used to be, all the way to
  the right wall.
- Win overlay appears with "Solved in 2 moves!" and a "Play Again" button.
- Tap "Play Again" (or Restart) — board resets to the original layout, move counter back
  to 0, overlay gone.
- Play again, make a move, tap Undo — board and move counter both revert correctly.
- Try dragging a vehicle into a blocked position — it resists/shakes and does not move
  through the blocker.

- [ ] **Step 4: Run the full unit test suite one more time to confirm nothing regressed**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests across `BoardTest`, `SolverTest`,
`LevelParserTest`, `GameViewModelTest` pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rushi/unblock/MainActivity.kt app/src/main/java/com/rushi/unblock/ui/GameScreen.kt
git commit -m "feat: wire real level, ViewModel, HUD, and win overlay — vertical slice playable"
```

---

## Self-Review Notes

- **Spec coverage:** Gradle+Compose scaffold (Task 2), domain model (Task 3), solver
  (Task 4), level format + starter levels (Task 5, one level — spec said "a handful";
  a second/third hand-authored level is a good follow-up but not required for "one level
  playable"), drag-to-slide/snap/blocked feedback (Task 8), undo/move counter/restart/win
  (Task 6 + 9). Procedural wood/glass rendering: Task 7 + 8. All spec sections have a
  task.
- **Type consistency:** `Board`, `Vehicle`, `Orientation`, `Solver.Result`, `LevelDto`,
  `GameViewModel` field/method names (`board`, `moveCount`, `isWon`, `attemptMove`,
  `undo`, `restart`) are used identically across Tasks 3, 4, 5, 6, 8, 9 — checked.
- **Known rough edge, called out inline rather than hidden:** the drag-end spring
  animation in Task 8 is a first-pass approximation (board state updates instantly,
  spring is a visual flourish layered on top) — flagged in Task 8 Step 1 as the first
  thing to revisit once it's actually playable, rather than guessing at gesture-timing
  polish before anyone has felt it.
