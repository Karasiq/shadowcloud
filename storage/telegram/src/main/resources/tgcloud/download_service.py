#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

import asyncio
# noinspection PyUnresolvedReferences
import errno
import lz4.frame
import pytz
import secret
import sys
from datetime import datetime, timedelta
from io import BytesIO
from quart import Quart, request, Response
from telethon import TelegramClient
from telethon.errors.rpcbaseerrors import RPCError
from telethon.tl.types import DocumentAttributeFilename

app = Quart(__name__)
app.config.from_object(__name__)

client = TelegramClient(secret.entity, secret.api_id, secret.api_hash, timeout=60, request_retries=0, retry_delay=0,
                        flood_sleep_threshold=10, lang_code='ru', system_lang_code='ru')

entity_name = "tgcloud"
entity = None


@app.before_request
async def auto_reconnect():
    await try_reconnect()


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
    path = request.args.get('path')
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


@app.route('/delete', methods=['POST', 'DELETE', 'GET'])
async def delete_route():
    path = request.args.get("path")
    assert path is not None
    assert await delete_data(path) == 0
    return Response("")


@app.route('/size', methods=['GET'])
async def size_route():
    path = request.args.get('path')
    size = await size_files(path)
    return Response(str(size))


async def download_block(uid, file_out):
    messages = client.iter_messages(entity, search=uid)
    async for msg in messages:
        if msg.document and msg.message == uid:
            if await client.download_media(msg, file_out):
                return 0
            else:
                return -1
    return -1


async def delete_data(uid):
    messages = client.iter_messages(entity, search=uid)
    ids_to_delete = []
    async for m in messages:
        if starts_with(m.message, uid):
            ids_to_delete.append(m.id)

    if len(await client.delete_messages(entity, ids_to_delete)) > 0:
        max_id = min(ids_to_delete)
        await delete_outdated_meta(uid, max_id)
        return 0
    else:
        return errno.EEXIST


async def upload_block(uid, file_in, overwrite=False):
    messages = list(filter(lambda m: m.message == uid, await client.get_messages(entity, limit=100, search=uid)))

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


def starts_with(path: str, required_path: str) -> bool:
    return path.startswith(required_path + '/') or path == required_path


meta_lock = asyncio.Lock()
meta_lock_local = {}


def get_meta_lock(path) -> asyncio.Lock:
    if path in meta_lock_local:
        return meta_lock_local[path]
    else:
        lock = asyncio.Lock()
        meta_lock_local[path] = lock
        return lock


async def delete_outdated_meta(path, min_id):
    to_delete = []
    async with meta_lock:
        async for m in client.iter_messages(entity, search='$tgcloud_meta', min_id=min_id):
            if starts_with(f'$tgcloud_meta/{path}', m.message):
                app.logger.warning(f"Removing meta file: {m}")
                to_delete.append(m.id)
        return await client.delete_messages(entity, to_delete)


async def _get_files_meta_unsafe(path) -> list:
    class AsObject:
        def __init__(self, obj):
            self.__dict__ = obj

    import json

    meta_file_path = f'$tgcloud_meta/{path}'

    async def get_meta_file():
        async for m in client.iter_messages(entity, search=meta_file_path):
            if m.message == meta_file_path:
                return m
        return None

    meta_file = await get_meta_file()
    data = {
        'messages': [],
        'last_id': -1
    }
    if meta_file:
        msg = meta_file
        compressed_data = BytesIO()
        await client.download_media(msg, compressed_data)
        compressed_data.seek(0)
        raw_data = lz4.frame.decompress(compressed_data.read())
        data = json.loads(raw_data)
        messages = client.iter_messages(entity, search=path, min_id=data['last_id'] + 1)
    else:
        messages = client.iter_messages(entity, search=path)

    added = 0
    async for m in messages:
        if m.message and m.document and starts_with(m.message, path):
            data['messages'].append({
                'id': m.id,
                'path': m.message,
                'size': m.document.size
            })
            data['last_id'] = max(data['last_id'], m.id)
            added += 1

    now = pytz.utc.localize(datetime.utcnow())
    is_outdated = added > 0 and (meta_file is None or now > (meta_file.date + timedelta(minutes=10)))
    if is_outdated and len(data['messages']) > 10:
        try:
            raw_json = bytes(json.dumps(data), "utf-8")
            compressed_json = lz4.frame.compress(raw_json)
            await client.send_file(entity,
                                   file=compressed_json,
                                   caption=f'{meta_file_path}',
                                   attributes=[DocumentAttributeFilename(f'{meta_file_path}')],
                                   allow_cache=False,
                                   part_size_kb=512,
                                   force_document=True)
        except RPCError:
            pass
    messages_objs = list(map(lambda m: AsObject(m), data['messages']))
    return messages_objs


async def get_files_meta(path) -> list:
    lock = get_meta_lock(path)
    async with lock:
        return await _get_files_meta_unsafe(path)


async def list_files(path, file_out):
    messages = await get_files_meta(path)
    for m in messages:
        if starts_with(m.path, path):
            file_out.write(bytes(m.path + "\n", "utf-8"))
    return 0


async def size_files(path):
    messages = await get_files_meta(path)
    total_size = 0
    for m in messages:
        if starts_with(m.path, path):
            total_size += m.size
    return total_size


async def try_reconnect():
    if not client.is_connected():
        await client.connect()
        await init_entity()


async def init_entity():
    global entity
    if entity_name == '':
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
            return await delete_data(uid)
        elif service == 'size':
            pattern = argv[2]
            size = await size_files(pattern)
            sys.stdout.write(str(size))
            return 0
        elif service == 'server':
            port = argv[2] or 5000
            entity_name = argv[3]
            await init_entity()
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
