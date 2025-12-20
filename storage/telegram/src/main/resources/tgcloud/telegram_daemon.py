# modules/storage-telegram/python/telegram_http_daemon.py

import os
import asyncio
import json
import logging
from typing import Optional
from pathlib import Path

from fastapi import FastAPI, HTTPException, UploadFile, File
from fastapi.responses import FileResponse, JSONResponse
from contextlib import asynccontextmanager
import uvicorn

from opentele.td import TDesktop
from opentele.tl import TelegramClient
from opentele.api import API, UseCurrentSession
from telethon.types import TypeInputPeer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class TelegramStorageDaemon:
    """Async Telegram storage daemon using opentele + FastAPI"""

    def __init__(
            self,
            tdata_path: Optional[str] = None,
            session_file: str = "/tmp/telegram.session",
            api_type: str = "android"
    ):
        self.tdata_path = tdata_path
        self.session_file = session_file
        self.client: Optional[TelegramClient] = None
        self.api_type = api_type
        self.upload_dir = Path("/tmp/telegram_uploads")
        self.upload_dir.mkdir(exist_ok=True)

    async def initialize(self):
        """Initialize Telegram client with opentele"""
        try:
            if self.tdata_path and os.path.exists(self.tdata_path):
                logger.info(f"Loading TDesktop session from {self.tdata_path}")
                tdesk = TDesktop(self.tdata_path)

                # Select API based on type
                api = getattr(API, f"Telegram{self.api_type.capitalize()}").Generate()

                # Convert tdata to Telethon client
                self.client = await tdesk.ToTelethon(
                    session=self.session_file,
                    flag=UseCurrentSession,
                    api=api
                )
            else:
                logger.info("Creating new Telethon session")
                api = getattr(API, f"Telegram{self.api_type.capitalize()}").Generate()
                self.client = TelegramClient(self.session_file, api=api)

            # Connect to Telegram
            await self.client.connect()

            if not await self.client.is_user_authorized():
                logger.warning("Client not authorized, QR code login required")
                raise RuntimeError("Client requires authorization via QR code")

            logger.info("Telegram client initialized successfully")
        except Exception as e:
            logger.error(f"Failed to initialize Telegram client: {e}")
            raise

    async def shutdown(self):
        """Gracefully shutdown"""
        if self.client:
            await self.client.disconnect()
            logger.info("Telegram client disconnected")

    async def upload_file(self, chat_id: int, file_path: str, caption: str = "") -> dict:
        """Upload file to Telegram chat"""
        try:
            logger.info(f"Uploading {file_path} to chat {chat_id}")

            # Get the chat entity
            entity = await self.client.get_entity(chat_id)

            # Send file with caption
            message = await self.client.send_file(
                entity,
                file_path,
                caption=caption,
                force_document=True
            )

            logger.info(f"File uploaded, message_id: {message.id}")
            return {
                "status": "ok",
                "message_id": message.id,
                "chat_id": chat_id,
                "file_size": os.path.getsize(file_path)
            }
        except Exception as e:
            logger.error(f"Upload failed: {e}")
            raise

    async def download_file(self, chat_id: int, message_id: int, output_path: str = None) -> dict:
        """Download file from Telegram message"""
        try:
            logger.info(f"Downloading message {message_id} from chat {chat_id}")

            entity = await self.client.get_entity(chat_id)
            message = await self.client.get_messages(entity, ids=message_id)

            if not message.media:
                raise ValueError("Message has no media")

            # Download to temporary location if not specified
            if not output_path:
                output_path = self.upload_dir / f"download_{message_id}"

            path = await self.client.download_media(message, file=str(output_path))

            logger.info(f"File downloaded to {path}")
            return {
                "status": "ok",
                "path": str(path),
                "message_id": message_id,
                "chat_id": chat_id,
                "file_size": os.path.getsize(path)
            }
        except Exception as e:
            logger.error(f"Download failed: {e}")
            raise

    async def list_messages(self, chat_id: int, limit: int = 100) -> dict:
        """List recent messages in chat"""
        try:
            logger.info(f"Listing messages from chat {chat_id}")

            entity = await self.client.get_entity(chat_id)
            messages = []

            async for message in self.client.iter_messages(entity, limit=limit):
                messages.append({
                    "id": message.id,
                    "date": message.date.isoformat(),
                    "has_media": bool(message.media),
                    "text": message.text[:100] if message.text else None
                })

            return {
                "status": "ok",
                "chat_id": chat_id,
                "messages": messages,
                "count": len(messages)
            }
        except Exception as e:
            logger.error(f"List messages failed: {e}")
            raise


# Global daemon instance
daemon: Optional[TelegramStorageDaemon] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events"""
    global daemon

    # Startup
    tdata_path = os.getenv("TDATA_PATH")
    session_file = os.getenv("SESSION_FILE", "/tmp/telegram.session")
    api_type = os.getenv("API_TYPE", "android")

    daemon = TelegramStorageDaemon(tdata_path, session_file, api_type)
    await daemon.initialize()

    yield

    # Shutdown
    await daemon.shutdown()


# Create FastAPI app
app = FastAPI(
    title="Telegram Storage Daemon",
    version="1.0.0",
    lifespan=lifespan
)


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    if daemon and daemon.client and daemon.client.is_connected():
        return {"status": "healthy", "client": "connected"}
    return {"status": "unhealthy", "client": "disconnected"}


@app.post("/upload")
async def upload(
        chat_id: int,
        file: UploadFile = File(...),
        caption: str = ""
):
    """Upload file to Telegram chat"""
    if not daemon:
        raise HTTPException(status_code=500, detail="Daemon not initialized")

    temp_path = daemon.upload_dir / file.filename
    try:
        # Save uploaded file
        with open(temp_path, "wb") as f:
            content = await file.read()
            f.write(content)

        result = await daemon.upload_file(chat_id, str(temp_path), caption)
        return result
    finally:
        # Clean up temp file
        if temp_path.exists():
            temp_path.unlink()


@app.get("/download")
async def download(chat_id: int, message_id: int):
    """Download file from Telegram message"""
    if not daemon:
        raise HTTPException(status_code=500, detail="Daemon not initialized")

    result = await daemon.download_file(chat_id, message_id)

    # Return file if requested
    if os.path.exists(result["path"]):
        return FileResponse(result["path"], media_type="application/octet-stream")
    return result


@app.get("/messages")
async def list_messages(chat_id: int, limit: int = 100):
    """List recent messages in chat"""
    if not daemon:
        raise HTTPException(status_code=500, detail="Daemon not initialized")

    return await daemon.list_messages(chat_id, limit)


@app.get("/info")
async def get_info():
    """Get daemon info"""
    if not daemon or not daemon.client:
        raise HTTPException(status_code=500, detail="Daemon not initialized")

    me = await daemon.client.get_me()
    return {
        "user_id": me.id,
        "username": me.username,
        "first_name": me.first_name,
        "is_bot": me.bot,
        "api_type": daemon.api_type
    }


if __name__ == "__main__":
    port = int(os.getenv("PORT", 8000))
    host = os.getenv("HOST", "127.0.0.1")

    uvicorn.run(app, host=host, port=port)
