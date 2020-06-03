#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

from __future__ import print_function
from __future__ import unicode_literals

import asyncio
# noinspection PyUnresolvedReferences
import errno
import sys
from io import BytesIO
from quart import Quart, request
from quart import Response
from secret import *
from telethon import TelegramClient
from telethon.tl.types import DocumentAttributeFilename

app = Quart(__name__)
app.config.from_object(__name__)

path_home = './'  # os.path.abspath('.') #  os.path.dirname(os.path.realpath(__file__))
client = TelegramClient(entity, api_id, api_hash, timeout=30)

entity_name = "tgcloud"
entity = None


@app.route('/download', methods=['GET'])
async def download_route():
    path = request.args.get('path')
    assert path is not None
    f = BytesIO()
    assert await download_block(path, f) == 0
    f.seek(0)
    return Response(f)


@app.route('/list', methods=['GET'])
async def list_route():
    path = request.args.get('path') or ''
    f = BytesIO()
    await list_files(path, f)
    f.seek(0)
    return Response(f, mimetype='text/plain')


@app.route('/upload', methods=['POST', 'PUT'])
async def upload_route():
    path = request.args.get('path')
    assert path is not None
    app.logger.info(f"Starting upload: {path}")
    result = await upload_block(path, await request.body, request.method == 'PUT')
    if result == 0:
        return Response("")
    elif result == errno.EEXIST:
        return Response("File already exists", 409)
    else:
        return Response("File upload failed", 500)


@app.route('/delete', methods=['POST', 'DELETE'])
async def delete_route():
    path = request.args.get("path")
    assert path is not None
    assert await delete_block(path) == 0
    return Response("")


@app.route('/size', methods=['GET'])
async def size_route():
    path = request.args.get('path') or ''
    size = await size_files(path)
    return Response(str(size))


async def download_block(uid, file_out):
    messages = await client.get_messages(entity, limit=1, search=uid)
    for i in range(len(messages)):
        msg = messages[i]
        if msg.message == uid:
            if await client.download_media(msg, file_out):
                return 0
            else:
                return -1
    return -1


async def delete_block(uid):
    messages = await client.get_messages(entity, limit=1, search=uid)
    for i in range(len(messages)):
        msg = messages[i]
        if msg.message == uid:
            await client.delete_messages(entity, [msg])
    return 0


async def upload_block(uid, file_in, overwrite=False):
    messages = await client.get_messages(entity, limit=1, search=uid)
    if len(messages):
        app.logger.warning(f"File already exists: {uid}")
        if overwrite:
            await client.delete_messages(entity, messages)
        else:
            return errno.EEXIST
    result = await client.send_file(entity,
                                    file=file_in,
                                    caption=f'{uid}',
                                    attributes=[DocumentAttributeFilename(f'{uid}')],
                                    allow_cache=False,
                                    part_size_kb=512,
                                    force_document=True)
    app.logger.info(f"Upload result: {result}")
    if result:
        return 0
    else:
        return -1


async def list_files(pattern, file_out):
    messages = await client.get_messages(entity, limit=100, search=pattern)
    last_id = 0
    while len(messages) != 0:
        last_id = messages[-1].id
        with_doc = filter(lambda m: m.document is not None and m.message != '', messages)
        texts = map(lambda m: m.message, with_doc)
        for file in texts:
            file_out.write(bytes(file + "\n", "utf-8"))
        messages = await client.get_messages(entity, limit=100, search=pattern, offset_id=last_id)
    return 0


async def size_files(pattern):
    messages = await client.get_messages(entity, limit=100, search=pattern)
    total_size = 0
    last_id = 0
    while len(messages) != 0:
        last_id = messages[-1].id
        with_doc = filter(lambda m: m.document is not None and m.message != '', messages)
        sizes = map(lambda m: m.document.size, with_doc)
        total_size += sum(sizes)
        messages = await client.get_messages(entity, limit=100, search=pattern, offset_id=last_id)
    return total_size


async def try_reconnect():
    if not client.is_connected():
        await client.connect()
        await init_entity()


async def init_entity():
    global entity
    if entity == '':
        entity = await client.get_entity(await client.get_me())
    else:
        entity = await (d async for d in client.iter_dialogs() if not d.is_user and d.name == entity_name).__anext__()
    app.logger.info(f"Writing to entity: {entity}")


async def main(argv):
    global entity_name
    await client.connect()
    if not await client.is_user_authorized():
        raise Exception("Telegram session not found - run from the project folder: python3 telegram_create_session.py")
    await init_entity()
    try:
        service = str(argv[1])
        if service == 'download':
            uid = str(argv[2])
            return await download_block(uid, sys.stdout.buffer)
        elif service == 'upload':
            uid = str(argv[2])
            return await upload_block(uid, sys.stdin.buffer, True)
        elif service == 'list':
            pattern = argv[2]
            return await list_files(pattern, sys.stdout.buffer)
        elif service == 'delete':
            uid = argv[2]
            return await delete_block(uid)
        elif service == 'size':
            pattern = argv[2]
            size = await size_files(pattern)
            sys.stdout.write(str(size))
            return 0
        elif service == 'server':
            port = argv[2] or 5000
            entity_name = argv[3]
            await app.run_task(port=port, use_reloader=False)
        else:
            return -1
    finally:
        await client.disconnect()


if __name__ == '__main__':
    loop = asyncio.get_event_loop()
    result = loop.run_until_complete(main(sys.argv[0:]))
    if isinstance(result, int):
        sys.exit(result)
    elif result:
        sys.exit(0)
    else:
        sys.exit(-1)
