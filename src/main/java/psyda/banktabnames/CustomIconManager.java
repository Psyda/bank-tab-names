package psyda.banktabnames;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Discovers and caches all PNG/JPG/GIF images from two locations:
 *
 * 1. Bundled resources: the plugin's classpath resource folder (icons/).
 *    These ship with the plugin jar.
 *
 * 2. User-uploaded icons: ~/.runelite/banktabnames/icons/
 *    Users can drop PNGs here at any time. Call {@link #loadAll()} or
 *    {@link #reloadUserIcons()} to pick up new files without restarting.
 *
 * Images are stored by their filename (with extension) as the key.
 * If a user icon has the same filename as a bundled one, the user icon wins.
 */
@Slf4j
@Singleton
public class CustomIconManager
{
	private static final String ICONS_RESOURCE_PATH = "/icons/";
	private static final String USER_ICONS_DIR = "banktabnames/icons";
	private static final String[] SUPPORTED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif"};

	/**
	 * Map of filename (e.g. "slayer.png") to loaded BufferedImage.
	 * Insertion-ordered so the panel displays them in a consistent order.
	 */
	@Getter
	private final Map<String, BufferedImage> icons = new LinkedHashMap<>();

	/**
	 * Sorted list of icon filenames for display in the picker.
	 */
	@Getter
	private final List<String> iconNames = new ArrayList<>();

	/**
	 * The resolved path to the user icons directory on disk.
	 * Created on first call to {@link #loadAll()} if it does not exist.
	 */
	@Getter
	private File userIconsDir;

	@Inject
	CustomIconManager()
	{
	}

	/**
	 * Scan both the bundled resource folder and the user icons folder,
	 * then load all images into memory. Called once during plugin startup.
	 */
	public void loadAll()
	{
		icons.clear();
		iconNames.clear();

		loadBundledIcons();
		loadUserIcons();

		Collections.sort(iconNames, String.CASE_INSENSITIVE_ORDER);
		log.info("Loaded {} total custom icons", icons.size());
	}

	/**
	 * Reload only the user icons folder. Useful when the user drops new
	 * files in at runtime without restarting the plugin.
	 */
	public void reloadUserIcons()
	{
		loadAll();
	}

	// -----------------------------------------------------------------------
	// Bundled resource icons
	// -----------------------------------------------------------------------

	private void loadBundledIcons()
	{
		try
		{
			URL dirUrl = getClass().getResource(ICONS_RESOURCE_PATH);
			if (dirUrl == null)
			{
				log.info("No bundled icons folder found at {}, skipping", ICONS_RESOURCE_PATH);
				return;
			}

			URI dirUri = dirUrl.toURI();
			Path dirPath;

			if ("jar".equals(dirUri.getScheme()))
			{
				FileSystem fs;
				try
				{
					fs = FileSystems.getFileSystem(dirUri);
				}
				catch (FileSystemNotFoundException e)
				{
					fs = FileSystems.newFileSystem(dirUri, Collections.emptyMap());
				}
				dirPath = fs.getPath(ICONS_RESOURCE_PATH);
			}
			else
			{
				dirPath = Paths.get(dirUri);
			}

			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath))
			{
				for (Path entry : stream)
				{
					String fileName = entry.getFileName().toString().toLowerCase();
					if (!isSupportedExtension(fileName))
					{
						continue;
					}

					try (InputStream is = Files.newInputStream(entry))
					{
						BufferedImage img = ImageIO.read(is);
						if (img != null)
						{
							String originalName = entry.getFileName().toString();
							icons.put(originalName, img);
							iconNames.add(originalName);
							log.debug("Loaded bundled icon: {} ({}x{})", originalName, img.getWidth(), img.getHeight());
						}
					}
					catch (IOException e)
					{
						log.warn("Failed to load bundled icon: {}", entry.getFileName(), e);
					}
				}
			}

			log.info("Loaded {} bundled icons from resources", icons.size());
		}
		catch (IOException | URISyntaxException e)
		{
			log.warn("Error scanning bundled icons folder", e);
		}
	}

	// -----------------------------------------------------------------------
	// User-uploaded icons from disk
	// -----------------------------------------------------------------------

	private void loadUserIcons()
	{
		try
		{
			userIconsDir = new File(RuneLite.RUNELITE_DIR, USER_ICONS_DIR);

			if (!userIconsDir.exists())
			{
				if (userIconsDir.mkdirs())
				{
					log.info("Created user icons directory: {}", userIconsDir.getAbsolutePath());
				}
				else
				{
					log.warn("Failed to create user icons directory: {}", userIconsDir.getAbsolutePath());
				}
				return;
			}

			File[] files = userIconsDir.listFiles();
			if (files == null)
			{
				return;
			}

			int count = 0;
			for (File file : files)
			{
				if (!file.isFile())
				{
					continue;
				}

				String fileName = file.getName().toLowerCase();
				if (!isSupportedExtension(fileName))
				{
					continue;
				}

				try
				{
					BufferedImage img = ImageIO.read(file);
					if (img != null)
					{
						String originalName = file.getName();
						// User icons override bundled ones with the same name
						if (!icons.containsKey(originalName))
						{
							iconNames.add(originalName);
						}
						icons.put(originalName, img);
						count++;
						log.debug("Loaded user icon: {} ({}x{})", originalName, img.getWidth(), img.getHeight());
					}
				}
				catch (IOException e)
				{
					log.warn("Failed to load user icon: {}", file.getName(), e);
				}
			}

			log.info("Loaded {} user icons from {}", count, userIconsDir.getAbsolutePath());
		}
		catch (Exception e)
		{
			log.warn("Error loading user icons", e);
		}
	}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	/**
	 * Get a custom icon image by filename.
	 */
	public BufferedImage getIcon(String fileName)
	{
		return icons.get(fileName);
	}

	/**
	 * Returns true if this icon came from the user's disk folder
	 * (as opposed to being bundled in the jar).
	 */
	public boolean isUserIcon(String fileName)
	{
		if (userIconsDir == null)
		{
			return false;
		}
		return new File(userIconsDir, fileName).exists();
	}

	/**
	 * Get the display name for a filename (strip extension).
	 */
	public static String getDisplayName(String fileName)
	{
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	/**
	 * Scale an image to fit within maxW x maxH while preserving aspect ratio.
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

	private static boolean isSupportedExtension(String fileNameLower)
	{
		for (String ext : SUPPORTED_EXTENSIONS)
		{
			if (fileNameLower.endsWith(ext))
			{
				return true;
			}
		}
		return false;
	}
}
