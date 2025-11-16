# SMS Receiver for Android

Advanced Android SMS management application with intelligent organization, tagging, and forwarding capabilities.

## Features

### Message Organization

#### Folder System
- **Inbox**: Messages with no tags (default folder for new messages)
- **Saved**: Messages tagged with "saved"
- **Receipts**: Messages tagged with "receipts"
- **Archive**: Messages tagged with "archived"
  - Archive Subfolders: All, Spam
  - Time-based grouping: Last 7 Days, Last Month, Last Quarter, Last Year, Older

#### Tag System
- Default tags: `saved`, `receipts`, `archived`, `spam`, `trash`
- Custom tags: Create, rename, and delete user-defined tags
- Multiple tags per message supported
- Tag-based filtering across folders

#### Message Grouping
- Group by sender in Inbox, Saved, and Receipts tabs
- Group by time period in Archive tab
- Collapsible/expandable groups
- Unread indicator (*) on groups with unread messages
- Message counts displayed in group headers

### Message Display

#### Visual Features
- Contact name resolution (phone number → contact name)
- Unread messages shown in bold text
- Unread indicator (*) in headers
- Tab counts with unread message indicators
- Dark theme UI
- Icon-only toolbar buttons for compact design

#### Message Interaction
- Copy message text with clipboard button (📋)
- Clickable links in messages (auto-detect URLs, emails, phone numbers)
- Sender name click to add/view contact
- Long-press to open tag management dialog

### Message Management

#### Read/Unread Status
- Mark individual message as read/unread
- Mark all messages in current folder as read (with confirmation)
- Auto-read after viewing for 5 seconds (optional, configurable in settings)
- Unread counts shown in tab titles

#### Message Actions
- **Delete**: Move message to trash (with confirmation)
- **Archive**: Add "archived" tag to message
- **Unarchive**: Remove "archived" tag from message
- **Move to Inbox**: Remove all tags from message
- **Tag Management**: Add/remove multiple tags per message
- **Copy Text**: Copy message content to clipboard

#### Bulk Operations
- Selection mode (long-press in Archive tab)
- Multi-select messages
- Bulk delete with confirmation
- Header selection to select all messages in group

### Gestures

- **Swipe Right**: Archive/Unarchive message (toggles archived tag)
- **Swipe Left**: Forward message via email or SMS
- **Long Press**:
  - In Inbox/Saved/Receipts: Open tag management dialog
  - In Archive: Enter selection mode
- **Click Sender**: Add sender to contacts (if not already a contact)
- **Click Copy Button**: Copy message text to clipboard

### Auto-Tagging

#### Receipt Detection
- Auto-tag messages containing Hebrew keywords:
  - חשבונית (invoice)
  - קבלה (receipt)
  - שובר (voucher)
- Automatically adds "receipts" tag to matching messages

#### Spam Detection
- Maintain spam sender list in settings
- Auto-tag future messages from spam senders
- Option to add sender to spam list when marking as spam

### Contact Management

- Click on sender phone number to add to contacts
- Contact name resolution across app
- Display contact name instead of phone number when available
- Add contact dialog with name input
- Integration with Android contacts app

### Message Forwarding

#### Email Forwarding
- Support for multiple email providers:
  - Gmail (smtp.gmail.com)
  - Hotmail/Outlook/Live (smtp.office365.com)
  - Yahoo (smtp.mail.yahoo.com)
- Configurable sender email and password (App Password required)
- Format: "Forwarded SMS from [sender]" with timestamp and message content

#### SMS Forwarding
- Forward messages to configurable phone number
- Format: "Fwd from [sender]: [message]"
- Automatic message splitting for long messages
- Requires SEND_SMS permission

### Settings

#### Forwarding Configuration
- **Email Forwarding**:
  - Enable/disable toggle
  - Forward-to email address
  - Sender email address
  - Email password (App Password for Microsoft/Google accounts)
- **SMS Forwarding**:
  - Enable/disable toggle
  - Forward-to phone number

#### Auto-Read Configuration
- Enable/disable auto-read after 5 seconds
- Applies only to messages visible in viewport

#### Tag Management
- View all default and custom tags
- Create new custom tags
- Rename custom tags (updates all messages)
- Delete custom tags (removes from all messages)

#### Spam Management
- View spam sender list
- Remove individual senders from list
- Clear entire spam list
- Add senders to spam list when marking messages as spam

#### Trash Management
- View all messages in trash
- Permanently delete selected messages
- Empty trash (delete all trash messages)
- Confirmation required for permanent deletion

### User Interface

#### Toolbar
- Settings button (⚙)
- Mark All as Read button (📭)
- Icon-only design for compact layout

#### Tab Navigation
- Inbox, Saved, Receipts, Archive tabs
- Tab counts: "Name (total, +X new)"
- Scrollable tabs for multiple folders

#### Selection Mode
- Checkboxes for messages and groups
- Floating delete button (red, bottom-right)
- Exit selection mode with back button

#### Confirmations
- Delete confirmation: "Are you sure?"
- Mark all as read: Shows count and folder name
- Empty trash: Requires typing "DELETE"
- Archive all: Shows message count

### Permissions Required

- **RECEIVE_SMS**: Receive incoming SMS messages
- **READ_SMS**: Read SMS from device database
- **SEND_SMS**: Forward messages via SMS
- **READ_CONTACTS**: Resolve contact names
- **POST_NOTIFICATIONS**: Show notifications (Android 13+)

### Data Storage

- Messages stored in SharedPreferences
- Format: timestamp|sender|message|deprecated|archiveTimestamp|timestampMillis|tags|isRead
- Settings stored in separate SharedPreferences
- Real-time sync with device SMS database via ContentObserver

### Background Features

- BroadcastReceiver for incoming SMS
- ContentObserver monitoring SMS database changes
- Viewport tracking for auto-read functionality
- Background email sending (threading)

## Important Notes

- All destructive actions require confirmation
- Archived messages retain other tags (e.g., can be both saved and archived)
- Trash messages are hidden from all folders except settings trash management
- Contact resolution attempts multiple phone number formats for better matching
- Supports Android 7.0 (API 24) and above
- Optimized for modern devices including Samsung Galaxy S25

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
