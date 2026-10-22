"""Regenerate Education's Android translations and shared web text from the English resource keys."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
english = ET.parse(ROOT / 'app/res/values/education.xml').getroot()
keys = [e.attrib['name'] for e in english]
translations = {
'pt': '''No modo infantil, observa qual aplicativo está aberto, conta o tempo com a tela ligada e retorna a Education ao terminar o tempo ou abrir outro aplicativo. Não lê o conteúdo da tela.
Um pouco de TV. Um pouco de aprendizado. Um espaço para crianças.
Modo dos pais
Modo adulto
3 horas por dia. Resolva 5 questões de matemática para ganhar mais 30 minutos.
Olá, pequeno explorador!
Escolha o que assistir ou faça uma pequena aventura de aprendizado.
123 · Vamos praticar
Use as setas para escolher · OK para abrir · O PIN protege o modo adulto
Restam %1$d h %2$02d min para assistir hoje
Peça a um adulto para ativar o serviço de acessibilidade Education. Os vídeos ficam bloqueados até ele funcionar.
Peça a um adulto para configurar este aplicativo no Modo dos pais.
Não foi possível ler os ajustes da família. Peça a um adulto para reparar o aplicativo.
Crie um PIN de 4 dígitos. Mantenha-o em segredo.
Digite o mesmo PIN novamente.
Digite seu PIN de 4 dígitos para gerenciar Education ou voltar ao modo adulto.
OK
Digite os 4 dígitos e escolha OK.
Os PINs não coincidem. Comece novamente.
Verificando…
PIN não aceito. Após 5 tentativas, aguarde 30 segundos.
Ajustes da família. Sair do modo infantil sempre exige seu PIN.
Iniciar modo infantil
Configure e aprove pelo menos um aplicativo infantil primeiro.
Ativar controles de Education
Mude para o modo adulto antes de abrir os ajustes da TV.
Progresso: %1$d dias de prática. Pequenos passos diários, sem saltos após dias ausentes.
Alterar PIN
Voltar à matemática fácil
Reiniciar com pequenas adições? O tempo de hoje não será alterado.
Os controles exigem o serviço Education. Não substituem o gerenciamento do dispositivo: ajustes, remoção do serviço, entradas HDMI e perfis continuam sob controle da TV ou de cada aplicativo.
Nenhum aplicativo compatível instalado. No modo adulto, instale YouTube ou ABC iview pela loja da TV e volte aqui.
No YouTube, selecione o perfil YouTube Kids e configure um código dos pais para proteger outros perfis e o uso sem login. Este início não seleciona nem bloqueia esse perfil. Confirme após configurar no aplicativo.
No ABC iview, crie e selecione um perfil infantil com conteúdo ABC Kids. Este início abre ABC iview, mas não impede a troca de perfil. Confira os controles do aplicativo antes de aprovar.
Remover aprovação
Configurei o perfil infantil
Hora da aventura matemática!
Questão %1$d de %2$d · Sem pressa. Não há cronômetro.
Escolha os números da resposta abaixo e pressione OK.
Quase! Tente contar. Você pode tentar novamente.
Você conseguiu! ★
Cinco respostas certas. Você ganhou mais 30 minutos!
Boa prática! A próxima aventura está esperando.
Voltar aos meus programas''',
'es': '''En modo infantil, observa qué aplicación está abierta, cuenta el tiempo con la pantalla encendida y vuelve a Education al agotarse el tiempo o abrir otra aplicación. No lee el contenido de la pantalla.
Un poco de tele. Un poco de aprendizaje. Un espacio para niños.
Modo de padres
Modo adulto
3 horas al día. Resuelve 5 preguntas de matemáticas para ganar 30 minutos más.
¡Hola, pequeño explorador!
Elige qué ver o disfruta de una pequeña aventura de aprendizaje.
123 · Vamos a practicar
Usa las flechas para elegir · OK para abrir · El PIN protege el modo adulto
Quedan %1$d h %2$02d min para ver hoy
Pide a un adulto que active el servicio de accesibilidad Education. Los vídeos quedan bloqueados hasta que funcione.
Pide a un adulto que configure esta aplicación en el Modo de padres.
No se pudieron leer los ajustes familiares. Pide a un adulto que repare la aplicación.
Crea un PIN de 4 dígitos. Mantenlo en secreto.
Introduce el mismo PIN otra vez.
Introduce tu PIN de 4 dígitos para gestionar Education o volver al modo adulto.
OK
Introduce los 4 dígitos y elige OK.
Los PIN no coinciden. Empieza de nuevo.
Comprobando…
PIN no aceptado. Tras 5 intentos, espera 30 segundos.
Ajustes familiares. Salir del modo infantil siempre requiere tu PIN.
Iniciar modo infantil
Configura y aprueba al menos una aplicación infantil primero.
Activar controles de Education
Cambia al modo adulto antes de abrir los ajustes del televisor.
Progreso: %1$d días de práctica. Pequeños pasos diarios, sin saltos tras días de ausencia.
Cambiar PIN
Volver a matemáticas fáciles
¿Empezar de nuevo con sumas pequeñas? El tiempo de hoy no cambiará.
Los controles requieren el servicio Education. No sustituyen la gestión del dispositivo: los ajustes, la eliminación del servicio, HDMI y los perfiles dependen del televisor o de cada aplicación.
No hay una aplicación compatible instalada. En modo adulto, instala YouTube o ABC iview desde la tienda del televisor y vuelve aquí.
En YouTube, selecciona el perfil YouTube Kids y configura un código parental para proteger otros perfiles y el uso sin sesión. Este inicio no selecciona ni bloquea ese perfil. Confirma tras configurarlo en la aplicación.
En ABC iview, crea y selecciona un perfil infantil con contenido ABC Kids. Este inicio abre ABC iview, pero no impide cambiar de perfil. Revisa sus controles parentales antes de aprobar.
Quitar aprobación
He configurado el perfil infantil
¡Hora de una aventura matemática!
Pregunta %1$d de %2$d · Sin prisa. No hay temporizador.
Elige los números de la respuesta y pulsa OK.
¡Casi! Prueba a contar. Puedes intentarlo otra vez.
¡Lo lograste! ★
Cinco respuestas correctas. ¡Has ganado 30 minutos más!
¡Bien practicado! Tu próxima aventura te espera.
Volver a mis programas''',
'de': '''Im Kindermodus wird der Name der geöffneten App erkannt und die Nutzungszeit bei eingeschaltetem Bildschirm gezählt. Bei Zeitablauf oder anderen Apps geht es zurück zu Education. Bildschirminhalte werden nicht gelesen.
Ein bisschen Fernsehen. Ein bisschen Lernen. Ein Platz für Kinder.
Elternmodus
Erwachsenenmodus
3 Stunden täglich. Löse 5 Rechenaufgaben für weitere 30 Minuten.
Hallo, kleiner Entdecker!
Wähle etwas zum Anschauen oder starte ein kleines Lernabenteuer.
123 · Lass uns üben
Mit Pfeilen wählen · Mit OK öffnen · Die PIN schützt den Erwachsenenmodus
Heute noch %1$d Std. %2$02d Min. Fernsehzeit
Bitte einen Erwachsenen, den Education-Bedienungshilfedienst zu aktivieren. Videos bleiben bis dahin gesperrt.
Bitte einen Erwachsenen, diese App im Elternmodus einzurichten.
Die Familieneinstellungen konnten nicht gelesen werden. Bitte einen Erwachsenen, die App zu reparieren.
Erstelle eine vierstellige Eltern-PIN. Halte sie geheim.
Gib dieselbe PIN erneut ein.
Gib deine vierstellige PIN ein, um Education zu verwalten oder zum Erwachsenenmodus zu wechseln.
OK
Gib alle 4 Ziffern ein und wähle OK.
Die PINs stimmen nicht überein. Bitte erneut beginnen.
Wird geprüft…
PIN nicht akzeptiert. Nach 5 Versuchen bitte 30 Sekunden warten.
Familieneinstellungen. Zum Verlassen des Kindermodus ist immer deine PIN nötig.
Kindermodus starten
Richte zuerst mindestens eine Kinder-App ein und bestätige sie.
Education-Kontrollen aktivieren
Wechsle zuerst zum Erwachsenenmodus, um die TV-Einstellungen zu öffnen.
Lernfortschritt: %1$d Übungstage. Kleine tägliche Schritte, kein Sprung nach Pausen.
PIN ändern
Rechnen wieder leicht machen
Wieder mit kleinen Additionen beginnen? Die heutige Fernsehzeit bleibt unverändert.
Die Kontrollen benötigen den Education-Dienst. Sie ersetzen keine Geräteverwaltung: Einstellungen, Dienstentfernung, HDMI und App-Profile werden weiterhin vom Fernseher oder der jeweiligen App verwaltet.
Keine unterstützte App installiert. Installiere im Erwachsenenmodus YouTube oder ABC iview aus dem TV-Store und kehre zurück.
Wähle in YouTube das YouTube-Kids-Profil und richte einen Elterncode ein, der andere Profile und die Nutzung ohne Anmeldung schützt. Dieser Startbildschirm kann das Profil nicht wählen oder sperren. Bestätige erst nach der Einrichtung.
Erstelle und wähle in ABC iview ein Kinderprofil mit ABC-Kids-Inhalten. Dieser Startbildschirm öffnet ABC iview, verhindert aber keinen Profilwechsel. Prüfe die Kindersicherung vor der Freigabe.
Freigabe entfernen
Kinderprofil ist eingerichtet
Zeit für ein Rechenabenteuer!
Aufgabe %1$d von %2$d · Lass dir Zeit. Es gibt keinen Timer.
Wähle die Ziffern deiner Antwort und drücke OK.
Fast! Versuche zu zählen. Du darfst es noch einmal versuchen.
Geschafft! ★
Fünf richtige Antworten. Du hast weitere 30 Minuten verdient!
Gut geübt! Dein nächstes Abenteuer wartet.
Zurück zu meinen Sendungen''',
'ru': '''В детском режиме служба определяет открытую программу, считает время при включённом экране и возвращает в Education, когда время вышло или открыта другая программа. Содержимое экрана не читается.
Немного просмотра. Немного учёбы. Пространство для детей.
Режим родителей
Взрослый режим
3 часа в день. Реши 5 задач и получи ещё 30 минут.
Привет, маленький исследователь!
Выбери, что посмотреть, или отправляйся в учебное приключение.
123 · Давай потренируемся
Стрелки — выбор · OK — открыть · PIN защищает взрослый режим
Сегодня осталось %1$d ч %2$02d мин просмотра
Попроси взрослого включить службу специальных возможностей Education. До этого просмотр заблокирован.
Попроси взрослого настроить эту программу в режиме родителей.
Не удалось прочитать настройки семьи. Попроси взрослого восстановить программу.
Создайте PIN из 4 цифр. Держите его в секрете.
Введите тот же PIN ещё раз.
Введите PIN из 4 цифр для настройки Education или перехода во взрослый режим.
OK
Введите все 4 цифры и выберите OK.
PIN-коды не совпали. Начните заново.
Проверка…
PIN не принят. После 5 попыток подождите 30 секунд.
Настройки семьи. Для выхода из детского режима всегда нужен ваш PIN.
Включить детский режим
Сначала настройте и разрешите хотя бы одну детскую программу.
Включить контроль Education
Сначала перейдите во взрослый режим, чтобы открыть настройки телевизора.
Прогресс: %1$d дней практики. Небольшие ежедневные шаги без скачков после перерывов.
Изменить PIN
Снова лёгкие задачи
Вернуться к простому сложению? Время просмотра сегодня не изменится.
Контроль требует службы Education и не заменяет управление устройством: настройки, удаление службы, HDMI и профили остаются под контролем телевизора или самой программы.
Совместимая программа не установлена. Во взрослом режиме установите YouTube или ABC iview из магазина телевизора и вернитесь сюда.
В YouTube выберите детский профиль YouTube Kids и задайте родительский код для защиты других профилей и просмотра без входа. Этот экран не может выбирать или блокировать профиль. Подтвердите после настройки программы.
В ABC iview создайте и выберите детский профиль с контентом ABC Kids. Этот экран открывает ABC iview, но не запрещает смену профиля. Проверьте родительский контроль перед разрешением.
Отменить разрешение
Детский профиль настроен
Время математических приключений!
Задача %1$d из %2$d · Не спеши. Таймера нет.
Выбери цифры ответа и нажми OK.
Почти! Попробуй посчитать. Можно попробовать ещё раз.
Получилось! ★
Пять верных ответов. Ты получил ещё 30 минут!
Отличная тренировка! Следующее приключение ждёт.
Вернуться к моим передачам'''
}
all_text = {'en': {e.attrib['name']:e.text for e in english}}
for lang, block in translations.items():
    values = block.splitlines()
    assert len(values) == len(keys), (lang, len(values), len(keys))
    root = ET.Element('resources')
    for key, value in zip(keys, values):
        ET.SubElement(root, 'string', name=key).text = value.replace("'", "\\'")
    ET.indent(root, space='    ')
    folder = ROOT / ('app/res/values-' + lang)
    folder.mkdir(exist_ok=True)
    ET.ElementTree(root).write(folder / 'education.xml', encoding='utf-8', xml_declaration=True)
    all_text[lang] = dict(zip(keys,values))
for lang, values in all_text.items():
    folder = ROOT / ('app/res/values' + ('' if lang == 'en' else '-' + lang))
    for name in ('education_simple.xml', 'education_games.xml', 'education_limits.xml'):
        for entry in ET.parse(folder / name).getroot():
            values[entry.attrib['name']] = entry.text
(ROOT/'web/education-text.js').write_text('/* Generated by tools/education-translations.py. */\nwindow.EducationText = '+json.dumps(all_text,ensure_ascii=False)+';\n',encoding='utf-8')
print('Generated complete Education translations:', ', '.join(all_text))
