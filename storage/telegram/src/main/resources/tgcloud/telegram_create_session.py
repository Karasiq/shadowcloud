#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

from __future__ import print_function
from __future__ import unicode_literals

import asyncio
from secret import *
from telethon import TelegramClient

client = TelegramClient(entity, api_id, api_hash)


async def create_session():
    await client.connect()

    if not client.is_user_authorized():
        client.start()

    await client.disconnect()


loop = asyncio.get_event_loop()
result = loop.run_until_complete(create_session())
