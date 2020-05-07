package com.karasiq.shadowcloud.webapp.locales.impl

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.locales.AppLocale

private[locales] object Russian extends AppLocale {
  val languageCode = "ru"

  val name = "Имя"
  val path = "Путь"
  val value = "Значение"
  val config = "Настройки"
  val size = "Размер"
  val empty = "Пусто"
  val show = "Показать"
  val edit = "Редактировать"
  val submit = "Отправить"
  val cancel = "Отмена"
  val close = "Закрыть"
  val unknown = "Неизвестно"
  val total = "Всего"
  def entries(value: Long): String = value + " записей"

  val file = "Файл"
  val folder = "Папка"

  val delete = "Удалить"
  val move = "Переместить"
  val copy = "Копировать"
  val rename = "Переименовать"

  val rootPath = "(Корень)"
  val emptyPath = "(Пусто)"
  val createFolder = "Создать папку"
  val deleteFolder = "Удалить папку"

  val image = "Изображение"
  val audio = "Аудио"
  val video = "Видео"

  val uploadFiles = "Загрузить"
  val downloadFile = "Скачать"
  val deleteFile = "Удалить"
  val repairFile = "Реплицировать"
  val playFile = "Просмотр"
  val viewTextFile = "Открыть текст"
  val inspectFile = "Подробности"
  val pasteText = "Вставить текст"
  val changeView = "Изменить отображение"

  val fileId = "ID файла"
  val createdDate = "Создан"
  val modifiedDate = "Изменён"
  val revision = "Ревизия"

  val preview = "Предпросмотр"
  val metadata = "Метаданные"
  val content = "Содержание"

  val availability = "Доступность"
  val revisions = "Ревизии"

  val foldersView = "Папки"
  val regionsView = "Регионы"
  val logs = "Журнал"

  val storage = "Хранилище"
  val storages = "Хранилища"
  val region = "Регион"
  val regions = "Регионы"
  val indexScope = "Контекст"
  val currentScope = "Текущий"
  val historyScope = "История"
  val indexSnapshotDate = "Дата снимка"

  val collectGarbage = "Собрать мусор"
  val compactIndex = "Сжать индекс"
  val repairRegion = "Репликация"
  val synchronize = "Синхронизация"

  val writableSpace = "Доступно"
  val totalSpace = "Всего"
  val freeSpace = "Свободно"
  val usedSpace = "Занято"

  val regionId = "ID региона"
  val regionIdHint = "Подсказка: рекомендуется формат [a-z0-9_-]"
  val uniqueRegionId = "Уникальный ID"
  val storageId = "ID хранилища"
  val storageType = "Тип хранилища"
  val createRegion = "Создать регион"
  val createStorage = "Создать хранилище"
  val registerStorage = "Зарегистрировать хранилище"
  val registerRegion = "Зарегистрировать регион"
  val unregisterStorage = "Разрегистрировать хранилище"
  val unregisterRegion = "Разрегистрировать регион"
  val suspend = "Остановить"
  val resume = "Запустить"
  val export = "Экспорт"
  val `import` = "Импорт"

  val keys = "Ключи"
  val keyId = "ID ключа"
  val keyEncAlgorithm = "Алгоритм шифрования"
  val keySignAlgorithm = "Алгоритм подписи"
  val keyPermissions = "Разрешения ключа"
  val keyForEncryption = "Шифрование"
  val keyForDecryption = "Дешифрование"
  val generateKey = "Сгенерировать ключ"
  val exportKey = "Экспорт ключа"
  val importKey = "Импорт ключа"

  val metadataParser = "Парсер"
  val metadataDisposition = "Назначение"
  val metadataType = "Тип"
  val metadataFormat = "Формат"

  val metadataText = "Текст"
  val metadataTable = "Таблица"
  val metadataFileList = "Список файлов"
  val metadataThumbnail = "Миниатюра"
  val metadataImageData = "Данные изображения"
  val metadataEmbeddedResources = "Встроенные ресурсы"

  def deleteFolderConfirmation(path: Path): String = {
    s"Вы уверены что хотите удалить папку $path?"
  }
}
