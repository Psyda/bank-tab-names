package psyda.banktabnames;

import com.google.inject.Injector;
import com.google.inject.Provides;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.annotations.Interface;
import net.runelite.api.events.ScriptPostFired;
//import net.runelite.api.widgets.ComponentID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
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

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private Injector injector;

    private BankTabNamesPanel panel;
    private NavigationButton navButton;

    private final Map<String, Supplier<Boolean>> tabDisablesConfig = new HashMap<>();
    private final Map<String, Supplier<String>> tabNameConfig = new HashMap<>();
    private final Map<String, Supplier<TabFonts>> tabFontsConfig = new HashMap<>();
    // Removed tabFontColorConfig as colors are now handled by the panel

//todo:remove?
    //private static final int TAB_MAX_LENGTH = 15;

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
        setupConfigMaps();
        clientThread.invoke(this::preformatBankTabs);

        panel = injector.getInstance(BankTabNamesPanel.class);
        panel.init();

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/bank-tab-icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Bank Tab Names")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("BankTabNames")) {
            return;
        }
        preformatBankTabs();
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired scriptPostFired) {
        if (IntStream.of(scriptIDs).anyMatch(x -> x == scriptPostFired.getScriptId())) {
            preformatBankTabs();
        }
    }



    public String getTabName(int tabIndex) {
        if (tabIndex < 0 || tabIndex > 9) {
            return "";
        }
        return tabNameConfig.get("tab" + tabIndex + "Name").get();
    }

    public TabFonts getTabFont(int tabIndex) {
        if (tabIndex < 0 || tabIndex > 9) {
            return TabFonts.QUILL_8;
        }
        return tabFontsConfig.get("bankFont" + tabIndex).get();
    }

    public Color getTabColor(int tabIndex) {
        if (tabIndex < 0 || tabIndex > 9) {
            return Color.WHITE;
        }
        // Always return white since colors are now handled by the panel via color tags
        return Color.WHITE;
    }

    public boolean isTabDisabled(int tabIndex) {
        if (tabIndex < 0 || tabIndex > 9) {
            return false;
        }
        return tabDisablesConfig.get("disableTab" + tabIndex).get();
    }

    /**
     * This loops over checking for different variables for each bank tab set in either mode and
     * sets them accordingly so that each mode looks identical with tags on.
     */
    private void preformatBankTabs() {
//        final Widget bankTabCont = client.getWidget(ComponentID.BANK_TAB_CONTAINER);
        final Widget bankTabCont = client.getWidget(InterfaceID.Bankmain.TABS);
        if (bankTabCont != null) {
            Widget firstTab = bankTabCont.getChild(10);
            if (firstTab != null) {
                for (int i = 10; i < 20; i++) {
                    Widget bankTabChild = bankTabCont.getChild(i);

                    if (bankTabChild != null) {

                        int tabIndex = i % 10;
                        if (tabDisablesConfig.get("disableTab" + tabIndex).get()) {
                            continue;
                        }

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
//        final Widget bankTabCont = client.getWidget(ComponentID.BANK_TAB_CONTAINER);
        final Widget bankTabCont = client.getWidget(InterfaceID.Bankmain.TABS);
        if (bankTabCont != null) {
            for (int i = 10; i < 20; i++) {
                Widget bankTabChild = bankTabCont.getChild(i);
                if (bankTabChild != null) {
                    int tabIndex = i % 10;

                    if (tabDisablesConfig.get("disableTab" + tabIndex).get()) {
                        continue;
                    }

                    bankTabChild.setText(tabNameConfig.get("tab" + tabIndex + "Name").get());
                    bankTabChild.setFontId(tabFontsConfig.get("bankFont" + tabIndex).get().tabFontId);
                    // Use white color as default - colors are now handled by color tags in the text
                    bankTabChild.setTextColor(Color.WHITE.getRGB());
                }
            }
        }
    }

    private void setupConfigMaps() {
        @SuppressWarnings("unchecked")
        Supplier<Boolean>[] disableSuppliers = new Supplier[] {
                config::disableTab0, config::disableTab1, config::disableTab2, config::disableTab3, config::disableTab4,
                config::disableTab5, config::disableTab6, config::disableTab7, config::disableTab8, config::disableTab9
        };

        @SuppressWarnings("unchecked")
        Supplier<String>[] nameSuppliers = new Supplier[] {
                config::tab0Name, config::tab1Name, config::tab2Name, config::tab3Name, config::tab4Name,
                config::tab5Name, config::tab6Name, config::tab7Name, config::tab8Name, config::tab9Name
        };

        @SuppressWarnings("unchecked")
        Supplier<TabFonts>[] fontSuppliers = new Supplier[] {
                config::bankFont0, config::bankFont1, config::bankFont2, config::bankFont3, config::bankFont4,
                config::bankFont5, config::bankFont6, config::bankFont7, config::bankFont8, config::bankFont9
        };

        for (int i = 0; i <= 9; i++) {
            tabDisablesConfig.put("disableTab" + i, disableSuppliers[i]);
            tabNameConfig.put("tab" + i + "Name", nameSuppliers[i]);
            tabFontsConfig.put("bankFont" + i, fontSuppliers[i]);
        }
    }
}