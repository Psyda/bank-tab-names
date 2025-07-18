package psyda.banktabnames;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("BankTabNames")
public interface BankTabNamesConfig extends Config {

    @ConfigSection(name = "Config moved to the panel", description = "Settings for the first Bank tab", position = 1)
    String bankTabSection0 = "bankTabSection0";

    @ConfigSection(name = "on the right ->", description = "Settings for Bank tab 1", position = 2)
    String bankTabSection1 = "bankTabSection1";

    @ConfigSection(name = "Sorry for inconvenience", description = "Settings for Bank tab 2", position = 3)
    String bankTabSection2 = "bankTabSection2";

    @ConfigSection(name = "update allowed for more", description = "Settings for Bank tab 3", position = 4)
    String bankTabSection3 = "bankTabSection3";

    @ConfigSection(name = "features to be added", description = "Settings for Bank tab 4", position = 5)
    String bankTabSection4 = "bankTabSection4";

    @ConfigSection(name = "Thanks, Psyda", description = "Settings for Bank tab 5", position = 6)
    String bankTabSection5 = "bankTabSection5";

    @ConfigSection(name = "Bank Tab 6", description = "Settings for Bank tab 6", position = 7)
    String bankTabSection6 = "bankTabSection6";

    @ConfigSection(name = "Bank Tab 7", description = "Settings for Bank tab 7", position = 8)
    String bankTabSection7 = "bankTabSection7";

    @ConfigSection(name = "Bank Tab 8", description = "Settings for Bank tab 8", position = 9)
    String bankTabSection8 = "bankTabSection8";

    @ConfigSection(name = "Bank Tab 9", description = "Settings for Bank tab 9", position = 10)
    String bankTabSection9 = "bankTabSection9";

    // Tab 0 Configuration
    @ConfigItem(keyName = "disableMainTabName", name = "Keep Tab 0 Icon", description = "Keeps the Infinity Symbol for the Primary tab.", position = 1, section = bankTabSection0, hidden = true)
    default boolean disableMainTabName() { return false; }

    @ConfigItem(keyName = "disableTab0", name = "Keep Tab 0 Icon", description = "Keeps the Infinity Symbol for the Primary tab.", position = 2, section = bankTabSection0, hidden = true)
    default boolean disableTab0() { return disableMainTabName(); }

    @ConfigItem(keyName = "bankFont0", name = "Font", description = "Select a font for each tab.", position = 3, section = bankTabSection0, hidden = true)
    default TabFonts bankFont0() { return TabFonts.QUILL_8; }

    @ConfigItem(keyName = "tab0Name", name = "Bank Tab 0", description = "The name of your bank tab.", position = 4, section = bankTabSection0, hidden = true)
    default String tab0Name() { return ""; }

    // Tab 1 Configuration
    @ConfigItem(keyName = "disableTab1", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection1, hidden = true)
    default boolean disableTab1() { return false; }

    @ConfigItem(keyName = "bankFont1", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection1, hidden = true)
    default TabFonts bankFont1() { return TabFonts.QUILL_8; }

    @ConfigItem(keyName = "tab1Name", name = "Bank Tab 1", description = "The name of your bank tab.", position = 3, section = bankTabSection1, hidden = true)
    default String tab1Name() { return ""; }

    // Tab 2 Configuration
    @ConfigItem(keyName = "disableTab2", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection2, hidden = true)
    default boolean disableTab2() { return false; }

    @ConfigItem(keyName = "bankFont2", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection2, hidden = true)
    default TabFonts bankFont2() { return TabFonts.QUILL_8; }

    @ConfigItem(keyName = "tab2Name", name = "Bank Tab 2", description = "The name of your bank tab.", position = 3, section = bankTabSection2, hidden = true)
    default String tab2Name() { return ""; }

    // Tab 3-9 Configuration (all hidden for panel-only management)
    @ConfigItem(keyName = "disableTab3", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection3, hidden = true)
    default boolean disableTab3() { return false; }
    @ConfigItem(keyName = "bankFont3", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection3, hidden = true)
    default TabFonts bankFont3() { return TabFonts.QUILL_8; }
    @ConfigItem(keyName = "tab3Name", name = "Bank Tab 3", description = "The name of your bank tab.", position = 3, section = bankTabSection3, hidden = true)
    default String tab3Name() { return ""; }

    @ConfigItem(keyName = "disableTab4", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection4, hidden = true)
    default boolean disableTab4() { return false; }
    @ConfigItem(keyName = "bankFont4", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection4, hidden = true)
    default TabFonts bankFont4() { return TabFonts.QUILL_8; }
    @ConfigItem(keyName = "tab4Name", name = "Bank Tab 4", description = "The name of your bank tab.", position = 3, section = bankTabSection4, hidden = true)
    default String tab4Name() { return ""; }

    @ConfigItem(keyName = "disableTab5", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection5, hidden = true)
    default boolean disableTab5() { return false; }
    @ConfigItem(keyName = "bankFont5", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection5, hidden = true)
    default TabFonts bankFont5() { return TabFonts.QUILL_8; }
    @ConfigItem(keyName = "tab5Name", name = "Bank Tab 5", description = "The name of your bank tab.", position = 3, section = bankTabSection5, hidden = true)
    default String tab5Name() { return ""; }

    @ConfigItem(keyName = "disableTab6", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection6, hidden = true)
    default boolean disableTab6() { return false; }
    @ConfigItem(keyName = "bankFont6", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection6, hidden = true)
    default TabFonts bankFont6() { return TabFonts.QUILL_8; }
    @ConfigItem(keyName = "tab6Name", name = "Bank Tab 6", description = "The name of your bank tab.", position = 3, section = bankTabSection6, hidden = true)
    default String tab6Name() { return ""; }

    @ConfigItem(keyName = "disableTab7", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection7, hidden = true)
    default boolean disableTab7() { return false; }
    @ConfigItem(keyName = "bankFont7", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection7, hidden = true)
    default TabFonts bankFont7() { return TabFonts.QUILL_8; }
    @ConfigItem(keyName = "tab7Name", name = "Bank Tab 7", description = "The name of your bank tab.", position = 3, section = bankTabSection7, hidden = true)
    default String tab7Name() { return ""; }

    @ConfigItem(keyName = "disableTab8", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection8, hidden = true)
    default boolean disableTab8() { return false; }
    @ConfigItem(keyName = "bankFont8", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection8, hidden = true)
    default TabFonts bankFont8() { return TabFonts.QUILL_8; }
    @ConfigItem(keyName = "tab8Name", name = "Bank Tab 8", description = "The name of your bank tab.", position = 3, section = bankTabSection8, hidden = true)
    default String tab8Name() { return ""; }

    @ConfigItem(keyName = "disableTab9", name = "Disable For Tab", description = "Disables the plugin for this tab.", position = 1, section = bankTabSection9, hidden = true)
    default boolean disableTab9() { return false; }
    @ConfigItem(keyName = "bankFont9", name = "Font", description = "Select a font for each tab.", position = 2, section = bankTabSection9, hidden = true)
    default TabFonts bankFont9() { return TabFonts.QUILL_8; }
    @ConfigItem(keyName = "tab9Name", name = "Bank Tab 9", description = "The name of your bank tab.", position = 3, section = bankTabSection9, hidden = true)
    default String tab9Name() { return ""; }
}