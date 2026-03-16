package psyda.banktabnames;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;

/**
 * Provides skill icons from RuneLite's built-in {@link SkillIconManager}.
 * Icons are loaded lazily and cached. Both full-size and small variants
 * are available.
 *
 * Skill icons are accessed by {@link Skill} ordinal, making them easy
 * to persist in config as a simple integer.
 */
@Slf4j
@Singleton
public class SkillIconProvider
{
	private final SkillIconManager skillIconManager;
	private final Map<Skill, BufferedImage> largeCache = new EnumMap<>(Skill.class);
	private final Map<Skill, BufferedImage> smallCache = new EnumMap<>(Skill.class);

	@Inject
	SkillIconProvider(SkillIconManager skillIconManager)
	{
		this.skillIconManager = skillIconManager;
	}

	/**
	 * Get the large skill icon for a given skill.
	 */
	public BufferedImage getSkillIcon(Skill skill, boolean small)
	{
		if (skill == null)
		{
			return null;
		}

		Map<Skill, BufferedImage> cache = small ? smallCache : largeCache;

		return cache.computeIfAbsent(skill, s ->
		{
			try
			{
				BufferedImage img = skillIconManager.getSkillImage(s, small);
				if (img != null)
				{
					log.debug("Loaded {} skill icon for {}: {}x{}", small ? "small" : "large",
						s.getName(), img.getWidth(), img.getHeight());
				}
				return img;
			}
			catch (Exception e)
			{
				log.warn("Failed to load skill icon for {}", s.getName(), e);
				return null;
			}
		});
	}

	/**
	 * Get a skill icon by ordinal. Returns null if ordinal is out of range.
	 */
	public BufferedImage getSkillIconByOrdinal(int ordinal, boolean small)
	{
		Skill[] skills = Skill.values();
		if (ordinal < 0 || ordinal >= skills.length)
		{
			return null;
		}
		return getSkillIcon(skills[ordinal], small);
	}

	/**
	 * Get the Skill enum value by ordinal, or null if invalid.
	 */
	public static Skill skillFromOrdinal(int ordinal)
	{
		Skill[] skills = Skill.values();
		if (ordinal < 0 || ordinal >= skills.length)
		{
			return null;
		}
		return skills[ordinal];
	}

	/**
	 * Scale an image to fit within the given bounds, preserving aspect ratio.
	 */
	public static BufferedImage scaleToFit(BufferedImage src, int maxW, int maxH)
	{
		if (src == null)
		{
			return null;
		}
		int srcW = src.getWidth();
		int srcH = src.getHeight();
		if (srcW <= maxW && srcH <= maxH)
		{
			return src;
		}
		double scale = Math.min((double) maxW / srcW, (double) maxH / srcH);
		int newW = Math.max(1, (int) (srcW * scale));
		int newH = Math.max(1, (int) (srcH * scale));

		BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, newW, newH, null);
		g.dispose();
		return scaled;
	}

	public void clearCache()
	{
		largeCache.clear();
		smallCache.clear();
	}
}
