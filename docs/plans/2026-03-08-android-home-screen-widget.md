# Android Home Screen Widget Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the Quick Settings tile with a 2x1 home screen widget that launches streaming with one tap.

**Architecture:** An `AppWidgetProvider` (BroadcastReceiver subclass) sets a `PendingIntent` on the widget button that launches `MainActivity` with `ACTION_QUICK_START_STREAMING` — the same mechanism used by the removed QS tile. The widget is static (no live streaming status updates).

**Tech Stack:** Android AppWidget API, RemoteViews, XML vector drawable, Kotlin

---

### Task 1: Remove Quick Settings tile artifacts

**Files:**
- Delete: `samples/CameraAccessAndroid/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/QuickStartTileService.kt`
- Delete: `samples/CameraAccessAndroid/app/src/main/res/drawable/ic_qs_tile.xml`
- Modify: `samples/CameraAccessAndroid/app/src/main/AndroidManifest.xml`
- Modify: `samples/CameraAccessAndroid/app/src/main/res/values/strings.xml`

**Step 1: Delete QuickStartTileService.kt**

```bash
rm samples/CameraAccessAndroid/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/QuickStartTileService.kt
```

**Step 2: Delete ic_qs_tile.xml**

```bash
rm samples/CameraAccessAndroid/app/src/main/res/drawable/ic_qs_tile.xml
```

**Step 3: Remove the QS tile `<service>` block from AndroidManifest.xml**

Remove this entire block:
```xml
<!-- Quick Settings tile: one-tap launch + quick stream start -->
<service
    android:name=".QuickStartTileService"
    android:enabled="true"
    android:exported="true"
    android:icon="@drawable/ic_qs_tile"
    android:label="@string/quick_tile_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
    <intent-filter>
        <action android:name="android.service.quicksettings.action.QS_TILE" />
    </intent-filter>
</service>
```

**Step 4: Replace the QS tile string in strings.xml**

Remove:
```xml
<!-- Quick settings tile -->
<string name="quick_tile_label" description="Quick Settings tile label">Start Streaming</string>
```

Add in its place:
```xml
<!-- Home screen widget -->
<string name="widget_label" description="Home screen widget label">Start Streaming</string>
<string name="widget_description" description="Home screen widget description">Tap to start streaming from your glasses</string>
```

**Step 5: Verify the project still compiles**

Open Android Studio and run **Build > Make Project** (or `./gradlew assembleDebug` from the `samples/CameraAccessAndroid/` directory). Expect no errors.

**Step 6: Commit**

```bash
git add -A
git commit -m "feat(android): remove Quick Settings tile"
```

---

### Task 2: Create widget icon drawable

**Files:**
- Create: `samples/CameraAccessAndroid/app/src/main/res/drawable/ic_widget_stream.xml`

**Step 1: Create the vector drawable**

The widget icon is a video-camera shape, white fill (same as the old QS tile icon but explicitly white since it sits on a dark widget background).

Create `samples/CameraAccessAndroid/app/src/main/res/drawable/ic_widget_stream.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:fillColor="#FFFFFF"
      android:pathData="M17,10.5V7c0-0.55-0.45-1-1-1H4C3.45,6 3,6.45 3,7v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z" />
</vector>
```

**Step 2: Commit**

```bash
git add samples/CameraAccessAndroid/app/src/main/res/drawable/ic_widget_stream.xml
git commit -m "feat(android): add widget stream icon drawable"
```

---

### Task 3: Create widget layout

**Files:**
- Create: `samples/CameraAccessAndroid/app/src/main/res/layout/widget_stream.xml`

**Step 1: Create the RemoteViews layout**

The widget is a 2x1 banner: dark rounded background, camera icon on the left, "Start Streaming" label on the right. RemoteViews only supports a limited set of views (LinearLayout, FrameLayout, TextView, ImageView, Button — no Compose).

Create `samples/CameraAccessAndroid/app/src/main/res/layout/widget_stream.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#CC000000"
    android:gravity="center"
    android:orientation="horizontal"
    android:padding="8dp">

    <ImageView
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:contentDescription="@string/widget_label"
        android:src="@drawable/ic_widget_stream" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:text="@string/widget_label"
        android:textColor="#FFFFFF"
        android:textSize="14sp"
        android:textStyle="bold" />

</LinearLayout>
```

**Step 2: Commit**

```bash
git add samples/CameraAccessAndroid/app/src/main/res/layout/widget_stream.xml
git commit -m "feat(android): add widget layout"
```

---

### Task 4: Create widget provider info XML

**Files:**
- Create: `samples/CameraAccessAndroid/app/src/main/res/xml/widget_info.xml`

Note: the `res/xml/` directory already exists (it has `file_paths.xml`).

**Step 1: Create the AppWidgetProviderInfo**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:minWidth="130dp"
    android:minHeight="50dp"
    android:targetCellWidth="2"
    android:targetCellHeight="1"
    android:previewLayout="@layout/widget_stream"
    android:resizeMode="none"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen" />
```

- `minWidth="130dp"` / `minHeight="50dp"` — safe minimum for a 2×1 cell across launchers
- `targetCellWidth/Height` — Android 12+ grid hint
- `updatePeriodMillis="0"` — widget is static, no periodic updates needed
- `previewLayout` — shows the actual layout in the widget picker

**Step 2: Commit**

```bash
git add samples/CameraAccessAndroid/app/src/main/res/xml/widget_info.xml
git commit -m "feat(android): add widget provider info XML"
```

---

### Task 5: Implement StreamWidgetProvider

**Files:**
- Create: `samples/CameraAccessAndroid/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/StreamWidgetProvider.kt`

**Step 1: Create the AppWidgetProvider**

```kotlin
package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class StreamWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_QUICK_START_STREAMING
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val views = RemoteViews(context.packageName, R.layout.widget_stream)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
```

**Step 2: Verify the project compiles**

Run `./gradlew assembleDebug` from `samples/CameraAccessAndroid/`. Expect no errors.

**Step 3: Commit**

```bash
git add samples/CameraAccessAndroid/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/StreamWidgetProvider.kt
git commit -m "feat(android): implement StreamWidgetProvider"
```

---

### Task 6: Register widget in AndroidManifest.xml

**Files:**
- Modify: `samples/CameraAccessAndroid/app/src/main/AndroidManifest.xml`

**Step 1: Add the widget receiver inside `<application>`**

Add this block after the `StreamingService` `<service>` declaration:

```xml
<!-- Home screen widget: one-tap stream start -->
<receiver
    android:name=".StreamWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/widget_info" />
</receiver>
```

**Step 2: Verify the project compiles and installs**

```bash
./gradlew installDebug
```

Expected: BUILD SUCCESSFUL, app installs on device/emulator.

**Step 3: Manual test on device**

1. Long-press the home screen → select Widgets → find "VisionClaw" → drag the widget to the home screen
2. Tap the widget — expect the app to open and streaming to start automatically (same behavior as the old QS tile)

**Step 4: Commit**

```bash
git add samples/CameraAccessAndroid/app/src/main/AndroidManifest.xml
git commit -m "feat(android): register StreamWidgetProvider in manifest"
```

---

### Task 7: Final cleanup and PR

**Step 1: Verify no references to the old QS tile remain**

```bash
grep -r "QuickStartTileService\|ic_qs_tile\|quick_tile_label\|BIND_QUICK_SETTINGS_TILE" samples/CameraAccessAndroid/
```

Expected: no output.

**Step 2: Run full build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL, 0 warnings related to removed files.

**Step 3: Invoke finishing-a-development-branch skill to wrap up**
