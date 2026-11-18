# LostFilm Desktop — полный план

Неофициальный десктопный клиент LostFilm.TV, функционально повторяющий
Android-приложение `ru.twicker.lostfilmtv` 0.1.50.

Документ описывает: что делает оригинал, на чём строим десктоп, архитектуру,
поэтапный план, критерии готовности и риски.

> Правовая рамка: это самостоятельная реализация по наблюдаемому поведению.
> Код, ресурсы и графика оригинала не копируются. Приложению нужна учётная
> запись пользователя; контент не распространяется. Перед публикацией —
> проверить условия использования сайта.

---

## 1. Что умеет оригинал (инвентаризация)

### Экраны

| Экран Android | Что делает |
|---|---|
| `MainActivity` (TV) | Leanback-обзор: строки «Новые сериалы», «Новые эпизоды», «Все сериалы», «Все фильмы», «Избранное», «История просмотра» |
| `mobile/MainActivity` | Телефонный вариант: вкладки и карточки |
| `SerieDetailsActivity` / `mobile/DetailActivity` | Карточка сериала/фильма: постер, описание, рейтинг, жанры, сезоны, эпизоды, «следить», «просмотрено» |
| `EpisodeDetailsActivity` | Эпизод: описание, фото, качества/раздачи, запуск |
| `SerieActivity` | Список сезонов/эпизодов сериала |
| `PlayVideoActivity` | Встроенный плеер (Leanback MediaPlayer), список видео |
| `SearchActivity` | Поиск по сайту, подсказки из локальной БД |
| `FilterActivity` | Фильтры: жанр, год, канал, тип |
| `CalendarActivity` | Календарь премьер |
| `SettingsActivity` | Хост/зеркало, воспроизведение, UI, каналы, «О программе» |
| `UpdateActivity` | Проверка и установка обновления APK |
| `AboutActivity`, `WebActivity` | О программе, встроенный браузер |
| `LoginActivity` (TV) / `mobile/LoginActivity` | E-mail, пароль, капча, выбор зеркала |

### Данные и синхронизация

- Room: `Series`, `Season`, `Episode`, `Movies`, `Video`, `Config`.
- WorkManager-воркеры: сериалы, сезоны, новые эпизоды, фильмы, видео,
  «featured» (будущие премьеры).
- Локальная БД — офлайн-каталог и история; поверх — догрузка с сайта.

### Сеть и авторизация

| Точка | Назначение |
|---|---|
| `GET https://lf.twicker.ru/mirror.php` | актуальное зеркало |
| `GET http://lf.twicker.ru/update.php?from=…` | версия обновления |
| `POST /ajaxik.users.php` | логин: `act=users&type=login&mail&pass&rem=1&captcha&need_captcha` |
| `POST /ajaxik.php` | `serial/getmarks`, `serial/follow`, `serial/markepisode`, `serial/markseason`, `common/search` |
| `GET /v_search.php?a=<episodeId>` | раздачи/качества эпизода |
| `GET /new/page_N/type_T`, `/new_seasons/page_N/type_T` | ленты новинок |
| `GET <series>/seasons`, `<series>/season_N/episode_M/` | сезоны и эпизоды |
| `GET <series>/video/N` | видео-блоки |
| `GET /simple_captcha.php` | капча |
| `GET <imageHost>/Images/...` | постеры |

- Сессия: cookie `lf_session`; `sessionId` — regex `[a-f0-9]{38,39}` рядом с
  `let userdata` в HTML. Всё хранится в SharedPreferences.
- User-Agent — Chrome/138, Referer — текущий хост.
- Зеркало определяется автоматически, можно задать вручную.
- Картинки: `static.lostfilm.top` либо хост из `src="//static..."`.

### Плеер

- Ссылка `Video.maxQualityLink` = `div.play-btn[data-src]`, качество —
  `data-quality`; страница `/video/N`.
- Воспроизведение прямым URI (mp4), без торрент-клиента.
- Позиция и длительность эпизода хранятся в БД.

### Настройки (ключи SharedPreferences)

`www_host`, `img_host`, `sessionId`, `SEECOOK`, `host_type`, `logged`,
`username`, `auto_watched`, `show_watched_dialog`, `auto_play_quality`,
`skip_details_card`, `main_hide_watched`, `auto_get_full_season`, `card_scale`,
`main_only_favorite`, `main_hide_finished`, `main_sort_serial`,
`main_sort_fav_serial`, `main_hide_not_started`.

### Android-специфика, которой нет на десктопе

| Android | Десктоп-аналог |
|---|---|
| LEANBACK_LAUNCHER, D-pad | режим «ТВ»: полноэкранный UI, управление клавишами/пультом |
| TV-каналы и Watch Next | строка «Продолжить просмотр» + системные медиа-уведомления |
| BOOT_COMPLETED | автозапуск в трее (опция) |
| POST_NOTIFICATIONS | системные уведомления о новых эпизодах |
| Голосовой поиск | поле поиска; опционально Windows Speech |
| Установка APK | автообновление MSI/EXE |
| Мобильный UI | адаптивное окно (узкая/широкая раскладка) |

---

## 2. Стек

**Kotlin/JVM + Compose Desktop** — тот же язык и те же библиотеки, что в
оригинале, поэтому логика переносится близко к поведению, а не переписывается
с нуля на другом языке.

| Слой | Выбор | Почему |
|---|---|---|
| Язык | Kotlin 2.1.x, JDK 21 | совпадает с оригиналом |
| UI | Compose Multiplatform Desktop 1.8.x | декларативный UI, фокус/клавиатура, темы |
| Сеть | OkHttp 4 + `java.net.CookieManager` | cookie `lf_session`, Referer, HTTP/2 |
| Парсинг | Jsoup | те же селекторы, что в оригинале |
| JSON | kotlinx.serialization + org.json для «рваных» ответов | сайт отдаёт неоднородный JSON |
| БД | SQLite (JDBC) + Exposed | локальный каталог, история, настройки |
| Картинки | Coil 3 Compose | диск/память, как Coil в оригинале |
| Плеер | VLCJ 4 (libVLC) | H.264/AAC, сеть, seek; VLC установлен |
| Планировщик | kotlinx.coroutines + собственный scheduler | аналог WorkManager |
| Логи | slf4j + logback | диагностика |
| Сборка | Gradle 8.x + jpackage `app-image` | портативная папка с `.exe`, JDK 21 |
| Тесты | JUnit5 + MockWebServer + golden HTML | парсеры и сеть |

Почему не Electron/Tauri: пришлось бы заново реализовывать всю HTML-логику и
модель данных на JS/Rust; Kotlin-вариант даёт прямую трассировку «экран → класс
→ запрос» и один язык для UI, сети и БД.

---

## 3. Архитектура

```
┌──────────────────────────── UI (Compose) ────────────────────────────┐
│ Home / Details / Episode / Player / Search / Filter / Calendar /     │
│ Settings / Login / About                                             │
└───────────────▲──────────────────────────────▲──────────────────────┘
                │ StateFlow<UiState>            │ events
┌───────────────┴───────────────┐  ┌───────────┴──────────────────────┐
│ ViewModel / Store             │  │ Навигация, фокус, горячие клавиши │
└───────────────▲───────────────┘  └──────────────────────────────────┘
                │
┌───────────────┴──────────────────────────────────────────────────────┐
│ Domain: use-cases (SyncCatalog, LoadSeries, ToggleWatched, Login…)   │
└───────────────▲──────────────────────────▲───────────────────────────┘
                │                          │
┌───────────────┴───────────────┐  ┌───────┴───────────────────────────┐
│ Data: LostFilmApi (OkHttp +   │  │ Repository: SQLite + кэш          │
│ Jsoup), MirrorResolver, Auth  │  │ (series, seasons, episodes, …)    │
└───────────────▲───────────────┘  └───────▲───────────────────────────┘
                │                          │
        ┌───────┴──────────┐      ┌────────┴─────────┐
        │ Сайт LostFilm.TV │      │ Локальная SQLite │
        └──────────────────┘      └──────────────────┘
```

Пакеты:

```
app.lostfilm.desktop
├── Main.kt                 — точка входа, окно, тема
├── core/                   — config, http, logging, utils
├── data/
│   ├── api/                — LostFilmApi, MirrorResolver, parsers/
│   ├── db/                 — schema, DAOs, migrations
│   └── repo/               — репозитории и синхронизация
├── domain/                 — модели и use-case
├── player/                 — VLCJ, прокси потока, resume
├── ui/
│   ├── theme/              — цвета, типографика, размеры
│   ├── components/         — карточки, ряды, шиммеры
│   ├── navigation/         — роутер, фокус
│   └── screens/            — home, details, episode, player, …
└── platform/               — трей, автозапуск, уведомления, обновления
```

Поток данных: UI → use-case → API/БД → StateFlow → UI. Сетевые ответы
нормализуются в доменные модели; HTML-парсеры изолированы и покрыты тестами
на сохранённых страницах.

---

## 4. Модель данных (SQLite)

| Таблица | Поля (основные) |
|---|---|
| `series` | id, title, title_orig, alias, link, date, rating, genres, channels, status, status_auto, status_season, description, country, plot, type, last_used, not_favorited |
| `season` | id, series_id, season, title, poster, release |
| `episode` | id, series_id, season, episode, titles ru/en, release_date, rating, posters, watched, hidden, watch_next, final_episode, link, description, photo, playback_position, playback_duration |
| `movie` | id, title, title_orig, alias, link, date, rating, genres, img, is_movie, watched, description, plot, last_used |
| `video` | series_id, title, desc, img, video_url, quality_list, max_quality_link |
| `config` | id, value (JSON) |

Индексы: `series(last_used)`, `episode(series_id, season, episode)`,
`episode(watched)`, FTS5 по `title/title_orig/searchable_title`.

---

## 5. Сетевой слой

1. **MirrorResolver** — `mirror.php` → хост; проверка доступности; ручной хост
   из настроек; fallback на `lostfilm.tv`.
2. **CookieJar** — `lf_session` + все Set-Cookie; персист в SQLite/JSON;
   подстановка cookie при запросах к `*.lostfilm.*`.
3. **LostFilmApi**:
   - `login(email, password, captcha)`, `getLoginForm()`, `getCaptcha()`;
   - `series(offset, sort, type)`, `movies(...)`, `filter(...)`;
   - `seriesInfo(link)`, `episodeInfo(link)`, `seasons(link)`;
   - `torrents(episodeId)`, `video(link, index)`;
   - `marks(seriesId)`, `setWatched`, `setFollow`, `search`.
4. **Парсеры** — Jsoup с селекторами оригинала; каждому парсеру — golden-тест.
5. **Ошибки** — 503 Cloudflare, истёкшая сессия, битая разметка: типизованные
   исключения и понятные сообщения в UI.
6. **Ограничения** — последовательные запросы с задержкой, кэш HTML на диск,
   User-Agent Chrome, Referer.

---

## 6. UI/UX

- Два режима одной раскладки: **TV** (крупные карточки, фокус, стрелки,
  Enter/Back) и **Desktop** (мышь, колесо, контекстное меню).
- Главный экран: горизонтальные ряды с постерами; TOP-строка — поиск,
  фильтры, календарь, настройки, профиль.
- Карточка: постер, название ru/en, рейтинг, жанры, описание, кнопки
  «Смотреть», «Следить», «Просмотрено», сезоны/эпизоды.
- Эпизод: кадр, описание, выбор качества, «Смотреть», отметка просмотра.
- Плеер: VLC-видео, панель управления, выбор качества, следующий эпизод,
  запоминание позиции, горячие клавиши (Space, ←/→, F, Esc).
- Тема: тёмная по умолчанию, акцент LostFilm; масштаб карточек из настроек.
- Локализация: русский; строки в ресурсах, чтобы позже добавить английский.

---

## 7. Плеер

- VLCJ `EmbeddedMediaPlayer` + `SwingPanel` в Compose.
- **Локальный прокси** (OkHttp, `127.0.0.1:random`): добавляет Cookie,
  Referer, User-Agent, поддерживает Range/seek. VLC играет
  `http://127.0.0.1:<port>/s/<token>` — так не утекают cookie в URL и работают
  сессионные ссылки.
- Автовыбор качества: `auto_play_quality` (`NONE`/`SD`/`HD`/`FHD`), иначе
  диалог.
- Resume: позиция пишется раз в 5 с и при паузе/выходе; при старте —
  предложение продолжить.
- «Следующий эпизод» по завершении, если включено.
- Fallback: открыть ссылку во внешнем плеере (VLC/mpv), если libVLC недоступен.

---

## 8. Синхронизация и офлайн

- При старте: быстрый показ БД → фоновая синхронизация.
- Очередь: сериалы → сезоны → эпизоды → видео; ограничение параллелизма.
- Инкрементально: обновляем только изменившиеся (по `last_used`/дате).
- Прогресс в статус-баре; отмена; повтор с backoff.
- Планировщик: раз в N часов проверять новые эпизоды → уведомление.
- Офлайн: каталог, описания, постеры из кэша; видео — только онлайн.

---

## 9. Авторизация

- Экран входа: зеркало (авто/вручную), e-mail, пароль, капча.
- Капча: `GET /simple_captcha.php?rand` → изображение; поле ввода.
- `POST /ajaxik.users.php`; разбор ответа (`session_expired`, ошибки).
- После успеха — `findSessionId` из HTML, сохранение cookie и `sessionId`.
- Выход: очистка cookie/сессии.
- Пароль не сохраняется (в оригинале тоже), только сессия.

---

## 10. Настройки

| Раздел | Пункты |
|---|---|
| Хост | авто/вручную, проверка, сброс |
| Воспроизведение | качество по умолчанию, автопереход, позиция, внешний плеер |
| Интерфейс | масштаб карточек, скрывать просмотренное, тема, режим ТВ |
| Каналы | вкл/выкл строк «Продолжить», уведомления |
| Система | автозапуск, трей, проверка обновлений, логи |
| О программе | версия, лицензии, ссылки |

Хранение: портативный режим — `data\settings.json` и `data\lostfilm.db` рядом
с `LostFilmDesktop.exe`; если папка недоступна для записи — fallback в
`%APPDATA%\LostFilmDesktop`. Режим определяется наличием `portable.marker`
рядом с exe или аргументом `--portable`.

---

## 11. Десктоп-интеграция

- Полноэкранный режим ТВ, скрытие курсора, управление стрелками.
- Трей: свернуть, «Продолжить просмотр», выход.
- Автозапуск (опция).
- Уведомления о новых эпизодах (Windows toast).
- Медиа-клавиши (Play/Pause/Next) через JNativeHook или System Media Transport
  Controls (опционально).
- Автообновление портативной сборки: JSON-манифест, SHA-256, скачивание нового
  ZIP, распаковка во временную папку и замена файлов при выходе (без установщика).
- Логи и «Открыть папку данных» в настройках.

---

## 12. Этапы

| Этап | Результат | Критерий готовности |
|---|---|---|
| **M0. Каркас** | Gradle-проект, окно Compose, тема, навигация-заглушка, логи | `gradlew run` открывает окно |
| **M1. Сеть и парсеры** | OkHttp+cookies, mirror, каталог, карточка, эпизод, видео, поиск | парсеры проходят golden-тесты на сохранённых страницах |
| **M2. БД и синхронизация** | SQLite-схема, репозитории, воркеры, прогресс | каталог сохраняется и показывается офлайн |
| **M3. Главный экран** | ряды, карточки, фокус, TV/desktop раскладка | навигация с клавиатуры и мыши |
| **M4. Детали и действия** | сериал, сезон, эпизод, «следить», «просмотрено» | действия меняют состояние на сайте и локально |
| **M5. Логин** | зеркало, капча, сессия, выход | вход реальным аккаунтом, сессия переживает перезапуск |
| **M6. Плеер** | VLCJ, прокси, качества, resume, следующий эпизод | видео играет, seek работает, позиция сохраняется |
| **M7. Поиск, фильтры, календарь** | экраны и запросы | результаты совпадают с сайтом |
| **M8. Настройки и интеграция** | все настройки, трей, автозапуск, уведомления, обновление | настройки применяются без перезапуска |
| **M9. Упаковка** | портативный `app-image` + ZIP, иконка, версия, README | запуск с флешки на чистой Windows без установки Java |

Порядок работ: M0 → M1 → M2 → M3 → M5 → M4 → M6 → M7 → M8 → M9.
Логин (M5) можно сдвинуть раньше M3, если нужен доступ к персональным лентам.

Оценка: M0–M2 — 1–2 недели, M3–M6 — 2–3 недели, M7–M9 — 1–2 недели при
одном разработчике и готовых селекторах.

---

## 13. Тестирование

- Unit: парсеры на golden HTML (каталог, карточка, эпизод, видео, поиск).
- Integration: MockWebServer — логин, cookie, редиректы, 503, истёкшая сессия.
- DB: миграции, вставка/обновление, FTS.
- UI: ручные сценарии TV/desktop, фокус, горячие клавиши.
- Player: локальный файл, прямой URL, seek, resume, смена качества.
- Приёмка: чек-лист функций из §1 — каждая строка закрыта.

---

## 14. Риски

| Риск | Митигация |
|---|---|
| Сайт меняет разметку | парсеры в одном слое, golden-тесты, быстрый фикс |
| Cloudflare/503 | пауза, смена зеркала, понятная ошибка, кэш |
| Сессионные ссылки на видео | локальный прокси с cookie/Referer |
| libVLC отсутствует | fallback на внешний плеер; инструкция по установке VLC |
| Ограничения сайта | уважать rate limit, не агрессивная синхронизация |
| Юридические ограничения | неофициальный статус, без копирования кода/ресурсов, свой аккаунт |

---

## 15. Что делаем прямо сейчас

1. Каркас Gradle + Compose Desktop (M0).
2. Сетевой слой: OkHttp + cookie jar + mirror.
3. Первые парсеры: каталог сериалов и карточка.
4. Экран-заглушка с реальными данными (список сериалов).
5. Далее — по этапам.

---

## 16. Портативная сборка (принято)

Сборка — `jpackage app-image`: без установщика и без системной Java.

| Что | Где |
|---|---|
| Портативная папка | `build\portable\LostFilmDesktop\` |
| Запуск | `build\portable\LostFilmDesktop\LostFilmDesktop.exe` |
| ZIP для переноса | `build\dist\LostFilmDesktop-portable-0.1.0.zip` |
| Размер | ~76 МБ (launcher + runtime + app) |

Команды:

```powershell
.\gradlew.bat createDistributable   # app-image
.\gradlew.bat portable              # копия в build\portable
.\gradlew.bat portableZip           # ZIP в build\dist
```

Правила портативности:

- данные и кэш — в `data\` рядом с exe (см. §10);
- никаких записей в реестр и `%APPDATA%` при наличии `portable.marker`;
- обновление — заменой папки/файлов, без MSI;
- папку можно копировать на флешку и запускать на другой Windows x64.

Один файл `.exe`: стандартный app-image — это папка с exe и runtime. Если нужен
ровно один файл, добавляется self-extracting обёртка (7-Zip SFX / IExpress), но
это ухудшает старт и повышает ложные срабатывания антивирусов. Базовая поставка
остаётся папкой + ZIP; один файл — по запросу.
