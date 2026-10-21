package io.freemytv;

/** Russian copy for the custom UI. Keys are the existing English copy, as in Spanish.java. */
final class Russian {
    private Russian() {}

    static String count(long n, String one, String few, String many) {
        long last = Math.abs(n % 100);
        String word = last >= 11 && last <= 14 ? many
                : last % 10 == 1 ? one : last % 10 >= 2 && last % 10 <= 4 ? few : many;
        return n + " " + word;
    }

    /** "1 приложение", "3 приложения", "14 приложений". */
    private static String apps(String n) {
        try {
            return count(Long.parseLong(n), "приложение", "приложения", "приложений");
        } catch (NumberFormatException e) {
            return n + " приложений";
        }
    }

    static String text(String english) {
        switch (english) {
            case "Live TV": return "Телеканалы";
            case "All Apps": return "Приложения";
            case "Settings": return "Настройки";
            // Not "Главный экран": that's the Home Screen row right above it in Free My TV's settings.
            case "Home Settings": return "Настройки";
            case "TV Settings": return "Настройки ТВ";
            case "HDMI Inputs": return "Входы HDMI";
            case "Inputs": return "Входы";
            case "Shortcuts": return "Ярлыки";
            case "Weather": return "Погода";
            case "No weather data right now.": return "Сейчас нет данных о погоде.";
            case "Sunrise ": return "Восход ";
            case "Sunset ": return "Закат ";
            case "Crypto": return "Криптовалюты";
            case "24h change": return "изменение за 24 ч";
            case "For You": return "Для вас";
            case "← → to move · OK to drop · Back to cancel": return "← → переместить · OK поставить · Назад отменить";
            case "Moving ": return "Перемещение: ";
            case ", position ": return ", место ";
            case "Couldn't open ": return "Не удалось открыть ";
            case " of ": return " из ";
            case ", tab ": return ", вкладка ";
            case " of 3": return " из 3";
            case "Dollar": return "Доллар";
            case "Series": return "Сериал";
            case "prices from ": return "котировки обновлены ";
            // The steps of a switch, in the Home Screen row (cut past ~30 letters: the row already says "home screen").
            case "Starting…": return "Запускаем…";
            case "Original": return "Заводской";
            case "Home Screen, ": return "Главный экран, ";
            case "Screensaver, preview": return "Заставка, предпросмотр";
            case "no ads, with the new screensaver": return "без рекламы, с новой заставкой";
            case "the TV's factory home screen": return "заводской главный экран телевизора";
            case ". In use": return ". Используется";
            case " of 2": return " из 2";
            case "Your TV's home screen and screensaver": return "Главный экран и заставка телевизора";
            case "Home Screen": return "Главный экран";
            case "Screensaver": return "Заставка";
            case "Preview": return "Предпросмотр";
            case "On the TV: tick “Always allow” and press OK.": return "На телевизоре отметьте «Всегда разрешать» и нажмите OK.";
            case "RECOMMENDATIONS": return "РЕКОМЕНДАЦИИ";
            case "No TMDB key: see the README (config.properties).": return "Нет ключа TMDB: см. README (config.properties).";
            case "Error: ": return "Ошибка: ";
            case "Fetching titles…": return "Загрузка рекомендаций…";
            case "This product uses the TMDB API but is not endorsed or certified by TMDB.": return "Продукт использует API TMDB, но не одобрен и не сертифицирован TMDB.";
            case "COMPARISON": return "СРАВНЕНИЕ";
            case "Home button": return "Кнопка «Домой»";
            case "Free memory": return "Свободная память";
            case "Home screen memory": return "Память главного экрана";
            case "NOTICES": return "УВЕДОМЛЕНИЯ";
            case "None yet.": return "Пока нет.";
            case "No ads, with the new screensaver": return "Без рекламы, с новой заставкой";
            case "The TV's factory home screen": return "Заводской главный экран телевизора";
            case "Switching takes a few seconds; the screen blinks while it measures.": return "Это займёт несколько секунд. Пока идут замеры, экран мигает.";
            case "now": return "сейчас";
            case "Measuring the original home screen…": return "Замеряем заводской экран…";
            case "Switching the home screen and screensaver…": return "Меняем экран и заставку…";
            case "Android didn't accept the new home screen": return "Android не принял новый главный экран";
            case "Turning off apps (": return "Отключаем приложения (";
            case "Measuring Free My TV…": return "Замеряем Free My TV…";
            case "Couldn't turn on Free My TV: ": return "Не удалось включить Free My TV: ";
            case "Turning the apps back on…": return "Включаем приложения…";
            case "Turning apps back on (": return "Включаем приложения (";
            case "Restoring the original home screen…": return "Возвращаем заводской экран…";
            case "Original mode: the factory home screen, screensaver and apps are back.": return "Заводской режим: восстановлены главный экран, заставка и приложения.";
            case "the original home screen": return "заводской главный экран";
            case "Original mode, but I couldn't turn back on: ": return "Заводской режим, но не удалось включить: ";
            case "Couldn't go back to the original: ": return "Не удалось восстановить заводской режим: ";
            case "Free My TV's home screen crashed 3 times in a row; I switched back to the original one.": return "Главный экран Free My TV дал сбой 3 раза подряд, поэтому вернулся заводской.";
            case "the home screen": return "главный экран";
            case "the screensaver": return "заставка";
            case "Couldn't check Free My TV: ": return "Не удалось проверить Free My TV: ";
            case "The TV update turned back on ": return "После обновления телевизора снова включены: ";
            case "Turned off what was missing: ": return "Дополнительно отключены: ";
            case "The TV was updated; Free My TV is still on.": return "Телевизор обновлён; Free My TV по-прежнему включён.";
            case "Connecting…": return "Подключаемся…";
            // SettingsActivity looks for "отладку по USB" in it, to show what to tick on the TV.
            case "Accept “Allow USB debugging?” on the TV (tick “Always allow”).": return "Разрешите отладку по USB на телевизоре (отметьте «Всегда разрешать»).";
            case "Language": return "Язык";
            case "Temperature": return "Температура";
            case "Theme": return "Тема";
            case "Automatic": return "Автоматически";
            case "Light": return "Светлая";
            case "Dark": return "Тёмная";
            case "Weather widget": return "Виджет погоды";
            // What TalkBack reads on the drawer's weather card (#47)
            case ". High ": return ". Максимум ";
            case ", low ": return ", минимум ";
            case " Next hours: ": return " Ближайшие часы: ";
            case " Sunrise ": return " Восход ";
            case " Sunset ": return " Закат ";
            case "City": return "Город";
            case "Searching…": return "Поиск…";
            case "Type the city's name": return "Введите название города";
            case "Search": return "Найти";
            case "Cancel": return "Отмена";
            case "No city with that name.": return "Город с таким названием не найден.";
            case "Couldn't search now: ": return "Не удалось выполнить поиск: ";
            case "Quotes widget": return "Виджет котировок";
            case "On": return "Вкл.";
            case "Off": return "Выкл.";
            case "Shelf": return "Избранные приложения";
            case "on the shelf, place ": return "в избранном, место ";
            case "not on the shelf": return "не в избранном";
            case "Remote Buttons": return "Кнопки пульта";
            case "Netflix button": return "Кнопка Netflix";
            case "YouTube button": return "Кнопка YouTube";
            case "Prime Video button": return "Кнопка Prime Video";
            case "TCL Channel button": return "Кнопка TCL Channel";
            case "Nothing": return "Не назначено";   // what a remote button opens
            case "Not installed": return "Не установлено";
            case ", selected": return ", выбрано";
            case "OK or the arrows change the value.": return "Нажмите OK или стрелки, чтобы изменить значение.";
            case "OK picks, Back cancels.": return "OK выбирает, «Назад» отменяет.";
            case "Disney and Globoplay: the TV itself controls those buttons.": return "Кнопками Disney и Globoplay управляет сам телевизор.";
            case "YouTube, Prime Video and Google Play: the TV itself controls those buttons.": return "Кнопками YouTube, Prime Video и Google Play управляет телевизор.";
            case "These are the TCL remote's buttons: on this TV, the buttons work as they always did.": return "Это кнопки пульта TCL: на этом телевизоре они работают как прежде.";
            case "Explore": return "Обзор";
            case "Movies": return "Фильмы";
            case "Shows": return "Сериалы";
            case "Clear": return "Ясно";
            case "Mostly Sunny": return "Малооблачно";
            case "Mostly Clear": return "Малооблачно";
            case "Partly Cloudy": return "Переменная облачность";
            case "Cloudy": return "Облачно";
            case "Fog": return "Туман";
            case "Drizzle": return "Морось";
            case "Heavy Rain": return "Сильный дождь";
            case "Snow": return "Снег";
            case "Thunderstorms": return "Гроза";
            case "Rain": return "Дождь";
            case "On · ": return "В избранном · ";
            case "OK adds or removes · ← → change the order": return "OK добавляет или убирает · ← → меняют порядок";
            case "Couldn't open TV Settings": return "Не удалось открыть настройки телевизора";
            case "THIS MORNING": return "СЕГОДНЯ УТРОМ";
            case "THIS AFTERNOON": return "СЕГОДНЯ ДНЁМ";
            case "TONIGHT": return "СЕГОДНЯ ВЕЧЕРОМ";
            case "Action & Adventure": return "Боевик и приключения";
            case "Sci-Fi & Fantasy": return "Фантастика и фэнтези";
            case "War & Politics": return "Война и политика";
            case "Kids": return "Для детей";
            case "News": return "Новости";
            case "Soap": return "Мыльная опера";
            case "Talk": return "Ток-шоу";
            case "": return "";
            case "Free My TV's home screen ran into a problem.\nGoing back to the TV's original home screen…": return "Главный экран Free My TV дал сбой.\nВозвращаем заводской главный экран телевизора…";
            case "debugging is off on the TV (port 5555 closed)": return "отладка на телевизоре выключена (порт 5555 закрыт)";
            case "debugging permission wasn't accepted on the TV": return "на телевизоре не разрешена отладка";
            case "the TV's ADB didn't respond": return "ADB телевизора не ответил";
            case "the TV asked for TLS (wireless debugging); use USB debugging": return "телевизор запросил TLS (беспроводная отладка); используйте отладку по USB";
            case "unexpected reply from ADB": return "неожиданный ответ ADB";
            case "the TV rejected the app's key": return "телевизор отклонил ключ приложения";
            case "ADB refused the command": return "ADB отклонил команду";
            case "invalid ADB message": return "некорректное сообщение ADB";
            case "ADB message too large": return "слишком большое сообщение ADB";
            case "the TV's first setup saved the Wi-Fi, and turning it off would erase those networks. "
                    + "In Settings → Network & Internet, forget the network and connect again (networks out of range don't "
                    + "show there: Claude Code can remove them from your computer), then try once more.":
                // Up to five lines in Free My TV's settings, after "Не удалось включить Free My TV: ".
                return "приложение первоначальной настройки сохранило Wi-Fi, и если его отключить, эти сети удалятся. "
                        + "Откройте Настройки → Сеть и Интернет, забудьте сеть и подключитесь заново (сетей вне зоны "
                        + "действия там не видно, их удалит с компьютера Claude Code), затем попробуйте снова.";
            // Why the picks didn't come (Recs), in Home Settings after "Ошибка: ".
            case "no TMDB key": return "нет ключа TMDB";
            case "TMDB rejected the key": return "TMDB не принял ключ";
            case "none of the TV's streaming services are on TMDB": return "в TMDB нет ни одного сервиса из установленных на ТВ";
            case "TMDB returned no recommendations": return "TMDB не прислал рекомендаций";
            case "couldn't save the image": return "не удалось сохранить картинку";
            case "unreadable image": return "не удалось прочитать картинку";
            default:
                // Notices are saved in the language in use when they were generated.
                String[] prefixes = {"Couldn't check Free My TV: ", "Couldn't turn on Free My TV: ",
                        "Couldn't go back to the original: ", "Original mode, but I couldn't turn back on: "};
                for (String p : prefixes) if (english.startsWith(p)) return text(p) + text(english.substring(p.length()));
                if (english.startsWith("TMDB returned HTTP ")) return "TMDB ответил ошибкой HTTP " + english.substring(19);
                String shelfPrefix = "OK adds or removes (up to ";
                String shelfSuffix = "). ← → change the order.";
                if (english.startsWith(shelfPrefix) && english.endsWith(shelfSuffix)) {
                    String n = english.substring(shelfPrefix.length(), english.length() - shelfSuffix.length());
                    return "OK добавляет или убирает (до " + n + "). ← → меняют порядок.";
                }
                String prefix = "Free My TV is on: new home screen and screensaver, ";
                if (english.startsWith(prefix) && english.endsWith(" apps turned off.")) {
                    String n = english.substring(prefix.length(), english.length() - " apps turned off.".length());
                    return "Free My TV включён: новый главный экран и заставка, отключено " + apps(n) + ".";
                }
                if (english.startsWith("On · ") && english.endsWith(" titles from the TV's apps, 3 new ones every hour.")) {
                    String n = english.substring(5, english.length() - " titles from the TV's apps, 3 new ones every hour.".length());
                    try {
                        return "Вкл. · " + count(Long.parseLong(n), "рекомендация", "рекомендации", "рекомендаций")
                                + " из приложений ТВ, 3 новые каждый час.";
                    } catch (NumberFormatException e) {
                        return english;
                    }
                }
                if (english.endsWith(" min ago")) return english.substring(0, english.length() - 8) + " мин назад";
                if (english.endsWith(" h ago")) return english.substring(0, english.length() - 6) + " ч назад";
                if (english.endsWith(" days ago")) {
                    try { return count(Long.parseLong(english.substring(0, english.length() - 9)), "день", "дня", "дней") + " назад"; }
                    catch (NumberFormatException ignored) { return english; }
                }
                return english;
        }
    }
}
