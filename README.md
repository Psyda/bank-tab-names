
# Bank Tab Names

A RuneLite plugin that lets you customize your bank tabs with styled names, colors, icons, and multiple fonts. Transform your banking experience with personalized tab organization!

## ✨ Features

- **Custom Tab Names**: Replace default bank tab icons with personalized text
- **Rich Text Formatting**: Use colors, line breaks, and icons within tab names
- **Multiple Fonts**: Choose from 13 different font styles including QUILL, BARBARIAN, VERDANA, and more
- **Icon Library**: Access 200+ game icons to enhance your tab designs
- **Color Palette**: Quick-access color picker with 32 preset colors
- **Built-in Presets**: Ready-made configurations for different account types
- **Custom Presets**: Save and share your own tab configurations
- **Live Preview**: See exactly how your tabs will look before applying

## 📦 Installation

1. Open RuneLite and go to the Plugin Hub
2. Search for "Bank Tab Names"
3. Install and enable the plugin
4. Click the Bank Tab Names icon in the sidebar to open the configuration panel

## 🎮 Usage

### Basic Usage
1. Open the Bank Tab Names panel from the RuneLite sidebar
2. Click on any tab row to select it
3. Type your desired name in the text field
4. Choose a font from the dropdown
5. Use the checkbox to enable/disable custom styling for each tab
6. Your changes are saved automatically!

### Adding Colors
- Click any color button to add a color tag at your cursor position
- Colors are added as `<col=HEXCODE>` tags
- Use `</col>` to end color formatting

### Adding Icons
- Click any icon in the icon grid to add it at your cursor position
- Icons are added as `<img=ID>` tags
- Over 200 game icons available

### Line Breaks
- Press **Enter** in the text fields to add `<br>` line break tags
- Perfect for multi-line tab names, works mest with `PLAIN_11` font!

## 🎨 Built-in Presets

The plugin includes professionally designed presets!:

- **Ironman**: Optimized for ironman accounts with efficient categorization
- **Main**: Perfect for main accounts with combat and skilling focus
- **PVP**: Designed for player vs player activities and gear management
- **Skiller**: Tailored for skilling-focused accounts
- **Efficient**: Advanced multi-line layouts for maximum organization
- **Colorful**: Vibrant designs showcasing advanced formatting

## 🛠️ Advanced Features

### XML Tags
```
<col=FF0000>Red Text</col>          <!-- Colored text -->
<img=45>                            <!-- Insert icon with ID 45 -->
<br>                                <!-- Line break -->
<col=00FF00>Green<br>Line 2</col>   <!-- Multi-line colored text -->
```

### Font Options
- `PLAIN_11` / `PLAIN_12` - Clean, readable fonts
- `BOLD_12` - Bold emphasis
- `QUILL_8` / `QUILL_MEDIUM` - Classic RuneScape style
- `BARBARIAN` - Decorative fantasy font
- `VERDANA_11` through `VERDANA_15` - Modern, crisp fonts
- `TAHOMA_11` - Compact, professional font

### Custom Presets
- Save your current configuration as a custom preset
- Share preset names with friends
- Overwrite existing custom presets
- Remove unwanted custom presets

## 📸 Examples

Hardcore Ironman icon replacing the default ∞ symbol.
Tab 0: `<img=10>` 

Red HEX code color for "Combat" text
Tab 1: `<col=FF0000>Combat`           

Green "Gear" and cyan "Tools" on separate lines use <br> to break the lines
Tab 2: `<col=00FF00>Gear<br><col=00FFFF>Tools`  

Iron Sword icon with purple "Slayer" text below
Tab 3: `<img=45><br><col=800080>Slayer`         

## 🎯 Tips

- **Live Editing**: Edit your tabs while your bank is open to see changes in real-time
- **Font Preview**: The preview panel shows text formatting but not fonts - use live editing for font preview
- **Color Codes**: Use any 6-digit hex color code for unlimited color options
- **Icon Discovery**: Hover over icons to see their ID numbers
- **Backup**: Save your favorite configurations as custom presets before experimenting

## 🤝 Support

- **GitHub**: [Report issues or contribute](https://github.com/psyda/bank-tab-names)

- **In-Game**: Find `Psyda` on discord to have your tab preset added as an official ingame preset, or add 'd1x' or 'Iron d1x' ingame if you want to chat!

## 📝 Version History

### 2.0.0
- Complete UI redesign with intuitive panel interface
- Added preset system with 6 built-in configurations
- Expanded icon library to 200+ icons
- Enhanced color picker with 32 preset colors
- Custom preset saving and management
- Live preview system
- Multiple font support
- Improved stability and performance

### 1.0.0
- Initial release with basic tab naming functionality

---

**Created by Psyda** | Transform your banking experience today!