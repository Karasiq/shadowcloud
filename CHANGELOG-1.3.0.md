# shadowcloud 1.3.0 - Official Google Drive API Integration

## Overview

Version 1.3.0 introduces a major refactoring of the Google Drive storage module to use official Google APIs directly, improving reliability, performance, and maintainability.

## Major Changes

### Google Drive Storage (`storage/gdrive`)

#### Dependency Updates
- **Before:** Custom `gdrive-api` wrapper library (com.karasiq:gdrive-api)
- **After:** Official Google APIs
  - `com.google.apis:google-api-services-drive:v3` (latest)
  - `com.google.auth:google-auth-library-oauth2-http:1.11.0`
  - `com.google.auth:google-auth-library-java-jwt:1.11.0`
  - `com.google.http-client:google-http-client:1.43.3`
  - `com.google.http-client:google-http-client-jackson2:1.43.3`

#### Implementation Improvements

**GDriveRepository.scala**
- Direct integration with `com.google.api.services.drive.Drive` service
- Async/reactive stream-based file operations using Akka Streams
- Concurrent folder ID caching with TrieMap for high-performance lookups
- Streaming file upload/download with automatic backpressure handling
- Proper Google Drive query escaping for folder/file names
- Timeout and supervision strategy (10-15s timeouts, stopping on failures)
- Support for automatic folder hierarchy creation

**GDriveStoragePlugin.scala**
- Official OAuth 2.0 flow using `google-auth-library`
- `UserCredentials` support with refresh token management
- Service account credentials support (ready for implementation)
- Proper error handling with user notifications
- Health monitoring via Google Drive API `about().get()` for space calculation
- Credential caching in H2 persistence layer

### Performance Enhancements

1. **Folder Caching:** Avoid redundant API calls via concurrent TrieMap
2. **Streaming Operations:** Non-blocking file I/O with backpressure
3. **Batch Operations:** Efficient pagination with 1000-item page size
4. **Timeouts:** 30s idle detection, 15s overall timeout for traversal
5. **Concurrent Uploads/Downloads:** 2 concurrent operations per storage instance

### Type Safety & Code Quality

- Immutable `Path` ADT for folder/file representation
- `StorageIOResult` for proper success/failure tracking
- Scala 2.12.4 with `-opt` inline optimization flags
- Proper resource cleanup via Akka Streams materialization

## Migration Guide

### Configuration Changes

Update `shadowcloud.conf` for GDrive storage:

```hocon
shadowcloud.storage.gdrive {
  # OAuth credentials (required)
  client-id = "YOUR_GOOGLE_CLIENT_ID"
  client-secret = "YOUR_GOOGLE_CLIENT_SECRET"
  
  # Application name for Drive API calls
  application-name = "shadowcloud"
  
  # Optional: Team Drive ID
  # team-drive = "0AAAN..."
}
```

### Authorization Flow

1. User initiates storage setup in UI
2. System prompts for Google OAuth authorization
3. Credential token stored in H2 database
4. Refresh token automatically managed by google-auth-library
5. No manual token refresh required

## Bug Fixes

- Fixed concurrent modification issues in folder cache
- Improved error messages for Google Drive API failures
- Proper handling of duplicate file names during upload
- Graceful timeout handling for slow network conditions

## Breaking Changes

- Removed dependency on `com.karasiq:gdrive-api` wrapper
- GDrive storage now requires valid OAuth credentials (no key-file support yet)
- Minimum Java version: 8 (no changes from previous)

## Future Enhancements

- [ ] Service account credentials support (JSON key file)
- [ ] Team Drive / Shared Drive optimization
- [ ] Advanced filtering and search capabilities
- [ ] Batch operations API for bulk uploads
- [ ] Automatic token refresh with exponential backoff

## Testing

To test locally:

```bash
# Build the project
sbt clean compile

# Run tests (if available)
sbt test

# Package for distribution
sbt universal:packageBin
```

## Dependencies

**Added:**
- com.google.apis:google-api-services-drive:v3-rev20230815-1.35.2
- com.google.auth:google-auth-library-oauth2-http:1.11.0
- com.google.auth:google-auth-library-java-jwt:1.11.0
- com.google.http-client:google-http-client:1.43.3
- com.google.http-client:google-http-client-jackson2:1.43.3
- com.fasterxml.jackson.core:jackson-core:2.15.2

**Removed:**
- com.github.karasiq:gdrive-api:1.1.1

## Version Info

- **Release Date:** 2025-12-13
- **Branch:** version-1.3.0
- **Scala:** 2.12.4
- **Akka:** 2.6.6
- **Java:** 8+

## Commits

1. `b6104ef` - chore: Update GDrive dependencies to official Google APIs v3
2. `eb2a241` - feat(gdrive): Refactor GDriveRepository to use official Google Drive API v3
3. `da0f307` - feat(gdrive): Update GDriveStoragePlugin for official Google APIs
4. `d10a04f` - chore: Bump version to 1.3.0 with official GDrive API integration

## Contributors

- Karasiq (Islam Amynov) - Core refactoring and API integration

## License

Apache License 2.0 - See LICENSE file
