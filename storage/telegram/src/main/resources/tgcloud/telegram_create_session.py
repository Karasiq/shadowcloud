#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

from __future__ import print_function
from __future__ import unicode_literals

from secret import *
from telegram_client_x import TelegramClientX

path_home = './'  # os.path.abspath('.')
client = TelegramClientX(entity, api_id, api_hash, update_workers=None, spawn_read_thread=True)
# client = TelegramClient(entity, api_id, api_hash, update_workers=None, spawn_read_thread=True)

client.connect()

if not client.is_user_authorized():
    client.start()

client.disconnect()
