# [shadowcloud](https://karasiq.github.io/shadowcloud) [![Build Status](https://travis-ci.org/Karasiq/shadowcloud.svg?branch=master)](https://travis-ci.org/Karasiq/shadowcloud) [![codecov.io](https://codecov.io/github/Karasiq/shadowcloud/coverage.svg?branch=master)](https://codecov.io/github/Karasiq/shadowcloud?branch=master)
shadowcloud is an enhanced cloud storage client. [Download last release](https://github.com/Karasiq/shadowcloud/releases)

Статья на русском: https://habr.com/post/428523/
![Files view](https://github.com/Karasiq/shadowcloud/raw/master/images/files%20view.png "Files view")

# Features
* Allows to use different storages as one "drive" (virtual region)
* Enables random file access, media streaming
* Removes limits on file size, file names, etc
* Full encryption by default
* Checksums
* Deduplication
* File and directory versioning
* Generates previews
* Extracts documents content and metadata
* Markdown notes
* Easy web pages archiving
* FUSE emulated file system (experimental, requires [fuse, osxfuse or winfsp](https://github.com/SerCeMan/jnr-fuse/blob/master/INSTALLATION.md))
* In-mem cache 
* Almost everything is customizable (see [reference.conf](https://github.com/Karasiq/shadowcloud/blob/master/core/src/main/resources/reference.conf))

# How to use
* Open http://localhost:1911/, switch to Regions tab
* Generate new key (copy and save output)
* Add your cloud storage
  * Example Dropbox configuration:
  ```
  credentials.login="test@firemail.cc"
  type=dropbox
  ```
  * Example Yandex.Disk configuration:
  ```
  address.uri="https://webdav.yandex.com"
  credentials {
    login="johndoe"
    password="123456"
  }
  type=webdav
  ```
* Create a region and attach storages to it
* Switch to Folders tab and upload your files

![Regions view](https://github.com/Karasiq/shadowcloud/raw/master/images/regions%20view.png "Regions view")

# Synchronization
* Import previously generated key on the second device
* Create region with a same id and storages set (or use the export/import feature)

# How to archive web page
shadowcloud uses [webzinc](https://github.com/Karasiq/webzinc) to capture web pages in a single HTML file.

Page will be bundled with all of the embedded resources, allowing you to read it even if the original website goes down.
* Open Upload form in the web interface
* Select "Paste text"
* Paste URL in the form and click Submit

# rclone integration
If you have configured a rclone drive, you can run `rclone mount drive123:/ /mnt/example`, and then point the shadowcloud to this location: 
```
address.uri="file:////mnt/example"
type=files
```

# Google Drive quota exceeded error
You can bypass quota error by creating your own API key.

1. Create API credentials as described here: https://rclone.org/drive/#making-your-own-client-id
2. Paste your client_id and secret into the storage props in Web UI:
```hocon
config.gdrive.oauth.secrets.installed {
  "client_id" = "YOUR_ID"
  "client_secret" = "YOUR_SECRET"
}
```
2.1: Or paste into your global ~/.shadowcloud/shadowcloud.conf: 
```hocon
shadowcloud.storage.gdrive.oauth.secrets.installed {
  "client_id" = "YOUR_ID"
  "client_secret" = "YOUR_SECRET"
}
```

# How to configure Telegram (free+unlimited!) storage
1. You should have Python 3 installed on your system: https://www.python.org/downloads/ and also the pip package installer: https://pip.pypa.io/en/stable/installing/
2. Install required packages with pip (executable can be `pip3` or `pip` depending on OS, on Windows it's most likely `pip`):
    * Linux/MacOS:
    ```bash
    sudo pip3 install "Telethon>=1.14.0" "cryptg==0.2.post1" "Quart>=0.12.0" "Hypercorn>=0.9.5" "lz4==3.1.0" "pytz>=2020.1"
    ```
    * Windows:
    ```bash
    pip install "Telethon>=1.14.0" "cryptg==0.2.post1" "Quart>=0.12.0" "Hypercorn>=0.9.5" "lz4==3.1.0" "pytz>=2020.1"
    ```
3. Create storage with `type=telegram` and follow instructions

# Plugins
Full list of currently supported algorithms (some requires libsodium):
```
Storages: dropbox, files, gdrive, mailrucloud, memory, webdav
Encryption: AES/CBC, AES/CCM, AES/CFB, AES/CTR, AES/EAX, AES/GCM, AES/OCB, AES/OFB, Blowfish/CBC, Blowfish/CFB, Blowfish/CTR, Blowfish/OFB, CAST5/CBC, CAST5/CFB, CAST5/CTR, CAST5/OFB, CAST6/CBC, CAST6/CCM, CAST6/CFB, CAST6/CTR, CAST6/EAX, CAST6/GCM, CAST6/OCB, CAST6/OFB, Camellia/CBC, Camellia/CCM, Camellia/CFB, Camellia/CTR, Camellia/EAX, Camellia/GCM, Camellia/OCB, Camellia/OFB, ChaCha20, ChaCha20/Poly1305, DES/CBC, DES/CFB, DES/CTR, DES/OFB, DESede/CBC, DESede/CFB, DESede/CTR, DESede/OFB, ECIES, GOST28147/CBC, GOST28147/CFB, GOST28147/CTR, GOST28147/OFB, IDEA/CBC, IDEA/CFB, IDEA/CTR, IDEA/OFB, Noekeon/CBC, Noekeon/CCM, Noekeon/CFB, Noekeon/CTR, Noekeon/EAX, Noekeon/GCM, Noekeon/OCB, Noekeon/OFB, RC2/CBC, RC2/CFB, RC2/CTR, RC2/OFB, RC6/CBC, RC6/CCM, RC6/CFB, RC6/CTR, RC6/EAX, RC6/GCM, RC6/OCB, RC6/OFB, RSA, Rijndael/CBC, Rijndael/CCM, Rijndael/CFB, Rijndael/CTR, Rijndael/EAX, Rijndael/GCM, Rijndael/OCB, Rijndael/OFB, SEED/CBC, SEED/CCM, SEED/CFB, SEED/CTR, SEED/EAX, SEED/GCM, SEED/OCB, SEED/OFB, Salsa20, Serpent/CBC, Serpent/CCM, Serpent/CFB, Serpent/CTR, Serpent/EAX, Serpent/GCM, Serpent/OCB, Serpent/OFB, Shacal2/CBC, Shacal2/CFB, Shacal2/CTR, Shacal2/OFB, Skipjack/CBC, Skipjack/CFB, Skipjack/CTR, Skipjack/OFB, TEA/CBC, TEA/CFB, TEA/CTR, TEA/OFB, Threefish/CBC, Threefish/CFB, Threefish/CTR, Threefish/OFB, Twofish/CBC, Twofish/CCM, Twofish/CFB, Twofish/CTR, Twofish/EAX, Twofish/GCM, Twofish/OCB, Twofish/OFB, X25519+XSalsa20/Poly1305, XSalsa20, XSalsa20/Poly1305, XTEA/CBC, XTEA/CFB, XTEA/CTR, XTEA/OFB
Signatures: ECDSA, Ed25519, RSA
Hashing: Blake2b, GOST3411, Keccak, MD2, MD4, MD5, RIPEMD128, RIPEMD160, RIPEMD256, RIPEMD320, SHA1, SHA224, SHA256, SHA3, SHA384, SHA512, SM3, Skein, Tiger, Whirlpool
```

# Docker image
[![Docker](http://dockeri.co/image/karasiq/shadowcloud)](https://hub.docker.com/r/karasiq/shadowcloud/)

Docker image is available at https://hub.docker.com/r/karasiq/shadowcloud/

Usage example:
```bash
docker run -v ~/.shadowcloud:/opt/docker/sc -p 1911:1911 -i -t karasiq/shadowcloud:latest
```

With FUSE drive:
```bash
docker run -v ~/.shadowcloud:/opt/docker/sc -v ~/sc:/opt/docker/sc-drive --device /dev/fuse --cap-add SYS_ADMIN -p 1911:1911 -i karasiq/shadowcloud:latest
```
