#!/usr/bin/env python3
# -*- coding: UTF-8 -*-

from __future__ import print_function
from __future__ import unicode_literals

import errno
import sys
from datetime import timedelta
from flask import Flask, Response, request
from io import BytesIO
from secret import *
from telegram_client_x import TelegramClientX
from telethon.tl.types import DocumentAttributeFilename

app = Flask(__name__)

path_home = './'  # os.path.abspath('.') #  os.path.dirname(os.path.realpath(__file__))
client = TelegramClientX(entity, api_id, api_hash, update_workers=None, spawn_read_thread=False,
                         timeout=timedelta(seconds=30))
# client = TelegramClient(entity, api_id, api_hash, update_workers=None, spawn_read_thread=True)
client.set_upload_threads_count(12)  # 24
client.set_download_threads_count(12)  # 8

entity_name = "tgcloud"
entity = None


@app.route('/download', methods=['GET'])
def download_route():
    path = request.args.get('path')
    assert path is not None
    f = BytesIO()
    assert download_block(path, f) == 0
    f.seek(0)
    return Response(f.read())


@app.route('/list', methods=['GET'])
def list_route():
    path = request.args.get('path') or ''
    f = BytesIO()
    list_files(path, f)
    f.seek(0)
    return Response(f.read(), mimetype='text/plain')


@app.route('/upload', methods=['POST', 'PUT'])
def upload_route():
    path = request.args.get('path')
    assert path is not None
    app.logger.info(f"Starting upload: {path}")
    result = upload_block(path, request.stream)
    if result == 0:
        return Response(status=200)
    elif result == errno.EEXIST:
        return Response("File already exists", 409)
    else:
        return Response("File upload failed", 500)


@app.route('/delete', methods=['POST', 'DELETE'])
def delete_route():
    path = request.args.get("path")
    assert path is not None
    assert delete_block(path) == 0
    return Response(status=200)


@app.route('/size', methods=['GET'])
def size_route():
    path = request.args.get('path') or ''
    size = size_files(path)
    return Response(str(size))


def download_block(uid, file_out):
    try_reconnect()
    messages = client.get_messages(entity, limit=1, search=uid)
    for i in range(len(messages)):
        msg = messages[i]
        if msg.message == uid:
            if client.download_media(msg, file_out):
                return 0
            else:
                return -1
    return -1


def delete_block(uid):
    try_reconnect()
    messages = client.get_messages(entity, limit=1, search=uid)
    for i in range(len(messages)):
        msg = messages[i]
        if msg.message == uid:
            client.delete_messages(entity, [msg])
    return 0


def upload_block(uid, file_in):
    try_reconnect()
    messages = client.get_messages(entity, limit=1, search=uid)
    if len(messages):
        app.logger.warning(f"File already exists: {uid}")
        # client.delete_messages(entity, messages)
        # app.logger.error(f"File already exists: {uid}")
        return errno.EEXIST
    result = client.send_file(entity,
                              file=file_in.read(),  # TODO remove .read()
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


def list_files(pattern, file_out):
    try_reconnect()
    messages = client.get_messages(entity, limit=100, search=pattern)
    last_id = 0
    while len(messages) != 0:
        last_id = messages[-1].id
        with_doc = filter(lambda m: hasattr(m.original_message, 'media') and m.document is not None and m.message != '',
                          messages)
        texts = map(lambda m: m.message, with_doc)
        for file in texts:
            file_out.write(bytes(file + "\n", "utf-8"))
        messages = client.get_messages(entity, limit=100, search=pattern, offset_id=last_id)
    return 0


def size_files(pattern):
    try_reconnect()
    messages = client.get_messages(entity, limit=100, search=pattern)
    total_size = 0
    last_id = 0
    while len(messages) != 0:
        last_id = messages[-1].id
        with_doc = filter(lambda m: m.document is not None and m.message != '', messages)
        sizes = map(lambda m: m.document.size, with_doc)
        total_size += sum(sizes)
        messages = client.get_messages(entity, limit=100, search=pattern, offset_id=last_id)
    return total_size


def try_reconnect():
    if not client.is_connected():
        client.connect()
        init_entity()


def init_entity():
    global entity
    if entity == '':
        entity = client.get_entity(client.get_me())
    else:
        entity = next(d for d in client.iter_dialogs() if not d.is_user and d.name == entity_name)
    app.logger.info(f"Writing to entity: {entity}")


def main(argv):
    global entity_name
    try:
        service = str(argv[1])
        if service == 'download':
            uid = str(argv[2])
            return download_block(uid, sys.stdout.buffer)
        elif service == 'upload':
            uid = str(argv[2])
            return upload_block(uid, sys.stdin.buffer)
        elif service == 'list':
            pattern = argv[2]
            return list_files(pattern, sys.stdout.buffer)
        elif service == 'delete':
            uid = argv[2]
            return delete_block(uid)
        elif service == 'size':
            pattern = argv[2]
            size = size_files(pattern)
            sys.stdout.write(str(size))
            return 0
        elif service == 'server':
            port = argv[2]
            entity_name = argv[3]
            app.run(debug=True, port=port, threaded=True)
        else:
            return -1
    finally:
        client.disconnect()


client.connect()
if not client.is_user_authorized():
    raise Exception("Telegram session not found - run from the project folder: python3 telegram_create_session.py")
init_entity()

if __name__ == '__main__':
    result = main(sys.argv[0:])
    if isinstance(result, int):
        sys.exit(result)
    elif result:
        sys.exit(0)
    else:
        sys.exit(-1)
