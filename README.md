# network-monitor-android

Android network monitoring contracts and `ConnectivityManager.NetworkCallback` implementation.

## Usage

```kotlin
val monitor = AndroidNetworkMonitor(context)
monitor.start()

val statusFlow = monitor.status
```

## Notes

- Uses OS callbacks; no polling by default.
- Distinguishes connection availability from validated internet.
- Provides optional `InternetReachabilityChecker` for explicit HTTP probe checks.
- Requires `ACCESS_NETWORK_STATE`; explicit probe checks require `INTERNET`.

