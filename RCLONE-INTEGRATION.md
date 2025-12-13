# RClone Storage Integration for shadowcloud

## Overview

The RClone storage module enables shadowcloud to access 40+ cloud storage backends by leveraging the industry-standard [RClone](https://rclone.org/) command-line tool. This provides a unified interface to diverse storage providers without implementing separate integrations for each.

## Supported Backends (40+)

RClone supports an extensive list of storage providers:

### Object Storage
- **Amazon S3** - AWS Simple Storage Service
- **Google Cloud Storage (GCS)** - Google's object storage
- **Azure Blob Storage** - Microsoft Azure
- **DigitalOcean Spaces** - DO's S3-compatible storage
- **Wasabi** - S3-compatible cloud storage
- **Backblaze B2** - Cloud backup and storage
- **IBM Cloud Object Storage** - IBM's S3-compatible service
- **Oracle Object Storage** - Oracle Cloud
- **Alibaba OSS** - Alibaba Cloud Object Storage Service

### File Hosting & Sync
- **OneDrive / SharePoint** - Microsoft's cloud storage
- **iCloud Drive** - Apple's cloud storage
- **Dropbox** - File hosting and sync
- **Google Drive** - Google's cloud storage
- **Box** - Enterprise file hosting
- **Nextcloud** - Self-hosted cloud storage
- **OwnCloud** - Self-hosted file sharing
- **Seafile** - Self-hosted file sync

### Protocols
- **SFTP** - SSH File Transfer Protocol
- **FTPS** - FTP with TLS/SSL
- **WebDAV** - HTTP-based file storage
- **HTTP** - Read-only HTTP file serving
- **SSH** - SSH/SFTP remote access

### Archive & Backup
- **Mega** - Encrypted cloud storage
- **pCloud** - Cloud storage service
- **Yandex.Disk** - Yandex's cloud storage
- **Mail.ru Cloud** - Russian cloud storage
- **Koofr** - Cloud storage provider
- **JottaCloud** - Norwegian cloud storage

### Local & Specialized
- **Local filesystem** - Access local drives
- **SMB/CIFS** - Windows network shares
- **NFS** - Network filesystem
- **Google Photos** - Photo backup
- **Tresorit** - Zero-knowledge cloud storage
- **Citrix ShareFile** - Enterprise file sharing

## Installation

### Linux/macOS

```bash
# Using package managers
# Ubuntu/Debian
sudo apt-get install rclone

# macOS (Homebrew)
brew install rclone

# Using official installer
curl https://rclone.org/install.sh | sudo bash
```

### Windows

```powershell
# Using Chocolatey
choco install rclone

# Using Scoop
scoop install rclone

# Manual download
# https://downloads.rclone.org/
```

### Docker

```dockerfile
# RClone is pre-installed in shadowcloud Docker images
FROM shadowcloud:latest
# RClone binary is available at /usr/bin/rclone
```

## Configuration

### shadowcloud.conf

```hocon
shadowcloud.storage.rclone {
  # RClone remote name (must be configured in rclone config)
  remote-name = "myremote"
  
  # Remote type (informational)
  remote-type = "s3"
  
  # Path to RClone binary (auto-detected if not specified)
  # rclone-path = "/usr/bin/rclone"
  
  # Optional: Path to RClone config file
  # config-path = "/home/user/.config/rclone/rclone.conf"
  
  # Optional: Cache directory for temporary files
  # cache-dir = "/var/cache/shadowcloud/rclone"
}
```

### RClone Remote Configuration

Configure RClone remotes using the interactive setup:

```bash
rclone config
```

#### Amazon S3 Example

```bash
$ rclone config

n) New remote
s) Set configuration password
q) Quit config
e/n/d/r/c/s/q> n

name> aws-s3

Type of storage to configure:
Storage> s3
Provider> AWS
Access Key ID> [Your AWS Key]
Secret Access Key> [Your AWS Secret]
Region> us-west-2
ACL> private
```

#### Google Cloud Storage Example

```bash
name> gcs
Storage> gcs
Google Application Credentials> [Path to service account JSON]
Project ID> [Your GCP Project]
```

#### Dropbox Example

```bash
name> dropbox
Storage> dropbox
OAuth 2.0 setup
# Will open browser for authentication
```

#### SFTP Example

```bash
name> sftp-server
Storage> sftp
Host> example.com
User> username
Password> [Enter password or use key]
```

### List Configured Remotes

```bash
rclone listremotes
```

## Usage

### Basic Storage Configuration

```hocon
storage {
  rclone-aws {
    type = "rclone"
    remote-name = "aws-s3"
    remote-type = "s3"
  }
  
  rclone-gcs {
    type = "rclone"
    remote-name = "gcs"
    remote-type = "gcs"
  }
  
  rclone-dropbox {
    type = "rclone"
    remote-name = "dropbox"
    remote-type = "dropbox"
  }
}
```

### File Operations

All standard shadowcloud file operations work seamlessly:

```scala
// List files
repository.subKeys(path)

// Read file
repository.read(key)

// Write file
repository.write(path)

// Delete file
repository.delete
```

## Performance

### Optimization Settings

```bash
# Enable RClone cache for faster access
rclone ls --vfs-cache-mode full myremote:/path

# Use parallel transfers for S3
rclone sync --transfers=4 --checkers=4 source:/ dest:/

# Increase connection pool for better throughput
rclone ls --use-server-modtime --no-modtime myremote:/path
```

### Performance Metrics

| Operation | Concurrency | Notes |
|-----------|------------|-------|
| Upload | 2 concurrent | Streaming via pipes |
| Download | 2 concurrent | Streaming via pipes |
| Delete | 2 concurrent | Batch deletion |
| List | 1 sequential | JSON output parsing |

### Bandwidth Optimization

1. **Compression:** Enable RClone compression for compatible backends
2. **Deduplication:** RClone's dedupe command removes duplicates
3. **Caching:** Use VFS caching layer for frequently accessed files
4. **Parallelism:** Increase concurrent operations for large transfers

## Advanced Usage

### Chained Remotes

Combine multiple backends:

```bash
rclone config
name> combined
Storage> alias
Remote> /aws-s3/folder:/gcs/another
```

### Encrypted Storage

```bash
rclone config
name> encrypted-s3
Storage> crypt
Remote> aws-s3:/encrypted
Password> [Encryption password]
```

### Tiering Strategy

```hocon
storage {
  # Hot storage for frequent access
  hot {
    type = "rclone"
    remote-name = "gcs"  # Low latency
  }
  
  # Cold storage for archival
  cold {
    type = "rclone"
    remote-name = "b2"   # Low cost
  }
}
```

### Bandwidth Limiting

```bash
# Limit bandwidth during operations
rclone ls --bwlimit=10M myremote:/path

# Schedule-based limiting (off-peak hours)
rclone --bwlimit='08:00,512k 12:00,10M' ...
```

## Monitoring & Debugging

### Enable RClone Verbose Output

```hocon
shadowcloud.storage.rclone {
  remote-name = "myremote"
  # Add debug logging
  debug = true
  verbose = true
}
```

### Check RClone Health

```bash
# Test connectivity
rclone ls myremote:/

# Check account quota
rclone about myremote:/

# Verify configuration
rclone config show
```

### Monitor Transfers

```bash
# Real-time progress
rclone sync --progress source:/ dest:/

# JSON output for parsing
rclone lsf --json myremote:/path
```

## Error Handling

### Common Issues

#### RClone Binary Not Found

```
Error: RClone binary not found or not executable at: rclone
Solution: Install RClone or set rclone-path in config
```

#### Remote Not Configured

```
Error: Remote 'myremote' not found
Solution: Run 'rclone config' to configure the remote
```

#### Authentication Failed

```
Error: 401 Unauthorized
Solution: Check credentials, refresh OAuth tokens with 'rclone authorize'
```

#### Timeout on Large Files

```
Solution: Increase RClone timeouts
rclone --timeout=10m ls myremote:/path
```

### Retry Strategy

RClone automatically retries failed operations:

```bash
# Configure retry behavior
rclone --retries=3 --retry-delay=1s sync source:/ dest:/
```

## Security Best Practices

1. **Encrypt Credentials**
   ```bash
   rclone config set-password
   ```

2. **Use Service Accounts**
   - Prefer service account keys over user credentials
   - Rotate keys regularly

3. **Restrict Permissions**
   ```bash
   # Limit remote access to specific path
   rclone config
   # Set path filter during config
   ```

4. **Enable Logging**
   - RClone logs all operations
   - Review logs for suspicious activity

5. **Secure Config Storage**
   - Use file permissions: `chmod 600 ~/.config/rclone/rclone.conf`
   - Consider encrypted filesystem for config

## Cost Optimization

### Storage Selection

| Provider | Cost | Speed | Best For |
|----------|------|-------|----------|
| AWS S3 | Medium | Fast | General purpose |
| Google Cloud | Medium | Fast | Large workloads |
| Backblaze B2 | Low | Slow | Archival |
| Wasabi | Medium | Fast | S3 alternative |
| Yandex.Disk | Low | Medium | Budget storage |

### Lifecycle Policies

```bash
# Archive old files to cheaper storage
rclone copy --min-age=30d hot:/ cold:/
```

### Bandwidth Cost Reduction

```bash
# Reduce API calls
rclone sync --no-check-dest source:/ dest:/

# Enable deduplication
rclone dedupe oldest myremote:/
```

## Integration Examples

### Multi-Cloud Strategy

```hocon
storage {
  primary-aws {
    type = "rclone"
    remote-name = "aws-s3"
  }
  
  backup-gcs {
    type = "rclone"
    remote-name = "gcs-backup"
  }
  
  archive-b2 {
    type = "rclone"
    remote-name = "b2-archive"
  }
}
```

### Hybrid Cloud

```hocon
storage {
  local-cache {
    type = "rclone"
    remote-name = "local-ssd"
  }
  
  cloud-primary {
    type = "rclone"
    remote-name = "aws-s3"
  }
}
```

## Troubleshooting

### Slow Operations

1. Check RClone process CPU/memory
2. Increase concurrent transfers
3. Enable RClone cache
4. Verify network bandwidth
5. Check remote provider status

### Missing Files

1. Verify RClone config with `rclone listremotes`
2. Check file permissions
3. Use `rclone check` to verify integrity
4. Review RClone logs for errors

### Authentication Issues

1. Refresh OAuth tokens: `rclone authorize`
2. Verify credentials in config
3. Check service account permissions
4. Test with `rclone ls`

## See Also

- [RClone Official Documentation](https://rclone.org/)
- [RClone Forum](https://forum.rclone.org/)
- [Shadowcloud Documentation](https://github.com/Karasiq/shadowcloud)

## License

RClone: MIT License  
Shadowcloud RClone Integration: Apache License 2.0
