# Agenda

Android app for tasks, reminders and ideas that haven't found their place yet.
Nothing leaves the phone.

The bottom navigation has three pages:

- Today: the top 3 of the day, then a schedule with everything that has a
  notification today and the calendar events of the day, sorted by time.
  Empty top 3 slots read "Choose a task" and open a list of open tasks and
  their subtasks. Ticked items stay on the page, crossed out, until the day
  ends. Open items from earlier days stay at the top of the schedule with
  their date in red.
- Upcoming: the next seven days with their items and calendar events. Pulling
  up past the end adds another week, as often as needed. After a long press,
  a task or reminder can be dragged to another day and keeps its time.
  Calendar events stay where they are.
- Backlog: everything without a notification time that isn't in today's top
  3, in three sections. The inbox collects entries that aren't sorted yet,
  then come tasks and reminders. Chips at the top filter by place, and "No
  place" shows the items without one. With no chip picked, everything shows.
  After a long press, items can be dragged to a new place in their section.

Tapping the time of an item on Today or Upcoming offers in one hour, this
evening, tomorrow morning, or a picked day and time.

The add button opens the editor for a new item of the first kind in Settings,
Order of kinds. Search and Settings sit in the top bar of every page.

## Items

There are three kinds of items, picked with the chips at the top of the
editor. Their order is set in Settings, Order of kinds:

- Inbox entries have a title and a description. Turning one into a task or a
  reminder moves it out of the inbox. The menu of an inbox entry does the same.
- Tasks have a description, a place, subtasks, a notification and a repeat.
  A task or one of its subtasks can go into today's top 3. Subtasks have all
  fields of a task except subtasks of their own. The arrow next to a saved
  subtask opens it.
- Reminders have the same fields as tasks without subtasks.

A new notification starts from the same choices as rescheduling. Its day and
time then show as two buttons that change each on its own. Times are typed
as digits everywhere: 7 is 7:00, 730 or 0730 is 7:30.

Tasks and reminders are ticked off with the circle in front of them, or with
Done on their notification. A repeating item adds its next copy when it is
ticked off. The next date counts either from the schedule (every Monday,
monthly on the 1st) or from the day it was done (4 days after done).

Places are free text. Settings, Places renames, merges or removes them.

## The day

The day starts at midnight, or at the time set in Settings, New day starts
at. Then the top 3 empties, unfinished top 3 items go back to their list, and
items ticked off before that are deleted.

## Notifications

A notification shows at the item's time, with Done and Reschedule buttons.
Reschedule offers in one hour, this evening, tomorrow morning, or a picked
date and time. The morning and evening times are set in the settings.

The status bar shows a check mark, or a double check mark when more than one
notification is open. Several notifications are grouped with their count.

Notifications stay until Done is pressed. They are pinned, and one that is
swiped away anyway shows again. They come back after a restart, and the alarm
is set again after a restart, an update or a change of the clock or time
zone. Agenda uses exact alarms, which Android allows from version 13 without
asking. On Android 12 the settings link to the permission.

## Calendars

After allowing calendar access in the settings, the calendars switched on
there show their events on Today and Upcoming. Tapping an event opens it in
the calendar app.

## Backup

After a backup folder is picked in the settings, a job writes a JSON file with
all items and settings there once a day and keeps the newest 3. Back up now
writes one right away. Restore replaces all items and settings with the ones
in a backup file, after asking.

## Content provider API

Other apps, such as Quick Add, can read items and add new ones through a
content provider.

- URI: `content://com.josiglo.agenda.api/v0/items`
- Permissions: `com.josiglo.agenda.permission.READ_ITEMS` for queries and
  `com.josiglo.agenda.permission.WRITE_ITEMS` for inserts. Both are runtime
  permissions the user grants.

| Column | Type | Meaning |
| --- | --- | --- |
| `_id` | long | Item ID |
| `kind` | string | `inbox`, `task` or `reminder` |
| `title` | string | Title |
| `note` | string or null | Description |
| `place` | string or null | Free text such as `Home` |
| `parent_id` | long or null | The task a subtask belongs to. Read only. |
| `remind_at` | long or null | Epoch milliseconds of the notification |
| `done_at` | long or null | Epoch milliseconds when ticked off. Read only. |

Operations:

- `query` returns all items. Selections work, for example `kind = 'task'`.
- `insert` adds an item. `title` is required. `kind` defaults to `inbox`.
  The notification time comes from `remind_at`, or from `date` (`2026-10-30`)
  and `time` (`07:30`), or is now when `notify_now` is 1. A date without a
  time takes the morning time from the settings. A time without a date is
  today, or tomorrow once that time has passed. An inbox entry with a time
  becomes a reminder.

Calling apps need a `<queries>` entry to see the provider on Android 11 and newer:

```xml
<queries>
    <provider android:authorities="com.josiglo.agenda.api" />
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
