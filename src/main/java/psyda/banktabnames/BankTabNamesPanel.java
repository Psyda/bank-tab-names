package psyda.banktabnames;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class BankTabNamesPanel extends PluginPanel {

    @Inject
    private ConfigManager configManager;

    @Inject
    private IconSpriteExtractor imageExtractor;

    private JPanel tabConfigPanel;
    private JPanel iconGridPanel;
    private JPanel colorPanel;
    private JComboBox<String> presetCombo;
    private List<TabConfigRow> tabRows;
    private List<Integer> availableIconIds;
    private int selectedTabIndex = 0;

    // Expanded color spectrum (32 colors)
    private static final Color[] PRESET_COLORS = {
            // Reds
            Color.WHITE, new Color(255, 0, 0), new Color(255, 100, 100), new Color(139, 0, 0),
            // Oranges
            new Color(255, 165, 0), new Color(255, 140, 0), new Color(255, 69, 0), new Color(205, 92, 92),
            // Yellows
            new Color(255, 255, 0), new Color(255, 215, 0), new Color(255, 228, 181), new Color(240, 230, 140),
            // Greens
            new Color(0, 255, 0), new Color(0, 128, 0), new Color(34, 139, 34), new Color(144, 238, 144),
            // Cyans
            new Color(0, 255, 255), new Color(0, 206, 209), new Color(64, 224, 208), new Color(175, 238, 238),
            // Blues
            new Color(0, 0, 255), new Color(0, 0, 139), new Color(30, 144, 255), new Color(135, 206, 235),
            // Purples
            new Color(128, 0, 128), new Color(75, 0, 130), new Color(138, 43, 226), new Color(221, 160, 221),
            // Browns/Grays
            new Color(165, 42, 42), new Color(160, 82, 45), new Color(128, 128, 128), new Color(0, 0, 0)
    };

    private static final String[] PRESET_NAMES = {"Ironman", "Main", "PVP", "Skiller", "Efficient", "Colorful"};

    // Preset configurations
    private static class PresetConfig {
        final String name;
        final TabFonts font;
        final boolean enabled;

        PresetConfig(String name, TabFonts font, boolean enabled) {
            this.name = name;
            this.font = font;
            this.enabled = enabled;
        }
    }

    private static final PresetConfig[][] PRESETS = {
            // Ironman preset
            {
                    new PresetConfig("<img=10>", TabFonts.QUILL_8, true),                    // Tab 0: HCIM icon
                    new PresetConfig("<col=00FF00>Gear", TabFonts.PLAIN_11, true),          // Tab 1: Green Gear
                    new PresetConfig("<col=00FF00>Tools<br>&<br><col=00FFFF>Craft", TabFonts.PLAIN_11, true), // Tab 2: Green/Cyan Tools & Craft
                    new PresetConfig("<col=FFFF00>Herblore<br><col=00FF00>Farm", TabFonts.PLAIN_11, true), // Tab 3: Yellow/Green Herblore Farm
                    new PresetConfig("<col=FF0000>Food<br><col=FFA500>Potions", TabFonts.PLAIN_11, true), // Tab 4: Red/Orange Food Potions
                    new PresetConfig("<col=800080>Runes<br><col=0000FF>Magic", TabFonts.PLAIN_11, true), // Tab 5: Purple/Blue Runes Magic
                    new PresetConfig("<col=A52A2A>Logs<br><col=00FF00>Seeds", TabFonts.PLAIN_11, true), // Tab 6: Brown/Green Logs Seeds
                    new PresetConfig("<col=808080>Ores<br><col=FFFF00>Bars", TabFonts.PLAIN_11, true), // Tab 7: Gray/Yellow Ores Bars
                    new PresetConfig("<col=00FFFF>Fish<br><col=FF00FF>Gems", TabFonts.PLAIN_11, true), // Tab 8: Cyan/Magenta Fish Gems
                    new PresetConfig("<img=84><br><col=FFA500>Misc", TabFonts.PLAIN_11, true)           // Tab 9: Orange Misc
            },
            // Main preset
            {
                    new PresetConfig("<col=FFD700>Main", TabFonts.BARBARIAN, true),   // Tab 0: Gold Main
                    new PresetConfig("<br><col=FF0000>Combat", TabFonts.PLAIN_11, true), // Tab 1: Red Combat with sword
                    new PresetConfig("<br><col=00FF00>Skilling", TabFonts.PLAIN_11, true), // Tab 2: Green Skilling
                    new PresetConfig("<br><col=0000FF>Magic", TabFonts.PLAIN_11, true), // Tab 3: Blue Magic
                    new PresetConfig("<img=45><br><col=800080>Slayer", TabFonts.PLAIN_11, true), // Tab 4: Purple Slayer
                    new PresetConfig("<img=15><br><col=FFA500>Quests", TabFonts.PLAIN_11, true), // Tab 5: Orange Quests
                    new PresetConfig("<br><col=00FFFF>Fishing", TabFonts.PLAIN_11, true), // Tab 6: Cyan Fishing
                    new PresetConfig("<br><col=A52A2A>Mining", TabFonts.PLAIN_11, true), // Tab 7: Brown Mining
                    new PresetConfig("<br><col=00FF00>Farming", TabFonts.PLAIN_11, true), // Tab 8: Green Farming
                    new PresetConfig("<col=FF00FF>Misc<br><img=83>", TabFonts.BOLD_12, true)  // Tab 9: Magenta Misc
            },
            // PVP preset
            {
                    new PresetConfig("<img=9><img=4>", TabFonts.QUILL_MEDIUM, true),     // Tab 0: Red PVP
                    new PresetConfig("<col=FF0000>Gear<br>&<br><col=00FF00>Sets", TabFonts.PLAIN_11, true), // Tab 1: Red Weapons
                    new PresetConfig("<col=00FF00>Food<br><col=FF6464>Pots", TabFonts.PLAIN_11, true), // Tab 2: Gray Armour
                    new PresetConfig("<col=AFEEEE>Teles<br><col=00FF00>&<br><col=AFEEEE>Runes", TabFonts.PLAIN_11, true), // Tab 3: Green Food
                    new PresetConfig("<img=168><br>Alchs", TabFonts.PLAIN_11, true), // Tab 4: Blue Potions
                    new PresetConfig("<col=FFA500>Dump", TabFonts.PLAIN_11, true), // Tab 5: Purple Runes
                    new PresetConfig("<img=82><br><col=FFFF00>Loot", TabFonts.PLAIN_11, true), // Tab 6: Yellow Arrows
                    new PresetConfig("Pets", TabFonts.PLAIN_11, true), // Tab 7: Magenta Specs
                    new PresetConfig("<col=F0E68C>Skilling", TabFonts.PLAIN_11, true), // Tab 8: Cyan Teleports
                    new PresetConfig("<img=83><br>Quest", TabFonts.PLAIN_11, true)  // Tab 9: Orange Misc
            },
            // Skiller preset
            {
                    new PresetConfig("<col=00FF00>Xyz", TabFonts.QUILL_MEDIUM, true),      // Tab 0: Xyz (QUILL_MEDIUM demo)
                    new PresetConfig("<img=16><br><col=FFE4B5>Herblore", TabFonts.PLAIN_11, true),
                    new PresetConfig("<img=161><br><col=FF6464>Mining", TabFonts.PLAIN_11, true),
                    new PresetConfig("<img=18><br><col=00FFFF>Fish", TabFonts.PLAIN_11, true),
                    new PresetConfig("<img=53><br><col=FFA500>Cook", TabFonts.PLAIN_11, true),
                    new PresetConfig("<col=F0E68C><img=22><br>Craft", TabFonts.PLAIN_11, true),
                    new PresetConfig("<img=53><br>Farm", TabFonts.PLAIN_11, true),
                    new PresetConfig("<img=121><img=123><img=124><img=125><br>RC", TabFonts.PLAIN_11, true),
                    new PresetConfig("<img=78><br>Hunter", TabFonts.PLAIN_11, true),
                    new PresetConfig("<img=83><br><col=FFFF00>Misc", TabFonts.PLAIN_11, true)   // Tab 9: GnomeChild Misc
            },
            // Efficient Ironman preset
            {
                    new PresetConfig("<col=FFD700>Main<br><col=FFFFFF>Account", TabFonts.PLAIN_11, true),     // Tab 0: Gold/White Main Account
                    new PresetConfig("<col=FF0000>Combat<br><col=C0C0C0>Gear", TabFonts.PLAIN_11, true),     // Tab 1: Red/Silver Combat Gear
                    new PresetConfig("<col=00FF00>Tools<br><col=00FFFF>Utilities", TabFonts.PLAIN_11, true), // Tab 2: Green/Cyan Tools Utilities
                    new PresetConfig("<col=FF0000>Food<br><col=FFA500>Potions", TabFonts.PLAIN_11, true),    // Tab 3: Red/Orange Food Potions
                    new PresetConfig("<col=800080>Runes<br><col=0000FF>Magic", TabFonts.PLAIN_11, true),     // Tab 4: Purple/Blue Runes Magic
                    new PresetConfig("<col=A52A2A>Logs<br><col=808080>Ores", TabFonts.PLAIN_11, true),      // Tab 5: Brown/Gray Logs Ores
                    new PresetConfig("<col=00FF00>Seeds<br><col=FFFF00>Farming", TabFonts.PLAIN_11, true),   // Tab 6: Green/Yellow Seeds Farming
                    new PresetConfig("<col=00FFFF>Fish<br><col=FF69B4>Cooking", TabFonts.PLAIN_11, true),   // Tab 7: Cyan/Pink Fish Cooking
                    new PresetConfig("<col=8B4513>Craft<br><col=DDA0DD>Fletch", TabFonts.PLAIN_11, true),   // Tab 8: Brown/Plum Craft Fletch
                    new PresetConfig("<col=32CD32>Herbs<br><col=FF00FF>Combo", TabFonts.PLAIN_11, true) // Tab 9: Lime/Magenta Herbs Secondaries
            },
            // Advanced Combo preset
            {
                    new PresetConfig("<col=FFD700>Wealth", TabFonts.TAHOMA_11, true), // Tab 0: Gold/White Wealth Valuables
                    new PresetConfig("<col=FF0000>Melee<br><col=00FF00>Ranged<br><col=0000FF>Magic", TabFonts.PLAIN_11, true), // Tab 1: Red/Green/Blue Combat Trinity
                    new PresetConfig("<col=C0C0C0>Armour<br><col=FFD700>Jewelry", TabFonts.TAHOMA_11, true), // Tab 2: Silver/Gold Armour Jewelry
                    new PresetConfig("<col=FF4500>Pots<br><col=98FB98>Food", TabFonts.TAHOMA_11, true), // Tab 3: Orange/Pale Green Consumables Restores
                    new PresetConfig("<col=4169E1>Teles<br><col=DDA0DD>Utils", TabFonts.TAHOMA_11, true), // Tab 4: Royal Blue/Plum Teleports Utilities
                    new PresetConfig("<col=8B4513>Sups<br><col=DAA520>Mats", TabFonts.TAHOMA_11, true), // Tab 5: Brown/Goldenrod Resources Materials
                    new PresetConfig("<col=228B22>Prod<br><col=FF1493>Home", TabFonts.TAHOMA_11, true), // Tab 6: Forest Green/Deep Pink Production Supplies
                    new PresetConfig("<col=4682B4>Gather<br><col=F0E68C>Process", TabFonts.TAHOMA_11, true), // Tab 7: Steel Blue/Khaki Gathering Processed
                    new PresetConfig("<col=DC143C>Skilling<br><col=00CED1>Outfits", TabFonts.TAHOMA_11, true), // Tab 8: Crimson/Dark Turquoise Skilling Outfits
                    new PresetConfig("<col=9370DB>Quest<br><col=FFB6C1>Misc", TabFonts.TAHOMA_11, true)      // Tab 9: Medium Purple/Light Pink Quest Misc
            }
    };

    private static final String[] COLOR_HEX = {
            "FFFFFF", "FF0000", "FF6464", "8B0000",
            "FFA500", "FF8C00", "FF4500", "CD5C5C",
            "FFFF00", "FFD700", "FFE4B5", "F0E68C",
            "00FF00", "008000", "228B22", "90EE90",
            "00FFFF", "00CED1", "40E0D0", "AFEEEE",
            "0000FF", "00008B", "1E90FF", "87CEEB",
            "800080", "4B0082", "8A2BE2", "DDA0DD",
            "A52A2A", "A0522D", "808080", "000000"
    };

    @Override
    public void onActivate() {
        super.onActivate();
        if (availableIconIds == null || availableIconIds.isEmpty()) {
            loadIcons();
        }
    }

    public BankTabNamesPanel() {
        super(false);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(6, 6, 6, 6));

        tabRows = new ArrayList<>();
        availableIconIds = new ArrayList<>();
    }

    public void init() {
        initComponents();
        loadConfigValues();
        updatePresetDropdown(); // Load saved presets
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Header
        JLabel titleLabel = new JLabel("Bank Tab Names - By Psyda");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        add(titleLabel, BorderLayout.NORTH);

        // Main content panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Tab config section
        tabConfigPanel = new JPanel();
        tabConfigPanel.setLayout(new BoxLayout(tabConfigPanel, BoxLayout.Y_AXIS));
        tabConfigPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        for (int i = 0; i < 10; i++) {
            TabConfigRow row = new TabConfigRow(i);
            tabRows.add(row);
            tabConfigPanel.add(row);
            if (i < 9) {
                tabConfigPanel.add(Box.createVerticalStrut(3));
            }
        }

        JScrollPane configScroll = new JScrollPane(tabConfigPanel);
        configScroll.setPreferredSize(new Dimension(210, 350));
        configScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        configScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        configScroll.setBorder(null);

        // Color picker section
        JLabel colorLabel = new JLabel("Colors (click to add):");
        colorLabel.setForeground(Color.WHITE);
        colorLabel.setFont(FontManager.getRunescapeSmallFont());
        colorLabel.setBorder(new EmptyBorder(10, 0, 5, 0));

        colorPanel = new JPanel(new GridLayout(4, 8, 1, 1));
        colorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        colorPanel.setBorder(new EmptyBorder(0, 0, 5, 0));

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            JButton colorButton = createColorButton(PRESET_COLORS[i], COLOR_HEX[i]);
            colorPanel.add(colorButton);
        }

        // Icon grid section
        JLabel iconLabel = new JLabel("Icons (click to add):");
        iconLabel.setForeground(Color.WHITE);
        iconLabel.setFont(FontManager.getRunescapeSmallFont());
        iconLabel.setBorder(new EmptyBorder(5, 0, 5, 0));

        iconGridPanel = new JPanel(new GridLayout(0, 8, 1, 1));
        iconGridPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane iconScroll = new JScrollPane(iconGridPanel);
        iconScroll.setPreferredSize(new Dimension(210, 100));
        iconScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        iconScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        iconScroll.setBorder(null);

        // Presets section
        JLabel presetLabel = new JLabel("Presets:");
        presetLabel.setForeground(Color.WHITE);
        presetLabel.setFont(FontManager.getRunescapeSmallFont());
        presetLabel.setBorder(new EmptyBorder(10, 0, 5, 0));

        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JComboBox<String> presetCombo = new JComboBox<>();
        presetCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        presetCombo.setForeground(Color.WHITE);
        presetCombo.setFont(FontManager.getRunescapeSmallFont());
        presetCombo.addActionListener(e -> {
            String selected = (String) presetCombo.getSelectedItem();
            if (selected != null && !"Custom".equals(selected)) {
                applyPreset(selected);
            }
        });

        JButton saveButton = new JButton("Save");
        saveButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        saveButton.setForeground(Color.WHITE);
        saveButton.setFont(FontManager.getRunescapeSmallFont());
        saveButton.setPreferredSize(new Dimension(55, 20));
        saveButton.addActionListener(e -> showSavePresetDialog());

        JButton removeButton = new JButton("Remove");
        removeButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        removeButton.setForeground(Color.WHITE);
        removeButton.setFont(FontManager.getRunescapeSmallFont());
        removeButton.setPreferredSize(new Dimension(75, 20));
        removeButton.addActionListener(e -> showRemovePresetDialog());

        presetPanel.add(presetCombo);
        presetPanel.add(saveButton);
        presetPanel.add(removeButton);

        // Store reference for later updates
        this.presetCombo = presetCombo;
        updatePresetDropdown();

        mainPanel.add(configScroll);
        mainPanel.add(presetLabel);
        mainPanel.add(presetPanel);
        mainPanel.add(colorLabel);
        mainPanel.add(colorPanel);
        mainPanel.add(iconLabel);
        mainPanel.add(iconScroll);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        footerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footerPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        JButton githubButton = new JButton("GitHub");
        githubButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        githubButton.setForeground(Color.WHITE);
        githubButton.setFont(FontManager.getRunescapeSmallFont());
        githubButton.setPreferredSize(new Dimension(120, 20));
        githubButton.setToolTipText("Visit GitHub for support!");
        githubButton.addActionListener(e -> openURL("https://github.com/psyda/bank-tab-names"));

        footerPanel.add(githubButton);

        mainPanel.add(footerPanel);

        add(mainPanel, BorderLayout.CENTER);
    }

    private void openURL(String url) {
        LinkBrowser.browse(url);
    }

    private JButton createColorButton(Color color, String hex) {
        JButton button = new JButton();
        button.setBackground(color);
        button.setPreferredSize(new Dimension(16, 12));
        button.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        button.setToolTipText("Add color: #" + hex);

        button.addActionListener(e -> addColorToSelectedTab(hex));

        return button;
    }

    private void addColorToSelectedTab(String colorHex) {
        if (selectedTabIndex >= 0 && selectedTabIndex < tabRows.size()) {
            TabConfigRow row = tabRows.get(selectedTabIndex);
            int caretPos = row.nameField.getCaretPosition();
            String currentText = row.nameField.getText();
            String colorTag = "<col=" + colorHex + ">";
            String newText = currentText.substring(0, caretPos) + colorTag + currentText.substring(caretPos);
            row.nameField.setText(newText);
            row.nameField.setCaretPosition(caretPos + colorTag.length());
            row.nameField.requestFocus();
            row.updatePreview();
            saveTabConfig(selectedTabIndex);
        }
    }

    private void loadIcons() {
        if (imageExtractor == null) {
            return;
        }
        CompletableFuture<Void> extractionFuture = imageExtractor.extractAllImages();
        extractionFuture.thenRun(() -> {
            SwingUtilities.invokeLater(() -> {
                availableIconIds.clear();
                for (int i = 0; i <= 200; i++) {
                    availableIconIds.add(i);
                }
                populateIconGrid();
            });
        });
    }

    private void populateIconGrid() {
        iconGridPanel.removeAll();
        for (Integer iconId : availableIconIds) {
            JPanel iconContainer = createIconContainer(iconId);
            iconGridPanel.add(iconContainer);
        }
        iconGridPanel.revalidate();
        iconGridPanel.repaint();
    }

    private JPanel createIconContainer(int id) {
        JPanel container = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                BufferedImage image = imageExtractor.getIcon(id);
                if (image == null) {
                    image = IconSpriteExtractor.createPlaceholderImage(id, 16, 16);
                }
                int x = (getWidth() - image.getWidth()) / 2;
                int y = (getHeight() - image.getHeight()) / 2;
                g.drawImage(image, x, y, null);
            }
        };

        container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        container.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        container.setPreferredSize(new Dimension(20, 20));
        container.setToolTipText("ID: " + id);

        container.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                addIconToSelectedTab(id);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                container.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
                container.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                container.repaint();
            }
        });

        return container;
    }

    private void addIconToSelectedTab(int iconId) {
        if (selectedTabIndex >= 0 && selectedTabIndex < tabRows.size()) {
            TabConfigRow row = tabRows.get(selectedTabIndex);
            int caretPos = row.nameField.getCaretPosition();
            String currentText = row.nameField.getText();
            String iconTag = "<img=" + iconId + ">";
            String newText = currentText.substring(0, caretPos) + iconTag + currentText.substring(caretPos);
            row.nameField.setText(newText);
            row.nameField.setCaretPosition(caretPos + iconTag.length());
            row.nameField.requestFocus();
            row.updatePreview();
            saveTabConfig(selectedTabIndex);
        }
    }

    private void loadConfigValues() {
        if (configManager == null) {
            return;
        }
        for (int i = 0; i < tabRows.size(); i++) {
            TabConfigRow row = tabRows.get(i);

            String name = configManager.getConfiguration("BankTabNames", "tab" + i + "Name");
            if (name != null) {
                row.nameField.setText(name);
            }

            String fontName = configManager.getConfiguration("BankTabNames", "bankFont" + i);
            if (fontName != null) {
                try {
                    TabFonts font = TabFonts.valueOf(fontName);
                    row.fontCombo.setSelectedItem(font);
                } catch (Exception e) {
                    // Use default
                }
            }

            String disabledStr = configManager.getConfiguration("BankTabNames", "disableTab" + i);
            boolean disabled = Boolean.parseBoolean(disabledStr);
            row.enabledCheck.setSelected(!disabled);
        }
    }

    private void saveTabConfig(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabRows.size() || configManager == null) return;

        TabConfigRow row = tabRows.get(tabIndex);

        configManager.setConfiguration("BankTabNames", "tab" + tabIndex + "Name", row.nameField.getText());
        configManager.setConfiguration("BankTabNames", "bankFont" + tabIndex, row.fontCombo.getSelectedItem().toString());
        configManager.setConfiguration("BankTabNames", "disableTab" + tabIndex, String.valueOf(!row.enabledCheck.isSelected()));
    }

    private void showTutorial() {
        String tutorialText =
                "Bank Tab Names Tutorial\n\n" +
                        "You've clicked on a preview widget,"+
                        "so I figure you want some helpful tips:\n"+
                        "• Click on any tab row to select it (The smaller fields)\n" +
                        "• Type text directly into the text field\n" +
                        "• Press ENTER to add line breaks (<br>)\n" +
                        "• Click color buttons to add colors at cursor position\n" +
                        "• Click icons to add them at cursor position\n" +
                        "• Use the checkbox to enable/disable each tab\n" +
                        "• Preview shows how your tab should look\n\n" +
                        "• Preview doesn't show fonts, edit while in bank to view live\n\n" +
                        "Advanced:\n" +
                        "• Use </col> to end color formatting\n" +
                        "• Multiple colors and icons can be combined\n" +
                        "• Changes are saved automatically!\n\n" +
                        "I'm Psyda, Ingame I am 'd1x' or 'Iron d1x', come say hi!";

        JOptionPane.showMessageDialog(
                this,
                tutorialText,
                "How to Use Bank Tab Names",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private class TabConfigRow extends JPanel {
        JTextField nameField;
        JComboBox<TabFonts> fontCombo;
        JCheckBox enabledCheck;
        JPanel previewPanel;
        int tabIndex;

        TabConfigRow(int index) {
            this.tabIndex = index;
            setLayout(new BorderLayout(2, 1));
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setMaximumSize(new Dimension(210, 80));
            setPreferredSize(new Dimension(210, 80));

            // Font selector row
            JPanel fontPanel = new JPanel(new BorderLayout(2, 0));
            fontPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            fontPanel.setPreferredSize(new Dimension(210, 18));
            fontPanel.setMaximumSize(new Dimension(210, 18));

            JLabel fontLabel = new JLabel("Tab " + index + " Font:");
            fontLabel.setForeground(Color.WHITE);
            fontLabel.setFont(FontManager.getRunescapeSmallFont());
            fontLabel.setPreferredSize(new Dimension(60, 16));

            fontCombo = new JComboBox<>(TabFonts.values());
            fontCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            fontCombo.setForeground(Color.WHITE);
            fontCombo.setFont(FontManager.getRunescapeSmallFont());
            fontCombo.setPreferredSize(new Dimension(100, 16));
            fontCombo.addActionListener(e -> {
                saveTabConfig(tabIndex);
                updatePreview();
            });

            enabledCheck = new JCheckBox();
            enabledCheck.setSelected(true);
            enabledCheck.setBackground(ColorScheme.DARK_GRAY_COLOR);
            enabledCheck.setForeground(Color.WHITE);
            enabledCheck.setFont(FontManager.getRunescapeSmallFont());
            enabledCheck.setPreferredSize(new Dimension(20, 16));
            enabledCheck.setToolTipText("Enable Custom Tab Icon/Text?");
            enabledCheck.addActionListener(e -> saveTabConfig(tabIndex));

            fontPanel.add(fontLabel, BorderLayout.WEST);
            fontPanel.add(fontCombo, BorderLayout.CENTER);
            fontPanel.add(enabledCheck, BorderLayout.EAST);

            // Text field row
            JPanel textPanel = new JPanel(new BorderLayout(2, 0));
            textPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            textPanel.setPreferredSize(new Dimension(210, 20));
            textPanel.setMaximumSize(new Dimension(210, 20));

            JLabel tabLabel = new JLabel("" + index + ":");
            tabLabel.setForeground(Color.WHITE);
            tabLabel.setFont(FontManager.getRunescapeSmallFont());
            tabLabel.setPreferredSize(new Dimension(15, 20));
            tabLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedTabIndex = tabIndex;
                    updateSelection();
                }
            });

            nameField = new JTextField();
            nameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            nameField.setForeground(Color.WHITE);
            nameField.setFont(FontManager.getRunescapeSmallFont());
            nameField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            nameField.setPreferredSize(new Dimension(190, 20));

            // Handle Enter key to insert <br>
            nameField.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                        e.consume();
                        int caretPos = nameField.getCaretPosition();
                        String text = nameField.getText();
                        String newText = text.substring(0, caretPos) + "<br>" + text.substring(caretPos);
                        nameField.setText(newText);
                        nameField.setCaretPosition(caretPos + 4);
                        updatePreview();
                    }
                }

                @Override
                public void keyReleased(java.awt.event.KeyEvent e) {
                    updatePreview();
                }
            });

            nameField.addFocusListener(new java.awt.event.FocusListener() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    selectedTabIndex = tabIndex;
                    updateSelection();
                }
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    saveTabConfig(tabIndex);
                    updatePreview();
                }
            });

            textPanel.add(tabLabel, BorderLayout.WEST);
            textPanel.add(nameField, BorderLayout.CENTER);

            // Preview panel (3 lines high for <br> handling)
            previewPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    renderPreview(g);
                }
            };
            previewPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            previewPanel.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
            previewPanel.setPreferredSize(new Dimension(210, 42)); // 3 lines * 14px line height
            previewPanel.setToolTipText("Click for tutorial");
            previewPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showTutorial();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    previewPanel.setBorder(BorderFactory.createLineBorder(Color.YELLOW));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    previewPanel.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
                }
            });

            add(fontPanel, BorderLayout.NORTH);
            add(textPanel, BorderLayout.CENTER);
            add(previewPanel, BorderLayout.SOUTH);

            setBorder(selectedTabIndex == tabIndex ?
                    BorderFactory.createLineBorder(Color.YELLOW, 1) :
                    BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1));
        }

        private void updatePreview() {
            if (previewPanel != null) {
                previewPanel.repaint();
            }
        }

        private void renderPreview(Graphics g) {
            String text = nameField.getText();
            if (text == null || text.isEmpty()) {
                return;
            }

            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Handle <br> tags for line breaks
            String[] lines = text.split("<br>");
            int y = 12;

            for (String line : lines) {
                if (y > previewPanel.getHeight()) break;

                int x = 4;
                Color currentColor = Color.WHITE;
                g2d.setColor(currentColor);

                // Parse line for color and image tags
                Pattern pattern = Pattern.compile("(<col=([A-Fa-f0-9]{6})>|</col>|<img=(\\d+)>)");
                Matcher matcher = pattern.matcher(line);

                int lastEnd = 0;

                while (matcher.find()) {
                    // Draw text before tag
                    String beforeTag = line.substring(lastEnd, matcher.start());
                    if (!beforeTag.isEmpty()) {
                        g2d.setColor(currentColor);
                        g2d.drawString(beforeTag, x, y);
                        x += g2d.getFontMetrics().stringWidth(beforeTag);
                    }

                    String fullMatch = matcher.group(1);

                    if (fullMatch.startsWith("<col=")) {
                        // Color tag
                        String colorHex = matcher.group(2);
                        try {
                            currentColor = Color.decode("#" + colorHex);
                        } catch (NumberFormatException e) {
                            currentColor = Color.WHITE;
                        }
                    } else if (fullMatch.equals("</col>")) {
                        // End color tag
                        currentColor = Color.WHITE;
                    } else if (fullMatch.startsWith("<img=")) {
                        // Image tag
                        try {
                            int iconId = Integer.parseInt(matcher.group(3));
                            BufferedImage icon = imageExtractor != null ? imageExtractor.getIcon(iconId) : null;
                            if (icon != null) {
                                g2d.drawImage(icon, x, y - icon.getHeight() + 2, null);
                                x += icon.getWidth() + 2;
                            } else {
                                String fallback = "[" + iconId + "]";
                                g2d.setColor(Color.CYAN);
                                g2d.drawString(fallback, x, y);
                                x += g2d.getFontMetrics().stringWidth(fallback);
                                g2d.setColor(currentColor);
                            }
                        } catch (NumberFormatException e) {
                            g2d.setColor(currentColor);
                            g2d.drawString(fullMatch, x, y);
                            x += g2d.getFontMetrics().stringWidth(fullMatch);
                        }
                    }

                    lastEnd = matcher.end();
                }

                // Draw remaining text
                if (lastEnd < line.length()) {
                    String remaining = line.substring(lastEnd);
                    g2d.setColor(currentColor);
                    g2d.drawString(remaining, x, y);
                }

                y += 14; // Line height
            }
        }

        void updateSelection() {
            for (TabConfigRow row : tabRows) {
                row.setBorder(selectedTabIndex == row.tabIndex ?
                        BorderFactory.createLineBorder(Color.YELLOW, 1) :
                        BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1));
                row.repaint();
            }
        }
    }

    public void updateAllSelections() {
        if (tabRows != null && !tabRows.isEmpty()) {
            tabRows.get(0).updateSelection();
        }
    }

    public void updateAllPreviews() {
        if (tabRows != null) {
            for (TabConfigRow row : tabRows) {
                row.updatePreview();
            }
        }
    }

    private void updatePresetDropdown() {
        if (presetCombo == null) return;

        presetCombo.removeAllItems();
        presetCombo.addItem("Custom");

        // Add built-in presets
        for (String preset : PRESET_NAMES) {
            presetCombo.addItem(preset);
        }

        // Add custom presets
        if (configManager != null) {
            String savedPresets = configManager.getConfiguration("BankTabNames", "savedPresets");
            if (savedPresets != null && !savedPresets.isEmpty()) {
                String[] presets = savedPresets.split(",");
                for (String preset : presets) {
                    if (!preset.trim().isEmpty()) {
                        presetCombo.addItem(preset.trim());
                    }
                }
            }
        }
    }

    private void showSavePresetDialog() {
        JPanel dialogPanel = new JPanel(new BorderLayout(5, 5));

        // Text field for preset name
        JTextField nameField = new JTextField(15);
        nameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameField.setForeground(Color.WHITE);
        nameField.setFont(FontManager.getRunescapeSmallFont());

        // Dropdown of existing presets
        JComboBox<String> existingPresetsCombo = new JComboBox<>();
        existingPresetsCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        existingPresetsCombo.setForeground(Color.WHITE);
        existingPresetsCombo.setFont(FontManager.getRunescapeSmallFont());

        // Populate with existing custom presets
        updateExistingPresetsDropdown(existingPresetsCombo);

        // Add action listener to populate name field when preset is selected
        existingPresetsCombo.addActionListener(e -> {
            String selected = (String) existingPresetsCombo.getSelectedItem();
            if (selected != null && !"-- Select to overwrite --".equals(selected)) {
                nameField.setText(selected);
            }
        });

        JLabel nameLabel = new JLabel("Choose a Preset name:");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel existingLabel = new JLabel("Existing presets:");
        existingLabel.setForeground(Color.WHITE);
        existingLabel.setFont(FontManager.getRunescapeSmallFont());

        dialogPanel.add(nameLabel, BorderLayout.NORTH);
        dialogPanel.add(nameField, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.add(existingLabel, BorderLayout.NORTH);
        bottomPanel.add(existingPresetsCombo, BorderLayout.CENTER);

        dialogPanel.add(bottomPanel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(
                this,
                dialogPanel,
                "Save Preset",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String presetName = nameField.getText().trim();
            if (!presetName.isEmpty()) {
                saveCustomPreset(presetName);
            }
        }
    }

    private void showRemovePresetDialog() {
        // Get list of custom presets
        List<String> customPresets = new ArrayList<>();
        if (configManager != null) {
            String savedPresets = configManager.getConfiguration("BankTabNames", "savedPresets");
            if (savedPresets != null && !savedPresets.isEmpty()) {
                String[] presets = savedPresets.split(",");
                for (String preset : presets) {
                    if (!preset.trim().isEmpty()) {
                        customPresets.add(preset.trim());
                    }
                }
            }
        }

        // Check if there are any custom presets to remove
        if (customPresets.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "No custom presets found to remove.\n\nBuilt-in presets cannot be removed.",
                    "No Custom Presets",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // Create dialog panel
        JPanel dialogPanel = new JPanel(new BorderLayout(5, 5));

        JLabel instructionLabel = new JLabel("Select a preset to remove:");
        instructionLabel.setForeground(Color.WHITE);
        instructionLabel.setFont(FontManager.getRunescapeSmallFont());

        JComboBox<String> presetSelectCombo = new JComboBox<>();
        presetSelectCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        presetSelectCombo.setForeground(Color.WHITE);
        presetSelectCombo.setFont(FontManager.getRunescapeSmallFont());

        // Populate with custom presets only
        presetSelectCombo.addItem("-- Select preset to remove --");
        for (String preset : customPresets) {
            presetSelectCombo.addItem(preset);
        }

        JLabel warningLabel = new JLabel("<html><font color='red'>Warning: This action cannot be undone!</font></html>");
        warningLabel.setFont(FontManager.getRunescapeSmallFont());

        dialogPanel.add(instructionLabel, BorderLayout.NORTH);
        dialogPanel.add(presetSelectCombo, BorderLayout.CENTER);
        dialogPanel.add(warningLabel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(
                this,
                dialogPanel,
                "Remove Preset",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String selectedPreset = (String) presetSelectCombo.getSelectedItem();
            if (selectedPreset != null && !"-- Select preset to remove --".equals(selectedPreset)) {
                // Final confirmation
                int confirmResult = JOptionPane.showConfirmDialog(
                        this,
                        "Are you sure you want to remove the preset:\n\"" + selectedPreset + "\"?\n\nThis action cannot be undone.",
                        "Confirm Removal",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (confirmResult == JOptionPane.YES_OPTION) {
                    removeCustomPreset(selectedPreset);
                }
            }
        }
    }

    private void removeCustomPreset(String presetName) {
        if (configManager == null) return;

        // Remove all configuration entries for this preset
        for (int i = 0; i < 10; i++) {
            configManager.unsetConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Name");
            configManager.unsetConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Font");
            configManager.unsetConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Enabled");
        }

        // Remove from saved presets list
        String savedPresets = configManager.getConfiguration("BankTabNames", "savedPresets");
        if (savedPresets != null) {
            List<String> presetList = new ArrayList<>();
            String[] presets = savedPresets.split(",");
            for (String preset : presets) {
                if (!preset.trim().isEmpty() && !preset.trim().equals(presetName)) {
                    presetList.add(preset.trim());
                }
            }

            // Update the saved presets string
            if (presetList.isEmpty()) {
                configManager.unsetConfiguration("BankTabNames", "savedPresets");
            } else {
                String newSavedPresets = String.join(",", presetList);
                configManager.setConfiguration("BankTabNames", "savedPresets", newSavedPresets);
            }
        }

        // Update dropdown
        updatePresetDropdown();

        // Show confirmation
        JOptionPane.showMessageDialog(
                this,
                "Preset \"" + presetName + "\" has been removed successfully.",
                "Preset Removed",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void updateExistingPresetsDropdown(JComboBox<String> dropdown) {
        dropdown.removeAllItems();
        dropdown.addItem("-- Select to overwrite --");

        // Add custom presets only (built-in presets can't be overwritten)
        if (configManager != null) {
            String savedPresets = configManager.getConfiguration("BankTabNames", "savedPresets");
            if (savedPresets != null && !savedPresets.isEmpty()) {
                String[] presets = savedPresets.split(",");
                for (String preset : presets) {
                    if (!preset.trim().isEmpty()) {
                        dropdown.addItem(preset.trim());
                    }
                }
            }
        }
    }

    private void saveCustomPreset(String presetName) {
        if (configManager == null) return;

        // Save current configuration as custom preset
        for (int i = 0; i < tabRows.size(); i++) {
            TabConfigRow row = tabRows.get(i);

            configManager.setConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Name", row.nameField.getText());
            configManager.setConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Font", row.fontCombo.getSelectedItem().toString());
            configManager.setConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Enabled", String.valueOf(row.enabledCheck.isSelected()));
        }

        // Add to saved presets list
        String savedPresets = configManager.getConfiguration("BankTabNames", "savedPresets");
        if (savedPresets == null) {
            savedPresets = presetName;
        } else if (!savedPresets.contains(presetName)) {
            savedPresets += "," + presetName;
        }
        configManager.setConfiguration("BankTabNames", "savedPresets", savedPresets);

        // Update dropdown
        updatePresetDropdown();

        // Show confirmation
        JOptionPane.showMessageDialog(
                this,
                "Saved preset: " + presetName + "\n\nYour current configuration has been saved.",
                "Preset Saved",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void applyPreset(String presetName) {
        // Check if it's a built-in preset
        int presetIndex = -1;
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            if (PRESET_NAMES[i].equals(presetName)) {
                presetIndex = i;
                break;
            }
        }

        if (presetIndex != -1) {
            // Apply built-in preset
            PresetConfig[] preset = PRESETS[presetIndex];
            applyPresetConfig(preset, presetName);
        } else {
            // Apply custom preset
            applyCustomPreset(presetName);
        }
    }

    private void applyPresetConfig(PresetConfig[] preset, String presetName) {
        // Apply preset to all tabs
        for (int i = 0; i < Math.min(preset.length, tabRows.size()); i++) {
            TabConfigRow row = tabRows.get(i);
            PresetConfig config = preset[i];

            // Apply configuration
            row.nameField.setText(config.name);
            row.fontCombo.setSelectedItem(config.font);
            row.enabledCheck.setSelected(config.enabled);

            // Save to config
            saveTabConfig(i);

            // Update preview
            row.updatePreview();
        }

        // Show confirmation
        JOptionPane.showMessageDialog(
                this,
                "Applied " + presetName + " preset!\n\nYour bank tab configuration has been updated.",
                "Preset Applied",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void applyCustomPreset(String presetName) {
        if (configManager == null) return;

        // Load custom preset from config
        for (int i = 0; i < 10; i++) {
            String name = configManager.getConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Name");
            String font = configManager.getConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Font");
            String enabled = configManager.getConfiguration("BankTabNames", "customPreset_" + presetName + "_tab" + i + "Enabled");

            if (name != null && font != null && enabled != null) {
                TabConfigRow row = tabRows.get(i);
                row.nameField.setText(name);

                try {
                    row.fontCombo.setSelectedItem(TabFonts.valueOf(font));
                } catch (Exception e) {
                    // Use default font if invalid
                }

                row.enabledCheck.setSelected(Boolean.parseBoolean(enabled));

                // Save to main config
                saveTabConfig(i);

                // Update preview
                row.updatePreview();
            }
        }

        // Show confirmation
        JOptionPane.showMessageDialog(
                this,
                "Applied " + presetName + " preset!\n\nYour bank tab configuration has been updated.",
                "Preset Applied",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}