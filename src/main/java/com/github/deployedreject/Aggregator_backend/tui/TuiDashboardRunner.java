package com.github.deployedreject.Aggregator_backend.tui;

import com.github.deployedreject.Aggregator_backend.entity.Plugin;
import com.github.deployedreject.Aggregator_backend.entity.PluginChannel;
import com.github.deployedreject.Aggregator_backend.repository.PluginRepository;
import com.github.deployedreject.Aggregator_backend.service.GitSyncService;
import com.github.deployedreject.Aggregator_backend.service.PluginService;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class TuiDashboardRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TuiDashboardRunner.class);

    private final PluginRepository pluginRepository;
    private final PluginService pluginService;
    private final GitSyncService gitSyncService;

    public TuiDashboardRunner(PluginRepository pluginRepository, PluginService pluginService, GitSyncService gitSyncService) {
        this.pluginRepository = pluginRepository;
        this.pluginService = pluginService;
        this.gitSyncService = gitSyncService;
    }

    @Override
    public void run(String... args) {
        boolean enableTui = Arrays.asList(args).contains("--tui");
        if (!enableTui) {
            log.info("TUI Dashboard disabled. (Start with '--tui' argument to launch the Terminal UI)");
            return;
        }

        Thread tuiThread = new Thread(this::startTui, "Aggregator-TUI");
        tuiThread.setDaemon(false);
        tuiThread.start();
    }

    private void startTui() {
        try {
            DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
            Screen screen = terminalFactory.createScreen();
            screen.startScreen();

            WindowBasedTextGUI textGUI = new MultiWindowTextGUI(screen);

            BasicWindow window = new BasicWindow("Aggregator Plugin Registry • Moderation Dashboard");
            window.setHints(List.of(Window.Hint.CENTERED));

            Panel mainPanel = new Panel();
            mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

            Label header = new Label("Hotkeys: [S] Promote to STABLE | [N] Demote to NIGHTLY | [D] Toggle Active | [V] View Code | [P] Git Pull/Sync | [Q] Quit");
            header.setForegroundColor(TextColor.ANSI.CYAN);
            mainPanel.addComponent(header);

            Table<String> table = new Table<>("ID", "Name", "Channel", "Version", "Likes", "Dislikes", "Ratio", "Status");
            table.setPreferredSize(new TerminalSize(95, 14));
            mainPanel.addComponent(table);

            refreshTableData(table);

            Panel buttonPanel = new Panel();
            buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));

            buttonPanel.addComponent(new Button("[S] Promote", () -> {
                String id = getSelectedPluginId(table);
                if (id != null) {
                    pluginService.updateChannel(id, PluginChannel.STABLE);
                    refreshTableData(table);
                }
            }));

            buttonPanel.addComponent(new Button("[N] Demote", () -> {
                String id = getSelectedPluginId(table);
                if (id != null) {
                    pluginService.updateChannel(id, PluginChannel.NIGHTLY);
                    refreshTableData(table);
                }
            }));

            buttonPanel.addComponent(new Button("[D] Toggle Active", () -> {
                String id = getSelectedPluginId(table);
                if (id != null) {
                    pluginService.toggleActive(id);
                    refreshTableData(table);
                }
            }));

            buttonPanel.addComponent(new Button("[V] View Code", () -> {
                String id = getSelectedPluginId(table);
                if (id != null) {
                    try {
                        String code = pluginService.downloadPluginCode(id);
                        showCodeViewer(textGUI, id, code);
                    } catch (Exception e) {
                        MessageDialog.showMessageDialog(textGUI, "Error", "Could not load code: " + e.getMessage(), MessageDialogButton.Close);
                    }
                }
            }));

            buttonPanel.addComponent(new Button("[P] Git Sync", () -> {
                gitSyncService.pullAndSync();
                refreshTableData(table);
                MessageDialog.showMessageDialog(textGUI, "Git Sync", "Git repository pulled and database re-synchronized!", MessageDialogButton.Close);
            }));

            buttonPanel.addComponent(new Button("[Q] Close TUI", window::close));

            mainPanel.addComponent(buttonPanel);
            window.setComponent(mainPanel);

            textGUI.addWindowAndWait(window);
            screen.stopScreen();

        } catch (Exception e) {
            log.error("TUI Dashboard crashed: {}", e.getMessage(), e);
        }
    }

    private void refreshTableData(Table<String> table) {
        table.getTableModel().clear();
        List<Plugin> plugins = pluginRepository.findAll();
        for (Plugin p : plugins) {
            int total = p.getLikesCount() + p.getDislikesCount();
            double ratio = total > 0 ? (p.getDislikesCount() * 100.0 / total) : 0.0;

            table.getTableModel().addRow(
                p.getId(),
                p.getName(),
                p.getChannel().name(),
                p.getVersion(),
                String.valueOf(p.getLikesCount()),
                String.valueOf(p.getDislikesCount()),
                String.format("%.1f%%", ratio),
                p.isActive() ? "ACTIVE" : "DISABLED"
            );
        }
    }

    private String getSelectedPluginId(Table<String> table) {
        int row = table.getSelectedRow();
        if (row >= 0 && row < table.getTableModel().getRowCount()) {
            return table.getTableModel().getCell(0, row);
        }
        return null;
    }

    private void showCodeViewer(WindowBasedTextGUI textGUI, String pluginId, String code) {
        BasicWindow codeWindow = new BasicWindow("Source Code: " + pluginId + ".js");
        codeWindow.setHints(List.of(Window.Hint.CENTERED));

        Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
        TextBox codeBox = new TextBox(new TerminalSize(80, 20), code, TextBox.Style.MULTI_LINE);
        codeBox.setReadOnly(true);
        panel.addComponent(codeBox);

        panel.addComponent(new Button("Close", codeWindow::close));
        codeWindow.setComponent(panel);

        textGUI.addWindow(codeWindow);
    }
}
