# EntityAiRange

Paper-Plugin für Minecraft 26.3. EntityAiRange deaktiviert die KI geladener Mobs außerhalb einer einstellbaren Spielerreichweite und stellt sie wieder her, wenn Spieler zurückkehren. Zusätzlich können Despawn-Zeiten für gedroppte Items und Minecraft-Entity-Typen verwaltet werden.

## Voraussetzungen

- Paper **26.3**
- **Java 25**
- Berechtigung **entityairange.admin** (standardmäßig Operatoren)

## Installation

1. Lade **entity-ai-range-0.3.1.jar** aus dem [Release v0.3.1](https://github.com/mcsnack/EntityAiRange/releases/tag/v0.3.1) herunter.
2. Lege die JAR in den Ordner `plugins` deines Paper-Servers.
3. Starte den Server neu.
4. Öffne das Menü mit `/entityairange gui`.

## Befehle

| Befehl | Funktion |
| --- | --- |
| `/entityairange gui` | Öffnet die Kategorien **Mob AI** und **Item Despawn**. |
| `/entityairange status` | Zeigt die Zahl der aktuell verwalteten Mobs. |
| `/entityairange restore` | Stellt verwaltete Mob-KI wieder her und pausiert die automatische Verwaltung. |
| `/entityairange resume` | Setzt die automatische Mob-KI-Verwaltung fort. |
| `/entity despawn add <minecraft:typ> [Sekunden]` | Fügt eine Regel für einen Item- oder Entity-Typ hinzu. Ohne Zeit gilt die im GUI eingestellte Item-Standardzeit. |
| `/entity despawn remove <minecraft:typ>` | Entfernt die Regel für den angegebenen Typ. |

Die Befehle unterstützen TAB-Vervollständigung: TAB zeigt Unterbefehle, passende Item- und Entity-IDs, vorhandene Regeln bei `remove` sowie Zeitwerte. Auch `/entityairange` vervollständigt `gui`, `status`, `restore` und `resume`.

Beispiele:

```text
/entity despawn add minecraft:item 60
/entity despawn add minecraft:diamond 30
/entity despawn add minecraft:zombie 120
/entity despawn remove minecraft:diamond
```

Die Zeit muss **15 bis 300 Sekunden** betragen. `minecraft:item` gilt für alle gedroppten Items; ein Item-Material wie `minecraft:diamond` gilt nur für gedroppte Diamanten. Ein Entity-Typ wie `minecraft:zombie` gilt für alle passenden Entities, die bereits geladen sind oder später erscheinen. Für Items hat eine Material-Regel Vorrang vor `minecraft:item` und der globalen GUI-Standardzeit.

Beim Entfernen einer Regel gilt für Items wieder die spezifischere verbleibende Regel oder die globale Standardzeit. Für Mob-Typen gilt danach wieder das normale Minecraft-Verhalten.

## GUI

`/entityairange gui` öffnet die Kategorien:

- **Mob AI**: Reichweite wie bisher anpassen.
- **Item Despawn**: globale Lebenszeit aller gedroppten Items von 15 Sekunden bis 5 Minuten ändern und aktive Typ-Regeln einsehen.

Typ-Regeln fügst du über die Befehle oben hinzu und entfernst sie per `remove`. Im Item-Despawn-Menü werden die aktiven Regeln samt Zeit angezeigt. Änderungen der globalen Item-Zeit wirken auf geladene und neu erscheinende Items, sofern keine passendere Typ-Regel gesetzt ist.

Despawn-Zeiten zählen in Minecraft-Ticks und pausieren, solange der Chunk nicht geladen ist. Die Timer für Typ-Regeln bleiben über Chunk-Entladen und Serverneustarts erhalten.

## Konfiguration

Datei: `plugins/EntityAiRange/config.yml`

```yaml
disable-distance: 128
scan-interval-ticks: 100
log-scan-summary: false
item-despawn-seconds: 300
despawn-rules: {}
despawn-rule-ids: {}
```

Typ-Regeln werden automatisch in der Konfiguration gespeichert. Die GUI speichert `item-despawn-seconds` sofort. Reichweite, Scan-Intervall und Log-Zusammenfassung funktionieren wie in den vorherigen Versionen.

## Hinweise

- Nur Mobs, deren KI vorher aktiv war, werden verwaltet. Entities, die bereits mit deaktivierter KI geladen werden, bleiben unberührt. So übernimmt das Plugin keine von Stillagers veränderten Villager.
- Deaktivierte Mob-KI bedeutet auch keine normale Bewegung, Wegfindung oder Zielsuche. Mob-Farmen, Raids, Haustiere und andere Mechaniken können sich daher anders verhalten.
- Despawn-Regeln entfernen passende Entities endgültig, sobald die eingestellte Zeit erreicht ist.

## Build

Mit Maven und Java 25:

```sh
mvn --batch-mode package
```

Die JAR liegt unter `target/entity-ai-range-0.3.1.jar`.
