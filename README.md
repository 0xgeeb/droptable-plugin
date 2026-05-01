# Wiki Drop Table RuneLite Plugin

This external RuneLite plugin adds a sidebar panel that lets the player search for an OSRS enemy or boss and view parsed drop tables from the Old School RuneScape Wiki.

## What it does

- Adds a sidebar button named `Wiki Drop Table`
- Searches the OSRS Wiki by enemy or boss name
- Loads the matched wiki page through the MediaWiki API
- Parses wiki drop tables and shows item, quantity, rarity, and extra notes

## Project layout

- `src/main/java/com/droptable/DropTablePlugin.java`: plugin entrypoint and sidebar registration
- `src/main/java/com/droptable/DropTablePanel.java`: sidebar UI
- `src/main/java/com/droptable/WikiDropService.java`: wiki API calls and table parsing

## Running

Use a RuneLite-compatible Gradle setup to build or run the plugin test launcher:

```bash
gradle test
```

or launch through:

```bash
gradle run
```

If you want this ready for Plugin Hub submission, add the usual wrapper and plugin-hub metadata expected by your target workflow.
