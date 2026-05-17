```

**Validation Rules:**
- Date must be in ISO format (YYYY-MM-DD)
- CNS score must be > 0 (0 indicates invalid/no data)
- Array can contain multiple entries (typically one per broadcast)

## Data Flow

### Import Process

1. **Broadcast Reception**
   - External app sends broadcast with action `com.tripath.action.IMPORT_CNS_DATA`
   - Android system validates signature permission
   - `CnsImportReceiver.onReceive()` is invoked

2. **Intent Validation**
   - Verify action matches `ACTION_IMPORT_CNS_DATA`
   - Extract `cns_json` extra from intent
   - Log reception for debugging

3. **Async Processing**
   - Call `goAsync()` to keep receiver process alive
   - Launch coroutine on `Dispatchers.IO`
   - Access `ExternalDataImporter` via EntryPoint

4. **Data Processing**
   - Parse JSON array to `List<CnsDataEntry>`
   - Filter invalid entries (cnsScore <= 0)
   - For each valid entry:
     a. Parse date from ISO format
     b. Query strength workouts on that date
     c. Query raw workout data for date range
     d. Match by `connectId`
     e. Update `RawWorkoutData.cnsJson`

5. **Completion**
   - Log success/failure
   - Show Toast notification
   - Call `pendingResult.finish()` to release resources

### Date Matching Logic

CNS data is matched to workouts using:
1. **Date Matching:** CNS entry date must match workout date
2. **Workout Type:** Only `WorkoutType.STRENGTH` workouts are matched
3. **ConnectId Matching:** `WorkoutLog.connectId` must match `RawWorkoutData.connectId`

**Time Range Calculation:**
```kotlin
val startOfDayMillis = workoutDate.atStartOfDay(ZoneId.systemDefault())
    .toInstant().toEpochMilli()
val endOfDayMillis = workoutDate.plusDays(1).atStartOfDay(ZoneId.systemDefault())
    .toInstant().toEpochMilli()
```

## Dependency Injection

### Hilt Configuration

**Application Class:**
```kotlin
@HiltAndroidApp
class TriPathApplication : Application()
```

**ServiceModule:**
- Provides `ExternalDataImporter` as singleton
- Installed in `SingletonComponent`
- Explicitly constructs with DAO dependencies

**DatabaseModule:**
- Provides `RawWorkoutDataDao` and `WorkoutLogDao`
- Installed in `SingletonComponent`

### EntryPoint Pattern

Since `BroadcastReceiver` cannot use `@AndroidEntryPoint`, the EntryPoint pattern is used:

```kotlin
val importer = EntryPointAccessors.fromApplication(
    context.applicationContext,
    ImporterEntryPoint::class.java
).getImporter()
```

This allows manual access to Hilt-managed dependencies in non-Hilt contexts.

## Error Handling

### Validation Checks

1. **Action Validation:**
   - Logs mismatch and returns early if action doesn't match

2. **JSON Extraction:**
   - Logs warning and shows Toast if `cns_json` extra is null

3. **JSON Parsing:**
   - Catches `SerializationException` and logs error
   - Throws exception to propagate to receiver

4. **Date Parsing:**
   - Catches `DateTimeParseException` for invalid date formats
   - Logs error and continues to next entry

5. **Data Matching:**
   - Logs warning if no strength workouts found on date
   - Logs warning if no matching `RawWorkoutData` found

### Exception Propagation

Exceptions in `importCnsData()` are:
- Caught and logged in receiver
- Displayed to user via Toast
- Propagated to allow proper cleanup

## Threading Model

### Coroutine Dispatchers

- **BroadcastReceiver:** Main thread (Android system)
- **CnsImportReceiver:** Uses `CoroutineScope(Dispatchers.IO)` for async work
- **ExternalDataImporter:** Uses `withContext(Dispatchers.IO)` for database operations

### Process Lifecycle

- `goAsync()` keeps BroadcastReceiver process alive during async work
- `pendingResult.finish()` releases WakeLock and system resources
- Prevents ANR (Application Not Responding) crashes

## Logging

### Log Tags

- `CnsImportReceiver`: All receiver activity
- `ExternalDataImporter`: Import processing details

### Log Levels

- **DEBUG:** Action validation, entry processing, individual updates
- **INFO:** Successful imports, completion status
- **WARN:** Missing data, no workouts found, no matches
- **ERROR:** Parsing failures, exceptions

### Example Log Output

```
D/CnsImportReceiver: onReceive called with action: com.tripath.action.IMPORT_CNS_DATA
D/CnsImportReceiver: CNS import broadcast received!
I/CnsImportReceiver: CNS Data received: 35 characters
D/ExternalDataImporter: Importing CNS data: 35 characters
D/ExternalDataImporter: Updated CNS data for workout: abc123, score: 45
I/ExternalDataImporter: Successfully updated CNS data for 1 workout(s) on 2024-12-31
I/CnsImportReceiver: CNS Data imported successfully
```

## Testing

### Manual Testing via ADB

```bash
adb shell am broadcast \
  -a com.tripath.action.IMPORT_CNS_DATA \
  --es cns_json '[{"date":"2024-12-31","cnsScore":45}]' \
  com.tripath
```

**Note:** This will fail if signature permission is required. Temporarily remove permission for testing.

### Test Function in Settings

`SettingsViewModel.testCnsImport()` creates test data for today's date:
- Creates JSON with current date
- Calls `externalDataImporter.importCnsData()`
- Provides immediate feedback via UI state

## AndroidManifest Configuration

### Receiver Declaration

```xml
<receiver
    android:name=".receivers.CnsImportReceiver"
    android:exported="true"
    android:permission="com.tripath.permission.IMPORT_CNS">
    <intent-filter>
        <action android:name="com.tripath.action.IMPORT_CNS_DATA" />
    </intent-filter>
</receiver>
```

**Attributes:**
- `android:exported="true"`: Allows external apps to send broadcasts
- `android:permission`: Requires signature permission
- `intent-filter`: Matches broadcast action

### Permission Declaration

```xml
<permission
    android:name="com.tripath.permission.IMPORT_CNS"
    android:protectionLevel="signature" />
```

## Future Enhancements

### Potential Improvements

1. **Batch Processing:** Support multiple CNS entries in single broadcast
2. **Data Validation:** Enhanced validation for CNS score ranges
3. **Conflict Resolution:** Handle multiple CNS entries for same workout
4. **Retry Logic:** Automatic retry for failed imports
5. **Metrics:** Track import success/failure rates
6. **UI Integration:** Display CNS data in workout detail screens
7. **Historical Import:** Support importing historical CNS data

## Related Files

- `app/src/main/java/com/tripath/receivers/CnsImportReceiver.kt`
- `app/src/main/java/com/tripath/data/local/importer/ExternalDataImporter.kt`
- `app/src/main/java/com/tripath/data/local/importer/CnsData.kt`
- `app/src/main/java/com/tripath/di/ServiceModule.kt`
- `app/src/main/java/com/tripath/data/local/database/entities/RawWorkoutData.kt`
- `app/src/main/java/com/tripath/data/local/database/dao/RawWorkoutDataDao.kt`
- `app/src/main/AndroidManifest.xml`

## References

- [Android BroadcastReceiver Documentation](https://developer.android.com/reference/android/content/BroadcastReceiver)
- [Hilt EntryPoint Documentation](https://dagger.dev/hilt/entry-points)
- [Android Signature Permissions](https://developer.android.com/guide/topics/manifest/permission-element#plevel)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)


