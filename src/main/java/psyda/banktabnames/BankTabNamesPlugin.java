package psyda.banktabnames;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.SpritePixels;
import net.runelite.client.util.Text;

/**
 * Bank Tab Names - Customize bank tabs with styled text, item/skill/custom icons,
 * manual sizing controls, and drag-to-rearrange tab designs.
 */
@PluginDescriptor(
		name = "Bank Tab Names",
		description = "Customize your bank tabs with styled text, item icons, skill icons, custom images, and manual sizing",
		tags = {"bank", "tab", "tags", "creative", "custom", "icon", "art", "edit", "psyda"}
)
@Slf4j
public class BankTabNamesPlugin extends Plugin
{
	static final String CONFIG_GROUP = "banktabnames";

	// Config key prefixes
	private static final String KEY_ENABLED = "enabled_";
	private static final String KEY_TEXT = "text_";
	private static final String KEY_FONT = "font_";
	private static final String KEY_ICONS_JSON = "icons_";
	private static final String KEY_DISABLE_FITTING = "disablefit_";
	private static final String KEY_CONFIG_VERSION = "config_version";

	/**
	 * Current config schema version. Increment this when making breaking changes
	 * to the config format. The plugin checks the stored version on startup and
	 * runs any necessary migrations before loading tab configs.
	 *
	 * Version history:
	 *   0 (absent) - Original format, single icon per tab via iconmode/iconitem/iconcustom keys
	 *   1          - Multi-icon list (icons_ JSON), manual size/offset, zIndex, disableFitting
	 */
	private static final int CURRENT_CONFIG_VERSION = 1;

	// Legacy keys (for migration)
	private static final String KEY_ICON_MODE = "iconmode_";
	private static final String KEY_ICON_ITEM = "iconitem_";
	private static final String KEY_ICON_CUSTOM = "iconcustom_";

	// Menu options
	private static final String EDIT_TEXT = "Edit text";
	private static final String CLEAR_TEXT = "Clear text";
	private static final String SET_ITEM_ICON = "Add item icon";
	private static final String SET_SKILL_ICON = "Add skill icon";
	private static final String SET_CUSTOM_ICON = "Add custom icon";
	private static final String SET_GAME_SPRITE = "Add game sprite";
	private static final String CLEAR_ALL_ICONS = "Clear all icons";
	private static final String EDIT_ICONS = "Edit icons";

	static final int MAX_TABS = 10;
	private static final int TAB_CHILD_START = 10;

	static final int TAB_WIDTH = 41;
	static final int TAB_HEIGHT = 40;

	// Script IDs that trigger bank tab rebuilds
	private final int[] BANK_REBUILD_SCRIPTS = {
			ScriptID.BANKMAIN_BUILD,
			ScriptID.BANKMAIN_INIT,
			ScriptID.BANKMAIN_FINISHBUILDING,
			ScriptID.BANKMAIN_SEARCH_REFRESH,
			ScriptID.BANKMAIN_SEARCH_TOGGLE,
			ScriptID.BANKMAIN_SIZE_CHECK
	};

	/**
	 * Subset of BANK_REBUILD_SCRIPTS that are known to delete dynamic children
	 * from the tab container (BUILD and INIT). When these fire, all our pooled
	 * overlay widgets are destroyed by the client and references become stale.
	 */
	private final int[] DESTRUCTIVE_SCRIPTS = {
			ScriptID.BANKMAIN_BUILD,
			ScriptID.BANKMAIN_INIT
	};

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Getter @Inject private ConfigManager configManager;
	@Inject private ItemManager itemManager;
	@Getter @Inject private SpriteManager spriteManager;
	@Inject private ChatboxPanelManager chatboxPanelManager;
	@Inject private ChatboxItemSearch searchProvider;
	@Inject private ClientToolbar clientToolbar;
	@Getter @Inject private CustomIconManager customIconManager;
	@Inject private CustomIconOverlay customIconOverlay;
	@Getter @Inject private SkillIconProvider skillIconProvider;
	@Getter @Inject private BankTabNamesConfig config;

	@Getter private BankTabNamesPanel panel;
	private NavigationButton navButton;

	@Getter @Inject private Gson gson;

	/**
	 * Reusable overlay widget pool per tab. Widgets are recycled across non-destructive
	 * rebuilds (search, resize, etc.) to prevent unbounded accumulation.
	 *
	 * When a destructive script fires (BUILD, INIT), the client deletes all dynamic
	 * children from the tab container, invalidating every widget reference in the pool.
	 * We detect this and clear the pool so fresh widgets are created.
	 *
	 * This is the fix for the FPS leak: the original code created new children on every
	 * script fire (including non-destructive ones) and only hid the old ones. Over 10-15
	 * minutes, thousands of hidden widgets accumulated in the container.
	 */
	@SuppressWarnings("unchecked")
	private final List<Widget>[] iconOverlayPool = new List[MAX_TABS];

	/**
	 * How many widgets in the pool are currently active (visible) per tab.
	 */
	private final int[] activeOverlayCount = new int[MAX_TABS];

	/**
	 * Text overlay widgets per tab, parented on Bankmain.INFINITE so text
	 * is not clipped by the TABS container. One widget per tab (or null).
	 */
	private final Widget[] textOverlayPool = new Widget[MAX_TABS];

	/**
	 * Set true for a tab when a new icon child is created on a rebuild. A new
	 * icon child gets a higher child index than the existing text widget and
	 * would render on top of it, so the text widget is recreated once to return
	 * it to the top. This only fires when icons are added, never per frame, so
	 * it does not leak widgets the way unconditional recreation did.
	 */
	private final boolean[] textTopDirty = new boolean[MAX_TABS];

	/**
	 * Set to true when a destructive script fires, signaling that all pooled widget
	 * references are now stale and must be discarded before the next apply.
	 */
	private boolean poolInvalidated = true;

	@Getter private boolean bankOpen;

	// -----------------------------------------------------------------------
	// Drag-to-rearrange state
	// -----------------------------------------------------------------------

	/** True while the player is dragging a tab widget. */
	private boolean dragActive;

	/** The tab index where the drag started, or -1 if not dragging. */
	private int dragStartTab = -1;

	/** The tab index currently under the mouse during a drag, or -1. */
	private int dragHoverTab = -1;

	/**
	 * Ghost overlay widget that follows the cursor during a drag operation.
	 * Invalidated alongside the pool when destructive scripts fire.
	 */
	private Widget dragGhostWidget;

	@Provides
	BankTabNamesConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankTabNamesConfig.class);
	}

	@Override
	protected void startUp()
	{
		// --- Config version check and migration ---
		migrateConfigIfNeeded();

		for (int i = 0; i < MAX_TABS; i++)
		{
			iconOverlayPool[i] = new ArrayList<>();
			activeOverlayCount[i] = 0;
		}
		poolInvalidated = true;

		customIconManager.loadAll();
		customIconOverlay.register();

		panel = getInjector().getInstance(BankTabNamesPanel.class);
		panel.init(this);

		BufferedImage icon;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/bank-tab-icon.png");
		}
		catch (Exception e)
		{
			log.debug("bank-tab-icon.png not found, using fallback");
			icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		}

		navButton = NavigationButton.builder()
				.tooltip("Bank Tab Names")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		// Respect "hide side panel" config. The nav button is always built so we
		// can add/remove it dynamically, but only shown on the toolbar if not hidden.
		if (!config.hideSidePanel() && !config.onlyShowInBank())
		{
			clientToolbar.addNavigation(navButton);
		}

		clientThread.invokeLater(this::applyAllTabs);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		customIconOverlay.unregister();
		skillIconProvider.clearCache();
		clientThread.invokeLater(() ->
		{
			clearSkillSprites();
			clearGameSprites();
			hideAllPooledOverlays();
			destroyDragGhost();
		});
		bankOpen = false;
		dragActive = false;
		dragStartTab = -1;
		dragHoverTab = -1;
	}

	// -----------------------------------------------------------------------
	// Config persistence
	// -----------------------------------------------------------------------

	public TabConfig loadTabConfig(int tabIndex)
	{
		TabConfig tc = new TabConfig();

		String enabledStr = configManager.getConfiguration(CONFIG_GROUP, KEY_ENABLED + tabIndex);
		if (enabledStr != null)
		{
			tc.setEnabled(Boolean.parseBoolean(enabledStr));
		}

		String text = configManager.getConfiguration(CONFIG_GROUP, KEY_TEXT + tabIndex);
		if (text != null)
		{
			tc.setText(text);
		}

		String fontStr = configManager.getConfiguration(CONFIG_GROUP, KEY_FONT + tabIndex);
		if (fontStr != null)
		{
			try
			{
				tc.setFont(TabFonts.valueOf(fontStr));
			}
			catch (IllegalArgumentException ignored)
			{
			}
		}

		String fitStr = configManager.getConfiguration(CONFIG_GROUP, KEY_DISABLE_FITTING + tabIndex);
		if (fitStr != null)
		{
			tc.setDisableFitting(Boolean.parseBoolean(fitStr));
		}

		// Load icons list (new format)
		String iconsJson = configManager.getConfiguration(CONFIG_GROUP, KEY_ICONS_JSON + tabIndex);
		if (iconsJson != null && !iconsJson.isEmpty())
		{
			try
			{
				Type listType = new TypeToken<List<TabConfig.IconEntry>>(){}.getType();
				List<TabConfig.IconEntry> loaded = gson.fromJson(iconsJson, listType);
				if (loaded != null)
				{
					tc.setIcons(loaded);
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to parse icons JSON for tab {}: {}", tabIndex, e.getMessage());
			}
		}

		// Migrate legacy single-icon fields if no new-format icons exist
		if (tc.getIcons().isEmpty())
		{
			String modeStr = configManager.getConfiguration(CONFIG_GROUP, KEY_ICON_MODE + tabIndex);
			if (modeStr != null)
			{
				try
				{
					tc.setIconMode(TabConfig.IconMode.valueOf(modeStr));
				}
				catch (IllegalArgumentException ignored)
				{
				}
			}

			Integer itemId = configManager.getConfiguration(CONFIG_GROUP, KEY_ICON_ITEM + tabIndex, Integer.class);
			if (itemId != null)
			{
				tc.setIconItemId(itemId);
			}

			String customName = configManager.getConfiguration(CONFIG_GROUP, KEY_ICON_CUSTOM + tabIndex);
			if (customName != null)
			{
				tc.setIconCustomName(customName);
			}

			tc.migrateLegacyIcon();

			// If migration happened, persist the new format and clear legacy keys
			if (!tc.getIcons().isEmpty())
			{
				saveTabConfig(tabIndex, tc);
				configManager.unsetConfiguration(CONFIG_GROUP, KEY_ICON_MODE + tabIndex);
				configManager.unsetConfiguration(CONFIG_GROUP, KEY_ICON_ITEM + tabIndex);
				configManager.unsetConfiguration(CONFIG_GROUP, KEY_ICON_CUSTOM + tabIndex);
			}
		}

		return tc;
	}

	public void saveTabConfig(int tabIndex, TabConfig tc)
	{
		configManager.setConfiguration(CONFIG_GROUP, KEY_ENABLED + tabIndex, String.valueOf(tc.isEnabled()));
		configManager.setConfiguration(CONFIG_GROUP, KEY_TEXT + tabIndex, tc.getText());
		configManager.setConfiguration(CONFIG_GROUP, KEY_FONT + tabIndex, tc.getFont().name());
		configManager.setConfiguration(CONFIG_GROUP, KEY_DISABLE_FITTING + tabIndex, String.valueOf(tc.isDisableFitting()));

		// Persist icons as JSON
		String iconsJson = gson.toJson(tc.getIcons());
		configManager.setConfiguration(CONFIG_GROUP, KEY_ICONS_JSON + tabIndex, iconsJson);
	}

	/**
	 * Saves only the text-related fields (enabled, text, font) for a tab.
	 * Does NOT touch the icons config key. Called by the panel's saveToConfig
	 * to avoid clobbering icon data that may have been set by a concurrent
	 * icon add/remove operation.
	 */
	public void saveTabText(int tabIndex, boolean enabled, String text, TabFonts font)
	{
		configManager.setConfiguration(CONFIG_GROUP, KEY_ENABLED + tabIndex, String.valueOf(enabled));
		configManager.setConfiguration(CONFIG_GROUP, KEY_TEXT + tabIndex, text);
		configManager.setConfiguration(CONFIG_GROUP, KEY_FONT + tabIndex, font.name());
	}

	/**
	 * Saves only the icons JSON for a tab. Does NOT touch enabled, text, or font.
	 * Called by icon add/remove/swap operations to avoid clobbering text that may
	 * have been set by the panel or chatbox edit.
	 */
	public void saveTabIcons(int tabIndex, List<TabConfig.IconEntry> icons)
	{
		String iconsJson = gson.toJson(icons);
		configManager.setConfiguration(CONFIG_GROUP, KEY_ICONS_JSON + tabIndex, iconsJson);
	}

	/**
	 * Saves only the disableFitting flag for a tab.
	 */
	public void saveTabFitting(int tabIndex, boolean disableFitting)
	{
		configManager.setConfiguration(CONFIG_GROUP, KEY_DISABLE_FITTING + tabIndex,
				String.valueOf(disableFitting));
	}

	/**
	 * Checks the stored config version and runs any necessary migrations.
	 * Called once during startUp before any tab configs are loaded.
	 *
	 * To add a new migration:
	 *   1. Increment CURRENT_CONFIG_VERSION
	 *   2. Add a new "if (storedVersion < N)" block below
	 *   3. Document the change in the version history comment on CURRENT_CONFIG_VERSION
	 */
	private void migrateConfigIfNeeded()
	{
		String versionStr = configManager.getConfiguration(CONFIG_GROUP, KEY_CONFIG_VERSION);
		int storedVersion = 0;
		if (versionStr != null)
		{
			try
			{
				storedVersion = Integer.parseInt(versionStr);
			}
			catch (NumberFormatException ignored)
			{
			}
		}

		if (storedVersion >= CURRENT_CONFIG_VERSION)
		{
			return;
		}

		log.info("Bank Tab Names config version {} -> {}, running migrations",
				storedVersion, CURRENT_CONFIG_VERSION);

		// Migration: version 0 -> 1
		// Legacy single-icon fields are already handled by loadTabConfig's
		// migrateLegacyIcon() call, so no additional work needed here.
		// This block exists as a template for future migrations.
		if (storedVersion < 1)
		{
			log.info("Migration 0->1: legacy icon migration handled by loadTabConfig");
		}

		// Stamp the current version
		configManager.setConfiguration(CONFIG_GROUP, KEY_CONFIG_VERSION,
				String.valueOf(CURRENT_CONFIG_VERSION));
		log.info("Config migration complete, now at version {}", CURRENT_CONFIG_VERSION);
	}

	/**
	 * Returns the current config schema version. Can be used by the panel
	 * or external tools to check compatibility.
	 */
	public int getConfigVersion()
	{
		return CURRENT_CONFIG_VERSION;
	}

	// -----------------------------------------------------------------------
	// Event handlers
	// -----------------------------------------------------------------------

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		int scriptId = event.getScriptId();

		// Check if this is a destructive script that wipes TABS dynamic children.
		// Our icon/text overlays and drag ghost all live on Bankmain.INFINITE,
		// so they survive these scripts. Nothing to invalidate here.
		for (int id : DESTRUCTIVE_SCRIPTS)
		{
			if (id == scriptId)
			{
				break;
			}
		}

		for (int id : BANK_REBUILD_SCRIPTS)
		{
			if (id == scriptId)
			{
				boolean wasAlreadyOpen = bankOpen;
				bankOpen = true;
				applyAllTabs();

				// Auto-show panel when bank opens (if configured)
				if (!wasAlreadyOpen && config.onlyShowInBank())
				{
					showPanel();
				}
				break;
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			bankOpen = false;
			clearPoolRefs();
			poolInvalidated = true;
			dragActive = false;
			dragStartTab = -1;
			dragHoverTab = -1;
			dragGhostWidget = null;

			// Auto-hide panel when bank closes (if configured)
			if (config.onlyShowInBank() || config.hideSidePanel())
			{
				hidePanel();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(CONFIG_GROUP))
		{
			clientThread.invokeLater(this::applyAllTabs);
			if (panel != null)
			{
				panel.refreshFromConfig();
			}

			// Handle panel visibility config changes in real time
			String key = event.getKey();
			if ("hideSidePanel".equals(key) || "onlyShowInBank".equals(key))
			{
				SwingUtilities.invokeLater(this::updatePanelVisibility);
			}
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!bankOpen)
		{
			return;
		}

		String option = Text.removeTags(event.getOption());
		String target = Text.removeTags(event.getTarget());
		int tabIndex = -1;

		if (option.equals("View tab"))
		{
			try
			{
				tabIndex = Integer.parseInt(target.trim());
			}
			catch (NumberFormatException e)
			{
				return;
			}
		}
		else if (option.equals("View all items"))
		{
			tabIndex = 0;
		}

		if (tabIndex < 0 || tabIndex >= MAX_TABS)
		{
			return;
		}

		Widget tabContainer = client.getWidget(InterfaceID.Bankmain.TABS);
		if (tabContainer == null)
		{
			return;
		}

		final int idx = tabIndex;
		TabConfig tc = loadTabConfig(tabIndex);

		// Edit icons
		if (!tc.getIcons().isEmpty())
		{
			client.getMenu().createMenuEntry(-1)
					.setParam0(event.getActionParam0())
					.setParam1(event.getActionParam1())
					.setTarget(event.getTarget())
					.setOption(EDIT_ICONS)
					.setType(MenuAction.RUNELITE)
					.setIdentifier(tabIndex)
					.onClick(e -> onEditIcons(idx));
		}

		// Clear all icons
		if (!tc.getIcons().isEmpty())
		{
			client.getMenu().createMenuEntry(-1)
					.setParam0(event.getActionParam0())
					.setParam1(event.getActionParam1())
					.setTarget(event.getTarget())
					.setOption(CLEAR_ALL_ICONS)
					.setType(MenuAction.RUNELITE)
					.setIdentifier(tabIndex)
					.onClick(e -> onClearAllIcons(idx));
		}

		// Add custom icon (only if custom icons are available)
		if (!customIconManager.getIconNames().isEmpty())
		{
			client.getMenu().createMenuEntry(-1)
					.setParam0(event.getActionParam0())
					.setParam1(event.getActionParam1())
					.setTarget(event.getTarget())
					.setOption(SET_CUSTOM_ICON)
					.setType(MenuAction.RUNELITE)
					.setIdentifier(tabIndex)
					.onClick(e -> onSetCustomIcon(idx));
		}

		// Add game sprite
		client.getMenu().createMenuEntry(-1)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setTarget(event.getTarget())
				.setOption(SET_GAME_SPRITE)
				.setType(MenuAction.RUNELITE)
				.setIdentifier(tabIndex)
				.onClick(e -> onSetGameSprite(idx));

		// Add skill icon
		client.getMenu().createMenuEntry(-1)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setTarget(event.getTarget())
				.setOption(SET_SKILL_ICON)
				.setType(MenuAction.RUNELITE)
				.setIdentifier(tabIndex)
				.onClick(e -> onSetSkillIcon(idx));

		// Add item icon
		client.getMenu().createMenuEntry(-1)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setTarget(event.getTarget())
				.setOption(SET_ITEM_ICON)
				.setType(MenuAction.RUNELITE)
				.setIdentifier(tabIndex)
				.onClick(e -> onSetItemIcon(idx));

		// Clear text
		if (!tc.getText().isEmpty())
		{
			client.getMenu().createMenuEntry(-1)
					.setParam0(event.getActionParam0())
					.setParam1(event.getActionParam1())
					.setTarget(event.getTarget())
					.setOption(CLEAR_TEXT)
					.setType(MenuAction.RUNELITE)
					.setIdentifier(tabIndex)
					.onClick(e -> onClearText(idx));
		}

		// Edit text
		client.getMenu().createMenuEntry(-1)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setTarget(event.getTarget())
				.setOption(EDIT_TEXT)
				.setType(MenuAction.RUNELITE)
				.setIdentifier(tabIndex)
				.onClick(e -> onEditText(idx));
	}

	// -----------------------------------------------------------------------
	// Drag-to-rearrange: ClientTick handler
	// -----------------------------------------------------------------------

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!bankOpen)
		{
			return;
		}

		Widget tabContainer = client.getWidget(InterfaceID.Bankmain.TABS);
		if (tabContainer == null)
		{
			return;
		}

		Widget dragged = client.getDraggedWidget();
		boolean isDraggingTab = dragged != null
				&& dragged.getId() == tabContainer.getId();

		if (isDraggingTab && !dragActive)
		{
			// Drag just started
			dragActive = true;
			dragStartTab = resolveTabUnderMouse(tabContainer);
			dragHoverTab = dragStartTab;

			if (dragStartTab >= 0)
			{
				log.debug("Tab drag started from tab {}", dragStartTab);
				showDragGhost(tabContainer, dragStartTab);
			}
		}
		else if (isDraggingTab && dragActive)
		{
			// Drag in progress, update hover and ghost position
			int current = resolveTabUnderMouse(tabContainer);
			if (current >= 0 && current != dragHoverTab)
			{
				dragHoverTab = current;
			}
			updateDragGhostPosition();
		}
		else if (!isDraggingTab && dragActive)
		{
			// Drag ended
			dragActive = false;
			hideDragGhost();

			// Tab 0 is the "View all items" tab and cannot hold custom designs.
			// Swapping with it causes icon overlays to desync from bank content.
			if (dragStartTab > 0 && dragHoverTab > 0
					&& dragStartTab != dragHoverTab
					&& dragStartTab < MAX_TABS && dragHoverTab < MAX_TABS)
			{
				log.debug("Tab drag: swapping design {} <-> {}", dragStartTab, dragHoverTab);
				swapTabDesigns(dragStartTab, dragHoverTab);
			}

			dragStartTab = -1;
			dragHoverTab = -1;
		}
	}

	/**
	 * Determines which tab index the mouse cursor is currently hovering over
	 * by checking the bounding rectangles of tab children 10-19.
	 */
	private int resolveTabUnderMouse(Widget tabContainer)
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return -1;
		}

		for (int i = 0; i < MAX_TABS; i++)
		{
			Widget tab = tabContainer.getChild(TAB_CHILD_START + i);
			if (tab == null || tab.isHidden())
			{
				continue;
			}

			if (tab.getBounds().contains(mouse.getX(), mouse.getY()))
			{
				return i;
			}
		}

		return -1;
	}

	/**
	 * Swaps the entire custom design (text, font, enabled, icons) between two
	 * tab indices and persists the result.
	 */
	private void swapTabDesigns(int tabA, int tabB)
	{
		TabConfig configA = loadTabConfig(tabA);
		TabConfig configB = loadTabConfig(tabB);

		saveTabConfig(tabA, configB);
		saveTabConfig(tabB, configA);

		applyAllTabs();

		if (panel != null)
		{
			SwingUtilities.invokeLater(() -> panel.refreshFromConfig());
		}
	}

	// -----------------------------------------------------------------------
	// Drag ghost overlay
	// -----------------------------------------------------------------------

	/**
	 * Creates (or reuses) a ghost overlay widget that visually represents the
	 * tab design being dragged. The ghost follows the mouse cursor each tick.
	 * Parented on Bankmain.INFINITE so it renders above all icon/text overlays.
	 */
	private void showDragGhost(Widget tabContainer, int sourceTab)
	{
		TabConfig tc = loadTabConfig(sourceTab);

		// Only show ghost if the tab has some visible design
		if (tc.getText().isEmpty() && !tc.hasAnyIcon())
		{
			return;
		}

		// Parent on INFINITE for z-priority above all other overlays
		Widget ghostParent = client.getWidget(InterfaceID.Bankmain.INFINITE);
		if (ghostParent == null)
		{
			ghostParent = tabContainer;
		}

		// Reuse the existing ghost widget if we still have a valid reference.
		// Only create a new one if the reference is null (first drag, or after
		// bank close cleared it). This prevents orphaned hidden children from
		// piling up on INFINITE.
		if (dragGhostWidget == null)
		{
			dragGhostWidget = ghostParent.createChild(-1, WidgetType.TEXT);
			dragGhostWidget.setName("BTN_drag_ghost");
		}

		dragGhostWidget.setHidden(false);
		dragGhostWidget.setOriginalWidth(TAB_WIDTH);
		dragGhostWidget.setOriginalHeight(TAB_HEIGHT);
		dragGhostWidget.setXTextAlignment(1);
		dragGhostWidget.setYTextAlignment(1);
		dragGhostWidget.setTextShadowed(true);
		dragGhostWidget.setTextColor(0xFFFF00);
		dragGhostWidget.setOpacity(80);
		dragGhostWidget.setNoClickThrough(false);
		dragGhostWidget.setHasListener(false);

		String ghostText = tc.getText().isEmpty() ? "[tab " + sourceTab + "]" : tc.getText();
		dragGhostWidget.setText(ghostText);
		dragGhostWidget.setFontId(tc.getFont().tabFontId);

		updateDragGhostPosition();
		dragGhostWidget.revalidate();
	}

	/**
	 * Moves the ghost widget to follow the mouse cursor. Widget positions are
	 * relative to the ghost's parent (Bankmain.INFINITE), so we convert canvas
	 * coordinates using the parent's bounds.
	 */
	private void updateDragGhostPosition()
	{
		if (dragGhostWidget == null)
		{
			return;
		}

		Widget ghostParent = client.getWidget(InterfaceID.Bankmain.INFINITE);
		if (ghostParent == null)
		{
			// Fall back to TABS
			ghostParent = client.getWidget(InterfaceID.Bankmain.TABS);
		}
		if (ghostParent == null)
		{
			return;
		}

		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return;
		}

		// Convert canvas coords to parent-relative coords
		java.awt.Rectangle parentBounds = ghostParent.getBounds();
		int relX = mouse.getX() - parentBounds.x - TAB_WIDTH / 2;
		int relY = mouse.getY() - parentBounds.y - TAB_HEIGHT / 2;

		dragGhostWidget.setOriginalX(relX);
		dragGhostWidget.setOriginalY(relY);
		dragGhostWidget.revalidate();
	}

	/**
	 * Hides the drag ghost widget but keeps the reference for reuse.
	 * The reference is only nulled on bank close (clearPoolRefs / onWidgetClosed)
	 * when INFINITE's children are destroyed.
	 */
	private void hideDragGhost()
	{
		if (dragGhostWidget != null)
		{
			dragGhostWidget.setHidden(true);
		}
	}

	/**
	 * Fully discards the drag ghost reference. Called when the widget is known
	 * to be destroyed (bank close, plugin shutdown).
	 */
	private void destroyDragGhost()
	{
		if (dragGhostWidget != null)
		{
			dragGhostWidget.setHidden(true);
			dragGhostWidget = null;
		}
	}

	// -----------------------------------------------------------------------
	// Menu action handlers
	// -----------------------------------------------------------------------

	private void onEditText(int tabIndex)
	{
		TabConfig tc = loadTabConfig(tabIndex);

		// Show tag syntax help as a popup the first time (dismissible)
		if (!config.suppressTagHelp())
		{
			SwingUtilities.invokeLater(() ->
			{
				JCheckBox dontShow = new JCheckBox("Don't show this again");
				dontShow.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());

				Object[] message = {
						"Tag Reference for text editing:\n\n"
								+ "  <br>                    Line break\n"
								+ "  <col=HEX>          Color text (e.g. <col=FF0000> for red)\n"
								+ "  </col>                 Reset color to white\n\n"
								+ "Tip: You can also edit text directly in the\n"
								+ "plugin panel's text field where tags show as\n"
								+ "literal text. Use the color palette to insert tags.",
						dontShow
				};

				JOptionPane.showMessageDialog(panel, message,
						"Text Tag Help",
						JOptionPane.INFORMATION_MESSAGE);

				if (dontShow.isSelected())
				{
					configManager.setConfiguration(CONFIG_GROUP,
							"suppressTagHelp", "true");
				}
			});
		}

		chatboxPanelManager.openTextInput("Tab " + tabIndex + " text:")
				.value(tc.getText())
				.onDone((Consumer<String>) (text) ->
						clientThread.invokeLater(() ->
						{
							TabConfig current = loadTabConfig(tabIndex);
							saveTabText(tabIndex, current.isEnabled(), text, current.getFont());
							applyAllTabs();
							if (panel != null)
							{
								panel.refreshFromConfig();
							}
						}))
				.build();
	}

	private void onClearText(int tabIndex)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		saveTabText(tabIndex, tc.isEnabled(), "", tc.getFont());
		clientThread.invokeLater(this::applyAllTabs);
		if (panel != null)
		{
			panel.refreshFromConfig();
		}
	}

	private void onSetItemIcon(int tabIndex)
	{
		searchProvider
				.tooltipText("Choose item icon for tab " + tabIndex)
				.onItemSelected((itemId) ->
						clientThread.invokeLater(() ->
						{
							TabConfig tc = loadTabConfig(tabIndex);
							TabConfig.IconEntry entry = new TabConfig.IconEntry();
							entry.setMode(TabConfig.IconMode.ITEM);
							entry.setItemId(itemId);
							tc.getIcons().add(entry);
							saveTabIcons(tabIndex, tc.getIcons());
							applyAllTabs();
							if (panel != null)
							{
								panel.refreshFromConfig();
							}
						}))
				.build();
	}

	private void onSetSkillIcon(int tabIndex)
	{
		if (panel != null)
		{
			openPluginPanel();
			panel.openSkillIconPicker(tabIndex);
		}
	}

	private void onSetCustomIcon(int tabIndex)
	{
		if (panel != null)
		{
			openPluginPanel();
			panel.openIconPicker(tabIndex);
		}
	}

	private void onSetGameSprite(int tabIndex)
	{
		if (panel != null)
		{
			openPluginPanel();
			panel.openSpritePicker(tabIndex);
		}
	}

	public void addGameSprite(int tabIndex, int spriteArchiveId, int frame)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		TabConfig.IconEntry entry = new TabConfig.IconEntry();
		entry.setMode(TabConfig.IconMode.SPRITE);
		entry.setSpriteArchiveId(spriteArchiveId);
		entry.setSpriteFrame(frame);
		tc.getIcons().add(entry);
		saveTabIcons(tabIndex, tc.getIcons());
		clientThread.invokeLater(this::applyAllTabs);
	}

	/**
	 * Opens the plugin's side panel programmatically. If the panel is currently
	 * hidden (via hideSidePanel config), temporarily adds the nav button back
	 * to the toolbar so the panel can be opened. It will be removed again when
	 * the bank closes or the user manually changes config.
	 *
	 * Uses two chained invokeLater calls: the first ensures addNavigation has
	 * completed its layout pass, the second opens the panel once it is actually
	 * present on the toolbar. Without this separation, openPanel fires before
	 * Swing has finished adding the button and silently does nothing.
	 */
	private void openPluginPanel()
	{
		if (navButton != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				clientToolbar.addNavigation(navButton);
				SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
			});
		}
	}

	/**
	 * Shows the panel nav button on the toolbar (does not open the panel itself).
	 */
	private void showPanel()
	{
		if (navButton != null)
		{
			SwingUtilities.invokeLater(() -> clientToolbar.addNavigation(navButton));
		}
	}

	/**
	 * Hides the panel by removing the nav button from the toolbar.
	 */
	private void hidePanel()
	{
		if (navButton != null)
		{
			SwingUtilities.invokeLater(() -> clientToolbar.removeNavigation(navButton));
		}
	}

	/**
	 * Evaluates the current config and bank state to decide whether the panel
	 * nav button should be visible on the toolbar. Called when config changes.
	 */
	private void updatePanelVisibility()
	{
		if (navButton == null)
		{
			return;
		}

		boolean shouldShow;
		if (config.hideSidePanel())
		{
			// Hidden unless bank is open and a menu action forced it visible
			shouldShow = bankOpen;
		}
		else if (config.onlyShowInBank())
		{
			shouldShow = bankOpen;
		}
		else
		{
			shouldShow = true;
		}

		if (shouldShow)
		{
			clientToolbar.addNavigation(navButton);
		}
		else
		{
			clientToolbar.removeNavigation(navButton);
		}
	}

	public void addSkillIcon(int tabIndex, Skill skill)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		TabConfig.IconEntry entry = new TabConfig.IconEntry();
		entry.setMode(TabConfig.IconMode.SKILL);
		entry.setSkillOrdinal(skill.ordinal());
		tc.getIcons().add(entry);
		saveTabIcons(tabIndex, tc.getIcons());
		clientThread.invokeLater(this::applyAllTabs);
	}

	public void setCustomIcon(int tabIndex, String fileName)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		TabConfig.IconEntry entry = new TabConfig.IconEntry();
		entry.setMode(TabConfig.IconMode.CUSTOM);
		entry.setCustomName(fileName);
		tc.getIcons().add(entry);
		saveTabIcons(tabIndex, tc.getIcons());
		clientThread.invokeLater(this::applyAllTabs);
	}

	private void onClearAllIcons(int tabIndex)
	{
		saveTabIcons(tabIndex, new ArrayList<>());
		clientThread.invokeLater(this::applyAllTabs);
		if (panel != null)
		{
			panel.refreshFromConfig();
		}
	}

	private void onEditIcons(int tabIndex)
	{
		if (panel != null)
		{
			openPluginPanel();
			panel.openIconEditor(tabIndex);
		}
	}

	/**
	 * Update manual size for a specific icon entry and re-render.
	 */
	public void updateIconSize(int tabIndex, int iconIndex, int width, int height)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		if (iconIndex >= 0 && iconIndex < tc.getIcons().size())
		{
			TabConfig.IconEntry entry = tc.getIcons().get(iconIndex);
			entry.setManualWidth(width);
			entry.setManualHeight(height);
			saveTabIcons(tabIndex, tc.getIcons());
			clientThread.invokeLater(this::applyAllTabs);
		}
	}

	/**
	 * Update the X/Y offset for a specific icon entry and re-render.
	 */
	public void updateIconOffset(int tabIndex, int iconIndex, int offsetX, int offsetY)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		if (iconIndex >= 0 && iconIndex < tc.getIcons().size())
		{
			TabConfig.IconEntry entry = tc.getIcons().get(iconIndex);
			entry.setOffsetX(offsetX);
			entry.setOffsetY(offsetY);
			saveTabIcons(tabIndex, tc.getIcons());
			clientThread.invokeLater(this::applyAllTabs);
		}
	}

	/**
	 * Update the render priority (z-index) for a specific icon entry and re-render.
	 * Higher values draw on top of lower values.
	 */
	public void updateIconZIndex(int tabIndex, int iconIndex, int zIndex)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		if (iconIndex >= 0 && iconIndex < tc.getIcons().size())
		{
			TabConfig.IconEntry entry = tc.getIcons().get(iconIndex);
			entry.setZIndex(zIndex);
			saveTabIcons(tabIndex, tc.getIcons());
			clientThread.invokeLater(this::applyAllTabs);
		}
	}

	/**
	 * Remove a specific icon entry by index.
	 */
	public void removeIcon(int tabIndex, int iconIndex)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		if (iconIndex >= 0 && iconIndex < tc.getIcons().size())
		{
			tc.getIcons().remove(iconIndex);
			saveTabIcons(tabIndex, tc.getIcons());
			clientThread.invokeLater(this::applyAllTabs);
			if (panel != null)
			{
				panel.refreshFromConfig();
			}
		}
	}

	/**
	 * Swap two icons within the same tab and re-render.
	 */
	public void swapIcons(int tabIndex, int indexA, int indexB)
	{
		TabConfig tc = loadTabConfig(tabIndex);
		List<TabConfig.IconEntry> icons = tc.getIcons();

		if (indexA < 0 || indexA >= icons.size() || indexB < 0 || indexB >= icons.size())
		{
			return;
		}

		Collections.swap(icons, indexA, indexB);
		saveTabIcons(tabIndex, icons);
		clientThread.invokeLater(this::applyAllTabs);
		if (panel != null)
		{
			panel.refreshFromConfig();
		}
	}

	/**
	 * Reload custom icons from both bundled resources and the user's disk folder,
	 * re-register all sprite overrides, and refresh the display.
	 *
	 * Also resets skill sprite registrations since the widget sprite cache is
	 * cleared during the reload, which invalidates the cached textures for
	 * any previously registered skill sprites.
	 */
	public void reloadCustomIcons()
	{
		customIconOverlay.unregister();
		customIconManager.reloadUserIcons();
		customIconOverlay.register();

		// The unregister() call above resets the widget sprite cache, which
		// evicts all rendered textures including skill sprites. Mark all skill
		// sprites as unregistered so they get re-inserted on the next apply.
		Arrays.fill(skillSpritesRegistered, false);

		// Also evict game sprite overrides (non-zero frame registrations).
		// They'll be re-registered lazily during the next applyAllTabs.
		for (int id : gameSpriteMap.values())
		{
			client.getSpriteOverrides().remove(id);
		}
		gameSpriteMap.clear();

		clientThread.invokeLater(this::applyAllTabs);
		if (panel != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				panel.closeAllPickers();
				panel.refreshFromConfig();
			});
		}
	}

	// -----------------------------------------------------------------------
	// Tab rendering
	// -----------------------------------------------------------------------

	/**
	 * Apply all tab configurations. This does two things per tab:
	 * 1. Primes enabled tabs (remove default item, convert to text, set text content)
	 * 2. Creates/reuses icon overlay widgets for tabs that have icons configured
	 *
	 * <b>FPS leak fix:</b> Widget overlay objects are pooled per tab and reused across
	 * non-destructive script rebuilds (search, resize, size check). When a destructive
	 * script fires (BUILD, INIT), the pool is invalidated because the client has
	 * deleted all dynamic children, and fresh widgets are created on the next apply.
	 *
	 * Non-destructive scripts (search refresh, size check, etc.) reuse existing pool
	 * entries by hiding them all at the start, then selectively unhiding and
	 * reconfiguring the ones needed. No new widgets are created unless the pool is
	 * too small, which only happens when the user adds more icons than before.
	 */
	private void applyAllTabs()
	{
		Widget tabContainer = client.getWidget(InterfaceID.Bankmain.TABS);
		if (tabContainer == null)
		{
			return;
		}

		// Clean up any lingering drag ghost from a same-tab drop or rebuild
		if (!dragActive)
		{
			hideDragGhost();
		}

		// Resolve the top-level bank container for icon overlays. Parenting
		// overlays here instead of on TABS avoids the tab bar clipping children
		// to its own bounds, allowing icons to extend beyond the tab area for
		// decorative offset/size use cases.
		Widget overlayParent = client.getWidget(InterfaceID.Bankmain.INFINITE);
		if (overlayParent == null)
		{
			// Fall back to TABS if INFINITE isn't available
			overlayParent = tabContainer;
		}

		// Compute the offset from TABS coordinate space to the overlay parent's
		// coordinate space. Walk from TABS up to INFINITE accumulating relative
		// positions. If they're the same widget (fallback), offset is 0,0.
		int tabsOffsetX = 0;
		int tabsOffsetY = 0;
		if (overlayParent != tabContainer)
		{
			Widget walker = tabContainer;
			while (walker != null && walker != overlayParent)
			{
				tabsOffsetX += walker.getRelativeX();
				tabsOffsetY += walker.getRelativeY();
				walker = walker.getParent();
			}
		}

		// In "First item in tab" display mode (varbit 0), the game positions
		// tab widgets 3px further right than in Digit/Roman modes to make room
		// for the item sprite. Since our overlays derive position from the tab
		// widget's originalX, we subtract 3 to align with the visible tab area.
		int modeOffsetX = 0;
		try
		{
			int tabDisplay = client.getVarbitValue(
					net.runelite.api.gameval.VarbitID.BANK_TAB_DISPLAY);
			if (tabDisplay == 0)
			{
				modeOffsetX = -3;
			}
		}
		catch (Exception ignored)
		{
			modeOffsetX = -3; // assume default (item) mode
		}

		// If a destructive script wiped our dynamic children, discard stale refs
		if (poolInvalidated)
		{
			clearPoolRefs();
			poolInvalidated = false;
		}
		else
		{
			// Non-destructive rebuild: hide all pooled overlays, we'll re-show as needed
			hideAllPooledOverlays();
		}

		// If the game has hidden the tab container (e.g. bank settings page is open),
		// keep our overlays hidden and don't render anything on top.
		if (tabContainer.isHidden() || tabContainer.isSelfHidden())
		{
			return;
		}

		// Also check if the first real tab is hidden, which happens when
		// sub-pages like settings or potion storage are displayed.
		Widget firstTab = tabContainer.getChild(TAB_CHILD_START);
		if (firstTab != null && firstTab.isSelfHidden())
		{
			return;
		}

		for (int tabIndex = 0; tabIndex < MAX_TABS; tabIndex++)
		{
			TabConfig tc = loadTabConfig(tabIndex);

			Widget tabWidget = tabContainer.getChild(TAB_CHILD_START + tabIndex);
			if (tabWidget == null)
			{
				continue;
			}

			if (!tc.isEnabled())
			{
				continue;
			}

			boolean hasText = !tc.getText().isEmpty();
			boolean hasIcon = tc.hasAnyIcon();

			// Skip if nothing configured
			if (!hasText && !hasIcon)
			{
				continue;
			}

			// Skip "New tab" buttons that shouldn't be modified
			if (tabWidget.getActions() != null && Arrays.asList(tabWidget.getActions()).contains("New tab"))
			{
				continue;
			}

			// --- Phase 1: Clear default tab display ---
			if (hasText || hasIcon)
			{
				clearTabDefaultDisplay(tabWidget, tabIndex);
			}

			// When disableFitting is true, icons and text ignore each other's
			// presence. Both render centered as if they're the only element.
			boolean fittingText = hasText && !tc.isDisableFitting();

			// --- Phase 2: Create/reuse icon overlay widget(s) ---
			// Icons are created before text so that text (created later) gets a
			// higher child index on INFINITE and renders on top of icons.
			if (hasIcon)
			{
				createIconOverlays(overlayParent, tabWidget, tc, fittingText, tabIndex,
						tabsOffsetX, tabsOffsetY, modeOffsetX);
			}

			// --- Phase 3: Create/reuse text overlay on INFINITE ---
			if (hasText)
			{
				createTextOverlay(overlayParent, tabWidget, tc, tabIndex,
						tabsOffsetX, tabsOffsetY, modeOffsetX);
			}
		}
	}

	/**
	 * Creates a text overlay widget on Bankmain.INFINITE for the given tab.
	 * Always creates a fresh widget (hiding any previous one) to guarantee it
	 * has a higher child index than icon overlays and renders on top.
	 */
	private void createTextOverlay(Widget overlayParent, Widget tabWidget, TabConfig tc,
								   int tabIndex, int tabsOffsetX, int tabsOffsetY,
								   int modeOffsetX)
	{
		int textX = tabWidget.getOriginalX() + tabsOffsetX + modeOffsetX;
		int textY = tabWidget.getOriginalY() + tabsOffsetY;

		// Reuse the pooled text widget. Recreating it every cycle (the previous
		// behaviour) only hid the old one and left it parented on INFINITE, so
		// hidden BTN_tabN_text widgets piled up without bound while the bank
		// stayed open, dragging frame rate down until the bank was closed.
		//
		// We only create a fresh widget when there isn't one yet, when a new
		// icon child was just added (textTopDirty, to restore text-on-top
		// z-order), or when the pooled ref is stale after a destructive rebuild.
		Widget textOverlay = textOverlayPool[tabIndex];
		boolean needNew = textOverlay == null
				|| textTopDirty[tabIndex]
				|| textOverlay.getParentId() != overlayParent.getId();

		if (needNew)
		{
			if (textOverlay != null)
			{
				textOverlay.setHidden(true);
			}
			textOverlay = overlayParent.createChild(-1, WidgetType.TEXT);
			textOverlay.setName("BTN_tab" + tabIndex + "_text");
			textOverlayPool[tabIndex] = textOverlay;
			textTopDirty[tabIndex] = false;
		}

		textOverlay.setOriginalX(textX);
		textOverlay.setOriginalY(textY);
		textOverlay.setOriginalWidth(TAB_WIDTH);
		textOverlay.setOriginalHeight(TAB_HEIGHT);
		textOverlay.setXTextAlignment(1);
		boolean shouldFitText = tc.hasAnyIcon() && !tc.isDisableFitting();
		textOverlay.setYTextAlignment(shouldFitText ? 2 : 1);
		textOverlay.setTextShadowed(true);
		textOverlay.setText(tc.getText());
		textOverlay.setFontId(tc.getFont().tabFontId);
		textOverlay.setTextColor(0xFFFFFF);
		textOverlay.setNoClickThrough(false);
		textOverlay.setHasListener(false);
		textOverlay.setHidden(false);
		textOverlay.revalidate();
	}

	/**
	 * Clears the default display of a tab widget without adding text.
	 * Used when only icons are configured (no text).
	 */
	private void clearTabDefaultDisplay(Widget tabWidget, int tabIndex)
	{
		tabWidget.setOpacity(0);
		tabWidget.setOriginalY(0);
		tabWidget.setOriginalWidth(TAB_WIDTH);
		tabWidget.setOriginalHeight(TAB_HEIGHT);
		tabWidget.setItemId(-1);
		tabWidget.setType(4);
		tabWidget.setText("");
		tabWidget.setTextShadowed(true);

		tabWidget.revalidate();
	}

	/**
	 * Creates one or more icon overlay widgets on top of a tab button.
	 * Uses the widget pool to recycle overlays when possible.
	 *
	 * @param container     the parent widget for overlay children (typically Bankmain.INFINITE)
	 * @param tabWidget     the tab button widget whose position we overlay on
	 * @param tc            the tab's config
	 * @param hasText       whether the tab has text configured
	 * @param tabIndex      index of the tab (0-9)
	 * @param tabsOffsetX   X offset from TABS coordinate space to the overlay parent
	 * @param tabsOffsetY   Y offset from TABS coordinate space to the overlay parent
	 */
	private void createIconOverlays(Widget container, Widget tabWidget, TabConfig tc,
									boolean hasText, int tabIndex,
									int tabsOffsetX, int tabsOffsetY, int modeOffsetX)
	{
		List<TabConfig.IconEntry> entries = tc.getIcons();
		if (entries.isEmpty())
		{
			return;
		}

		int tabX = tabWidget.getOriginalX() + tabsOffsetX + modeOffsetX;
		int tabY = tabWidget.getOriginalY() + tabsOffsetY;

		// Total available area - always use the full tab dimensions. Icons are
		// never scaled down to fit text. Instead they are offset upward when text
		// is present, relying on the INFINITE parent's unclipped rendering to
		// display them above the text line without squishing.
		int totalAvailW = TAB_WIDTH;
		int availH = TAB_HEIGHT;

		int iconCount = entries.size();
		boolean stacking = tc.isDisableFitting();
		// When fitting is disabled, all icons share the full tab area and stack
		// on top of each other (controlled by z-index). When fitting is enabled,
		// icons are divided into equal-width slots arranged left-to-right.
		int slotWidth = stacking ? totalAvailW : totalAvailW / iconCount;

		// Build index array sorted by zIndex (ascending) so lower-z icons are
		// created first (drawn behind). Position slots are still assigned by
		// the original list order so icons stay left-to-right as configured.
		Integer[] renderOrder = new Integer[iconCount];
		for (int i = 0; i < iconCount; i++)
		{
			renderOrder[i] = i;
		}
		Arrays.sort(renderOrder, (a, b) -> Integer.compare(
				entries.get(a).getZIndex(), entries.get(b).getZIndex()));

		int used = 0;
		for (int ri = 0; ri < iconCount; ri++)
		{
			int i = renderOrder[ri];
			TabConfig.IconEntry entry = entries.get(i);
			// When stacking, all icons share the same origin. When fitting,
			// each icon gets its own slot offset left-to-right.
			int slotX = stacking ? tabX : tabX + 1 + (i * slotWidth);

			Widget iconWidget = acquirePooledOverlay(container, tabIndex, used);
			boolean configured = configureSingleIconOverlay(iconWidget, entry, slotX, tabY,
					slotWidth, availH, hasText);

			if (configured)
			{
				iconWidget.setHidden(false);
				iconWidget.revalidate();
				used++;
			}
			else
			{
				iconWidget.setHidden(true);
			}
		}

		activeOverlayCount[tabIndex] = used;
	}

	/**
	 * Gets a widget from the pool for the given tab at the given pool index.
	 * If the pool doesn't have enough widgets, a new child is created and added.
	 */
	private Widget acquirePooledOverlay(Widget container, int tabIndex, int poolIndex)
	{
		List<Widget> pool = iconOverlayPool[tabIndex];

		if (poolIndex < pool.size())
		{
			return pool.get(poolIndex);
		}

		// Pool exhausted, create a new widget
		Widget w = container.createChild(-1, WidgetType.GRAPHIC);
		w.setName("BTN_tab" + tabIndex + "_icon" + poolIndex);
		pool.add(w);
		// This new icon child outranks the tab's text widget in child index, so
		// flag the text widget to be recreated on top during Phase 3.
		textTopDirty[tabIndex] = true;
		return w;
	}

	/**
	 * Configures a single pooled overlay widget for one icon entry within a tab slot.
	 * Returns true if the icon was successfully configured, false if it should be hidden.
	 */
	private boolean configureSingleIconOverlay(Widget icon, TabConfig.IconEntry entry,
											   int slotX, int tabY, int slotW, int slotH,
											   boolean hasText)
	{
		int iconW, iconH, iconX, iconY;

		// Reset widget state for reuse
		icon.setItemId(-1);
		icon.setSpriteId(-1);
		icon.setType(WidgetType.GRAPHIC);

		switch (entry.getMode())
		{
			case ITEM:
			{
				int srcW = Constants.ITEM_SPRITE_WIDTH;
				int srcH = Constants.ITEM_SPRITE_HEIGHT;
				entry.setCachedNativeWidth(srcW);
				entry.setCachedNativeHeight(srcH);

				if (entry.hasManualSize())
				{
					iconW = entry.getManualWidth() > 0 ? entry.getManualWidth() : srcW;
					iconH = entry.getManualHeight() > 0 ? entry.getManualHeight() : srcH;
				}
				else
				{
					int[] scaled = scaleToFit(srcW, srcH, slotW, slotH);
					iconW = scaled[0];
					iconH = scaled[1];
				}

				iconX = slotX + (slotW - iconW) / 2 + entry.getOffsetX();
				iconY = (hasText ? tabY : tabY + (TAB_HEIGHT - iconH) / 2) + entry.getOffsetY();

				icon.setOriginalX(iconX);
				icon.setOriginalY(iconY);
				icon.setOriginalWidth(iconW);
				icon.setOriginalHeight(iconH);

				icon.setItemId(entry.getItemId());
				icon.setItemQuantity(-1);
				icon.setBorderType(1);
				icon.setItemQuantityMode(ItemQuantityMode.NEVER);
				break;
			}

			case SKILL:
			{
				Skill skill = SkillIconProvider.skillFromOrdinal(entry.getSkillOrdinal());
				if (skill == null)
				{
					return false;
				}

				// Use the large (full-res) skill icon for rendering. The small variant
				// is only ~16px and looks blurry when the widget stretches it.
				BufferedImage img = skillIconProvider.getSkillIconByOrdinal(entry.getSkillOrdinal(), false);
				if (img == null)
				{
					// Fall back to small if large is unavailable
					img = skillIconProvider.getSkillIconByOrdinal(entry.getSkillOrdinal(), true);
				}
				if (img == null)
				{
					return false;
				}

				int srcW = img.getWidth();
				int srcH = img.getHeight();
				entry.setCachedNativeWidth(srcW);
				entry.setCachedNativeHeight(srcH);

				if (entry.hasManualSize())
				{
					iconW = entry.getManualWidth() > 0 ? entry.getManualWidth() : srcW;
					iconH = entry.getManualHeight() > 0 ? entry.getManualHeight() : srcH;
				}
				else
				{
					int[] scaled = scaleToFit(srcW, srcH, slotW, slotH);
					iconW = scaled[0];
					iconH = scaled[1];
				}

				iconX = slotX + (slotW - iconW) / 2 + entry.getOffsetX();
				iconY = (hasText ? tabY : tabY + (TAB_HEIGHT - iconH) / 2) + entry.getOffsetY();

				icon.setOriginalX(iconX);
				icon.setOriginalY(iconY);
				icon.setOriginalWidth(iconW);
				icon.setOriginalHeight(iconH);

				int spriteId = getOrRegisterSkillSprite(skill, img);
				if (spriteId == -1)
				{
					return false;
				}

				icon.setSpriteId(spriteId);
				break;
			}

			case CUSTOM:
			{
				int spriteId = customIconOverlay.getSpriteId(entry.getCustomName());
				if (spriteId == -1)
				{
					return false;
				}

				BufferedImage img = customIconManager.getIcon(entry.getCustomName());
				int srcW = img != null ? img.getWidth() : 32;
				int srcH = img != null ? img.getHeight() : 32;
				entry.setCachedNativeWidth(srcW);
				entry.setCachedNativeHeight(srcH);

				if (entry.hasManualSize())
				{
					iconW = entry.getManualWidth() > 0 ? entry.getManualWidth() : srcW;
					iconH = entry.getManualHeight() > 0 ? entry.getManualHeight() : srcH;
				}
				else
				{
					int[] scaled = scaleToFit(srcW, srcH, slotW, slotH);
					iconW = scaled[0];
					iconH = scaled[1];
				}

				iconX = slotX + (slotW - iconW) / 2 + entry.getOffsetX();
				iconY = (hasText ? tabY : tabY + (TAB_HEIGHT - iconH) / 2) + entry.getOffsetY();

				icon.setOriginalX(iconX);
				icon.setOriginalY(iconY);
				icon.setOriginalWidth(iconW);
				icon.setOriginalHeight(iconH);

				icon.setSpriteId(spriteId);
				break;
			}

			case SPRITE:
			{
				int archiveId = entry.getSpriteArchiveId();
				if (archiveId < 0)
				{
					return false;
				}

				int frame = entry.getSpriteFrame();

				// For frame 0 we can use the archive ID directly with setSpriteId().
				// For non-zero frames we must load the image, register it as a
				// sprite override with a synthetic ID, and reference that ID.
				int widgetSpriteId;
				if (frame == 0)
				{
					widgetSpriteId = archiveId;
				}
				else
				{
					widgetSpriteId = getOrRegisterGameSprite(archiveId, frame);
					if (widgetSpriteId == -1)
					{
						return false;
					}
				}

				// Get the real sprite canvas dimensions from SpritePixels.
				// SpriteManager.getSprite() returns a trimmed BufferedImage with
				// transparent padding removed, giving wrong (smaller) dimensions.
				// The widget engine renders using the full canvas size (maxWidth x
				// maxHeight), so we must match that or the sprite gets squished.
				int srcW = 32;
				int srcH = 32;
				SpritePixels[] rawSprites = client.getSprites(client.getIndexSprites(), archiveId, 0);
				if (rawSprites != null && frame < rawSprites.length && rawSprites[frame] != null)
				{
					srcW = rawSprites[frame].getMaxWidth();
					srcH = rawSprites[frame].getMaxHeight();
				}
				else
				{
					// Sprite archive not loaded yet, defer to next rebuild
					return false;
				}
				entry.setCachedNativeWidth(srcW);
				entry.setCachedNativeHeight(srcH);
				spriteDimensionCache.put(archiveId + ":" + frame, new int[]{srcW, srcH});

				if (entry.hasManualSize())
				{
					iconW = entry.getManualWidth() > 0 ? entry.getManualWidth() : srcW;
					iconH = entry.getManualHeight() > 0 ? entry.getManualHeight() : srcH;
				}
				else
				{
					int[] scaled = scaleToFit(srcW, srcH, slotW, slotH);
					iconW = scaled[0];
					iconH = scaled[1];
				}

				iconX = slotX + (slotW - iconW) / 2 + entry.getOffsetX();
				iconY = (hasText ? tabY : tabY + (TAB_HEIGHT - iconH) / 2) + entry.getOffsetY();

				icon.setOriginalX(iconX);
				icon.setOriginalY(iconY);
				icon.setOriginalWidth(iconW);
				icon.setOriginalHeight(iconH);

				icon.setSpriteId(widgetSpriteId);
				break;
			}

			default:
				return false;
		}

		// Allow clicks to pass through to the actual tab button
		icon.setNoClickThrough(false);
		icon.setHasListener(false);

		return true;
	}

	// -----------------------------------------------------------------------
	// Skill sprite registration
	// -----------------------------------------------------------------------

	/**
	 * Sprite ID base for skill icons. Deterministic mapping: each Skill
	 * enum ordinal maps to exactly one sprite ID via (SKILL_SPRITE_BASE - ordinal).
	 * Attack is always -600, Strength is always -601, etc.
	 */
	private static final int SKILL_SPRITE_BASE = -600;

	/**
	 * Tracks which skill ordinals have had their SpritePixels inserted into
	 * client.getSpriteOverrides(). Reset when the widget sprite cache is cleared
	 * (e.g. during icon reload) to ensure re-insertion.
	 */
	private final boolean[] skillSpritesRegistered = new boolean[Skill.values().length];

	/**
	 * Gets (or lazily registers) a sprite override for the given skill icon.
	 * Must be called on the client thread.
	 */
	private int getOrRegisterSkillSprite(Skill skill, BufferedImage image)
	{
		int ordinal = skill.ordinal();
		int spriteId = SKILL_SPRITE_BASE - ordinal;

		if (skillSpritesRegistered[ordinal])
		{
			// Verify the override is still present (may have been evicted)
			if (client.getSpriteOverrides().containsKey(spriteId))
			{
				return spriteId;
			}
			skillSpritesRegistered[ordinal] = false;
		}

		try
		{
			SpritePixels sp = ImageUtil.getImageSpritePixels(image, client);
			if (sp == null)
			{
				log.warn("Failed to convert skill icon to SpritePixels for {}", skill.getName());
				return -1;
			}

			client.getSpriteOverrides().put(spriteId, sp);
			skillSpritesRegistered[ordinal] = true;
			log.debug("Registered skill sprite for {} with ID {}", skill.getName(), spriteId);
			return spriteId;
		}
		catch (Exception e)
		{
			log.warn("Failed to register skill sprite for {}: {}", skill.getName(), e.getMessage());
			return -1;
		}
	}

	/**
	 * Remove skill sprite overrides from the client. Called on shutdown.
	 */
	private void clearSkillSprites()
	{
		for (int i = 0; i < skillSpritesRegistered.length; i++)
		{
			if (skillSpritesRegistered[i])
			{
				client.getSpriteOverrides().remove(SKILL_SPRITE_BASE - i);
				skillSpritesRegistered[i] = false;
			}
		}
	}

	// -----------------------------------------------------------------------
	// Game sprite registration (for non-zero frame variants)
	// -----------------------------------------------------------------------

	/**
	 * Sprite ID base for game sprite frame overrides. Maps (archiveId, frame)
	 * pairs to synthetic negative sprite IDs so the widget renderer can display
	 * non-zero frames that setSpriteId(archiveId) alone cannot access.
	 *
	 * Frame 0 sprites don't need this, they use the archive ID directly.
	 */
	private static final int GAME_SPRITE_BASE = -700;

	/**
	 * Maps "archiveId:frame" to the synthetic sprite ID assigned.
	 */
	private final java.util.Map<String, Integer> gameSpriteMap = new java.util.HashMap<>();

	/**
	 * Caches actual pixel dimensions of game sprites, keyed by "archiveId:frame".
	 * Populated on the client thread during configureSingleIconOverlay.
	 * Read by the panel on the EDT for spinner initialization.
	 * Values are {width, height}.
	 */
	private final java.util.Map<String, int[]> spriteDimensionCache = new java.util.HashMap<>();

	/**
	 * Returns cached native dimensions for a game sprite, or null if not yet cached.
	 * Safe to call from any thread (reads only).
	 */
	public int[] getCachedSpriteDimensions(int archiveId, int frame)
	{
		return spriteDimensionCache.get(archiveId + ":" + frame);
	}

	private int nextGameSpriteId = GAME_SPRITE_BASE;

	/**
	 * Gets (or lazily registers) a sprite override for a game sprite archive at
	 * a specific frame index. Must be called on the client thread.
	 */
	private int getOrRegisterGameSprite(int archiveId, int frame)
	{
		String key = archiveId + ":" + frame;
		Integer existing = gameSpriteMap.get(key);

		if (existing != null && client.getSpriteOverrides().containsKey(existing))
		{
			return existing;
		}

		try
		{
			BufferedImage img = spriteManager.getSprite(archiveId, frame);
			if (img == null)
			{
				log.warn("SpriteManager returned null for archive {} frame {}", archiveId, frame);
				return -1;
			}

			SpritePixels sp = ImageUtil.getImageSpritePixels(img, client);
			if (sp == null)
			{
				return -1;
			}

			int syntheticId = (existing != null) ? existing : nextGameSpriteId--;
			client.getSpriteOverrides().put(syntheticId, sp);
			gameSpriteMap.put(key, syntheticId);
			log.debug("Registered game sprite override for {}:{} with ID {}", archiveId, frame, syntheticId);
			return syntheticId;
		}
		catch (Exception e)
		{
			log.warn("Failed to register game sprite {}:{}: {}", archiveId, frame, e.getMessage());
			return -1;
		}
	}

	/**
	 * Remove game sprite overrides from the client. Called on shutdown.
	 */
	private void clearGameSprites()
	{
		for (int id : gameSpriteMap.values())
		{
			client.getSpriteOverrides().remove(id);
		}
		gameSpriteMap.clear();
	}

	// -----------------------------------------------------------------------
	// Utility
	// -----------------------------------------------------------------------

	/**
	 * Scale dimensions to fit within maxW x maxH while preserving aspect ratio.
	 */
	static int[] scaleToFit(int srcW, int srcH, int maxW, int maxH)
	{
		if (srcW <= maxW && srcH <= maxH)
		{
			return new int[]{srcW, srcH};
		}

		double scaleW = (double) maxW / srcW;
		double scaleH = (double) maxH / srcH;
		double scale = Math.min(scaleW, scaleH);

		return new int[]{
				Math.max(1, (int) (srcW * scale)),
				Math.max(1, (int) (srcH * scale))
		};
	}

	/**
	 * Hides all pooled overlay widgets across all tabs without clearing refs.
	 * Used for non-destructive rebuilds where the widgets are still valid.
	 */
	private void hideAllPooledOverlays()
	{
		for (int i = 0; i < MAX_TABS; i++)
		{
			if (iconOverlayPool[i] != null)
			{
				for (Widget w : iconOverlayPool[i])
				{
					if (w != null)
					{
						w.setHidden(true);
					}
				}
			}
			activeOverlayCount[i] = 0;

			if (textOverlayPool[i] != null)
			{
				textOverlayPool[i].setHidden(true);
			}
		}
	}

	/**
	 * Clears all widget references from the pool. Called when widgets are known
	 * to be destroyed (bank close, plugin shutdown).
	 */
	private void clearPoolRefs()
	{
		for (int i = 0; i < MAX_TABS; i++)
		{
			if (iconOverlayPool[i] != null)
			{
				iconOverlayPool[i].clear();
			}
			activeOverlayCount[i] = 0;
			textOverlayPool[i] = null;
			textTopDirty[i] = false;
		}
	}

	private int parseTabIndex(String option)
	{
		try
		{
			return Integer.parseInt(option.substring("View tab ".length()).trim());
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	ClientThread getClientThread()
	{
		return clientThread;
	}
}