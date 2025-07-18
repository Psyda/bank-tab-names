package psyda.banktabnames;

public enum TabFonts {
    PLAIN_11("PLAIN_11", 494),
    PLAIN_12("PLAIN_12", 495),
    BOLD_12("BOLD_12", 496),
    QUILL_8("QUILL_8", 497),
    QUILL_MEDIUM("QUILL_MEDIUM", 645),
    BARBARIAN("BARBARIAN", 764),
    SUROK("SUROK", 819),
    VERDANA_11("VERDANA_11", 1442),
    VERDANA_11_BOLD("VERDANA_11_BOLD", 1443),
    TAHOMA_11("TAHOMA_11", 1444),
    VERDANA_13("VERDANA_13", 1445),
    VERDANA_13_BOLD("VERDANA_13_BOLD", 1446),
    VERDANA_15("VERDANA_15", 1447),
    ;

    private final String tabFontName;
    public final int tabFontId;

    TabFonts(String tabFontName) {
        this.tabFontName = tabFontName;
        this.tabFontId = 0;
    }

    TabFonts(String tabFontName, int tabFontId) {
        this.tabFontName = tabFontName;
        this.tabFontId = tabFontId;
    }
}