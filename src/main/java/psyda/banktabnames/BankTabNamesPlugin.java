package psyda.banktabnames;

import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.IntStream;

@Slf4j
@PluginDescriptor(
        name = "Bank Tab Names",
        description = "Customize your bank tabs with custom styled names",
        tags = {"bank", "tab", "name", "custom", "edit", "psyda"}
)

public class BankTabNamesPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private BankTabNamesConfig config;

    private static final int TAB_MAX_LENGTH = 15;

    private final int[] scriptIDs = {
            ScriptID.BANKMAIN_BUILD,
            ScriptID.BANKMAIN_INIT,
            ScriptID.BANKMAIN_FINISHBUILDING,
            ScriptID.BANKMAIN_SEARCH_REFRESH,
            ScriptID.BANKMAIN_SEARCH_TOGGLE,
            ScriptID.BANKMAIN_SIZE_CHECK,
            3275,
            276,
            504
    };

    @Provides
    BankTabNamesConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(BankTabNamesConfig.class);
    }

    @Override
    protected void startUp() {
        clientThread.invoke(this::preformatBankTabs);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        preformatBankTabs();
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired scriptPostFired) {
        if (IntStream.of(scriptIDs).anyMatch(x -> x == scriptPostFired.getScriptId())) {
            preformatBankTabs();
        }
    }

    /**
     * This loops over checking for different variables for each bank tab set in either mode and
     * sets them accordingly so that each mode looks identical with tags on.
     */
    private void preformatBankTabs() {
        final Widget bankTabCont = client.getWidget(ComponentID.BANK_TAB_CONTAINER);
        if (bankTabCont != null) {
            Widget firstTab = bankTabCont.getChild(11);
            if (firstTab != null) {
                for (int i = 11; i < 20; i++) {
                    Widget bankTabChild = bankTabCont.getChild(i);
                    if (bankTabChild != null) {
                        int getChildX = (bankTabChild.getOriginalX());
                        int widgetType = bankTabChild.getType();

                        if (bankTabChild.getActions() != null) {
                            // Don't change anything about the New Tab button
                            if (Arrays.asList(bankTabChild.getActions()).contains("New tab")) {
                                continue;
                            }
                        }

                        if (widgetType == 4 && bankTabChild.getHeight() != 35) {
                            continue;
                        }

                        bankTabChild.setOpacity(0);
                        bankTabChild.setOriginalY(0);
                        bankTabChild.setXTextAlignment(1);
                        bankTabChild.setYTextAlignment(1);
                        bankTabChild.setOriginalWidth(41);
                        bankTabChild.setOriginalHeight(40);
                        bankTabChild.setOriginalHeight(40);
                        bankTabChild.setItemId(-1);
                        bankTabChild.setType(4);
                        bankTabChild.setTextShadowed(true);

                        if (widgetType != 4) {
                            bankTabChild.setOriginalX(getChildX - 3);
                        }

                        clientThread.invoke(bankTabChild::revalidate);
                    }
                }
            }
            replaceText();
            clientThread.invokeLater(bankTabCont::revalidate);
        }
    }


    /**
     * This replaces the bank tabs with custom configuration
     */
    private void replaceText() {
        final Widget bankTabCont = client.getWidget(ComponentID.BANK_TAB_CONTAINER);
        if (bankTabCont != null) {
            for (int i = 10; i < 20; i++) {
                Widget bankTabChild = bankTabCont.getChild(i);
                if (bankTabChild != null) {
                    Method tabNameMethod;
                    Method tabFontIdMethod;
                    Method tabTextColorMethod;
                    int tabIndex = i % 10;
                    try {
                        tabNameMethod = config.getClass().getMethod("tab" + tabIndex + "Name");
                        tabFontIdMethod = config.getClass().getMethod("bankFont" + tabIndex);
                        tabTextColorMethod = config.getClass().getMethod("bankFontColor" + tabIndex);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }

                    try {
                        bankTabChild.setText((String) tabNameMethod.invoke(config));
                        bankTabChild.setFontId(((TabFonts) tabFontIdMethod.invoke(config)).tabFontId);
                        bankTabChild.setTextColor(((Color) tabTextColorMethod.invoke(config)).getRGB());
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            Widget mainTab = bankTabCont.getChild(10);

            if (mainTab != null) {
                if (!config.disableMainTabName()) {
                    mainTab.setType(4);
                    mainTab.setOpacity(0);
                    mainTab.setOriginalY(0);
                    mainTab.setXTextAlignment(1);
                    mainTab.setYTextAlignment(1);
                    mainTab.setOriginalWidth(41);
                    mainTab.setOriginalHeight(40);
                    mainTab.setText(config.tab0Name());
                    mainTab.setTextColor(config.bankFontColor0().getRGB());
                    mainTab.setFontId(config.bankFont0().tabFontId);
                }

                if (config.disableMainTabName()) {
                    mainTab.setOpacity(20);
                    mainTab.setType(5);
                    mainTab.setOriginalWidth(36);
                    mainTab.setOriginalHeight(32);
                    mainTab.setOriginalY(4);
                }
                clientThread.invoke(mainTab::revalidate);
            }
        }
    }
}
