package psyda.banktabnames;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class CustomIconOverlay
{
	// Sprite ID range allocation across the plugin:
	//   -500 and below: custom icon overrides (this class)
	//   -600 and below: skill icon overrides (BankTabNamesPlugin.SKILL_SPRITE_BASE)
	//   -700 and below: game sprite frame overrides (BankTabNamesPlugin.GAME_SPRITE_BASE)
	private static final int SPRITE_ID_BASE = -500;

	private final SpriteManager spriteManager;
	private final CustomIconManager customIconManager;

	@Inject
	private Client client;

	// Maintain persistent mapping to prevent IDs from shifting when new files are inserted.
	private final Map<String, Integer> spriteIdMap = new HashMap<>();
	private int nextSpriteId = SPRITE_ID_BASE;

	private CustomSpriteOverride[] bundledOverrides;

	@Inject
	CustomIconOverlay(SpriteManager spriteManager, CustomIconManager customIconManager)
	{
		this.spriteManager = spriteManager;
		this.customIconManager = customIconManager;
	}

	public void register()
	{
		unregister();

		var iconNames = customIconManager.getIconNames();
		if (iconNames.isEmpty())
		{
			return;
		}

		// DO NOT sort iconNames here. customIconManager.getIconNames() returns a reference to
		// the shared list. It is already sorted case-insensitively by the manager.

		var bundled = new ArrayList<CustomSpriteOverride>();

		for (String name : iconNames)
		{
			// Assign a permanent ID for the lifecycle of the session
			int spriteId = spriteIdMap.computeIfAbsent(name, k -> nextSpriteId--);

			if (customIconManager.isUserIcon(name))
			{
				BufferedImage img = customIconManager.getIcon(name);
				if (img != null)
				{
					try
					{
						SpritePixels sp = ImageUtil.getImageSpritePixels(img, client);
						if (sp != null)
						{
							client.getSpriteOverrides().put(spriteId, sp);
						}
					}
					catch (Exception e)
					{
						log.warn("Failed to register user icon: {}", name, e);
					}
				}
			}
			else
			{
				bundled.add(new CustomSpriteOverride(spriteId, "/icons/" + name));
			}
		}

		if (!bundled.isEmpty())
		{
			bundledOverrides = bundled.toArray(new CustomSpriteOverride[0]);
			spriteManager.addSpriteOverrides(bundledOverrides);
		}
	}

	public void unregister()
	{
		if (bundledOverrides != null)
		{
			spriteManager.removeSpriteOverrides(bundledOverrides);
			bundledOverrides = null;
		}

		// Remove our assigned IDs from the client's sprite override map.
		// spriteManager.removeSpriteOverrides() above de-registers from the manager,
		// but the entries it injected into client.getSpriteOverrides() may linger.
		for (int id : spriteIdMap.values())
		{
			client.getSpriteOverrides().remove(id);
		}

		// DO NOT clear spriteIdMap. This ensures filenames map to the same ID after a reload.
		// spriteIdMap.clear();

		// Force the client to drop rendered textures and rebuild them on the next frame.
		if (client.getWidgetSpriteCache() != null)
		{
			client.getWidgetSpriteCache().reset();
		}
	}

	public int getSpriteId(String fileName)
	{
		Integer id = spriteIdMap.get(fileName);
		return id != null ? id : -1;
	}
}