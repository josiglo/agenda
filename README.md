# Reminders

Android app for reminders, with or without a time. Nothing leaves the phone.

The bottom navigation has three pages:

- Today: everything with a notification today and the calendar events of the
  day, sorted by time. Ticked reminders stay on the page, crossed out, until
  the day ends.
- Week: one week from Monday to Sunday with its reminders and calendar
  events, and the dates of the week under the title. Swiping sideways shows
  the next or previous week, and the calendar button goes back to this week.
  Past days are dimmed. Every day from today on has a button that adds a
  reminder on that day. After a long press, a reminder can be dragged to
  today or a later day and keeps its time. Holding a drag at the left or
  right edge turns to the next or previous week. Calendar events stay where
  they are.
- Backlog: reminders without a time, then the ones ticked off today. Chips at
  the top filter by place. One is always picked: "No place" for the
  reminders without one, or a place. After a long press, open reminders can
  be dragged to a new place in the list.

Tapping the time of a reminder on Today or Week offers shortcuts such as in
one hour, this evening, tomorrow morning or Monday morning, or a picked day
and time. Typed times come with buttons for common times of day.

The add button opens the editor for a new reminder. Search and Settings sit
in the top bar of every page.

## Reminders

A reminder has a title, a description, a place, a notification and a
repeat. Without a notification it waits in the backlog.

A new notification starts from the same choices as rescheduling. Its day and
time then show as two buttons that change each on its own. Times are typed
as digits everywhere: 7 is 7:00, 730 or 0730 is 7:30.

Reminders are ticked off with the circle in front of them, or with Done on
their notification. A repeating reminder adds its next copy when it is
ticked off. The next date counts either from the schedule (every Monday,
monthly on the 1st) or from the day it was done (4 days after done).

Places are free text. Settings, Places renames, merges or removes them.

The app was called Agenda before (`com.josiglo.agenda`) and had inbox
entries, tasks with subtasks and a top 3. Reminders is a separate app for
Android, so data moves over through a backup: Back up now in Agenda, then
Restore in Reminders. Inbox entries, tasks and subtasks from the backup all
become reminders.

## Sharing

Text shared from another app to "Add to Reminders" opens a sheet over that
app. The shared subject, often a page title, becomes the title and the text
the description. Without a subject, the first line is the title and the rest
the description. The Date, Time and Notify now chips set the notification,
and without them the reminder goes to the backlog.

## The day

The day starts at midnight, or at the time set in Settings, New day starts
at. Then reminders ticked off before that are deleted, and open reminders
with a notification on a past day move to the new day at the same time and
notify again. This includes repeating reminders.

## Notifications

A notification shows at the item's time, with Done and Reschedule buttons.
Reschedule offers in one hour, this evening, tomorrow morning, or a picked
date and time. The morning and evening times are set in the settings.

The status bar shows a check mark, or a double check mark when more than one
notification is open. Several notifications are grouped with their count.

Notifications stay until Done is pressed. They are pinned, and one that is
swiped away anyway shows again. They come back after a restart, and the alarm
is set again after a restart, an update or a change of the clock or time
zone. Reminders uses exact alarms, which Android allows from version 13 without
asking. On Android 12 the settings link to the permission.

## Calendars

After allowing calendar access in the settings, the calendars switched on
there show their events on Today and Week. Tapping an event opens it in
the calendar app.

## Backup

After a backup folder is picked in the settings, a job writes a JSON file with
all items and settings there once a day and keeps the newest 3. Back up now
writes one right away. Restore replaces all items and settings with the ones
in a backup file, after asking.

## Content provider API

Other apps, such as Quick Add, can read items and add new ones through a
content provider.

- URI: `content://com.josiglo.reminders.api/v0/items`
- Permissions: `com.josiglo.reminders.permission.READ_ITEMS` for queries and
  `com.josiglo.reminders.permission.WRITE_ITEMS` for inserts. Both are runtime
  permissions the user grants.

| Column | Type | Meaning |
| --- | --- | --- |
| `_id` | long | Item ID |
| `title` | string | Title |
| `note` | string or null | Description |
| `place` | string or null | Free text such as `Home` |
| `remind_at` | long or null | Epoch milliseconds of the notification |
| `done_at` | long or null | Epoch milliseconds when ticked off. Read only. |

Operations:

- `query` returns all reminders. Selections work, for example `remind_at IS NULL`.
- `insert` adds a reminder. `title` is required. The notification time comes
  from `remind_at`, or from `date` (`2026-10-30`) and `time` (`07:30`), or is
  now when `notify_now` is 1. A date without a time takes the morning time
  from the settings. A time without a date is today, or tomorrow once that
  time has passed. Without any of them the reminder goes to the backlog. A
  `kind` value from older callers is ignored.

Calling apps need a `<queries>` entry to see the provider on Android 11 and newer:

```xml
<queries>
    <provider android:authorities="com.josiglo.reminders.api" />
</queries>
```

## Releases

Every merge to `main` that changes the app (`app/`, `gradle/`, the Gradle
build files) builds a signed APK and publishes it as a GitHub release. The
merged PR's labels pick the version bump: none for minor, `patch` or `major`.
`gh workflow run release.yml -f bump=patch` releases by hand.

Signing uses the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` and
`KEY_ALIAS`. Without them, builds are signed with the debug key.

## Building locally

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Requires JDK 17 and the Android SDK.
