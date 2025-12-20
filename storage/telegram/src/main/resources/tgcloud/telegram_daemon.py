#!/usr/bin/env python3
"""
Telegram storage daemon with HTTP REST API and streaming support.
Runs embedded HTTP server on localhost:19999
Uses official Telegram Desktop API via OpenTele.
"""
import argparse
import asyncio
import io
import logging
import sys
from typing import Optional

from aiohttp import web
from telethon import TelegramClient
from telethon.errors import (
    FileReferenceExpiredError
)
from telethon.sessions import StringSession
from telethon.tl.types import Message, DocumentAttributeFilename

try:
    from opentele.td import TDesktop
    from opentele.api import API, UseCurrentSession
    OPENTELE_AVAILABLE = True
except ImportError:
    OPENTELE_AVAILABLE = False

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)

# Global state
_client: Optional[TelegramClient] = None
_entity_name: Optional[str] = None
_initialized: bool = False


async def create_tdesktop_client(session_string: str) -> TelegramClient:
    """Create TelegramClient with official Telegram Desktop API."""
    if not OPENTELE_AVAILABLE:
        raise RuntimeError("OpenTele not installed")

    logger.info("Initializing Telegram Desktop client...")

    tdesk = TDesktop()
    session = StringSession(session_string)

    client = await tdesk.ToTelethon(
        session=session,
        flag=UseCurrentSession,
        api=API.TelegramDesktop
    )

    logger.info(f"Using official Telegram Desktop API (ID: {client.api_id})")
    return client


# ============================================================================
# HTTP Request Handlers - Init & Management
# ============================================================================

async def handle_init(request: web.Request) -> web.Response:
    """Initialize daemon with session and entity."""
    global _client, _entity_name, _initialized

    try:
        data = await request.json()
        session_string = data.get("session")
        entity = data.get("entity", "me")

        if not session_string:
            return web.json_response({"error": "Missing session string"}, status=400)

        _client = await create_tdesktop_client(session_string)
        await _client.connect()

        if not await _client.is_user_authorized():
            return web.json_response({"error": "Session not authorized"}, status=401)

        _entity_name = entity
        _initialized = True

        me = await _client.get_me()
        logger.info(f"Initialized as {me.first_name} (@{me.username or 'N/A'})")

        return web.json_response({
            "status": "initialized",
            "user": {
                "id": me.id,
                "username": me.username,
                "first_name": me.first_name
            },
            "entity": entity
        })

    except Exception as e:
        logger.error(f"Initialization failed: {e}")
        return web.json_response({"error": str(e)}, status=500)


async def handle_health(request: web.Request) -> web.Response:
    """Health check endpoint."""
    if not _initialized or not _client:
        return web.json_response({
            "status": "not_initialized",
            "connected": False
        }, status=503)

    try:
        is_authorized = await _client.is_user_authorized()
        return web.json_response({
            "status": "healthy" if is_authorized else "unauthorized",
            "connected": _client.is_connected(),
            "authorized": is_authorized,
            "entity": _entity_name
        })
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return web.json_response({
            "status": "error",
            "error": str(e)
        }, status=500)


async def handle_status(request: web.Request) -> web.Response:
    """Get detailed status."""
    if not _initialized or not _client:
        return web.json_response({"status": "not_initialized"})

    try:
        me = await _client.get_me()
        return web.json_response({
            "status": "healthy",
            "user_id": me.id,
            "username": me.username,
            "entity": _entity_name,
            "initialized": True
        })
    except Exception as e:
        return web.json_response({
            "status": "error",
            "error": str(e)
        }, status=500)


async def handle_shutdown(request: web.Request) -> web.Response:
    """Shutdown daemon gracefully."""
    global _client

    logger.info("Shutdown requested")

    if _client:
        await _client.disconnect()
        _client = None

    asyncio.create_task(shutdown_server(request.app))
    return web.json_response({"status": "shutdown"})


async def shutdown_server(app: web.Application):
    """Shutdown the aiohttp server."""
    await asyncio.sleep(1)
    app.loop.stop()


# ============================================================================
# Upload Handlers - Streaming & Bytes
# ============================================================================

async def handle_upload_stream(request: web.Request) -> web.Response:
    """
    Upload file from multipart stream.
    No disk write - streams directly from client to Telegram.

    Multipart fields:
    - file: binary data
    - entity: target entity (e.g., "me")
    - caption: optional caption
    """
    if not _initialized or not _client:
        return web.json_response({"error": "Not initialized"}, status=503)

    try:
        reader = await request.multipart()
        file_data = None
        entity = _entity_name
        caption = ""
        file_name = "file"

        async for field in reader:
            if field.name == "file":
                file_name = field.filename or "file"
                # Read all chunks from field into bytes
                file_data = await field.read()
                logger.debug(f"Received file: {file_name} ({len(file_data)} bytes)")

            elif field.name == "entity":
                entity = (await field.read()).decode("utf-8")

            elif field.name == "caption":
                caption = (await field.read()).decode("utf-8")

        if file_data is None:
            return web.json_response({"error": "No file data"}, status=400)

        # Create in-memory file-like object
        file_obj = io.BytesIO(file_data)

        logger.info(f"Streaming upload: {file_name} ({len(file_data)} bytes)")

        # Send to Telegram directly from memory
        message: Message = await _client.send_file(
            entity,
            file_obj,
            caption=caption,
            force_document=True,
            attributes=[DocumentAttributeFilename(file_name)]
        )

        logger.info(f"Stream upload complete: {file_name} (msg#{message.id})")

        return web.json_response({
            "status": "uploaded",
            "message_id": message.id,
            "file_name": file_name,
            "file_size": len(file_data)
        })

    except Exception as e:
        logger.error(f"Stream upload failed: {e}")
        return web.json_response({"error": str(e)}, status=500)


async def handle_upload_bytes(request: web.Request) -> web.Response:
    """
    Upload file from raw bytes in request body.
    No disk write - streams directly to Telegram.

    Query parameters:
    - name: filename
    - caption: optional caption
    """
    if not _initialized or not _client:
        return web.json_response({"error": "Not initialized"}, status=503)

    try:
        # Read binary body
        file_data = await request.read()

        # Get parameters from query string
        file_name = request.query.get("name", "file")
        caption = request.query.get("caption", "")

        logger.info(f"Bytes upload: {file_name} ({len(file_data)} bytes)")

        # Create in-memory file-like object
        file_obj = io.BytesIO(file_data)

        # Send to Telegram directly from memory
        message: Message = await _client.send_file(
            _entity_name,
            file_obj,
            caption=caption,
            force_document=True,
            attributes=[DocumentAttributeFilename(file_name)]
        )

        logger.info(f"Bytes upload complete: {file_name} (msg#{message.id})")

        return web.json_response({
            "status": "uploaded",
            "message_id": message.id,
            "file_name": file_name,
            "file_size": len(file_data)
        })

    except Exception as e:
        logger.error(f"Bytes upload failed: {e}")
        return web.json_response({"error": str(e)}, status=500)


# ============================================================================
# Download Handlers - Streaming & Bytes
# ============================================================================

async def handle_download_stream(request: web.Request) -> web.StreamResponse:
    """
    Download file as streaming response.
    Chunks file directly from Telegram to client.
    """
    if not _initialized or not _client:
        return web.json_response({"error": "Not initialized"}, status=503)

    try:
        message_id = int(request.match_info["message_id"])

        logger.info(f"Stream download: message {message_id}")

        message: Message = await _client.get_messages(_entity_name, ids=message_id)

        if not message or not message.document:
            return web.json_response(
                {"error": "Message not found or has no document"},
                status=404
            )

        # Get file name
        file_name = "file"
        for attr in message.document.attributes:
            if isinstance(attr, DocumentAttributeFilename):
                file_name = attr.file_name
                break

        # Create streaming response
        response = web.StreamResponse(
            status=200,
            headers={
                "Content-Type": "application/octet-stream",
                "Content-Disposition": f'attachment; filename="{file_name}"',
                "Content-Length": str(message.document.size)
            }
        )

        await response.prepare(request)

        # Stream file in chunks
        chunk_size = 256 * 1024  # 256 KB chunks
        async for chunk in _client.iter_download(message.document, chunk_size=chunk_size):
            await response.write(chunk)

        logger.info(f"Stream download complete: {file_name}")
        await response.write_eof()

        return response

    except FileReferenceExpiredError:
        logger.warning(f"File reference expired for message {message_id}")
        return web.json_response(
            {"error": "File reference expired"},
            status=410
        )

    except Exception as e:
        logger.error(f"Stream download failed: {e}")
        return web.json_response({"error": str(e)}, status=500)


async def handle_download_bytes(request: web.Request) -> web.Response:
    """
    Download file as complete ByteString in response body.
    Loads entire file into memory.
    """
    if not _initialized or not _client:
        return web.json_response({"error": "Not initialized"}, status=503)

    try:
        message_id = int(request.match_info["message_id"])

        logger.info(f"Bytes download: message {message_id}")

        message: Message = await _client.get_messages(_entity_name, ids=message_id)

        if not message or not message.document:
            return web.json_response(
                {"error": "Message not found or has no document"},
                status=404
            )

        # Download file into memory
        file_data = await _client.download_media(message, file=bytes)

        # Get file name
        file_name = "file"
        for attr in message.document.attributes:
            if isinstance(attr, DocumentAttributeFilename):
                file_name = attr.file_name
                break

        logger.info(f"Bytes download complete: {file_name} ({len(file_data)} bytes)")

        return web.Response(
            body=file_data,
            status=200,
            headers={
                "Content-Type": "application/octet-stream",
                "Content-Disposition": f'attachment; filename="{file_name}"',
                "Content-Length": str(len(file_data))
            }
        )

    except FileReferenceExpiredError:
        logger.warning(f"File reference expired for message {message_id}")
        return web.json_response(
            {"error": "File reference expired"},
            status=410
        )

    except Exception as e:
        logger.error(f"Bytes download failed: {e}")
        return web.json_response({"error": str(e)}, status=500)


# ============================================================================
# List & Delete Handlers
# ============================================================================

async def handle_list(request: web.Request) -> web.Response:
    """List files in entity."""
    if not _initialized or not _client:
        return web.json_response({"error": "Not initialized"}, status=503)

    try:
        data = await request.json()
        limit = data.get("limit", 100)
        offset = data.get("offset", 0)

        if not (0 < limit <= 1000):
            return web.json_response({"error": "limit must be 1-1000"}, status=400)
        if offset < 0:
            return web.json_response({"error": "offset must be >= 0"}, status=400)

        logger.info(f"Listing files (limit={limit}, offset={offset})...")

        files = []
        async for message in _client.iter_messages(_entity_name, limit=limit, offset_id=offset, reverse=True):
            if message.document:
                file_name = "file"
                for attr in message.document.attributes:
                    if isinstance(attr, DocumentAttributeFilename):
                        file_name = attr.file_name
                        break

                files.append({
                    "message_id": message.id,
                    "file_name": file_name,
                    "file_size": message.document.size,
                    "mime_type": message.document.mime_type,
                    "date": message.date.isoformat()
                })

        logger.info(f"Found {len(files)} files")

        return web.json_response({
            "status": "listed",
            "files": files,
            "count": len(files)
        })

    except Exception as e:
        logger.error(f"List failed: {e}")
        return web.json_response({"error": str(e)}, status=500)


async def handle_delete(request: web.Request) -> web.Response:
    """Delete file from Telegram."""
    if not _initialized or not _client:
        return web.json_response({"error": "Not initialized"}, status=503)

    try:
        data = await request.json()
        message_id = data.get("message_id")

        if not message_id:
            return web.json_response({"error": "Missing message_id"}, status=400)

        logger.info(f"Deleting message {message_id}...")

        await _client.delete_messages(_entity_name, [message_id])

        logger.info(f"Deleted message {message_id}")

        return web.json_response({
            "status": "deleted",
            "message_id": message_id
        })

    except Exception as e:
        logger.error(f"Delete failed: {e}")
        return web.json_response({"error": str(e)}, status=500)


# ============================================================================
# Server Setup
# ============================================================================

def create_app() -> web.Application:
    """Create aiohttp application with routes."""
    if not OPENTELE_AVAILABLE:
        logger.error("OpenTele not installed")
        sys.exit(1)

    app = web.Application()

    # Initialization & management
    app.router.add_post('/cmd/init', handle_init)
    app.router.add_post('/cmd/list', handle_list)
    app.router.add_post('/cmd/delete', handle_delete)
    app.router.add_post('/cmd/shutdown', handle_shutdown)

    # Streaming upload/download
    app.router.add_post('/upload/stream', handle_upload_stream)
    app.router.add_post('/upload/bytes', handle_upload_bytes)
    app.router.add_get('/download/stream/{message_id}', handle_download_stream)
    app.router.add_get('/download/bytes/{message_id}', handle_download_bytes)

    # Health checks
    app.router.add_get('/health', handle_health)
    app.router.add_get('/status', handle_status)

    return app


def main():
    """Entry point."""
    parser = argparse.ArgumentParser(description="Telegram storage daemon")
    parser.add_argument("--host", default="127.0.0.1", help="HTTP server host")
    parser.add_argument("--port", type=int, default=19999, help="HTTP server port")
    args = parser.parse_args()

    logger.info(f"Starting Telegram daemon on {args.host}:{args.port}")

    app = create_app()
    web.run_app(app, host=args.host, port=args.port, print=logger.info)


if __name__ == "__main__":
    main()
