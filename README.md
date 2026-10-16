# EntityAiRange

Paper-Plugin für Minecraft 26.3. Das Plugin deaktiviert die KI geladener Mobs, wenn sich kein Spieler in der eingestellten Reichweite befindet. Kommt ein Spieler zurück, wird die zuvor vom Plugin deaktivierte KI wieder eingeschaltet.

## Voraussetzungen

- Paper **26.3**
- **Java 25**
- Ingame-Rechte: **entityairange.admin** (standardmäßig für Operatoren)

## Installation

1. Lade **entity-ai-range-0.1.jar** aus dem [Release v0.1](https://github.com/mcsnack/EntityAiRange/releases/tag/v0.1) herunter.
2. Lege die JAR-Datei in den Ordner **plugins** deines Paper-Servers.
3. Starte den Server neu.
4. Öffne im Spiel das Menü mit **/entityairange gui**.

Beim ersten Start erstellt das Plugin **plugins/EntityAiRange/config.yml**. Änderungen an dieser Datei werden nach einem Serverneustart geladen.

## GUI und Befehle

Die Einstellungen können nur von Spielern mit der Berechtigung **entityairange.admin** geändert werden. Operatoren haben diese standardmäßig.

| Befehl | Funktion |
| --- | --- |
| **/entityairange** | Öffnet das Einstellungsmenü. |
| **/entityairange gui** | Öffnet ebenfalls das Einstellungsmenü. |
| **/eair** oder **/eair gui** | Kurzform für das Menü. |
| **/entityairange status** | Zeigt, wie viele geladene Mobs das Plugin aktuell verwaltet. |
| **/entityairange restore** | Stellt die KI verwalteter, geladener Mobs wieder her und pausiert die automatische Verwaltung. |
| **/entityairange resume** | Setzt die automatische Verwaltung fort. |

Die GUI zeigt die aktuelle Reichweite in der Mitte. Klicke auf die jeweiligen Gegenstände:

| Gegenstand | Aktion |
| --- | --- |
| Redstone links | Reichweite um 64 Blöcke verringern |
| Redstone daneben | Reichweite um 16 Blöcke verringern |
| Kompass in der Mitte | Zeigt die aktuelle Reichweite |
| Smaragd daneben | Reichweite um 16 Blöcke erhöhen |
| Smaragd rechts | Reichweite um 64 Blöcke erhöhen |
| Uhr unten | Auf 128 Blöcke zurücksetzen |
| Barriere unten rechts | Menü schließen |

Die Reichweite ist auf **16 bis 1024 Blöcke** begrenzt. Jede Änderung wird sofort in der Konfiguration gespeichert und gilt für die laufenden Scans.

Wenn du einem Nicht-Operator die GUI erlauben möchtest, kannst du die Berechtigung beispielsweise mit LuckPerms vergeben:

```text
/lp user <Spieler> permission set entityairange.admin true
```

## Konfiguration

Datei: **plugins/EntityAiRange/config.yml**

```yaml
# AI wird deaktiviert, wenn kein Spieler innerhalb dieser Distanz ist.
disable-distance: 128

# Abstand zwischen Prüfungen: 100 Ticks entsprechen 5 Sekunden.
scan-interval-ticks: 100

# Schreibt nach jedem Scan eine Zusammenfassung ins Serverlog.
log-scan-summary: false
```

Die GUI aktualisiert **disable-distance** direkt. Änderungen an **scan-interval-ticks** oder **log-scan-summary** benötigen einen Neustart.

## Verhalten und Hinweise

- Standardmäßig prüft das Plugin alle 5 Sekunden die geladenen Mobs in allen Welten. Ein Mob bleibt aktiv, solange mindestens ein Spieler in derselben Welt innerhalb der Reichweite ist.
- Es deaktiviert nur die KI von Mobs, deren KI vorher aktiv war. Entities, die bereits mit deaktivierter KI geladen werden, bleiben unberührt. Damit übernimmt das Plugin beispielsweise keine von Stillagers veränderten Villager.
- Das Plugin markiert Mobs, deren KI es selbst deaktiviert hat. Kommt ein solcher Mob wieder in Reichweite, stellt es dessen KI wieder her.
- **/entityairange restore** stellt markierte Mobs in geladenen Chunks wieder her. Während der Pause werden markierte Mobs auch beim Laden ihrer Chunks wiederhergestellt. Die Pause endet mit einem Neustart oder **/entityairange resume**.
- Beim Herunterfahren wird die KI verwalteter Mobs in geladenen Chunks wiederhergestellt.
- Deaktivierte Mob-KI bedeutet auch keine normale Bewegung, Wegfindung oder Zielsuche. Mob-Farmen, Raids, Haustiere und andere Mechaniken können sich daher anders verhalten.
- Das Plugin durchsucht geladene Mobs. Auf großen Servern solltest du nach der Installation die MSPT beobachten. Bei messbarer Zusatzlast kann ein längeres **scan-interval-ticks** helfen.

Vor dem produktiven Einsatz empfiehlt sich ein Backup oder ein Test auf einer Kopie des Servers.

### Plugin dauerhaft entfernen

Führe zuerst **/entityairange restore** aus und lade anschließend die Chunks mit zuvor verwalteten Mobs, damit deren KI wiederhergestellt wird. Solange ein Chunk nicht geladen wurde, bleibt dort die Markierung gespeichert. Entfernst du das Plugin, bevor diese Chunks geladen wurden, kann das Plugin die KI dieser Mobs später nicht mehr wiederherstellen.

### Stillagers

Stillagers-Villager, die bereits mit deaktivierter KI geladen werden, werden nicht vom Plugin übernommen. Die Trading- und Restock-Funktionen bleiben damit Sache von Stillagers.

## Build aus dem Quellcode

Mit Maven und Java 25:

```sh
mvn --batch-mode package
```

Die gebaute JAR liegt unter **target/entity-ai-range-0.1.jar**. GitHub Actions baut das Projekt bei Pushes und Pull Requests und stellt die JAR als Workflow-Artefakt bereit.
