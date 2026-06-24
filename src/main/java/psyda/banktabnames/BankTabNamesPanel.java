package psyda.banktabnames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.RuneLite;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@Singleton
public class BankTabNamesPanel extends PluginPanel
{
	private BankTabNamesPlugin plugin;
	private int selectedTabIndex = 0;
	private int iconPickerTargetTab = -1;

	private final List<TabRow> tabRows = new ArrayList<>();
	private JPanel tabConfigPanel;
	private JPanel iconPickerPanel;
	private JPanel skillPickerPanel;
	private JPanel spritePickerPanel;
	private JPanel iconEditorPanel;
	private JPanel colorPanel;
	private JComboBox<String> configPresetCombo;

	/**
	 * Config key under which saved presets are stored as a JSON map of
	 * {name -> {tab_0: TabConfig, tab_1: TabConfig, ...}}.
	 */
	private static final String SAVED_CONFIGS_KEY = "saved_configs";

	/**
	 * Directory for file-based config import/export, under the plugin's own
	 * folder in .runelite. All config file I/O is confined here.
	 */
	private static final File CONFIG_DIR = new File(RuneLite.RUNELITE_DIR, "banktabnames/configs");

	/**
	 * Guard flag to suppress listener-triggered saves while programmatically
	 * loading config values into UI controls. Without this, setText/setSelected
	 * on Swing components fires their change listeners, which call saveToConfig,
	 * which does a full load-save cycle that clobbers concurrent changes from
	 * other sources (chatbox edits, icon adds, etc.).
	 */
	private boolean loading;

	private static final String[] COLOR_HEX = {
			"FF0000", "00FFFF", "0000FF", "FFFF00", "00FF00", "FF00FF", "FF6600", "0088FF",
			"FFA8BE", "B3FFFF", "A8C2FF", "FFFFB3", "B3FFB3", "FFB3FF", "FFD9B3", "B3D9FF",
			"FE654F", "28AFB0", "4A64A1", "FFE74C", "B9F18C", "A64BA6", "FE9116", "4B75A6",
			"FF5964", "005959", "000059", "595900", "005900", "590059", "592D00", "002D59",
			"FFFFFF", "D9D9D9", "B3B3B3", "808080", "4D4D4D", "262626", "121212", "000000"
	};

	@Inject
	public BankTabNamesPanel()
	{
		super(false);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	public void init(BankTabNamesPlugin plugin)
	{
		this.plugin = plugin;
		this.configGson = plugin.getGson().newBuilder().setPrettyPrinting().create();
		tabRows.clear();
		removeAll();
		setLayout(new BorderLayout());

		JLabel title = new JLabel("Bank Tab Names");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
		add(title, BorderLayout.NORTH);

		JPanel main = new JPanel();
		main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
		main.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Tab config rows
		tabConfigPanel = new JPanel();
		tabConfigPanel.setLayout(new BoxLayout(tabConfigPanel, BoxLayout.Y_AXIS));
		tabConfigPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (int i = 0; i < 10; i++)
		{
			TabRow row = new TabRow(i);
			tabRows.add(row);
			tabConfigPanel.add(row);
			tabConfigPanel.add(Box.createVerticalStrut(2));
		}

		JScrollPane configScroll = new JScrollPane(tabConfigPanel);
		configScroll.setPreferredSize(new Dimension(210, 300));
		configScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		configScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		configScroll.setBorder(null);
		configScroll.setAlignmentX(LEFT_ALIGNMENT);

		// Color buttons
		JLabel colorLabel = new JLabel("Colors (click to insert):");
		colorLabel.setForeground(Color.WHITE);
		colorLabel.setFont(FontManager.getRunescapeSmallFont());
		colorLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 3, 0));
		colorLabel.setAlignmentX(LEFT_ALIGNMENT);

		colorPanel = new JPanel(new GridLayout(5, 8, 1, 1));
		colorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Constrain color panel so buttons stay square and small in fullscreen
		colorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		colorPanel.setAlignmentX(LEFT_ALIGNMENT);
		for (int i = 0; i < COLOR_HEX.length; i++)
		{
			final String hex = COLOR_HEX[i];
			JButton btn = new JButton();
			btn.setBackground(Color.decode("#" + hex));
			btn.setPreferredSize(new Dimension(16, 16));
			btn.setMinimumSize(new Dimension(16, 16));
			btn.setMaximumSize(new Dimension(24, 24));
			btn.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
			btn.setToolTipText("#" + hex);
			btn.addActionListener(e -> insertColorTag(hex));
			colorPanel.add(btn);
		}

		// Reload icons button
		JPanel reloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		reloadPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		reloadPanel.setAlignmentX(LEFT_ALIGNMENT);

		JButton reloadBtn = new JButton("Reload icons");
		reloadBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		reloadBtn.setForeground(Color.WHITE);
		reloadBtn.setFont(FontManager.getRunescapeSmallFont());
		reloadBtn.setToolTipText("Rescan bundled + user icon folders");
		reloadBtn.addActionListener(e -> plugin.reloadCustomIcons());

		JButton openFolderBtn = new JButton("Open folder");
		openFolderBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		openFolderBtn.setForeground(Color.WHITE);
		openFolderBtn.setFont(FontManager.getRunescapeSmallFont());
		openFolderBtn.setToolTipText("Open the user icons folder on disk");
		openFolderBtn.addActionListener(e -> openUserIconsFolder());

		reloadPanel.add(reloadBtn);
		reloadPanel.add(openFolderBtn);

		// Config presets: dropdown, load, save, delete, import/export
		JPanel configPresetPanel = new JPanel();
		configPresetPanel.setLayout(new BoxLayout(configPresetPanel, BoxLayout.Y_AXIS));
		configPresetPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		configPresetPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		configPresetPanel.setAlignmentX(LEFT_ALIGNMENT);

		JLabel presetLabel = new JLabel("Saved Configs:");
		presetLabel.setForeground(Color.WHITE);
		presetLabel.setFont(FontManager.getRunescapeSmallFont());
		presetLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
		presetLabel.setAlignmentX(LEFT_ALIGNMENT);
		configPresetPanel.add(presetLabel);

		configPresetCombo = new JComboBox<>();
		configPresetCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		configPresetCombo.setForeground(Color.WHITE);
		configPresetCombo.setFont(FontManager.getRunescapeSmallFont());
		configPresetCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		configPresetCombo.setAlignmentX(LEFT_ALIGNMENT);
		configPresetPanel.add(configPresetCombo);
		configPresetPanel.add(Box.createVerticalStrut(2));

		JPanel presetBtnRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		presetBtnRow1.setBackground(ColorScheme.DARK_GRAY_COLOR);
		presetBtnRow1.setAlignmentX(LEFT_ALIGNMENT);
		presetBtnRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JButton loadPresetBtn = new JButton("Load");
		loadPresetBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		loadPresetBtn.setForeground(Color.WHITE);
		loadPresetBtn.setFont(FontManager.getRunescapeSmallFont());
		loadPresetBtn.setToolTipText("Load the selected config preset");
		loadPresetBtn.addActionListener(e -> loadSelectedPreset());

		JButton savePresetBtn = new JButton("Save");
		savePresetBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		savePresetBtn.setForeground(Color.WHITE);
		savePresetBtn.setFont(FontManager.getRunescapeSmallFont());
		savePresetBtn.setToolTipText("Save current tabs as a new preset");
		savePresetBtn.addActionListener(e -> saveCurrentAsPreset());

		JButton deletePresetBtn = new JButton("Delete");
		deletePresetBtn.setBackground(new Color(139, 0, 0));
		deletePresetBtn.setForeground(Color.WHITE);
		deletePresetBtn.setFont(FontManager.getRunescapeSmallFont());
		deletePresetBtn.setToolTipText("Delete the selected preset");
		deletePresetBtn.addActionListener(e -> deleteSelectedPreset());

		presetBtnRow1.add(loadPresetBtn);
		presetBtnRow1.add(savePresetBtn);
		presetBtnRow1.add(deletePresetBtn);
		configPresetPanel.add(presetBtnRow1);

		JPanel presetBtnRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		presetBtnRow2.setBackground(ColorScheme.DARK_GRAY_COLOR);
		presetBtnRow2.setAlignmentX(LEFT_ALIGNMENT);
		presetBtnRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JButton exportBtn = new JButton("Export");
		exportBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		exportBtn.setForeground(Color.WHITE);
		exportBtn.setFont(FontManager.getRunescapeSmallFont());
		exportBtn.setToolTipText("Export current tabs to clipboard or file");
		exportBtn.addActionListener(e -> exportConfig());

		JButton importBtn = new JButton("Import");
		importBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		importBtn.setForeground(Color.WHITE);
		importBtn.setFont(FontManager.getRunescapeSmallFont());
		importBtn.setToolTipText("Import a config from clipboard or file");
		importBtn.addActionListener(e -> importConfig());

		presetBtnRow2.add(exportBtn);
		presetBtnRow2.add(importBtn);
		configPresetPanel.add(presetBtnRow2);

		// Footer with GitHub link
		JPanel footerPanel = new JPanel();
		footerPanel.setLayout(new BoxLayout(footerPanel, BoxLayout.Y_AXIS));
		footerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		footerPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		footerPanel.setAlignmentX(LEFT_ALIGNMENT);

		JButton githubButton = new JButton("GitHub");
		githubButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		githubButton.setForeground(Color.WHITE);
		githubButton.setFont(FontManager.getRunescapeSmallFont());
		githubButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		githubButton.setAlignmentX(CENTER_ALIGNMENT);
		githubButton.setToolTipText("Visit GitHub for support!");
		githubButton.addActionListener(e -> LinkBrowser.browse("https://github.com/psyda/bank-tab-names"));

		JButton sponsorButton = new JButton("\u2764 Sponsor");
		sponsorButton.setBackground(new Color(0x5C2D91));
		sponsorButton.setForeground(Color.WHITE);
		sponsorButton.setFont(FontManager.getRunescapeSmallFont());
		sponsorButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		sponsorButton.setAlignmentX(CENTER_ALIGNMENT);
		sponsorButton.setToolTipText("Support the developer!");
		sponsorButton.addActionListener(e -> showSponsorDialog());

		footerPanel.add(githubButton);
		footerPanel.add(Box.createVerticalStrut(2));
		footerPanel.add(sponsorButton);

		// Custom icon picker (hidden by default)
		iconPickerPanel = new JPanel();
		iconPickerPanel.setLayout(new BoxLayout(iconPickerPanel, BoxLayout.Y_AXIS));
		iconPickerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		iconPickerPanel.setVisible(false);
		iconPickerPanel.setAlignmentX(LEFT_ALIGNMENT);

		// Skill icon picker (hidden by default)
		skillPickerPanel = new JPanel();
		skillPickerPanel.setLayout(new BoxLayout(skillPickerPanel, BoxLayout.Y_AXIS));
		skillPickerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		skillPickerPanel.setVisible(false);
		skillPickerPanel.setAlignmentX(LEFT_ALIGNMENT);

		// Game sprite picker (hidden by default)
		spritePickerPanel = new JPanel();
		spritePickerPanel.setLayout(new BoxLayout(spritePickerPanel, BoxLayout.Y_AXIS));
		spritePickerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		spritePickerPanel.setVisible(false);
		spritePickerPanel.setAlignmentX(LEFT_ALIGNMENT);

		// Icon editor (hidden by default, replaces old resizer)
		iconEditorPanel = new JPanel();
		iconEditorPanel.setLayout(new BoxLayout(iconEditorPanel, BoxLayout.Y_AXIS));
		iconEditorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		iconEditorPanel.setVisible(false);
		iconEditorPanel.setAlignmentX(LEFT_ALIGNMENT);

		main.add(configScroll);
		main.add(colorLabel);
		main.add(colorPanel);
		main.add(reloadPanel);
		main.add(configPresetPanel);
		main.add(iconPickerPanel);
		main.add(skillPickerPanel);
		main.add(spritePickerPanel);
		main.add(iconEditorPanel);
		main.add(footerPanel);

		add(main, BorderLayout.CENTER);

		loadConfigValues();
		refreshPresetCombo();
		revalidate();
	}

	private void openUserIconsFolder()
	{
		File dir = plugin.getCustomIconManager().getUserIconsDir();
		if (dir != null && dir.exists())
		{
			LinkBrowser.open(dir.toString());
		}
	}

	/**
	 * Opens the config import/export folder on disk, creating it first so the
	 * button always lands on a real directory.
	 */
	private void openConfigFolder()
	{
		if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs())
		{
			log.warn("Failed to create config dir: {}", CONFIG_DIR.getAbsolutePath());
			return;
		}
		LinkBrowser.open(CONFIG_DIR.toString());
	}

	// -----------------------------------------------------------------------
	// Close all pickers/editors (used after icon reload to force rebuild)
	// -----------------------------------------------------------------------

	public void closeAllPickers()
	{
		iconPickerPanel.setVisible(false);
		skillPickerPanel.setVisible(false);
		spritePickerPanel.setVisible(false);
		iconEditorPanel.setVisible(false);
		iconPickerTargetTab = -1;
		revalidate();
	}

	// -----------------------------------------------------------------------
	// Custom Icon Picker
	// -----------------------------------------------------------------------

	public void openIconPicker(int tabIndex)
	{
		skillPickerPanel.setVisible(false);
		spritePickerPanel.setVisible(false);
		iconEditorPanel.setVisible(false);

		iconPickerTargetTab = tabIndex;
		iconPickerPanel.removeAll();

		JLabel pickerTitle = new JLabel("Custom Icons - Tab " + tabIndex + " (click to add):");
		pickerTitle.setForeground(Color.WHITE);
		pickerTitle.setFont(FontManager.getRunescapeSmallFont());
		pickerTitle.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
		iconPickerPanel.add(pickerTitle);

		JButton closeBtn = new JButton("Close picker");
		closeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		closeBtn.setForeground(Color.WHITE);
		closeBtn.setFont(FontManager.getRunescapeSmallFont());
		closeBtn.addActionListener(e ->
		{
			iconPickerPanel.setVisible(false);
			iconPickerTargetTab = -1;
			revalidate();
		});

		CustomIconManager mgr = plugin.getCustomIconManager();
		if (mgr.getIconNames().isEmpty())
		{
			JLabel empty = new JLabel("No icons found. Add PNGs to the icons folder.");
			empty.setForeground(Color.GRAY);
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 0));
			iconPickerPanel.add(empty);
		}
		else
		{
			JPanel grid = new JPanel(new GridLayout(0, 5, 3, 3));
			grid.setBackground(ColorScheme.DARK_GRAY_COLOR);

			for (String fileName : mgr.getIconNames())
			{
				BufferedImage img = mgr.getIcon(fileName);
				BufferedImage thumb = CustomIconManager.scaleToFit(img, 32, 32);
				String displayName = CustomIconManager.getDisplayName(fileName);

				JPanel cell = new JPanel()
				{
					@Override
					protected void paintComponent(Graphics g)
					{
						super.paintComponent(g);
						if (thumb != null)
						{
							int x = (getWidth() - thumb.getWidth()) / 2;
							int y = (getHeight() - thumb.getHeight()) / 2;
							g.drawImage(thumb, x, y, null);
						}
					}
				};
				cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				cell.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
				cell.setPreferredSize(new Dimension(36, 36));
				cell.setToolTipText(displayName + (mgr.isUserIcon(fileName) ? " (user)" : ""));
				cell.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						if (iconPickerTargetTab >= 0)
						{
							int targetTab = iconPickerTargetTab;
							plugin.setCustomIcon(iconPickerTargetTab, fileName);
							iconPickerPanel.setVisible(false);
							iconPickerTargetTab = -1;
							refreshFromConfig();
							openIconEditor(targetTab);
							revalidate();
						}
					}

					@Override
					public void mouseEntered(MouseEvent e)
					{
						cell.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
						cell.repaint();
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
						cell.repaint();
					}
				});

				grid.add(cell);
			}

			JScrollPane iconScroll = new JScrollPane(grid);
			iconScroll.setPreferredSize(new Dimension(210, 120));
			iconScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
			iconScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
			iconScroll.setBorder(null);
			iconPickerPanel.add(iconScroll);
		}

		iconPickerPanel.add(closeBtn);
		iconPickerPanel.setVisible(true);
		revalidate();
	}

	// -----------------------------------------------------------------------
	// Skill Icon Picker
	// -----------------------------------------------------------------------

	public void openSkillIconPicker(int tabIndex)
	{
		iconPickerPanel.setVisible(false);
		spritePickerPanel.setVisible(false);
		iconEditorPanel.setVisible(false);

		iconPickerTargetTab = tabIndex;
		skillPickerPanel.removeAll();

		JLabel pickerTitle = new JLabel("Skill Icons - Tab " + tabIndex + " (click to add):");
		pickerTitle.setForeground(Color.WHITE);
		pickerTitle.setFont(FontManager.getRunescapeSmallFont());
		pickerTitle.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
		skillPickerPanel.add(pickerTitle);

		JPanel grid = new JPanel(new GridLayout(0, 5, 3, 3));
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);

		SkillIconProvider provider = plugin.getSkillIconProvider();

		for (Skill skill : Skill.values())
		{
			if (skill.getName().equals("Overall"))
			{
				continue;
			}

			BufferedImage img = provider.getSkillIcon(skill, true);
			BufferedImage thumb = img != null ? SkillIconProvider.scaleToFit(img, 28, 28) : null;

			JPanel cell = new JPanel()
			{
				@Override
				protected void paintComponent(Graphics g)
				{
					super.paintComponent(g);
					if (thumb != null)
					{
						int x = (getWidth() - thumb.getWidth()) / 2;
						int y = (getHeight() - thumb.getHeight()) / 2;
						g.drawImage(thumb, x, y, null);
					}
				}
			};
			cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			cell.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
			cell.setPreferredSize(new Dimension(36, 36));
			cell.setToolTipText(skill.getName());

			final Skill selectedSkill = skill;
			cell.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (iconPickerTargetTab >= 0)
					{
						int targetTab = iconPickerTargetTab;
						plugin.addSkillIcon(iconPickerTargetTab, selectedSkill);
						skillPickerPanel.setVisible(false);
						iconPickerTargetTab = -1;
						refreshFromConfig();
						openIconEditor(targetTab);
						revalidate();
					}
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					cell.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
					cell.repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					cell.repaint();
				}
			});

			grid.add(cell);
		}

		JScrollPane skillScroll = new JScrollPane(grid);
		skillScroll.setPreferredSize(new Dimension(210, 160));
		skillScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		skillScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		skillScroll.setBorder(null);
		skillPickerPanel.add(skillScroll);

		JButton closeBtn = new JButton("Close picker");
		closeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		closeBtn.setForeground(Color.WHITE);
		closeBtn.setFont(FontManager.getRunescapeSmallFont());
		closeBtn.addActionListener(e ->
		{
			skillPickerPanel.setVisible(false);
			iconPickerTargetTab = -1;
			revalidate();
		});
		skillPickerPanel.add(closeBtn);

		skillPickerPanel.setVisible(true);
		revalidate();
	}

	// -----------------------------------------------------------------------
	// Game Sprite Picker
	// -----------------------------------------------------------------------

	public void openSpritePicker(int tabIndex)
	{
		iconPickerPanel.setVisible(false);
		skillPickerPanel.setVisible(false);
		iconEditorPanel.setVisible(false);

		iconPickerTargetTab = tabIndex;
		spritePickerPanel.removeAll();

		JLabel pickerTitle = new JLabel("Game Sprites - Tab " + tabIndex + ":");
		pickerTitle.setForeground(Color.WHITE);
		pickerTitle.setFont(FontManager.getRunescapeSmallFont());
		pickerTitle.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
		spritePickerPanel.add(pickerTitle);

		// Category selector dropdown
		JComboBox<GameSpriteCategory> categoryCombo = new JComboBox<>(GameSpriteCategory.values());
		categoryCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		categoryCombo.setForeground(Color.WHITE);
		categoryCombo.setFont(FontManager.getRunescapeSmallFont());
		categoryCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		categoryCombo.setAlignmentX(LEFT_ALIGNMENT);
		spritePickerPanel.add(categoryCombo);
		spritePickerPanel.add(Box.createVerticalStrut(4));

		// Scrollable grid for curated sprites
		JPanel gridWrapper = new JPanel();
		gridWrapper.setLayout(new BoxLayout(gridWrapper, BoxLayout.Y_AXIS));
		gridWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane spriteScroll = new JScrollPane(gridWrapper);
		spriteScroll.setPreferredSize(new Dimension(210, 160));
		spriteScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		spriteScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		spriteScroll.setBorder(null);
		spriteScroll.setAlignmentX(LEFT_ALIGNMENT);
		spritePickerPanel.add(spriteScroll);

		// SpriteManager for loading preview images into Swing labels
		SpriteManager spriteMgr = plugin.getSpriteManager();

		// Populate grid with the selected category
		Runnable refreshGrid = () ->
		{
			gridWrapper.removeAll();
			GameSpriteCategory cat = (GameSpriteCategory) categoryCombo.getSelectedItem();
			if (cat == null)
			{
				return;
			}

			JPanel grid = new JPanel(new GridLayout(0, 4, 3, 3));
			grid.setBackground(ColorScheme.DARK_GRAY_COLOR);

			for (GameSpriteCategory.SpriteEntry se : cat.getEntryList())
			{
				JPanel cell = new JPanel(new BorderLayout());
				cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				cell.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
				cell.setPreferredSize(new Dimension(48, 40));
				cell.setToolTipText(se.getName() + " (archive: " + se.getSpriteId() + ", frame: " + se.getFrame() + ")");

				// Sprite preview image, loaded async from the game cache
				JLabel iconLabel = new JLabel("", JLabel.CENTER);
				iconLabel.setPreferredSize(new Dimension(48, 24));
				spriteMgr.addSpriteTo(iconLabel, se.getSpriteId(), se.getFrame());
				cell.add(iconLabel, BorderLayout.CENTER);

				JLabel nameLabel = new JLabel(truncate(se.getName(), 8), JLabel.CENTER);
				nameLabel.setForeground(new Color(0x90EE90));
				nameLabel.setFont(FontManager.getRunescapeSmallFont());
				cell.add(nameLabel, BorderLayout.SOUTH);

				final int spriteId = se.getSpriteId();
				final int spriteFrame = se.getFrame();
				cell.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						if (iconPickerTargetTab >= 0)
						{
							int targetTab = iconPickerTargetTab;
							plugin.addGameSprite(iconPickerTargetTab, spriteId, spriteFrame);
							spritePickerPanel.setVisible(false);
							iconPickerTargetTab = -1;
							refreshFromConfig();
							openIconEditor(targetTab);
							revalidate();
						}
					}

					@Override
					public void mouseEntered(MouseEvent e)
					{
						cell.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
						cell.repaint();
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
						cell.repaint();
					}
				});

				grid.add(cell);
			}

			gridWrapper.add(grid);
			gridWrapper.revalidate();
			gridWrapper.repaint();
		};

		categoryCombo.addActionListener(e -> refreshGrid.run());
		refreshGrid.run();

		spritePickerPanel.add(Box.createVerticalStrut(6));

		// Free-form sprite ID input with frame support - Row 1: fields
		JPanel inputRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		inputRow1.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inputRow1.setAlignmentX(LEFT_ALIGNMENT);
		inputRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

		JLabel idInputLabel = new JLabel("ID:");
		idInputLabel.setForeground(Color.LIGHT_GRAY);
		idInputLabel.setFont(FontManager.getRunescapeSmallFont());

		JTextField idField = new JTextField(5);
		idField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		idField.setForeground(Color.WHITE);
		idField.setFont(FontManager.getRunescapeSmallFont());
		idField.setToolTipText("Sprite archive ID (e.g. 439)");

		JLabel frameLabel = new JLabel("Frame:");
		frameLabel.setForeground(Color.LIGHT_GRAY);
		frameLabel.setFont(FontManager.getRunescapeSmallFont());

		JTextField frameField = new JTextField(2);
		frameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		frameField.setForeground(Color.WHITE);
		frameField.setFont(FontManager.getRunescapeSmallFont());
		frameField.setText("0");
		frameField.setToolTipText("Frame index within the archive (default 0). E.g. 439 frame 1 = red skull.");

		inputRow1.add(idInputLabel);
		inputRow1.add(idField);
		inputRow1.add(frameLabel);
		inputRow1.add(frameField);
		spritePickerPanel.add(inputRow1);

		// Row 2: preview + add buttons
		JPanel inputRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		inputRow2.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inputRow2.setAlignmentX(LEFT_ALIGNMENT);
		inputRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		// Preview label for the free-form input
		JLabel previewLabel = new JLabel();
		previewLabel.setPreferredSize(new Dimension(24, 22));
		previewLabel.setHorizontalAlignment(JLabel.CENTER);

		JButton previewBtn = new JButton("Preview");
		previewBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		previewBtn.setForeground(Color.WHITE);
		previewBtn.setFont(FontManager.getRunescapeSmallFont());
		previewBtn.setToolTipText("Preview this sprite");
		previewBtn.addActionListener(e ->
		{
			int[] parsed = parseSpriteInput(idField.getText(), frameField.getText());
			if (parsed != null)
			{
				spriteMgr.addSpriteTo(previewLabel, parsed[0], parsed[1]);
			}
		});

		JButton addIdBtn = new JButton("Add");
		addIdBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		addIdBtn.setForeground(Color.WHITE);
		addIdBtn.setFont(FontManager.getRunescapeSmallFont());
		addIdBtn.addActionListener(e ->
		{
			int[] parsed = parseSpriteInput(idField.getText(), frameField.getText());
			if (parsed == null)
			{
				return;
			}
			if (iconPickerTargetTab >= 0)
			{
				int targetTab = iconPickerTargetTab;
				plugin.addGameSprite(iconPickerTargetTab, parsed[0], parsed[1]);
				idField.setText("");
				frameField.setText("0");
				previewLabel.setIcon(null);
				refreshFromConfig();
				spritePickerPanel.setVisible(false);
				iconPickerTargetTab = -1;
				openIconEditor(targetTab);
				revalidate();
			}
		});

		inputRow2.add(previewBtn);
		inputRow2.add(previewLabel);
		inputRow2.add(addIdBtn);
		spritePickerPanel.add(inputRow2);

		spritePickerPanel.add(Box.createVerticalStrut(4));

		JButton closeBtn = new JButton("Close picker");
		closeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		closeBtn.setForeground(Color.WHITE);
		closeBtn.setFont(FontManager.getRunescapeSmallFont());
		closeBtn.addActionListener(e ->
		{
			spritePickerPanel.setVisible(false);
			iconPickerTargetTab = -1;
			revalidate();
		});
		spritePickerPanel.add(closeBtn);

		spritePickerPanel.setVisible(true);
		revalidate();
	}

	/**
	 * Parses the sprite ID and frame fields. Returns {archiveId, frame}
	 * or null if the input is invalid (shows a dialog in that case).
	 */
	private int[] parseSpriteInput(String idText, String frameText)
	{
		String rawId = idText.trim();
		String rawFrame = frameText.trim();
		if (rawId.isEmpty())
		{
			return null;
		}
		try
		{
			int id = Integer.parseInt(rawId);
			int frame = rawFrame.isEmpty() ? 0 : Integer.parseInt(rawFrame);
			if (id < 0)
			{
				JOptionPane.showMessageDialog(this,
						"Sprite ID must be non-negative.",
						"Invalid ID",
						JOptionPane.WARNING_MESSAGE);
				return null;
			}
			if (frame < 0)
			{
				JOptionPane.showMessageDialog(this,
						"Frame index must be non-negative.",
						"Invalid Frame",
						JOptionPane.WARNING_MESSAGE);
				return null;
			}
			return new int[]{id, frame};
		}
		catch (NumberFormatException ex)
		{
			JOptionPane.showMessageDialog(this,
					"Invalid number in sprite ID or frame field.",
					"Invalid Input",
					JOptionPane.WARNING_MESSAGE);
			return null;
		}
	}

	/**
	 * Truncate a string to maxLen characters, appending ".." if truncated.
	 */
	private static String truncate(String s, int maxLen)
	{
		if (s.length() <= maxLen)
		{
			return s;
		}
		return s.substring(0, maxLen - 2) + "..";
	}

	// -----------------------------------------------------------------------
	// Icon Editor (replaces old Icon Resizer)
	// -----------------------------------------------------------------------

	public void openIconEditor(int tabIndex)
	{
		iconPickerPanel.setVisible(false);
		skillPickerPanel.setVisible(false);
		spritePickerPanel.setVisible(false);

		iconEditorPanel.removeAll();

		TabConfig tc = plugin.loadTabConfig(tabIndex);
		List<TabConfig.IconEntry> icons = tc.getIcons();

		if (icons.isEmpty())
		{
			JLabel noIcons = new JLabel("No icons configured for tab " + tabIndex);
			noIcons.setForeground(Color.GRAY);
			noIcons.setFont(FontManager.getRunescapeSmallFont());
			noIcons.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 0));
			iconEditorPanel.add(noIcons);
		}
		else
		{
			// Title row: short label left, Fit checkbox right, fixed height
			JPanel titleRow = new JPanel();
			titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
			titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			titleRow.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
			titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			titleRow.setAlignmentX(LEFT_ALIGNMENT);

			JLabel editorTitle = new JLabel("Tab " + tabIndex);
			editorTitle.setForeground(Color.WHITE);
			editorTitle.setFont(FontManager.getRunescapeSmallFont());

			JCheckBox fitCheck = new JCheckBox("Fit");
			fitCheck.setSelected(!tc.isDisableFitting());
			fitCheck.setBackground(ColorScheme.DARK_GRAY_COLOR);
			fitCheck.setForeground(tc.isDisableFitting() ? Color.GRAY : new Color(0x90EE90));
			fitCheck.setFont(FontManager.getRunescapeSmallFont());
			fitCheck.setToolTipText("When enabled, icons shift up and text moves to the bottom. Disable to center both independently (useful for background sprites).");
			fitCheck.addActionListener(e ->
			{
				boolean fitting = fitCheck.isSelected();
				fitCheck.setForeground(fitting ? new Color(0x90EE90) : Color.GRAY);
				plugin.saveTabFitting(tabIndex, !fitting);
			});

			titleRow.add(editorTitle);
			titleRow.add(Box.createHorizontalGlue());
			titleRow.add(fitCheck);
			iconEditorPanel.add(titleRow);

			// Scrollable container for icon editor rows
			JPanel editorRows = new JPanel();
			editorRows.setLayout(new BoxLayout(editorRows, BoxLayout.Y_AXIS));
			editorRows.setBackground(ColorScheme.DARK_GRAY_COLOR);

			for (int i = 0; i < icons.size(); i++)
			{
				TabConfig.IconEntry entry = icons.get(i);
				editorRows.add(createIconEditorRow(tabIndex, i, icons.size(), entry));
				editorRows.add(Box.createVerticalStrut(4));
			}

			JScrollPane editorScroll = new JScrollPane(editorRows);
			editorScroll.setPreferredSize(new Dimension(210, Math.min(icons.size() * 134, 400)));
			editorScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
			editorScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
			editorScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
			editorScroll.setBorder(null);
			editorScroll.setAlignmentX(LEFT_ALIGNMENT);
			iconEditorPanel.add(editorScroll);
		}

		JButton closeBtn = new JButton("Close editor");
		closeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		closeBtn.setForeground(Color.WHITE);
		closeBtn.setFont(FontManager.getRunescapeSmallFont());
		closeBtn.addActionListener(e ->
		{
			iconEditorPanel.setVisible(false);
			revalidate();
		});
		iconEditorPanel.add(closeBtn);

		iconEditorPanel.setVisible(true);
		revalidate();
	}

	/**
	 * Backwards compatibility: old code may call openIconResizer.
	 */
	public void openIconResizer(int tabIndex)
	{
		openIconEditor(tabIndex);
	}

	private JPanel createIconEditorRow(int tabIndex, int iconIndex, int totalIcons, TabConfig.IconEntry entry)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		row.setMaximumSize(new Dimension(210, 130));

		// --- Top: label + move buttons + delete ---
		String label = getIconDescription(entry);
		JLabel iconLabel = new JLabel("#" + (iconIndex + 1) + ": " + label);
		iconLabel.setForeground(Color.WHITE);
		iconLabel.setFont(FontManager.getRunescapeSmallFont());
		iconLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));

		JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
		buttonBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Move up
		JButton upBtn = new JButton("\u25B2");
		upBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
		upBtn.setForeground(Color.LIGHT_GRAY);
		upBtn.setFont(FontManager.getRunescapeSmallFont());
		upBtn.setPreferredSize(new Dimension(22, 18));
		upBtn.setToolTipText("Move left");
		upBtn.setEnabled(iconIndex > 0);
		upBtn.addActionListener(e ->
		{
			plugin.swapIcons(tabIndex, iconIndex, iconIndex - 1);
			openIconEditor(tabIndex);
		});

		// Move down
		JButton downBtn = new JButton("\u25BC");
		downBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
		downBtn.setForeground(Color.LIGHT_GRAY);
		downBtn.setFont(FontManager.getRunescapeSmallFont());
		downBtn.setPreferredSize(new Dimension(22, 18));
		downBtn.setToolTipText("Move right");
		downBtn.setEnabled(iconIndex < totalIcons - 1);
		downBtn.addActionListener(e ->
		{
			plugin.swapIcons(tabIndex, iconIndex, iconIndex + 1);
			openIconEditor(tabIndex);
		});

		// Delete
		JButton removeBtn = new JButton("X");
		removeBtn.setBackground(new Color(139, 0, 0));
		removeBtn.setForeground(Color.WHITE);
		removeBtn.setFont(FontManager.getRunescapeSmallFont());
		removeBtn.setPreferredSize(new Dimension(22, 18));
		removeBtn.setToolTipText("Remove this icon");
		removeBtn.addActionListener(e ->
		{
			plugin.removeIcon(tabIndex, iconIndex);
			openIconEditor(tabIndex);
		});

		buttonBar.add(upBtn);
		buttonBar.add(downBtn);
		buttonBar.add(removeBtn);

		JPanel topRow = new JPanel(new BorderLayout());
		topRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topRow.add(iconLabel, BorderLayout.CENTER);
		topRow.add(buttonBar, BorderLayout.EAST);

		// --- Resolve the icon's native pixel dimensions for default size ---
		int nativeW = getNativeIconWidth(entry);
		int nativeH = getNativeIconHeight(entry);

		// --- Size controls ---
		// Max size 512 to accommodate large user-uploaded icons. The widget renderer
		// handles any size, and users explicitly want to go out of bounds for decoration.
		final int SIZE_MIN = 4;
		final int SIZE_MAX = 512;

		JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		sizePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Use manual size if set, otherwise fall back to native image dimensions.
		// Clamp to [SIZE_MIN, SIZE_MAX] so the SpinnerNumberModel never throws.
		int currentW = entry.getManualWidth() > 0 ? entry.getManualWidth() : nativeW;
		int currentH = entry.getManualHeight() > 0 ? entry.getManualHeight() : nativeH;
		currentW = Math.max(SIZE_MIN, Math.min(currentW, SIZE_MAX));
		currentH = Math.max(SIZE_MIN, Math.min(currentH, SIZE_MAX));

		JLabel wLabel = new JLabel("W:");
		wLabel.setForeground(Color.LIGHT_GRAY);
		wLabel.setFont(FontManager.getRunescapeSmallFont());

		JSpinner wSpinner = new JSpinner(new SpinnerNumberModel(
				currentW, SIZE_MIN, SIZE_MAX, 1));
		wSpinner.setPreferredSize(new Dimension(48, 18));

		JLabel hLabel = new JLabel("H:");
		hLabel.setForeground(Color.LIGHT_GRAY);
		hLabel.setFont(FontManager.getRunescapeSmallFont());

		JSpinner hSpinner = new JSpinner(new SpinnerNumberModel(
				currentH, SIZE_MIN, SIZE_MAX, 1));
		hSpinner.setPreferredSize(new Dimension(48, 18));

		// Aspect ratio link toggle
		JButton linkBtn = new JButton("\uD83D\uDD17");
		linkBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
		linkBtn.setForeground(Color.GRAY);
		linkBtn.setFont(FontManager.getRunescapeSmallFont());
		linkBtn.setPreferredSize(new Dimension(20, 18));
		linkBtn.setToolTipText("Link width/height (maintain aspect ratio)");
		linkBtn.setFocusPainted(false);
		// Track link state and aspect ratio per-row via array (mutable from lambda)
		final boolean[] linked = {false};
		final double[] aspectRatio = {(double) currentW / Math.max(1, currentH)};
		// Guard to prevent recursive spinner updates when link adjusts the other spinner
		final boolean[] adjusting = {false};

		linkBtn.addActionListener(e ->
		{
			linked[0] = !linked[0];
			if (linked[0])
			{
				// Capture current aspect ratio when linking
				int w = (Integer) wSpinner.getValue();
				int h = (Integer) hSpinner.getValue();
				aspectRatio[0] = (double) w / Math.max(1, h);
				linkBtn.setForeground(new Color(0x90EE90));
				linkBtn.setToolTipText("Linked: aspect ratio locked");
			}
			else
			{
				linkBtn.setForeground(Color.GRAY);
				linkBtn.setToolTipText("Link width/height (maintain aspect ratio)");
			}
		});

		JCheckBox autoCheck = new JCheckBox("Auto");
		autoCheck.setSelected(!entry.hasManualSize());
		autoCheck.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		autoCheck.setForeground(Color.LIGHT_GRAY);
		autoCheck.setFont(FontManager.getRunescapeSmallFont());

		wSpinner.setEnabled(entry.hasManualSize());
		hSpinner.setEnabled(entry.hasManualSize());

		autoCheck.addActionListener(e ->
		{
			boolean auto = autoCheck.isSelected();
			wSpinner.setEnabled(!auto);
			hSpinner.setEnabled(!auto);
			if (auto)
			{
				plugin.updateIconSize(tabIndex, iconIndex, -1, -1);
			}
			else
			{
				// When toggling off Auto, initialize to the icon's native size (clamped)
				int initW = Math.max(SIZE_MIN, Math.min(nativeW, SIZE_MAX));
				int initH = Math.max(SIZE_MIN, Math.min(nativeH, SIZE_MAX));
				adjusting[0] = true;
				wSpinner.setValue(initW);
				hSpinner.setValue(initH);
				adjusting[0] = false;
				aspectRatio[0] = (double) initW / Math.max(1, initH);
				plugin.updateIconSize(tabIndex, iconIndex, initW, initH);
			}
		});

		wSpinner.addChangeListener(e ->
		{
			if (autoCheck.isSelected() || adjusting[0])
			{
				return;
			}
			int newW = (Integer) wSpinner.getValue();
			if (linked[0])
			{
				int newH = Math.max(SIZE_MIN, Math.min(SIZE_MAX,
						(int) Math.round(newW / aspectRatio[0])));
				adjusting[0] = true;
				hSpinner.setValue(newH);
				adjusting[0] = false;
				plugin.updateIconSize(tabIndex, iconIndex, newW, newH);
			}
			else
			{
				plugin.updateIconSize(tabIndex, iconIndex, newW, (Integer) hSpinner.getValue());
			}
		});

		hSpinner.addChangeListener(e ->
		{
			if (autoCheck.isSelected() || adjusting[0])
			{
				return;
			}
			int newH = (Integer) hSpinner.getValue();
			if (linked[0])
			{
				int newW = Math.max(SIZE_MIN, Math.min(SIZE_MAX,
						(int) Math.round(newH * aspectRatio[0])));
				adjusting[0] = true;
				wSpinner.setValue(newW);
				adjusting[0] = false;
				plugin.updateIconSize(tabIndex, iconIndex, newW, newH);
			}
			else
			{
				plugin.updateIconSize(tabIndex, iconIndex, (Integer) wSpinner.getValue(), newH);
			}
		});

		sizePanel.add(wLabel);
		sizePanel.add(wSpinner);
		sizePanel.add(linkBtn);
		sizePanel.add(hLabel);
		sizePanel.add(hSpinner);
		sizePanel.add(autoCheck);

		// --- Offset controls (no clamping, allows decorating outside tab bounds) ---
		JPanel offsetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		offsetPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel xLabel = new JLabel("X:");
		xLabel.setForeground(Color.LIGHT_GRAY);
		xLabel.setFont(FontManager.getRunescapeSmallFont());

		JSpinner xSpinner = new JSpinner(new SpinnerNumberModel(
				entry.getOffsetX(), -200, 200, 1));
		xSpinner.setPreferredSize(new Dimension(52, 18));
		xSpinner.setToolTipText("Horizontal offset (px). Negative = left, positive = right.");

		JLabel yLabel = new JLabel("Y:");
		yLabel.setForeground(Color.LIGHT_GRAY);
		yLabel.setFont(FontManager.getRunescapeSmallFont());

		JSpinner ySpinner = new JSpinner(new SpinnerNumberModel(
				entry.getOffsetY(), -200, 200, 1));
		ySpinner.setPreferredSize(new Dimension(52, 18));
		ySpinner.setToolTipText("Vertical offset (px). Negative = up, positive = down.");

		JButton resetOffsetBtn = new JButton("Reset");
		resetOffsetBtn.setBackground(ColorScheme.DARK_GRAY_COLOR);
		resetOffsetBtn.setForeground(Color.LIGHT_GRAY);
		resetOffsetBtn.setFont(FontManager.getRunescapeSmallFont());
		resetOffsetBtn.setPreferredSize(new Dimension(44, 18));
		resetOffsetBtn.setToolTipText("Reset offset to 0,0");
		resetOffsetBtn.addActionListener(e ->
		{
			xSpinner.setValue(0);
			ySpinner.setValue(0);
			plugin.updateIconOffset(tabIndex, iconIndex, 0, 0);
		});

		xSpinner.addChangeListener(e ->
				plugin.updateIconOffset(tabIndex, iconIndex,
						(Integer) xSpinner.getValue(), (Integer) ySpinner.getValue()));

		ySpinner.addChangeListener(e ->
				plugin.updateIconOffset(tabIndex, iconIndex,
						(Integer) xSpinner.getValue(), (Integer) ySpinner.getValue()));

		offsetPanel.add(xLabel);
		offsetPanel.add(xSpinner);
		offsetPanel.add(yLabel);
		offsetPanel.add(ySpinner);
		offsetPanel.add(resetOffsetBtn);

		// --- Z-index (render priority) control ---
		JPanel zPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		zPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel zLabel = new JLabel("Z:");
		zLabel.setForeground(Color.LIGHT_GRAY);
		zLabel.setFont(FontManager.getRunescapeSmallFont());
		zLabel.setToolTipText("Render priority. Higher draws on top.");

		JSpinner zSpinner = new JSpinner(new SpinnerNumberModel(
				entry.getZIndex(), -100, 100, 1));
		zSpinner.setPreferredSize(new Dimension(48, 18));
		zSpinner.setToolTipText("Render priority. Higher values draw on top of lower values.");

		zSpinner.addChangeListener(e ->
				plugin.updateIconZIndex(tabIndex, iconIndex, (Integer) zSpinner.getValue()));

		zPanel.add(zLabel);
		zPanel.add(zSpinner);

		row.add(topRow);
		row.add(sizePanel);
		row.add(offsetPanel);
		row.add(zPanel);

		return row;
	}

	/**
	 * Returns the native pixel width of the source image for an icon entry.
	 * For SPRITE mode, reads from the plugin's dimension cache which is populated
	 * on the client thread during rendering. For other modes, reads from the
	 * image directly (safe on EDT for skill/custom icons) or uses known constants.
	 */
	private int getNativeIconWidth(TabConfig.IconEntry entry)
	{
		switch (entry.getMode())
		{
			case ITEM:
				return 36; // Constants.ITEM_SPRITE_WIDTH
			case SKILL:
			{
				java.awt.image.BufferedImage img = plugin.getSkillIconProvider()
						.getSkillIconByOrdinal(entry.getSkillOrdinal(), false);
				return img != null ? img.getWidth() : 25;
			}
			case CUSTOM:
			{
				java.awt.image.BufferedImage img = plugin.getCustomIconManager()
						.getIcon(entry.getCustomName());
				return img != null ? img.getWidth() : 32;
			}
			case SPRITE:
			{
				int[] dims = plugin.getCachedSpriteDimensions(
						entry.getSpriteArchiveId(), entry.getSpriteFrame());
				return dims != null ? dims[0] : 32;
			}
			default:
				return 16;
		}
	}

	/**
	 * Returns the native pixel height of the source image for an icon entry.
	 */
	private int getNativeIconHeight(TabConfig.IconEntry entry)
	{
		switch (entry.getMode())
		{
			case ITEM:
				return 32; // Constants.ITEM_SPRITE_HEIGHT
			case SKILL:
			{
				java.awt.image.BufferedImage img = plugin.getSkillIconProvider()
						.getSkillIconByOrdinal(entry.getSkillOrdinal(), false);
				return img != null ? img.getHeight() : 25;
			}
			case CUSTOM:
			{
				java.awt.image.BufferedImage img = plugin.getCustomIconManager()
						.getIcon(entry.getCustomName());
				return img != null ? img.getHeight() : 32;
			}
			case SPRITE:
			{
				int[] dims = plugin.getCachedSpriteDimensions(
						entry.getSpriteArchiveId(), entry.getSpriteFrame());
				return dims != null ? dims[1] : 32;
			}
			default:
				return 16;
		}
	}

	private String getIconDescription(TabConfig.IconEntry entry)
	{
		switch (entry.getMode())
		{
			case ITEM:
				return "Item #" + entry.getItemId();
			case CUSTOM:
				return CustomIconManager.getDisplayName(entry.getCustomName());
			case SKILL:
				Skill skill = SkillIconProvider.skillFromOrdinal(entry.getSkillOrdinal());
				return skill != null ? skill.getName() : "Unknown skill";
			case SPRITE:
				int frame = entry.getSpriteFrame();
				return frame == 0
						? "Sprite #" + entry.getSpriteArchiveId()
						: "Sprite #" + entry.getSpriteArchiveId() + "-" + frame;
			default:
				return "None";
		}
	}

	// -----------------------------------------------------------------------
	// Config Presets (saved configs)
	// -----------------------------------------------------------------------

	private Gson configGson;

	/**
	 * Type token for the outer map: preset name -> tab map.
	 */
	private static final Type PRESETS_MAP_TYPE =
			new TypeToken<LinkedHashMap<String, LinkedHashMap<String, TabConfig>>>(){}.getType();

	/**
	 * Type token for a single preset's tab map.
	 */
	private static final Type TAB_MAP_TYPE =
			new TypeToken<LinkedHashMap<String, TabConfig>>(){}.getType();

	/**
	 * Loads all saved presets from ConfigManager.
	 */
	private LinkedHashMap<String, LinkedHashMap<String, TabConfig>> loadAllPresets()
	{
		String json = plugin.getConfigManager().getConfiguration(
				BankTabNamesPlugin.CONFIG_GROUP, SAVED_CONFIGS_KEY);
		if (json != null && !json.trim().isEmpty())
		{
			try
			{
				LinkedHashMap<String, LinkedHashMap<String, TabConfig>> map =
						configGson.fromJson(json, PRESETS_MAP_TYPE);
				if (map != null)
				{
					return map;
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to parse saved configs", e);
			}
		}
		return new LinkedHashMap<>();
	}

	/**
	 * Persists all presets to ConfigManager.
	 */
	private void saveAllPresets(LinkedHashMap<String, LinkedHashMap<String, TabConfig>> presets)
	{
		String json = configGson.toJson(presets);
		plugin.getConfigManager().setConfiguration(
				BankTabNamesPlugin.CONFIG_GROUP, SAVED_CONFIGS_KEY, json);
	}

	/**
	 * Refreshes the preset dropdown from persisted data.
	 */
	private void refreshPresetCombo()
	{
		if (configPresetCombo == null)
		{
			return;
		}
		configPresetCombo.removeAllItems();
		LinkedHashMap<String, LinkedHashMap<String, TabConfig>> presets = loadAllPresets();
		for (String name : presets.keySet())
		{
			configPresetCombo.addItem(name);
		}
	}

	/**
	 * Serializes the current 10 tabs into a tab map.
	 */
	private LinkedHashMap<String, TabConfig> serializeCurrentTabs()
	{
		LinkedHashMap<String, TabConfig> tabMap = new LinkedHashMap<>();
		for (int i = 0; i < 10; i++)
		{
			tabMap.put("tab_" + i, plugin.loadTabConfig(i));
		}
		return tabMap;
	}

	/**
	 * Applies a tab map to the live config. Runs legacy migrations on each
	 * TabConfig to handle configs from older plugin versions.
	 */
	private void applyTabMap(Map<String, TabConfig> tabMap)
	{
		for (int i = 0; i < 10; i++)
		{
			TabConfig tc = tabMap.get("tab_" + i);
			if (tc != null)
			{
				tc.migrateLegacyIcon();
				plugin.saveTabConfig(i, tc);
			}
		}
		// saveTabConfig fires onConfigChanged which triggers applyAllTabs
		refreshFromConfig();
	}

	/**
	 * Save current tab layout as a named preset.
	 */
	private void saveCurrentAsPreset()
	{
		if (plugin == null)
		{
			return;
		}

		String name = JOptionPane.showInputDialog(this,
				"Enter a name for this config:",
				"Save Config Preset",
				JOptionPane.PLAIN_MESSAGE);

		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		name = name.trim();

		LinkedHashMap<String, LinkedHashMap<String, TabConfig>> presets = loadAllPresets();

		if (presets.containsKey(name))
		{
			int overwrite = JOptionPane.showConfirmDialog(this,
					"A preset named \"" + name + "\" already exists. Overwrite?",
					"Save Config Preset",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
			if (overwrite != JOptionPane.YES_OPTION)
			{
				return;
			}
		}

		presets.put(name, serializeCurrentTabs());
		saveAllPresets(presets);
		refreshPresetCombo();
		configPresetCombo.setSelectedItem(name);
	}

	/**
	 * Load the currently selected preset into the live config.
	 */
	private void loadSelectedPreset()
	{
		if (plugin == null || configPresetCombo.getSelectedItem() == null)
		{
			return;
		}

		String name = (String) configPresetCombo.getSelectedItem();
		LinkedHashMap<String, LinkedHashMap<String, TabConfig>> presets = loadAllPresets();
		LinkedHashMap<String, TabConfig> tabMap = presets.get(name);

		if (tabMap == null)
		{
			JOptionPane.showMessageDialog(this,
					"Preset \"" + name + "\" not found.",
					"Load Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		int confirm = JOptionPane.showConfirmDialog(this,
				"Load preset \"" + name + "\"? This will overwrite your current tabs.",
				"Load Config Preset",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);

		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		applyTabMap(tabMap);
	}

	/**
	 * Delete the currently selected preset.
	 */
	private void deleteSelectedPreset()
	{
		if (plugin == null || configPresetCombo.getSelectedItem() == null)
		{
			return;
		}

		String name = (String) configPresetCombo.getSelectedItem();

		int confirm = JOptionPane.showConfirmDialog(this,
				"Delete preset \"" + name + "\"?",
				"Delete Config Preset",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);

		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		LinkedHashMap<String, LinkedHashMap<String, TabConfig>> presets = loadAllPresets();
		presets.remove(name);
		saveAllPresets(presets);
		refreshPresetCombo();
	}

	// -----------------------------------------------------------------------
	// Config Import / Export
	// -----------------------------------------------------------------------

	/**
	 * Export current tabs. Asks for a config name, then offers clipboard or file.
	 * The name is embedded in the JSON so importing can use it as the preset name.
	 */
	private void exportConfig()
	{
		if (plugin == null)
		{
			return;
		}

		String name = JOptionPane.showInputDialog(this,
				"Enter a name for this config export:",
				"Export Config",
				JOptionPane.PLAIN_MESSAGE);

		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		name = name.trim();

		LinkedHashMap<String, Object> exportWrapper = new LinkedHashMap<>();
		exportWrapper.put("name", name);
		exportWrapper.put("version", plugin.getConfigVersion());
		exportWrapper.put("tabs", serializeCurrentTabs());
		String json = configGson.toJson(exportWrapper);

		String[] options = {"Copy to clipboard", "Save to file", "Cancel"};
		int choice = JOptionPane.showOptionDialog(this,
				"How would you like to export \"" + name + "\"?",
				"Export Config",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null, options, options[0]);

		if (choice == 0)
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
					.setContents(new StringSelection(json), null);
			JOptionPane.showMessageDialog(this,
					"Config \"" + name + "\" copied to clipboard!",
					"Export Config",
					JOptionPane.INFORMATION_MESSAGE);
		}
		else if (choice == 1)
		{
			if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs())
			{
				log.warn("Failed to create config dir: {}", CONFIG_DIR.getAbsolutePath());
				JOptionPane.showMessageDialog(this,
						"Could not create config folder:\n" + CONFIG_DIR.getAbsolutePath(),
						"Export Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}

			String safeName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".json";
			File file = new File(CONFIG_DIR, safeName);
			try
			{
				Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));

				JButton openFolderBtn = new JButton("Open Config Folder");
				openFolderBtn.setAlignmentX(LEFT_ALIGNMENT);
				openFolderBtn.addActionListener(ev -> openConfigFolder());

				JLabel savedLabel = new JLabel("<html>Config saved to:<br>" + file.getAbsolutePath() + "</html>");
				savedLabel.setAlignmentX(LEFT_ALIGNMENT);

				JPanel savedPanel = new JPanel();
				savedPanel.setLayout(new BoxLayout(savedPanel, BoxLayout.Y_AXIS));
				savedPanel.add(savedLabel);
				savedPanel.add(Box.createVerticalStrut(8));
				savedPanel.add(openFolderBtn);

				JOptionPane.showMessageDialog(this, savedPanel,
						"Export Config",
						JOptionPane.INFORMATION_MESSAGE);
			}
			catch (Exception e)
			{
				log.warn("Failed to export config", e);
				JOptionPane.showMessageDialog(this,
						"Failed to save file: " + e.getMessage(),
						"Export Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 * Import a config from clipboard or file. The imported JSON contains a name
	 * and tab data. The config is added to the saved presets and optionally
	 * loaded immediately.
	 */
	private void importConfig()
	{
		if (plugin == null)
		{
			return;
		}

		String[] options = {"Paste from clipboard", "Load from file", "Cancel"};
		int choice = JOptionPane.showOptionDialog(this,
				"How would you like to import a config?",
				"Import Config",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null, options, options[0]);

		String json = null;

		if (choice == 0)
		{
			try
			{
				json = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
						.getData(DataFlavor.stringFlavor);
			}
			catch (Exception e)
			{
				JOptionPane.showMessageDialog(this,
						"Failed to read clipboard: " + e.getMessage(),
						"Import Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
		else if (choice == 1)
		{
			// Create the folder up front so the "Open Config Folder" button always
			// lands somewhere real, and so first-time users have a place to drop files.
			if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs())
			{
				log.warn("Failed to create config dir: {}", CONFIG_DIR.getAbsolutePath());
			}

			File[] files = CONFIG_DIR.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));

			if (files == null || files.length == 0)
			{
				JButton openFolderBtn = new JButton("Open Config Folder");
				openFolderBtn.setAlignmentX(LEFT_ALIGNMENT);
				openFolderBtn.addActionListener(ev -> openConfigFolder());

				JLabel emptyLabel = new JLabel("No configs found in folder.");
				emptyLabel.setAlignmentX(LEFT_ALIGNMENT);

				JPanel emptyPanel = new JPanel();
				emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
				emptyPanel.add(emptyLabel);
				emptyPanel.add(Box.createVerticalStrut(8));
				emptyPanel.add(openFolderBtn);

				JOptionPane.showMessageDialog(this, emptyPanel,
						"Import Config",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			String[] names = new String[files.length];
			for (int i = 0; i < files.length; i++)
			{
				names[i] = files[i].getName();
			}

			JComboBox<String> fileCombo = new JComboBox<>(names);
			fileCombo.setAlignmentX(LEFT_ALIGNMENT);

			JButton openFolderBtn = new JButton("Open Config Folder");
			openFolderBtn.setAlignmentX(LEFT_ALIGNMENT);
			openFolderBtn.addActionListener(ev -> openConfigFolder());

			JLabel chooseLabel = new JLabel("Choose a config file to import:");
			chooseLabel.setAlignmentX(LEFT_ALIGNMENT);

			JPanel pickerPanel = new JPanel();
			pickerPanel.setLayout(new BoxLayout(pickerPanel, BoxLayout.Y_AXIS));
			pickerPanel.add(chooseLabel);
			pickerPanel.add(Box.createVerticalStrut(4));
			pickerPanel.add(fileCombo);
			pickerPanel.add(Box.createVerticalStrut(8));
			pickerPanel.add(openFolderBtn);

			int result = JOptionPane.showConfirmDialog(this, pickerPanel,
					"Import Config",
					JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE);

			if (result != JOptionPane.OK_OPTION)
			{
				return;
			}

			String selected = (String) fileCombo.getSelectedItem();
			if (selected == null)
			{
				return;
			}

			try
			{
				File file = new File(CONFIG_DIR, selected);
				json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			}
			catch (Exception e)
			{
				log.warn("Failed to read config file", e);
				JOptionPane.showMessageDialog(this,
						"Failed to read file: " + e.getMessage(),
						"Import Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		if (json == null || json.trim().isEmpty())
		{
			return;
		}

		try
		{
			// Try new format first (has "name" + "tabs" wrapper, optionally "version")
			LinkedHashMap<String, TabConfig> tabMap = null;
			String presetName = null;
			int importVersion = 0;

			try
			{
				com.google.gson.JsonObject root = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
				if (root.has("name") && root.has("tabs"))
				{
					presetName = root.get("name").getAsString();
					tabMap = configGson.fromJson(root.get("tabs"), TAB_MAP_TYPE);

					if (root.has("version"))
					{
						importVersion = root.get("version").getAsInt();
					}
				}
			}
			catch (Exception ignored)
			{
				// Not the new wrapper format
			}

			// Fall back to legacy flat format (direct tab_0..tab_9 map, version 0)
			if (tabMap == null)
			{
				tabMap = configGson.fromJson(json, TAB_MAP_TYPE);
				importVersion = 0;
			}

			if (tabMap == null || tabMap.isEmpty())
			{
				JOptionPane.showMessageDialog(this,
						"No valid tab configs found in the imported data.",
						"Import Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}

			// Warn if the imported config was created on an older plugin version.
			// Placement, sizing, or fitting behavior may have changed between versions.
			int currentVersion = plugin.getConfigVersion();
			if (importVersion < currentVersion && !plugin.getConfig().suppressImportVersionWarning())
			{
				JCheckBox dontShowAgain = new JCheckBox("Don't show this again");
				dontShowAgain.setFont(FontManager.getRunescapeSmallFont());

				Object[] message = {
						"This config was designed for Bank Tab Names v" + importVersion
								+ " (you have v" + currentVersion + ").\n"
								+ "You might run into some unexpected issues with\n"
								+ "placement or sizing that may need adjusting.",
						dontShowAgain
				};

				JOptionPane.showMessageDialog(this, message,
						"Older Config Version",
						JOptionPane.INFORMATION_MESSAGE);

				if (dontShowAgain.isSelected())
				{
					plugin.getConfigManager().setConfiguration(
							BankTabNamesPlugin.CONFIG_GROUP,
							"suppressImportVersionWarning", "true");
				}
			}

			// Run import migrations for older versions.
			// Each migration block upgrades TabConfig objects from one version to the next.
			// Add new blocks here as CURRENT_CONFIG_VERSION increases.
			if (importVersion < 1)
			{
				// Version 0 configs may have legacy single-icon fields.
				// migrateLegacyIcon() on each TabConfig handles this.
				for (TabConfig tc : tabMap.values())
				{
					if (tc != null)
					{
						tc.migrateLegacyIcon();
					}
				}
			}

			// If no name was in the JSON, ask for one
			if (presetName == null || presetName.trim().isEmpty())
			{
				presetName = JOptionPane.showInputDialog(this,
						"Enter a name for this imported config:",
						"Import Config",
						JOptionPane.PLAIN_MESSAGE);
				if (presetName == null || presetName.trim().isEmpty())
				{
					presetName = "Imported";
				}
			}
			presetName = presetName.trim();

			// Save to presets
			LinkedHashMap<String, LinkedHashMap<String, TabConfig>> presets = loadAllPresets();
			if (presets.containsKey(presetName))
			{
				int overwrite = JOptionPane.showConfirmDialog(this,
						"A preset named \"" + presetName + "\" already exists. Overwrite?",
						"Import Config",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE);
				if (overwrite != JOptionPane.YES_OPTION)
				{
					return;
				}
			}

			presets.put(presetName, new LinkedHashMap<>(tabMap));
			saveAllPresets(presets);
			refreshPresetCombo();
			configPresetCombo.setSelectedItem(presetName);

			// Offer to apply immediately
			int apply = JOptionPane.showConfirmDialog(this,
					"Config \"" + presetName + "\" saved to presets. Load it now?",
					"Import Config",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.QUESTION_MESSAGE);

			if (apply == JOptionPane.YES_OPTION)
			{
				applyTabMap(tabMap);
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to parse imported config", e);
			JOptionPane.showMessageDialog(this,
					"Invalid config format: " + e.getMessage(),
					"Import Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// -----------------------------------------------------------------------
	// Sponsor dialog
	// -----------------------------------------------------------------------

	private void showSponsorDialog()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JLabel message = new JLabel("<html><body style='width: 240px;'>"
				+ "This plugin, while simple in theory, has taken a total of over "
				+ "<b>300 hours</b> of development. If you'd like to see more creative "
				+ "projects around RuneLite, consider sponsoring your local developer "
				+ "\u2764</body></html>");
		message.setForeground(Color.WHITE);
		message.setFont(FontManager.getRunescapeSmallFont());
		message.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(message);
		panel.add(Box.createVerticalStrut(12));

		JButton patreonBtn = new JButton("Patreon - patreon.com/psyda");
		patreonBtn.setBackground(new Color(0xFF424D));
		patreonBtn.setForeground(Color.WHITE);
		patreonBtn.setFont(FontManager.getRunescapeSmallFont());
		patreonBtn.setAlignmentX(LEFT_ALIGNMENT);
		patreonBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		patreonBtn.addActionListener(e -> LinkBrowser.browse("https://patreon.com/psyda"));
		panel.add(patreonBtn);
		panel.add(Box.createVerticalStrut(6));

		JButton paypalBtn = new JButton("PayPal - paypal.me/mintyfresh");
		paypalBtn.setBackground(new Color(0x0070BA));
		paypalBtn.setForeground(Color.WHITE);
		paypalBtn.setFont(FontManager.getRunescapeSmallFont());
		paypalBtn.setAlignmentX(LEFT_ALIGNMENT);
		paypalBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		paypalBtn.addActionListener(e -> LinkBrowser.browse("https://paypal.me/mintyfresh"));
		panel.add(paypalBtn);

		JOptionPane.showMessageDialog(this, panel,
				"Support Bank Tab Names",
				JOptionPane.PLAIN_MESSAGE);
	}

	// -----------------------------------------------------------------------
	// Color tag insertion
	// -----------------------------------------------------------------------

	private void insertColorTag(String hex)
	{
		if (selectedTabIndex >= 0 && selectedTabIndex < tabRows.size())
		{
			TabRow row = tabRows.get(selectedTabIndex);
			int pos = row.textField.getCaretPosition();
			String text = row.textField.getText();
			String tag = "<col=" + hex + ">";
			row.textField.setText(text.substring(0, pos) + tag + text.substring(pos));
			row.textField.setCaretPosition(pos + tag.length());
			row.textField.requestFocus();
			row.saveToConfig();
		}
	}

	public void refreshFromConfig()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			loadConfigValues();
		}
		else
		{
			SwingUtilities.invokeLater(this::loadConfigValues);
		}
	}

	private void loadConfigValues()
	{
		if (plugin == null)
		{
			return;
		}

		// Set the loading guard so that programmatic setText/setSelected calls
		// on Swing components do not trigger their change listeners, which would
		// call saveToConfig and create a feedback loop that clobbers data.
		loading = true;
		try
		{
			for (int i = 0; i < tabRows.size(); i++)
			{
				TabConfig tc = plugin.loadTabConfig(i);
				TabRow row = tabRows.get(i);
				row.loadFrom(tc);
			}
		}
		finally
		{
			loading = false;
		}
	}

	// -----------------------------------------------------------------------
	// Tab config row
	// -----------------------------------------------------------------------

	private class TabRow extends JPanel
	{
		final int tabIndex;
		final JTextField textField;
		final JComboBox<TabFonts> fontCombo;
		final JCheckBox enabledCheck;
		final JButton iconButton;

		TabRow(int index)
		{
			this.tabIndex = index;
			setLayout(new BorderLayout(2, 1));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setMaximumSize(new Dimension(220, 62));
			setPreferredSize(new Dimension(220, 62));
			setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1));

			// Top row: font selector + enable checkbox
			JPanel topRow = new JPanel(new BorderLayout(2, 0));
			topRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			topRow.setPreferredSize(new Dimension(210, 18));

			JLabel fontLabel = new JLabel("Tab " + index + ":");
			fontLabel.setForeground(Color.WHITE);
			fontLabel.setFont(FontManager.getRunescapeSmallFont());
			fontLabel.setPreferredSize(new Dimension(40, 16));

			fontCombo = new JComboBox<>(TabFonts.values());
			fontCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			fontCombo.setForeground(Color.WHITE);
			fontCombo.setFont(FontManager.getRunescapeSmallFont());
			fontCombo.setPreferredSize(new Dimension(110, 16));
			fontCombo.addActionListener(e -> saveToConfig());

			enabledCheck = new JCheckBox();
			enabledCheck.setSelected(true);
			enabledCheck.setBackground(ColorScheme.DARK_GRAY_COLOR);
			enabledCheck.setToolTipText("Enable custom display");
			enabledCheck.addActionListener(e -> saveToConfig());

			topRow.add(fontLabel, BorderLayout.WEST);
			topRow.add(fontCombo, BorderLayout.CENTER);
			topRow.add(enabledCheck, BorderLayout.EAST);

			// Middle row: text field
			textField = new JTextField();
			textField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			textField.setForeground(Color.WHITE);
			textField.setFont(FontManager.getRunescapeSmallFont());
			textField.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
			textField.setPreferredSize(new Dimension(210, 20));

			textField.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.getKeyCode() == KeyEvent.VK_ENTER)
					{
						e.consume();
						int pos = textField.getCaretPosition();
						String text = textField.getText();
						textField.setText(text.substring(0, pos) + "<br>" + text.substring(pos));
						textField.setCaretPosition(pos + 4);
					}
				}

				@Override
				public void keyReleased(KeyEvent e)
				{
					saveToConfig();
				}
			});

			textField.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusGained(FocusEvent e)
				{
					selectedTabIndex = tabIndex;
					updateSelectionBorders();
				}

				@Override
				public void focusLost(FocusEvent e)
				{
					saveToConfig();
				}
			});

			// Bottom row: icon edit button (shows icon summary, click opens editor)
			iconButton = new JButton("Icons: none");
			iconButton.setForeground(Color.GRAY);
			iconButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
			iconButton.setFont(FontManager.getRunescapeSmallFont());
			iconButton.setPreferredSize(new Dimension(210, 14));
			iconButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
			iconButton.setHorizontalAlignment(JButton.LEFT);
			iconButton.setFocusPainted(false);
			iconButton.setContentAreaFilled(false);
			iconButton.setEnabled(false);
			iconButton.setToolTipText("No icons to edit");
			iconButton.addActionListener(e -> openIconEditor(tabIndex));

			add(topRow, BorderLayout.NORTH);
			add(textField, BorderLayout.CENTER);
			add(iconButton, BorderLayout.SOUTH);
		}

		void loadFrom(TabConfig tc)
		{
			textField.setText(tc.getText());
			fontCombo.setSelectedItem(tc.getFont());
			enabledCheck.setSelected(tc.isEnabled());
			updateIconLabel(tc);
		}

		void updateIconLabel(TabConfig tc)
		{
			List<TabConfig.IconEntry> icons = tc.getIcons();
			if (icons.isEmpty())
			{
				iconButton.setText("Icons: none");
				iconButton.setForeground(Color.GRAY);
				iconButton.setEnabled(false);
				iconButton.setToolTipText("No icons to edit");
				return;
			}

			StringBuilder sb = new StringBuilder("Edit: ");
			for (int i = 0; i < icons.size(); i++)
			{
				if (i > 0)
				{
					sb.append(" + ");
				}
				TabConfig.IconEntry entry = icons.get(i);
				switch (entry.getMode())
				{
					case ITEM:
						sb.append("Item#").append(entry.getItemId());
						break;
					case CUSTOM:
						sb.append(CustomIconManager.getDisplayName(entry.getCustomName()));
						break;
					case SKILL:
						Skill skill = SkillIconProvider.skillFromOrdinal(entry.getSkillOrdinal());
						sb.append(skill != null ? skill.getName() : "?");
						break;
					case SPRITE:
						sb.append("Sprite#").append(entry.getSpriteArchiveId());
						if (entry.getSpriteFrame() != 0)
						{
							sb.append("-").append(entry.getSpriteFrame());
						}
						break;
					default:
						sb.append("?");
				}
			}

			iconButton.setText(sb.toString());
			iconButton.setForeground(new Color(0x90EE90));
			iconButton.setEnabled(true);
			iconButton.setToolTipText("Click to edit icons for this tab");
		}

		/**
		 * Saves only the fields this panel controls (enabled, text, font) to
		 * config. Does NOT touch icons, which are managed by the plugin's
		 * icon add/remove/swap methods. This prevents the panel from
		 * accidentally clobbering icon data during save.
		 *
		 * Guarded by the {@link BankTabNamesPanel#loading} flag to prevent
		 * feedback loops when loadFrom() programmatically sets control values.
		 */
		void saveToConfig()
		{
			if (plugin == null || loading)
			{
				return;
			}
			plugin.saveTabText(tabIndex, enabledCheck.isSelected(),
					textField.getText(), (TabFonts) fontCombo.getSelectedItem());
		}

		void updateSelectionBorders()
		{
			for (TabRow row : tabRows)
			{
				row.setBorder(BorderFactory.createLineBorder(
						selectedTabIndex == row.tabIndex ? Color.YELLOW : ColorScheme.MEDIUM_GRAY_COLOR, 1));
			}
		}
	}
}