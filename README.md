# SMS Receiver for Android

A simple Android application that receives and displays SMS messages on your device.

## Features

- Receives all incoming SMS messages
- Displays message sender, content, and timestamp
- Stores the last 100 messages locally
- Material Design UI
- Clear all messages functionality
- Supports Android 7.0 (API 24) and above
- Optimized for modern devices including Samsung Galaxy S25

## Permissions Required

The app requests the following permissions:
- `RECEIVE_SMS` - To receive incoming SMS messages
- `READ_SMS` - To read SMS messages
- `POST_NOTIFICATIONS` - For Android 13+ notification support

## Building the APK

### Option 1: Using Android Studio (Recommended)
1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to this project folder
4. Wait for Gradle sync to complete
5. Click Build > Build Bundle(s) / APK(s) > Build APK(s)
6. The APK will be in `app/build/outputs/apk/debug/app-debug.apk`

### Option 2: Using Command Line
```bash
# On Linux/Mac:
./gradlew assembleDebug

# On Windows:
gradlew.bat assembleDebug
```

The APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

## Installing on Your Samsung S25

1. Transfer the APK to your phone (via USB, email, or cloud storage)
2. On your phone, go to Settings > Security
3. Enable "Install unknown apps" for your file manager or browser
4. Navigate to the APK file and tap to install
5. Grant the required SMS permissions when prompted

## Usage

1. Launch the app
2. Grant SMS permissions when prompted
3. The app will automatically start receiving SMS messages
4. View all received messages in the main screen
5. Use "Clear All Messages" button to remove all stored messages

## Project Structure

- `/app/src/main/java/com/smsreceiver/`
  - `MainActivity.kt` - Main UI and permission handling
  - `SmsReceiver.kt` - BroadcastReceiver for SMS
  - `SmsAdapter.kt` - RecyclerView adapter for message list
  - `SmsData.kt` - Data class for SMS messages
- `/app/src/main/res/`
  - `layout/` - XML layouts
  - `values/` - Strings and resources
  - `drawable/` - App icons

## Requirements

- Android SDK 34
- Gradle 8.0+
- Kotlin 1.9.0
- Java 17 or higher

## License

This project is open source and available for personal and educational use.
