package psyda.banktabnames;

import lombok.Getter;

@Getter
public enum TabFonts
{
	PLAIN_11("Plain 11", 494),
	PLAIN_12("Plain 12", 495),
	BOLD_12("Bold 12", 496),
	QUILL_8("Quill 8", 497),
	QUILL_MEDIUM("Quill Medium", 645),
	BARBARIAN("Barbarian", 764),
	SUROK("Surok", 819),
	VERDANA_11("Verdana 11", 1442),
	VERDANA_11_BOLD("Verdana 11 Bold", 1443),
	TAHOMA_11("Tahoma 11", 1444),
	VERDANA_13("Verdana 13", 1445),
	VERDANA_13_BOLD("Verdana 13 Bold", 1446),
	VERDANA_15("Verdana 15", 1447);

	private final String displayName;
	public final int tabFontId;

	TabFonts(String displayName, int tabFontId)
	{
		this.displayName = displayName;
		this.tabFontId = tabFontId;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
