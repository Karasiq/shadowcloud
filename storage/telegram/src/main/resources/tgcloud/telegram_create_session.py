#!/usr/bin/env python3
"""
Interactive Telegram session creator using OpenTele TDesktop emulation.
Uses official Telegram Desktop API credentials (no custom app_id/app_hash needed).
"""
import asyncio
import sys
import os
from typing import Optional

from telethon import TelegramClient
from telethon.sessions import StringSession
from telethon.types import User
from telethon.errors import (
    SessionPasswordNeededError,
    PhoneCodeInvalidError,
    PhoneCodeExpiredError,
    PasswordHashInvalidError,
    FloodWaitError
)

try:
    from opentele.td import TDesktop
    from opentele.tl import TelegramClient as OpenTeleClient
    from opentele.api import API, UseCurrentSession

    OPENTELE_AVAILABLE = True
except ImportError:
    OPENTELE_AVAILABLE = False

# Protocol tokens
PROMPT_PHONE = "PROMPT:PHONE"
PROMPT_CODE = "PROMPT:CODE"
PROMPT_PASSWORD = "PROMPT:PASSWORD"
INFO = "INFO:"
ERROR = "ERROR:"
SUCCESS = "SUCCESS:"
SESSION = "SESSION:"
REQUIRES_OPENTELE = "REQUIRES:OPENTELE"


def write_line(msg: str) -> None:
    """Write line and flush immediately."""
    sys.stdout.write(msg + "\n")
    sys.stdout.flush()


def read_line() -> str:
    """Read and strip line from stdin."""
    return sys.stdin.readline().strip()


async def create_tdesktop_client(
        existing_session: Optional[str] = None
) -> TelegramClient:
    """
    Create TelegramClient with official Telegram Desktop emulation.
    Uses OpenTele's built-in TDesktop API credentials.

    Args:
        existing_session: Optional existing session string to reuse

    Returns:
        TelegramClient with TDesktop API and StringSession
    """
    if not OPENTELE_AVAILABLE:
        write_line(f"{ERROR}OpenTele not installed")
        write_line(f"{INFO}Install with: pip install opentele")
        write_line(REQUIRES_OPENTELE)
        sys.exit(1)

    write_line(f"{INFO}Initializing official Telegram Desktop emulation...")

    # Create TDesktop instance with official API
    tdesk = TDesktop()

    # Use existing session if provided, otherwise create new
    session = StringSession(existing_session) if existing_session else StringSession()

    # Convert to Telethon client with official TDesktop API credentials
    # API.TelegramDesktop provides the official desktop client API layer
    client = await tdesk.ToTelethon(
        session=session,
        flag=UseCurrentSession,
        api=API.TelegramDesktop
    )

    write_line(f"{INFO}Using official Telegram Desktop API (ID: {client.api_id})")

    return client


async def interactive_login(
        existing_session: Optional[str] = None
) -> str:
    """
    Perform interactive Telegram login or verify existing session.
    Uses official Telegram Desktop API credentials from OpenTele.

    Args:
        existing_session: Optional existing session string to reuse

    Returns:
        Session string (new or verified existing)
    """
    # Create client with official TDesktop credentials
    client = await create_tdesktop_client(existing_session)

    try:
        await client.connect()

        # Check if already authorized (existing session)
        if await client.is_user_authorized():
            me = await client.get_me()
            write_line(f"{INFO}Already authorized as {me.first_name} (@{me.username or 'N/A'})")
            session_str = client.session.save()
            return session_str

        # Need to login - request phone number
        write_line(PROMPT_PHONE)
        phone = read_line()

        if not phone:
            raise ValueError("Empty phone number")

        write_line(f"{INFO}Requesting login code for {phone} via Telegram Desktop")

        # Send code request using official TDesktop client
        sent_code = await client.send_code_request(phone)
        write_line(f"{INFO}Verification code sent to {phone}")

        # Request OTP code
        write_line(PROMPT_CODE)
        code = read_line()

        if not code:
            raise ValueError("Empty verification code")

        try:
            # Attempt sign in with code
            write_line(f"{INFO}Signing in with verification code...")
            user = await client.sign_in(phone, code)

            if isinstance(user, User):
                write_line(f"{INFO}Signed in as {user.first_name} (@{user.username or 'N/A'})")
                write_line(f"{INFO}Device: Official Telegram Desktop")
                session_str = client.session.save()
                return session_str

        except SessionPasswordNeededError:
            # 2FA password required
            write_line(f"{INFO}Two-factor authentication enabled")
            write_line(PROMPT_PASSWORD)
            password = read_line()

            if not password:
                raise ValueError("Empty 2FA password")

            write_line(f"{INFO}Verifying 2FA password...")
            user = await client.sign_in(password=password)
            write_line(f"{INFO}Signed in as {user.first_name} (@{user.username or 'N/A'})")
            write_line(f"{INFO}Device: Official Telegram Desktop")
            session_str = client.session.save()
            return session_str

        except PhoneCodeInvalidError:
            raise ValueError("Invalid verification code")
        except PhoneCodeExpiredError:
            raise ValueError("Verification code expired, please try again")
        except FloodWaitError as e:
            raise ValueError(f"Too many attempts, wait {e.seconds} seconds and try again")

    except PasswordHashInvalidError:
        raise ValueError("Invalid 2FA password")
    finally:
        await client.disconnect()


async def main() -> None:
    """Main entry point."""
    # Parse optional existing session argument
    existing_session = None

    if len(sys.argv) > 1:
        arg = sys.argv[1]
        if arg.startswith("session:"):
            existing_session = arg[8:]  # Remove "session:" prefix
        elif not arg.startswith("-"):
            existing_session = arg

    # Also check environment variable
    if not existing_session:
        existing_session = os.getenv("TG_EXISTING_SESSION", "")
        existing_session = existing_session if existing_session else None

    if existing_session:
        write_line(f"{INFO}Attempting to use existing session (length: {len(existing_session)})")
    else:
        write_line(f"{INFO}Creating new session with official Telegram Desktop API")

    try:
        session_str = await interactive_login(existing_session)

        write_line(f"{SUCCESS}Login successful using official Telegram Desktop")
        write_line(f"{SESSION}{session_str}")
        sys.exit(0)

    except Exception as ex:
        write_line(f"{ERROR}{str(ex)}")
        import traceback
        write_line(f"{INFO}Traceback: {traceback.format_exc()}")
        sys.exit(1)


if __name__ == "__main__":
    # Ensure OpenTele is available
    if not OPENTELE_AVAILABLE:
        print("ERROR: OpenTele is required for this script")
        print("Install with: pip install opentele")
        sys.exit(1)

    # No .session files created - StringSession is in-memory only
    asyncio.run(main())
