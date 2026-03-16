# Bank Tab Names

<img width="760" height="340" alt="image" src="https://github.com/user-attachments/assets/949d11d4-6e4f-4b6e-a92a-e79642736993" />


A RuneLite plugin for customizing bank tabs with styled text, icons, game sprites, and manual layout controls.

## Features

### Text

Each tab can display custom text with color formatting and line breaks. Twelve fonts are available, ranging from the small Quill 8 to Verdana 15. Text supports inline color tags (`<col=HEX>`) and line breaks (`<br>`). A color palette in the side panel lets you insert color tags by clicking.

Text can be edited in two ways: through the plugin's side panel text field (where tags are visible as literal text), or through the in-game chatbox input by right-clicking a bank tab and selecting "Edit text."

You can also edit and add images by right-clicking a tab natively iname. There are many options for how you can approach customization. I've taken a lot of the limitations on creativity away to allow for a lot of flexibility and creative expression.


### Icons

Tabs support multiple icons simultaneously, arranged left-to-right across the tab area. Four icon types are available:

- **Item icons** searched through the standard RuneLite item search
- **Skill icons** from the full skill grid
- **Game sprites** from a curated category browser (Arrows, Creatures, Emotes, Labels, Leagues, Overheads, Prayer, Runes, Spells, Teleports, UI Icons, and more) or by entering a sprite archive ID and frame directly
- **Custom icons** loaded from PNG files in the user icons folder

Icons are added through the right-click menu on any bank tab. The plugin panel also shows an "Edit" button on each tab row that opens the icon editor when icons are present.

### Icon Editor

Each icon has individual controls:

- **Width and height** with a link button that locks the aspect ratio for proportional resizing
- **Auto sizing** that uses the sprite's real canvas dimensions (including transparent padding) for accurate rendering
- **X and Y offset** with no bounds clamping, allowing icons to extend beyond the tab area for decorative purposes
- **Z-index** controlling render priority (higher values draw on top)
- **Reorder** buttons to rearrange icons left-to-right
- **Delete** button per icon

The editor scrolls when more than a few icons are configured, preventing the panel from forcing the client window larger.

<img width="3840" height="2160" alt="image" src="https://github.com/user-attachments/assets/bb2c0de4-dfc2-4718-abd1-e1cee27b2b11" />

<img width="2233" height="1207" alt="image" src="https://github.com/user-attachments/assets/f1651b33-65d2-43b4-afe9-a3453dcae53e" />


### Fit Toggle

A per-tab "Fit" checkbox in the icon editor controls how icons and text interact. When enabled (default), icons shift upward and text aligns to the bottom, keeping them separated. When disabled, all icons stack at the center position and text centers over them. This is intended for designs where a sprite covers the entire tab area as a background, with text overlaid on top. Z-index controls which icon draws in front when stacking.

### Rendering

All overlays (icons, text, drag ghost) are rendered on the top-level bank container (`Bankmain.INFINITE`) rather than the tab bar widget. This means icons and text are not clipped by the tab bar boundaries and can extend freely in any direction using offsets.

Game sprite sizing uses `SpritePixels.getMaxWidth()` and `getMaxHeight()` from the client's sprite archive to get the full canvas dimensions including transparent padding. This prevents the squishing that occurs when using `SpriteManager.getSprite()`, which trims transparent pixels and returns smaller dimensions than the engine actually renders.

Overlays hide automatically when the bank settings page is open.

### Panel Visibility

Three modes are available in the plugin's RuneLite config:

- **Default**: the panel is always available in the sidebar
- **Hide side panel**: removes the panel from the sidebar entirely. It temporarily appears when you use any Edit or Add action from a bank tab's right-click menu, then hides again when the bank closes.
- **Only show while in bank**: the panel appears when you open the bank and disappears when you close it.

### Drag-to-Rearrange

Tab designs can be reordered by dragging tabs in the bank. A ghost overlay follows the cursor showing the tab's text. Dropping on a different tab swaps both designs.

### Config Presets

This wouldn't be a great plugin if you couldn't share your configs with others, just make sure to also share you image files so they can utilize them too! Tab layouts can be saved as named presets, loaded, and deleted from the side panel. Presets store all ten tabs including text, font, enabled state, icons with their sizing and offsets, fit toggle, and z-index values.

### Import and Export

Configs can be exported to clipboard or file as JSON, and imported from clipboard or file. The exported JSON includes a version number for forward compatibility. When importing a config created on an older plugin version, a warning appears (dismissible with "Don't show again") noting that placement or sizing may need adjusting.

The import system supports both the current format (name, version, and tab data wrapper) and the legacy flat format (direct tab map) for backwards compatibility.

### Custom Icons

The plugin scans a user icons folder for PNG files and makes them available in the custom icon picker. Bundled icons are included with the plugin. The "Reload icons" button rescans the folder and refreshes the panel immediately. The "Open folder" button opens the user icons directory on disk.

Custom icon sprite IDs are mapped by filename and persist for the session, so reloading does not shuffle IDs or break existing tab configurations.

## Configuration

The following settings are available in RuneLite's plugin configuration panel:

| Setting | Description |
|---|---|
| Hide side panel | Removes the panel from the sidebar |
| Only show panel while in bank | Auto-shows on bank open, hides on close |
| Suppress import version warning | Disables the version mismatch dialog on import |
| Suppress tag help popup | Disables the text tag reference popup on first Edit Text use |

## Tags Reference

| Tag | Effect |
|---|---|
| `<br>` | Line break |
| `<col=HEX>` | Set text color (e.g. `<col=FF0000>` for red) |
| `</col>` | Reset to white |

In the side panel text field, pressing Enter inserts a `<br>` tag automatically.

## Fonts

| Name | Size |
|---|---|
| Quill 8 | Small (default) |
| Quill Medium | Medium |
| Plain 11 | Standard |
| Plain 12 | Standard |
| Bold 12 | Standard bold |
| Barbarian | Decorative |
| Surok | Decorative |
| Verdana 11 | Clean |
| Verdana 11 Bold | Clean bold |
| Tahoma 11 | Clean |
| Verdana 13 | Large |
| Verdana 13 Bold | Large bold |
| Verdana 15 | Largest |

## Building

This is a standard RuneLite plugin. Clone the repository and build with Gradle:

```
./gradlew build
```

## Support

- [GitHub Issues](https://github.com/psyda/bank-tab-names/issues)
- [Patreon](https://patreon.com/psyda)
- [PayPal](https://paypal.me/mintyfresh)

More pics:

<img width="2062" height="1048" alt="image" src="https://github.com/user-attachments/assets/62cedcba-e2d3-4aba-bc87-ea1bc8ec14fd" />
