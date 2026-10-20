package io.freemytv;

/** Russian copy for text drawn directly by the custom UI. Source keys are the existing English copy. */
final class Russian {
    private Russian() {}

    static String text(String english) {
        switch (english) {
            case "Live TV": return "Эфирное ТВ";
            case "All Apps": return "Все приложения";
            case "Settings": return "Настройки";
            case "Home Settings": return "Настройки главного экрана";
            case "TV Settings": return "Настройки ТВ";
            case "HDMI Inputs": return "Входы HDMI";
            case "Inputs": return "Входы";
            case "Shortcuts": return "Ярлыки";
            case "Weather": return "Погода";
            case "No weather data right now.": return "Сейчас нет данных о погоде.";
            case "Sunrise ": return "Восход ";
            case "Sunset ": return "Закат ";
            case "Crypto": return "Крипта";
            case "24h change": return "изменение за 24 ч";
            case "For You": return "Для вас";
            case "Couldn't open ": return "Не удалось открыть ";
            case " of ": return " из ";
            case ", tab ": return ", вкладка ";
            case " of 3": return " из 3";
            case "Dollar": return "Доллар";
            case "Series": return "Сериал";
            case "prices from ": return "цены: ";
            case "Starting…": return "Запуск…";
            case "Original": return "Заводской";
            case "Home Screen, ": return "Главный экран, ";
            case "Screensaver, preview": return "Заставка, предпросмотр";
            case "no ads, with the new screensaver": return "без рекламы, с новой заставкой";
            case "the TV's factory home screen": return "заводской главный экран ТВ";
            case ". In use": return ". Используется";
            case " of 2": return " из 2";
            case "Your TV's home screen and screensaver": return "Главный экран и заставка ТВ";
            case "Home Screen": return "Главный экран";
            case "Screensaver": return "Заставка";
            case "Preview": return "Посмотреть";
            case "On the TV: tick “Always allow” and press OK.": return "На ТВ отметьте «Всегда разрешать» и нажмите OK.";
            case "RECOMMENDATIONS": return "РЕКОМЕНДАЦИИ";
            case "No TMDB key: see the README (config.properties).": return "Нет ключа TMDB: см. README (config.properties).";
            case "Error: ": return "Ошибка: ";
            case "Fetching titles…": return "Загрузка подборки…";
            case "This product uses the TMDB API but is not endorsed or certified by TMDB.": return "Продукт использует API TMDB, но не одобрен и не сертифицирован TMDB.";
            case "COMPARISON": return "СРАВНЕНИЕ";
            case "Home button": return "Кнопка «Домой»";
            case "Free memory": return "Свободная память";
            case "Home screen memory": return "Память главного экрана";
            case "NOTICES": return "УВЕДОМЛЕНИЯ";
            case "None yet.": return "Пока нет.";
            case "No ads, with the new screensaver": return "Без рекламы, с новой заставкой";
            case "The TV's factory home screen": return "Заводской главный экран ТВ";
            case "Switching takes a few seconds; the screen blinks while it measures.": return "Переключение занимает несколько секунд; во время замеров экран мигает.";
            case "now": return "сейчас";
            case "Measuring the original home screen…": return "Замер заводского главного экрана…";
            case "Switching the home screen and screensaver…": return "Переключение главного экрана и заставки…";
            case "Android didn't accept the new home screen": return "Android не принял новый главный экран";
            case "Turning off apps (": return "Отключение приложений (";
            case "Measuring Free My TV…": return "Замер Free My TV…";
            case "Couldn't turn on Free My TV: ": return "Не удалось включить Free My TV: ";
            case "Turning the apps back on…": return "Включаю приложения обратно…";
            case "Turning apps back on (": return "Включение приложений (";
            case "Restoring the original home screen…": return "Возврат заводского главного экрана…";
            case "Original mode: the factory home screen, screensaver and apps are back.": return "Заводской режим: главный экран, заставка и приложения возвращены.";
            case "the original home screen": return "заводской главный экран";
            case "Original mode, but I couldn't turn back on: ": return "Заводской режим, но не удалось включить обратно: ";
            case "Couldn't go back to the original: ": return "Не удалось вернуть заводской режим: ";
            case "Free My TV's home screen crashed 3 times in a row; I switched back to the original one.": return "Главный экран Free My TV упал 3 раза подряд; вернул заводской.";
            case "the home screen": return "главный экран";
            case "the screensaver": return "заставку";
            case "Couldn't check Free My TV: ": return "Не удалось проверить Free My TV: ";
            case "The TV update turned back on ": return "Обновление ТВ снова включило ";
            case "Turned off what was missing: ": return "Отключил недостающее: ";
            case "The TV was updated; Free My TV is still on.": return "ТВ обновился; Free My TV по-прежнему включён.";
            case "Connecting…": return "Подключение…";
            case "Accept “Allow USB debugging?” on the TV (tick “Always allow”).": return "Подтвердите на ТВ «Разрешить отладку по USB?» (отметьте «Всегда разрешать»).";
            case "Free My TV's home screen ran into a problem.\nGoing back to the TV's original home screen…": return "На главном экране Free My TV произошла ошибка.\nВозвращаю заводской главный экран…";
            // Home Settings, Shelf and Remote Buttons
            case "Language": return "Язык";
            case "Temperature": return "Температура";
            case "Theme": return "Тема";
            case "Automatic": return "Автоматически";
            case "Light": return "Светлая";
            case "Dark": return "Тёмная";
            case "Weather widget": return "Виджет погоды";
            case "Quotes widget": return "Виджет котировок";
            case "On": return "Вкл.";
            case "Off": return "Выкл.";
            case "Shelf": return "Полка";
            case "on the shelf, place ": return "на полке, место ";
            case "not on the shelf": return "не на полке";
            case "Remote Buttons": return "Кнопки пульта";
            case "Netflix button": return "Кнопка Netflix";
            case "YouTube button": return "Кнопка YouTube";
            case "Prime Video button": return "Кнопка Prime Video";
            case "TCL Channel button": return "Кнопка TCL Channel";
            case "Nothing": return "Ничего";
            case "Not installed": return "Не установлено";
            case ", selected": return ", выбрано";
            case "OK or the arrows change the value.": return "OK или стрелки меняют значение.";
            case "OK picks, Back cancels.": return "OK выбирает, «Назад» отменяет.";
            case "Disney and Globoplay: the TV itself controls those buttons.": return "Disney и Globoplay: этими кнопками управляет сам ТВ.";
            case "These are the TCL remote's buttons: on this TV, the buttons work as they always did.": return "Это кнопки пульта TCL: на этом ТВ они работают как раньше.";
            default:
                if (english.startsWith("Free My TV is on: new home screen and screensaver, ")) {
                    return "Free My TV включён: новый главный экран и заставка, "
                            + english.substring("Free My TV is on: new home screen and screensaver, ".length()).replace(" apps turned off.", " приложений отключено.");
                }
                if (english.startsWith("On · ")) {
                    return "Вкл. · " + english.substring(5).replace(" titles from the TV's apps, 3 new ones every hour.",
                            " фильмов и сериалов из приложений ТВ, 3 новых каждый час.");
                }
                if (english.endsWith(" min ago")) return english.substring(0, english.length() - 8) + " мин назад";
                if (english.endsWith(" h ago")) return english.substring(0, english.length() - 6) + " ч назад";
                if (english.endsWith(" days ago")) return english.substring(0, english.length() - 9) + " дн. назад";
                return english;
        }
    }
}
