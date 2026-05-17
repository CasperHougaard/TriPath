# CNS Import Debugging Guide

## Issue: No popup when opening TriPath after LiftPath export

If you're not seeing popups when CNS data is imported, here are the steps to debug:

## 1. Check Logs

The receiver logs all activity. Check logs using adb:

```bash
adb logcat | grep -E "CnsImportReceiver|ExternalDataImporter"
```

You should see logs like:
- `CnsImportReceiver: onReceive called with action: ...`
- `CnsImportReceiver: CNS import broadcast received!`
- `ExternalDataImporter: Importing CNS data: ...`

If you see NO logs with "CnsImportReceiver", the broadcast is not reaching the receiver.

## 2. Signature Permission Requirement

**CRITICAL**: Both apps (TriPath and LiftPath) MUST be signed with the same developer key for signature permissions to work.

### For LiftPath app (the sender):

The LiftPath app's `AndroidManifest.xml` MUST include:

```xml
<uses-permission android:name="com.tripath.permission.IMPORT_CNS" />
```

### Signing Requirement:

Both apps must be signed with the **same signing key**. If you're using debug builds, they should use the same debug keystore. If using release builds, they must use the same release keystore.

### Testing with Debug Builds:

1. Check if both apps use the same debug keystore:
   - Default debug keystore location: `~/.android/debug.keystore` (Linux/Mac) or `C:\Users\<username>\.android\debug.keystore` (Windows)
   - If they use different keystores, the signature permission will fail silently

2. To ensure both apps use the same keystore, you can:
   - Use the same keystore file in both projects
   - Or temporarily remove the signature permission for testing (not recommended for production)

## 3. Manual Test Import

You can test the import functionality directly from TriPath settings:

1. Open TriPath app
2. Go to Settings
3. Scroll to "Backup & Restore" section
4. Click "Test CNS Import" button
5. This will create test data for today's date and import it

If the test import works, the issue is with the broadcast delivery, not the import logic.

## 4. Verify Receiver is Registered

Check that the receiver is properly registered in `AndroidManifest.xml`:

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

## 5. Test Broadcast Manually (via ADB)

You can manually send a test broadcast using adb to verify the receiver is working:

```bash
adb shell am broadcast -a com.tripath.action.IMPORT_CNS_DATA --es cns_json '[{"date":"2024-01-15","cnsScore":45}]' com.tripath
```

**Note**: This will fail if signature permission is required and the broadcast isn't signed with the correct key. For testing, you might need to temporarily remove the `android:permission` attribute from the receiver in the manifest.

## 6. Common Issues

### Issue: "Permission Denial" in logs
- **Cause**: Apps are not signed with the same key
- **Fix**: Ensure both apps use the same signing keystore

### Issue: No logs at all
- **Cause**: Broadcast is not reaching the receiver (permission denied, wrong action, etc.)
- **Fix**: Check LiftPath is sending the broadcast with the correct action and package name

### Issue: Receiver logs but Toast doesn't show
- **Cause**: App was closed when broadcast was received
- **Fix**: Toasts require the app to be in the foreground. Check logs to verify import succeeded

### Issue: Import logs but no CNS data in workout details
- **Cause**: No strength workouts found on the date, or date mismatch
- **Fix**: Ensure you have strength workouts synced on the date matching the CNS data

## 7. Verify Import Success

After an import, check:
1. Logs show "Successfully updated CNS data for X workout(s)"
2. Open a strength workout detail screen from that date
3. CNS data should be displayed in a "CNS Data" card

If logs show success but UI doesn't show data, check:
- The workout is type STRENGTH
- The date matches exactly (YYYY-MM-DD format)
- The RawWorkoutData exists for that workout (requires Health Connect sync first)




