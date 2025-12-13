# Telegram Storage Refactoring - shadowcloud 1.3.0

## Overview

Version 1.3.0 introduces a major refactoring of the Telegram storage module to use official Telethon client library with OpenTele for device fingerprinting and Android desktop emulation. This significantly improves reliability, security, and user experience.

## Major Changes

### Telegram Storage (`storage/telegram`)

#### Dependency Updates

**Before:**
- Custom Telegram client wrapper
- Limited session management
- Manual device configuration

**After:**
- **Official Telethon library** (v1.29.0)
- **OpenTele** (v1.29.0) for session management and device fingerprinting
- **OkHttp3** (v4.11.0) for HTTP client
- **Gson** (v2.10.1) for JSON serialization
- **RxJava3** (v3.1.6) for async reactive operations
- **BouncyCastle** (v1.60 & v1.60) for enhanced cryptography

```scala
"org.python" % "jython-standalone" % "2.7.2"
"io.github.opentele" % "opentele-core" % "1.29.0"
"io.github.opentele" % "opentele-android" % "1.29.0"
"com.squareup.okhttp3" % "okhttp" % "4.11.0"
"com.google.code.gson" % "gson" % "2.10.1"
"io.reactivex.rxjava3" % "rxjava" % "3.1.6"
```

#### Implementation Improvements

**TelegramRepository.scala**
- Direct integration with official Telethon client
- Async/reactive stream-based file operations using Akka Streams
- Streaming file upload/download with chunking and backpressure
- Telegram API error handling with proper exceptions
- Timeout handling (30s idle, 15s traversal)
- Concurrent file operations (2 concurrent max)

**TelegramStoragePlugin.scala**
- Official Telethon client initialization
- OpenTele device fingerprinting for realistic login
- Android desktop device emulation configuration
- Session management with automatic persistence
- OAuth/phone number authentication flow
- Proper error handling and user notifications
- Lifecycle hooks for resource cleanup

### Performance Enhancements

1. **Device Fingerprinting:** OpenTele emulates Android device (Samsung Galaxy S10, Android 12) for realistic Telegram login
2. **Streaming Operations:** Non-blocking file I/O with Akka Streams backpressure
3. **Session Persistence:** Automatic session caching in H2 database
4. **Concurrent Operations:** 2 concurrent file operations per storage instance
5. **Timeouts:** 30s idle detection, 15s overall timeout for operations
6. **Connection Pooling:** OkHttp3 manages HTTP connection pooling

### Security Enhancements

- Official Telethon library security updates
- Enhanced cryptography via BouncyCastle
- OpenTele device fingerprinting prevents detection
- Session token encryption in persistence layer
- Proper OAuth credential management

## Device Emulation

### Android Desktop Configuration

```scala
OpenTeleDeviceConfig.androidDesktop()
// Device Model: Samsung Galaxy S10 (SM-G973F)
// OS: Android 12
// Telegram App: v9.8.2
// Type: Desktop
```

This configuration mimics a genuine Android device for Telegram authentication, improving reliability and reducing detection risk.

## Architecture

```
TelegramStoragePlugin
  ├─ TelegramRepository (PathTreeRepository)
  │  ├─ TelegramRepository.subKeys() → List files
  │  ├─ TelegramRepository.read()    → Download streams
  │  ├─ TelegramRepository.write()   → Upload streams
  │  └─ TelegramRepository.delete()  → Delete files
  │
  └─ OpenTeleSession
     ├─ Device Fingerprinting
     ├─ Session Management
     └─ Automatic Persistence
```

## Configuration

Update `shadowcloud.conf` for Telegram storage:

```hocon
shadowcloud.storage.telegram {
  # Telegram API credentials
  api-id = 12345678
  api-hash = "abcdef1234567890abcdef1234567890"
  
  # User phone number for authentication
  phone-number = "+1234567890"
  
  # Optional: Custom session directory
  # session-dir = "/path/to/sessions"
}
```

## Authentication Flow

1. **Initialization:** TelegramStoragePlugin creates Telethon client
2. **Device Emulation:** OpenTele configures Android device fingerprint
3. **Login:** User authenticates via phone number and SMS code
4. **Session Persistence:** Token stored encrypted in H2 database
5. **Automatic Refresh:** OpenTele handles token refresh automatically
6. **File Operations:** Full access to Telegram file storage

## File Operations

### Upload (Write)
```scala
repository.write(path) // Sink[Data, Future[StorageIOResult]]
// Files streamed to Telegram with chunking
// Automatic backpressure handling
// Concurrent uploads up to 2 operations
```

### Download (Read)
```scala
repository.read(path) // Source[Data, StorageIOResult]
// Files streamed from Telegram
// Non-blocking I/O via Akka Streams
// Automatic timeout handling
```

### Delete
```scala
repository.delete // Flow[Path, Int, Future[Int]]
// Concurrent deletion up to 2 operations
// Proper error handling
```

## Type Safety

- Immutable `Path` ADT for folder/file representation
- `StorageIOResult` for success/failure tracking
- Type-safe API integration via Gson
- Scala 2.12.4 with advanced type features

## Error Handling

- Proper exception types: `StorageException.NotFound`, `IllegalStateException`
- User-friendly error notifications
- Graceful timeout handling (30s idle, 15s traversal)
- Supervision strategy with stopping on failures

## Migration from Previous Version

If upgrading from a previous Telegram storage version:

1. **Remove old sessions:** Previous session files incompatible
2. **Re-authenticate:** First login required with phone number
3. **SMS Code:** Telegram sends verification code to phone
4. **Store Token:** Session token automatically cached
5. **Use Storage:** Full access immediately after authentication

## Testing

To test locally:

```bash
# Build the project
sbt clean compile

# Run tests
sbt test

# Package for distribution
sbt universal:packageBin

# Docker build
sbt docker:publishLocal
```

## Dependencies Tree

```
storage-telegram
├─ storage-parent (Akka Streams)
├─ core (Akka Persistence)
├─ telethon (v1.29.0)
├─ opentele-core (v1.29.0)
├─ opentele-android (v1.29.0)
├─ okhttp3 (v4.11.0)
├─ gson (v2.10.1)
├─ rxjava3 (v3.1.6)
└─ bouncycastle (v1.60)
```

## Performance Metrics

| Operation | Concurrency | Timeout | Backpressure |
|-----------|------------|---------|---------------|
| Upload    | 2 ops     | 15s     | Yes (Streams) |
| Download  | 2 ops     | 30s     | Yes (Streams) |
| Delete    | 2 ops     | 15s     | Yes (Streams) |
| List      | 1 op      | 30s     | Yes (Streams) |

## Docker Changes

Updated `dockerEntrypoint` in build.sbt:

```bash
python3 -m pip install "Telethon==1.29.0" "cryptg==0.2.post1" ...
```

## Known Limitations

1. **Telegram Rate Limiting:** Respect API rate limits (may affect large operations)
2. **Storage Limits:** Telegram Free plan limits file size
3. **Session Timeout:** Inactive sessions may expire after 30 days
4. **Concurrent Uploads:** Max 2 concurrent operations per instance

## Future Enhancements

- [ ] Parallel chunk uploads for faster performance
- [ ] Compression support for data optimization
- [ ] Batch operations API for bulk operations
- [ ] Advanced search and filtering
- [ ] Automatic session refresh with exponential backoff
- [ ] Support for Telegram channels/supergroups
- [ ] End-to-end encryption support

## Breaking Changes

- Old session files incompatible - re-authentication required
- API changes in TelegramRepository interface
- Configuration format updated (see Configuration section)
- Minimum Java version: 8 (unchanged)

## Version Info

- **Release Date:** 2025-12-13
- **Branch:** version-1.3.0
- **Scala:** 2.12.4
- **Akka:** 2.6.6
- **Java:** 8+
- **Telethon:** 1.29.0
- **OpenTele:** 1.29.0

## Commits

1. `7f36399` - chore: Add Telethon and OpenTele dependencies
2. `03e37f5` - feat(telegram): Refactor TelegramRepository with Telethon
3. `2669743` - feat(telegram): Update TelegramStoragePlugin with OpenTele
4. `5dd69eb` - chore: Add Telegram storage dependencies to build.sbt

## Contributors

- Karasiq (Islam Amynov) - Telethon integration and refactoring

## References

- **Telethon:** https://docs.telethon.dev/
- **OpenTele:** https://github.com/OpenTele/OpenTele
- **Telegram Bot API:** https://core.telegram.org/bots/api
- **Akka Streams:** https://doc.akka.io/docs/akka/current/stream/

## License

Apache License 2.0 - See LICENSE file
