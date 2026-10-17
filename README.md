# EntityAiRange

Paper-Plugin für Minecraft 26.3. EntityAiRange deaktiviert die KI geladener Mobs außerhalb einer einstellbaren Spielerreichweite und stellt sie wieder her, wenn Spieler zurückkehren. Zusätzlich lässt sich die Lebenszeit gedroppter Items verkürzen. Für einzelne Entities kann ein eigener Despawn-Timer gesetzt werden.

## Voraussetzungen

- Paper **26.3**
- **Java 25**
- Berechtigung **entityairange.admin** (standardmäßig Operatoren)

## Installation

1. Lade **entity-ai-range-0.2.jar** aus dem [Release v0.2](https://github.com/mcsnack/EntityAiRange/releases/tag/v0.2) herunter.
2. Lege die JAR in den Ordner `plugins` deines Paper-Servers.
3. Starte den Server neu.
4. Öffne das Menü mit `/entityairange gui`.

## Befehle

| Befehl | Funktion |
| --- | --- |
| `/entityairange gui` | Öffnet das Einstellungsmenü für Reichweite und Item-Despawnzeit. |
| `/entityairange status` | Zeigt die Zahl der aktuell verwalteten Mobs. |
| `/entityairange restore` | Stellt verwaltete Mob-KI wieder her und pausiert die automatische Verwaltung. |
| `/entityairange resume` | Setzt die automatische Mob-KI-Verwaltung fort. |
| `/entity despawn add <Selektor> [Sekunden]` | Setzt für die ausgewählten Entities einen einmaligen Timer. Ohne Sekundenwert wird die GUI-Einstellung verwendet. |

Beispiele:

```text
/entity despawn add @e[type=item,limit=1,sort=nearest] 60
/entity despawn add @e[type=zombie,distance=..8] 30
/entity despawn add @e[type=item,distance=..5]
```

Selektoren entsprechen den Vanilla-Selektoren. Der optionale Zeitwert ist auf **15 bis 300 Sekunden** begrenzt. Die Auswahl wird beim Befehl ausgewertet; der Timer wird nur für die Entities gesetzt, die der Selektor in diesem Moment findet. Ein Selektor mit `limit=1` zielt auf ein einzelnes Entity.

## GUI

Im Reichweitenmenü wählst du **Item-Despawn einstellen**, um die zweite Seite zu öffnen. Dort passen die Tasten die Zeit in Schritten von 15 oder 60 Sekunden an. Die erlaubte Spanne ist 15 Sekunden bis 5 Minuten; Änderungen werden sofort gespeichert. Das gilt für alle gedroppten Items, auch für bereits geladene Items. Fünf Minuten entspricht dem normalen Vanilla-Despawnwert.

Die Despawnzeit zählt in Minecraft-Ticks. Sie pausiert, solange der Chunk eines Entities nicht geladen ist. Beim Laden wird der Timer anhand des gespeicherten Entity-Alters fortgesetzt. Der Timer für per Befehl ausgewählte Entities bleibt ebenfalls über Chunk-Entladen und Serverneustarts erhalten.

## Konfiguration

Datei: `plugins/EntityAiRange/config.yml`

```yaml
disable-distance: 128
scan-interval-ticks: 100
log-scan-summary: false
item-despawn-seconds: 300
```

`item-despawn-seconds` wird auf 15 bis 300 Sekunden begrenzt. Die GUI speichert diesen Wert sofort. Reichweite, Scan-Intervall und Log-Zusammenfassung funktionieren wie in den vorherigen Versionen.

## Hinweise

- Nur Mobs, deren KI vorher aktiv war, werden verwaltet. Entities, die bereits mit deaktivierter KI geladen werden, bleiben unberührt. So übernimmt das Plugin keine von Stillagers veränderten Villager.
- Deaktivierte Mob-KI bedeutet auch keine normale Bewegung, Wegfindung oder Zielsuche. Mob-Farmen, Raids, Haustiere und andere Mechaniken können sich anders verhalten.
- Despawn-Timer entfernen die ausgewählten Entities endgültig. Wähle Selektoren daher sorgfältig.

## Build

Mit Maven und Java 25:

```sh
mvn --batch-mode package
```

Die JAR liegt unter `target/entity-ai-range-0.2.jar`.
