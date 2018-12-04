# shadowcloud [![Build Status](https://travis-ci.org/Karasiq/shadowcloud.svg?branch=master)](https://travis-ci.org/Karasiq/shadowcloud) [![codecov.io](https://codecov.io/github/Karasiq/shadowcloud/coverage.svg?branch=master)](https://codecov.io/github/Karasiq/shadowcloud?branch=master)
shadowcloud is an enhanced cloud storage client. [Download last release](https://github.com/Karasiq/shadowcloud/releases)

Описание на русском: https://habr.com/post/428523/
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
* Create region with a same id and storages set

# Plugins
Full list of currently supported algorithms (some requires libsodium):
```
Storages: dropbox, files, gdrive, mailrucloud, memory, webdav
Encryption: AES/CBC, AES/CCM, AES/CFB, AES/CTR, AES/EAX, AES/GCM, AES/OCB, AES/OFB, Blowfish/CBC, Blowfish/CFB, Blowfish/CTR, Blowfish/OFB, CAST5/CBC, CAST5/CFB, CAST5/CTR, CAST5/OFB, CAST6/CBC, CAST6/CCM, CAST6/CFB, CAST6/CTR, CAST6/EAX, CAST6/GCM, CAST6/OCB, CAST6/OFB, Camellia/CBC, Camellia/CCM, Camellia/CFB, Camellia/CTR, Camellia/EAX, Camellia/GCM, Camellia/OCB, Camellia/OFB, ChaCha20, ChaCha20/Poly1305, DES/CBC, DES/CFB, DES/CTR, DES/OFB, DESede/CBC, DESede/CFB, DESede/CTR, DESede/OFB, ECIES, GOST28147/CBC, GOST28147/CFB, GOST28147/CTR, GOST28147/OFB, IDEA/CBC, IDEA/CFB, IDEA/CTR, IDEA/OFB, Noekeon/CBC, Noekeon/CCM, Noekeon/CFB, Noekeon/CTR, Noekeon/EAX, Noekeon/GCM, Noekeon/OCB, Noekeon/OFB, RC2/CBC, RC2/CFB, RC2/CTR, RC2/OFB, RC6/CBC, RC6/CCM, RC6/CFB, RC6/CTR, RC6/EAX, RC6/GCM, RC6/OCB, RC6/OFB, RSA, Rijndael/CBC, Rijndael/CCM, Rijndael/CFB, Rijndael/CTR, Rijndael/EAX, Rijndael/GCM, Rijndael/OCB, Rijndael/OFB, SEED/CBC, SEED/CCM, SEED/CFB, SEED/CTR, SEED/EAX, SEED/GCM, SEED/OCB, SEED/OFB, Salsa20, Serpent/CBC, Serpent/CCM, Serpent/CFB, Serpent/CTR, Serpent/EAX, Serpent/GCM, Serpent/OCB, Serpent/OFB, Shacal2/CBC, Shacal2/CFB, Shacal2/CTR, Shacal2/OFB, Skipjack/CBC, Skipjack/CFB, Skipjack/CTR, Skipjack/OFB, TEA/CBC, TEA/CFB, TEA/CTR, TEA/OFB, Threefish/CBC, Threefish/CFB, Threefish/CTR, Threefish/OFB, Twofish/CBC, Twofish/CCM, Twofish/CFB, Twofish/CTR, Twofish/EAX, Twofish/GCM, Twofish/OCB, Twofish/OFB, X25519+XSalsa20/Poly1305, XSalsa20, XSalsa20/Poly1305, XTEA/CBC, XTEA/CFB, XTEA/CTR, XTEA/OFB
Signatures: ECDSA, Ed25519, RSA
Hashing: Blake2b, GOST3411, Keccak, MD2, MD4, MD5, RIPEMD128, RIPEMD160, RIPEMD256, RIPEMD320, SHA1, SHA224, SHA256, SHA3, SHA384, SHA512, SM3, Skein, Tiger, Whirlpool
```

# Docker image
Docker image is available at https://hub.docker.com/r/karasiq/shadowcloud/
Usage example:
```bash
docker run -v ~/.shadowcloud:/opt/docker/sc -p 1911:1911 -i -t karasiq/shadowcloud:latest
```
