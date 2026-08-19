# Zero-login cloud backup on Android

How to give an Android app automatic off-device backup **without** accounts, sign-in, OAuth, a
Google Cloud project, a server, or asking the user to remember to export a file.

This is the approach implemented in TriPath. Reference files are linked throughout; the pattern
is app-agnostic and can be lifted wholesale into another app.

---

## The idea

Android already backs up app data to the user's Google account. It's called **Auto Backup**, it's
part of the OS, and it's the same mechanism behind "restore from your old phone" during device
setup. The user has already logged into Google on their phone, so your app never sees a login.

What most apps get wrong is *what* they hand to it:

- Do nothing → you inherit the default, which backs up whatever happens to be in `filesDir` and
  `sharedPrefs`, and (if you use Room) an inconsistent copy of a live SQLite file.
- Exclude the database "because we have our own backup" → the user's actual data now reaches
  the cloud **never**, and the "own backup" is a manual button nobody presses.

The fix is to keep the raw database out of the cloud payload and instead maintain **one small
file** that is a complete, self-describing snapshot of all user data, and let Auto Backup carry
that file.

```
┌──────────────────────────────────────────────────────────────┐
│  Your app                                                    │
│                                                              │
│   Room DB  ──export──▶  JSON  ──gzip──▶                      │
│                                  files/cloud_backup/         │
│                                    snapshot.json.gz  ────┐   │
└──────────────────────────────────────────────────────────┼───┘
                                                           │
                          Android Auto Backup (no login)   │
                                                           ▼
                                          User's Google account
                                                           │
                        fresh install / new phone          │
                                                           ▼
                          files/cloud_backup/snapshot.json.gz
                                    │
                                    ▼
                       "Restore your data?"  ──▶  import
```

## Why a JSON snapshot and not the database file

You *can* just stop excluding the database and let Android copy `databases/`. It's a three-line
change. Don't — for three reasons that all bite silently.

| | Raw SQLite file | Gzipped JSON snapshot |
|---|---|---|
| **Quota** | DB + indices + free pages + `-wal`. Approaches the ~25 MB per-app limit. **Exceeding it makes Android skip the app's backup entirely, with no user-visible error.** | Measured **9.6:1** on real data in TriPath: 157,889 B → 16,437 B. Sample-heavy rows (heart rate, power, GPS) compress enormously. |
| **Version tolerance** | Restoring an older schema depends on your Room migrations lining up exactly. A *newer* backup onto an older build is refused by the platform or corrupts. | Replayed through your own versioned importer. A v4 file restores on a v5 build because you wrote the compatibility rules. |
| **Consistency** | Android copies files while your app may be mid-write. Room's WAL mode means the truth is split across `db` and `db-wal`. | Written from one read transaction, then renamed into place atomically. The platform can only ever see a complete file. |

The compression ratio is the decisive one. A raw DB that's fine at 5 MB today crosses the quota
after two years of use, and the failure mode is *the backup silently stops happening*.

---

## Implementation

### 1. Manifest

```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules">
```

`allowBackup="true"` is the default, but state it explicitly — it's the switch for the whole
feature.

### 2. Backup rules

`res/xml/data_extraction_rules.xml` — **this is the file that matters on Android 12+ (API 31+)**:

```xml
<data-extraction-rules>
    <cloud-backup>
        <!-- The raw DB is not uploaded; the snapshot carries the same data, smaller. -->
        <exclude domain="database" path="." />

        <include domain="file" path="cloud_backup/" />
        <include domain="file" path="datastore/" />

        <exclude domain="external" path="." />
        <exclude domain="sharedpref" path="." />
    </cloud-backup>

    <device-transfer>
        <!-- Phone-to-phone has no quota worry and lands on the same app version,
             so copy the database directly: faster, and needs no restore prompt. -->
        <include domain="database" path="." />
        <include domain="file" path="." />
    </device-transfer>
</data-extraction-rules>
```

`res/xml/backup_rules.xml` applies only to **API ≤ 30**. If your `minSdk` is 31+ it is never
consulted — but `fullBackupContent` still points at it, so mirror the intent there to stop the
two files drifting into contradiction.

Gotchas:

- The moment you write a single `<include>`, the rules become **allow-list only** for that domain.
  Anything not included is excluded.
- `domain="file"` is `filesDir`. `datastore/` lives there — include it, or the user's settings
  don't come back.
- `getNoBackupFilesDir()`, `cacheDir` and `codeCacheDir` are **never** backed up. That's load
  bearing later.

### 3. A complete export

The single most important property is **coverage**. A table you forget is data the user loses on a
new phone — and if your import wipes before restoring, forgetting a table means restoring
*deletes* it.

Make the coverage contract explicit and testable
([`BackupDtos.kt`](../app/src/main/java/com/tripath/data/local/backup/BackupDtos.kt)):

```kotlin
@Serializable
data class AppBackupData(
    val version: Int = BACKUP_VERSION,
    val timestamp: Long,
    val appVersionCode: Long? = null,
    // One collection per Room entity...
    val workoutLogs: List<WorkoutLogDto> = emptyList(),
    val sleepLogs: List<SleepLogDto> = emptyList(),
    /* ...every other table... */
    // ...plus the key-value store.
    val preferences: List<PreferenceEntryDto> = emptyList()
)
```

Rules that earn their keep:

- **Default every collection to `emptyList()`.** That alone is what lets an old backup parse on a
  new build after you add a table.
- **Store enums by `name`, and tolerate unknown names on the way in.** A backup can reference a
  constant you've since renamed. Fall back to a neutral value where one exists, and *skip the
  record* where it doesn't — silently mislabelling an `INJURY` period as `HOLIDAY` is worse than
  dropping it.
- **Never conflate `null` with `0`.** "Didn't log carbs" and "ate zero carbs" are different facts;
  keep the DTO fields nullable.
- **Export the key-value store generically**, not field by field
  ([`PreferencesManager.exportAll`](../app/src/main/java/com/tripath/data/local/preferences/PreferencesManager.kt)):

  ```kotlin
  suspend fun exportAll(): List<PreferenceEntry> =
      dataStore.data.first().asMap()
          .filterKeys { it.name !in TRANSIENT_KEY_NAMES }
          .mapNotNull { (key, value) -> PreferenceEntry.of(key.name, value) }
  ```

  Hand-mapping 25 known keys guarantees that the 26th, added next month, is silently missing from
  every backup. Reading the store generically means new settings are covered for free. DataStore
  keys are typed, so record a type tag (`b/i/l/f/d/s/ss`) to rebuild the right key on import —
  otherwise you get a `ClassCastException` on read.

- **Exclude device-local bookkeeping.** "Last synced at" or "migration already ran" describe *this
  phone*, not the user. Restore them onto a new device and it thinks it already synced, so it
  skips the first import.

### 4. The snapshot file

[`CloudSnapshotStore.kt`](../app/src/main/java/com/tripath/data/local/backup/CloudSnapshotStore.kt).
Write to a temp file, then rename — never write the live file in place:

```kotlin
val tempFile = File(snapshotDir, "$SNAPSHOT_FILE.tmp")
GZIPOutputStream(tempFile.outputStream().buffered()).use { it.write(payload.toByteArray()) }
if (!tempFile.renameTo(snapshotFile)) {
    snapshotFile.delete()                    // renameTo fails on some devices if dest exists
    tempFile.renameTo(snapshotFile)
}
android.app.backup.BackupManager(context).dataChanged()   // ask for a backup pass
```

Write an **uncompressed sidecar** `snapshot_meta.json` next to it (timestamp, app version, byte
size, per-table counts). The restore prompt and the settings UI can then describe a backup
without inflating a multi-megabyte payload.

`dataChanged()` doesn't upload anything — it tells the framework there's new data so it can
schedule a pass (typically idle + charging + unmetered).

### 5. When to refresh

This is where a design that looks fine loses real data. Refresh on:

| Trigger | Threshold | Why |
|---|---|---|
| App start | 24 h | Baseline; Auto Backup itself only runs about once a day. |
| App backgrounded | 10 min | **The important one.** This bounds what a restore can lose. |
| After a bulk import/sync | always | That's when most new data arrives. |
| Manual "Back up now" | always | Gives the user agency and something to verify. |

We learned the backgrounding trigger the hard way: with app-start-only refresh at a 24-hour
threshold, a snapshot sat 2.4 hours stale, the device was wiped, and the restore came back
missing everything logged in those 2.4 hours. **The refresh interval *is* your worst-case data
loss window.**

Use a singleton-scoped coroutine scope, not a screen's lifecycle scope — a snapshot triggered as
the user leaves must not be cancelled along with the screen:

```kotlin
private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun refreshInBackground(maxAgeMillis: Long = 10 * 60 * 1000L) {
    storeScope.launch { refreshIfStale(maxAgeMillis) }
}
```

### 6. Detecting a restore

Android gives you no callback that says "your data was just restored". But there's a reliable
signal: the platform restores `filesDir`, and **never** backs up `noBackupFilesDir`. So:

| `install.marker` in `noBackupFilesDir` | Snapshot in `filesDir` | Meaning |
|---|---|---|
| present | — | Normal launch. Do nothing. |
| absent | absent | Genuinely fresh install, no backup. Write marker. |
| **absent** | **present** | **A restore happened.** Offer it. |

[`RestoreCoordinator.kt`](../app/src/main/java/com/tripath/data/local/backup/RestoreCoordinator.kt):

```kotlin
suspend fun checkForRestoredBackup() = withContext(Dispatchers.IO) {
    if (installMarker.exists()) return@withContext        // not a fresh install
    val meta = snapshotStore.reloadMeta()
    if (meta != null && snapshotStore.hasSnapshot()) {
        _pendingRestore.value = meta                      // -> UI shows the prompt
    }
    markInstallSeen()                                     // written either way: runs once
}
```

This needs no custom `BackupAgent`, which sidesteps its restricted process lifecycle, DI
availability questions and execution time limit.

> ### The ordering bug to avoid
>
> A restored snapshot is, by definition, older than any refresh threshold. So if app start does
> `refreshIfStale()` before or independently of the restore check, it **overwrites the user's
> entire history with a snapshot of an empty database** before they're ever asked.
>
> ```kotlin
> restoreCoordinator.checkForRestoredBackup()
> if (restoreCoordinator.pendingRestore.value == null) {   // <-- the guard
>     cloudSnapshotStore.refreshIfStale()
> }
> ```

### 7. Ask, don't restore silently

Prompt once, on the first launch after a restore, naming what's in the backup ("12 workouts, 16
sleep records") so the user recognises it as theirs. A silent restore is indistinguishable from
data loss if it goes wrong, and it can overwrite work if they'd already started using the new
phone. Declining should keep the snapshot on disk so it stays restorable from your settings
screen — an offer, not the only chance.

### 8. Make import non-destructive by default

```kotlin
enum class ImportMode { MERGE, REPLACE_ALL }
```

- **`MERGE`** — upsert by primary key. Records in the backup overwrite their local counterpart;
  anything logged since survives. Importing an old backup can never delete newer data. Make this
  the default and the primary button.
- **`REPLACE_ALL`** — wipe, then restore. Exact copy, at the cost of anything newer. Offer it as
  the explicit, warned-about secondary.

Wrap the whole restore in one DB transaction so a mid-way failure rolls back — most important in
`REPLACE_ALL`, where the delete has already happened. Write key-value settings **after** the
transaction commits: a DB rollback can't undo a DataStore write, so doing it inside would leave
settings applied for records that never landed.

---

## Also worth fixing while you're here

Two adjacent landmines that undo everything above:

```kotlin
// Debug only. In release this silently deletes all user data the moment a
// migration is missing -- exactly what the backup exists to prevent.
if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
```

…and audit any `clearAllData()`/reset path for the same coverage problem as the export. TriPath's
cleared 5 of 12 tables, so "Reset all data" left orphaned health records stitched onto an
otherwise fresh dataset.

Requires `buildFeatures { buildConfig = true }` on AGP 8+.

---

## Honest limitations

Say these out loud in your own docs, and don't overclaim in the UI:

- **Restore only happens at install time** — device setup, or reinstall. Not on demand. (Your
  manual JSON export covers the on-demand case.)
- **No API tells you the last successful upload.** Show your snapshot's "last prepared" timestamp
  and label it as such. Claiming "backed up ✓" when you can't verify the upload is worse than
  saying nothing.
- **Requires the user to have device backup enabled** and a Google account. Most do; some don't.
- **~25 MB per app**, and exceeding it fails silently — hence the gzip, and hence logging a warning
  as you approach it.
- **Roughly daily**, on charge + unmetered network + idle. Not real time.
- **Restore is skipped if the backup's `versionCode` is newer** than the installed app, unless you
  set `android:restoreAnyVersion="true"`. Usually you want the default.

If you need on-demand cross-device restore with confirmation of upload, that's when Drive
`appDataFolder` (or your own backend) earns its complexity — and its sign-in. For "don't lose my
data when my phone dies", this is strictly better, because it requires nothing of the user.

---

## Verify it for real

Do not trust this until you've watched a restore work. All commands non-destructive except where
noted.

```bash
# Is backup even on, and which transport?
adb shell bmgr enabled
adb shell bmgr list transports

# Force a pass for your app. Watch the byte count: it should be snapshot-sized,
# NOT database-sized. That difference is proof the rules took effect.
adb shell bmgr backupnow com.example.app

# Inspect what the app produced (debuggable builds)
adb shell run-as com.example.app ls -l files/cloud_backup/
adb shell run-as com.example.app cat files/cloud_backup/snapshot_meta.json

# Decode the payload and check the counts match reality
adb exec-out run-as com.example.app cat files/cloud_backup/snapshot.json.gz > snap.json.gz
python -c "import gzip,json; d=json.load(gzip.open('snap.json.gz')); \
print({k:len(v) for k,v in d.items() if isinstance(v,list)})"
```

Full round trip — **this deletes local app data**, so export first or use a throwaway device:

```bash
adb shell bmgr list sets                  # note the token for this device
adb uninstall com.example.app
adb install -r app-debug.apk              # do NOT launch yet
adb shell bmgr restore <token> com.example.app

# The payoff: snapshot present, databases/ EMPTY (proving the DB was excluded)
adb shell run-as com.example.app ls -lR files/
adb shell run-as com.example.app ls -l databases/

adb shell am start -n com.example.app/.MainActivity   # prompt should appear
adb logcat -d | grep RestoreCoordinator
```

TriPath's actual result: `bmgr backupnow` uploaded ~24 KB against a 237 KB DB + 420 KB WAL;
after uninstall/reinstall/restore, Google delivered a byte-identical `snapshot.json.gz`,
`databases/` was empty, the prompt appeared, and all 12 workouts, 16 sleep logs, 4 body scans and
every setting came back.

> ⚠️ `./gradlew connectedAndroidTest` **uninstalls your app when it finishes**, wiping its data.
> On a device holding real data, export first. Ask me how I know.

### Tests worth writing

- A **coverage test** that fails when a new table isn't in the backup — the one test that prevents
  the whole class of silent data loss:
  ```kotlin
  assertEquals(expectedTableKeys, AppBackupData(timestamp = 0L).recordCounts().keys)
  ```
- Per-entity round trips: encode → decode → assert full equality, so a dropped field is caught at
  build time rather than on someone's new phone.
- A checked-in **old-format fixture** that must still parse.
- Instrumented (in-memory Room) tests for merge-keeps-newer, idempotency (importing twice must not
  duplicate), nulls staying null, and any "excluded/ignored" flags surviving. These catch type
  converter bugs that pure-JVM serialization tests can't.

---

## Checklist

- [ ] `allowBackup="true"`, both rules files present and consistent
- [ ] Raw DB excluded from `<cloud-backup>`, included in `<device-transfer>`
- [ ] `cloud_backup/` and `datastore/` explicitly included
- [ ] Export covers **every** table + all preferences, verified by a coverage test
- [ ] Collections defaulted; enums tolerant; nulls preserved; device-local keys excluded
- [ ] Snapshot gzipped, temp-file + rename, sidecar metadata, `dataChanged()` called
- [ ] Refresh on start, on background (short threshold), after sync, and manually
- [ ] Restore detected via `noBackupFilesDir` marker; refresh **guarded** while a restore is pending
- [ ] User is asked, not silently overwritten; declining keeps the snapshot
- [ ] Import merges by default; replace-all is explicit; one transaction; prefs written after commit
- [ ] `fallbackToDestructiveMigration()` gated to debug; reset path covers all tables
- [ ] Round trip verified with `bmgr` on a real device
