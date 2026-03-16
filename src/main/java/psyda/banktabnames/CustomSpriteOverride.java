package psyda.banktabnames;

import lombok.Getter;
import net.runelite.client.game.SpriteOverride;

/**
 * A SpriteOverride backed by a PNG loaded from the plugin's resource folder.
 * SpriteManager loads the image from the classpath using getFileName().
 */
public class CustomSpriteOverride implements SpriteOverride
{
	@Getter
	private final int spriteId;

	@Getter
	private final String fileName;

	public CustomSpriteOverride(int spriteId, String resourceFileName)
	{
		this.spriteId = spriteId;
		// SpriteManager resolves this relative to the class that registered it,
		// but sprite overrides use absolute resource paths within the jar.
		this.fileName = resourceFileName;
	}
}
