package psyda.banktabnames;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BankTabNamesPlugin.CONFIG_GROUP)
public interface BankTabNamesConfig extends Config
{
    @ConfigSection(
            name = "Panel Visibility",
            description = "Control when the plugin side panel is shown",
            position = 0
    )
    String panelSection = "panelSection";

    @ConfigItem(
            keyName = "hideSidePanel",
            name = "Hide side panel",
            description = "Hides the plugin side panel from the toolbar. It will still appear when you use an Edit/Add action on a bank tab.",
            section = "panelSection",
            position = 0
    )
    default boolean hideSidePanel()
    {
        return false;
    }

    @ConfigItem(
            keyName = "onlyShowInBank",
            name = "Only show panel while in bank",
            description = "Automatically shows the side panel when the bank opens and hides it when the bank closes.",
            section = "panelSection",
            position = 1
    )
    default boolean onlyShowInBank()
    {
        return false;
    }

    @ConfigSection(
            name = "Drag protection",
            description = "Guards against accidentally swapping tab designs when clicking near a tab edge",
            position = 1
    )
    String dragSection = "dragSection";

    @ConfigItem(
            keyName = "dragMinDistance",
            name = "Minimum drag distance",
            description = "How far the cursor must move from the drag start before tab designs swap. Raise this if misclicks still shuffle your tabs, lower it (or set 0) if fast intentional drags aren't being picked up, for example with the Anti Drag plugin changing drag timing.",
            section = "dragSection",
            position = 0
    )
    @Range(max = 50)
    @Units(Units.PIXELS)
    default int dragMinDistance()
    {
        return 10;
    }

    @ConfigItem(
            keyName = "dragMinHoldMs",
            name = "Minimum hold time",
            description = "How long the mouse button must be held before tab designs swap. Raise this if misclicks still shuffle your tabs, lower it (or set 0) if fast intentional drags aren't being picked up.",
            section = "dragSection",
            position = 1
    )
    @Range(max = 500)
    @Units(Units.MILLISECONDS)
    default int dragMinHoldMs()
    {
        return 80;
    }

    @ConfigSection(
            name = "Import / Export",
            description = "Settings for config sharing",
            position = 2
    )
    String importExportSection = "importExportSection";

    @ConfigItem(
            keyName = "suppressImportVersionWarning",
            name = "Suppress import version warning",
            description = "Don't show the version mismatch warning when importing configs created on an older plugin version.",
            section = "importExportSection",
            position = 0
    )
    default boolean suppressImportVersionWarning()
    {
        return false;
    }

    @ConfigItem(
            keyName = "suppressTagHelp",
            name = "Suppress tag help popup",
            description = "Don't show the text tag reference popup when using Edit Text on a bank tab.",
            section = "importExportSection",
            position = 1
    )
    default boolean suppressTagHelp()
    {
        return false;
    }

    @ConfigSection(
            name = "Bank menu safety",
            description = "Reduce accidental clicks on the game's risky bank tab options",
            position = 3
    )
    String bankMenuSection = "bankMenuSection";

    @ConfigItem(
            keyName = "bankMenuGuard",
            name = "Risky tab options",
            description = "Protect against accidental clicks on the game's 'Collapse' and 'Remove-placeholders' options on bank tab right-click menus. Show only on Shift, or hide them entirely.",
            section = "bankMenuSection",
            position = 0
    )
    default BankMenuGuard bankMenuGuard()
    {
        return BankMenuGuard.OFF;
    }
}