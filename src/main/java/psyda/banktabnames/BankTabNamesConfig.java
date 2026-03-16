package psyda.banktabnames;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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
}