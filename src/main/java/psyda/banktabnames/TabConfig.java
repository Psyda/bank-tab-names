package psyda.banktabnames;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Runtime configuration for a single bank tab. All fields are persisted
 * to RuneLite's ConfigManager under the {@link BankTabNamesPlugin#CONFIG_GROUP} group.
 *
 * Supports multiple icons per tab via the {@link #icons} list. Each icon entry
 * specifies its own mode (ITEM, CUSTOM, or SKILL), ID/name, and optional manual
 * size overrides. Icons are rendered left-to-right across the top of the tab
 * (or filling the whole tab if there is no text).
 */
@Data
public class TabConfig
{
	private boolean enabled = true;
	private String text = "";
	private TabFonts font = TabFonts.QUILL_8;

	/**
	 * When true, icons and text render at their default positions without
	 * trying to avoid each other. Icons stay centered, text stays centered.
	 * Useful when the user has created a background sprite for the tab and
	 * wants text overlaid on top without repositioning.
	 */
	private boolean disableFitting = false;

	/**
	 * Ordered list of icons to display on this tab.
	 * Empty list means no icons.
	 */
	private List<IconEntry> icons = new ArrayList<>();

	// ---- Legacy single-icon fields (kept for migration from old configs) ----

	/**
	 * @deprecated Use {@link #icons} list instead. Retained for config migration.
	 */
	private IconMode iconMode = IconMode.NONE;

	/**
	 * @deprecated Use {@link #icons} list instead.
	 */
	private int iconItemId = -1;

	/**
	 * @deprecated Use {@link #icons} list instead.
	 */
	private String iconCustomName = "";

	/**
	 * Returns true if this tab has any icon configured (either via new list or legacy fields).
	 */
	public boolean hasAnyIcon()
	{
		if (!icons.isEmpty())
		{
			return true;
		}
		return iconMode != IconMode.NONE;
	}

	/**
	 * Migrates legacy single-icon config into the icons list if needed.
	 * Called after loading from config. Idempotent.
	 */
	public void migrateLegacyIcon()
	{
		if (icons.isEmpty() && iconMode != IconMode.NONE)
		{
			IconEntry entry = new IconEntry();
			entry.setMode(iconMode);
			entry.setItemId(iconItemId);
			entry.setCustomName(iconCustomName);
			icons.add(entry);

			// Clear legacy fields so we don't re-migrate
			iconMode = IconMode.NONE;
			iconItemId = -1;
			iconCustomName = "";
		}
	}

	public enum IconMode
	{
		NONE,
		ITEM,
		CUSTOM,
		SKILL,
		SPRITE
	}

	/**
	 * A single icon entry within a tab's icon list.
	 */
	@Data
	public static class IconEntry
	{
		private IconMode mode = IconMode.NONE;

		/** For ITEM mode: the OSRS item ID. */
		private int itemId = -1;

		/** For CUSTOM mode: the resource file name (e.g. "slayer.png"). */
		private String customName = "";

		/** For SKILL mode: the Skill enum ordinal. */
		private int skillOrdinal = -1;

		/** For SPRITE mode: the game sprite archive ID. */
		private int spriteArchiveId = -1;

		/**
		 * For SPRITE mode: the file/frame index within the archive.
		 * Most sprites only have frame 0, but some archives contain
		 * multiple variants (e.g. archive 439 has skull variants at
		 * frames 0, 1, 2, etc.). Default 0.
		 */
		private int spriteFrame = 0;

		/**
		 * Manual width override. -1 means auto-fit.
		 */
		private int manualWidth = -1;

		/**
		 * Manual height override. -1 means auto-fit.
		 */
		private int manualHeight = -1;

		/**
		 * Manual X offset in pixels. Applied after centering calculation.
		 * Positive moves right, negative moves left. 0 means no adjustment.
		 * No bounds clamping: users can push icons well outside the tab area
		 * to decorate the bank interface freely.
		 */
		private int offsetX = 0;

		/**
		 * Manual Y offset in pixels. Applied after vertical positioning.
		 * Positive moves down, negative moves up. 0 means no adjustment.
		 */
		private int offsetY = 0;

		/**
		 * Render priority. Higher values draw on top of lower values.
		 * Icons with the same zIndex render in list order. Default 0.
		 */
		private int zIndex = 0;

		/**
		 * Cached native width of the source image, populated by the render path
		 * on the client thread. Transient so it is not persisted to config.
		 * -1 means not yet resolved.
		 */
		private transient int cachedNativeWidth = -1;

		/**
		 * Cached native height of the source image. Transient, not persisted.
		 * -1 means not yet resolved.
		 */
		private transient int cachedNativeHeight = -1;

		/**
		 * Returns true if the user has set a manual size for this icon.
		 */
		public boolean hasManualSize()
		{
			return manualWidth > 0 || manualHeight > 0;
		}

		/**
		 * Returns true if the user has set any position offset.
		 */
		public boolean hasOffset()
		{
			return offsetX != 0 || offsetY != 0;
		}
	}
}