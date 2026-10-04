package com.github.deployedreject.Aggregator_backend.tui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class StandaloneTui {

    private final String baseUrl;
    private final String adminToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public StandaloneTui(String baseUrl, String adminToken) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.adminToken = adminToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) {
        String url = System.getenv().getOrDefault("AGGREGATOR_URL", "http://localhost:8080");
        String token = System.getenv().getOrDefault("AGGREGATOR_ADMIN_TOKEN", "aggregator-admin-token");

        for (String arg : args) {
            if (arg.startsWith("--url=")) {
                url = arg.substring(6).trim();
            } else if (arg.startsWith("--token=")) {
                token = arg.substring(8).trim();
            }
        }

        StandaloneTui app = new StandaloneTui(url, token);
        app.run();
    }

    public void run() {
        Screen screen = null;
        try {
            DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
            terminalFactory.setTerminalEmulatorTitle("Aggregator Registry");
            screen = terminalFactory.createScreen();
            screen.startScreen();

            final Screen finalScreen = screen;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanupTerminal(finalScreen)));

            WindowBasedTextGUI textGUI = new MultiWindowTextGUI(screen);
            textGUI.setTheme(TokyoNightTheme.createTheme());

            BasicWindow window = new BasicWindow();
            window.setHints(List.of(Window.Hint.CENTERED));
            window.setEnableDirectionBasedMovements(false);

            Panel mainPanel = new Panel(new LinearLayout(Direction.VERTICAL));

            Label title = new Label("Aggregator Registry  [" + baseUrl + "]");
            title.setForegroundColor(TokyoNightTheme.CYAN);
            mainPanel.addComponent(title);

            Label hotkeyBar = new Label("[S] Promote  [N] Demote  [Space/T] Toggle  [Enter/V] Code  [X/Del] Delete  [P] Sync  [R] Refresh  [Q] Quit");
            hotkeyBar.setForegroundColor(TokyoNightTheme.ACCENT);
            mainPanel.addComponent(hotkeyBar);

            mainPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

            Table<String> table = new Table<>("ID", "Name", "Channel", "Version", "Likes", "Dislikes", "Dislike%", "Status");
            table.setCellSelection(false);
            table.setEscapeByArrowKey(false);
            table.setPreferredSize(new TerminalSize(100, 13));

            Border tableBorder = table.withBorder(Borders.singleLine("Plugins"));
            mainPanel.addComponent(tableBorder);

            mainPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

            Label statusLabel = new Label("Ready.");
            statusLabel.setForegroundColor(TokyoNightTheme.FG);
            Border statusBorder = statusLabel.withBorder(Borders.singleLine("Status"));
            mainPanel.addComponent(statusBorder);

            mainPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

            Runnable promoteAction = () -> {
                String id = getSelectedPluginId(table);
                if (id == null) {
                    setStatus(statusLabel, "No plugin selected.", TokyoNightTheme.WARNING);
                    return;
                }
                boolean ok = executeApiCall("PUT", "/api/v1/admin/plugins/" + id + "/channel?channel=STABLE", null, statusLabel);
                if (ok) {
                    refreshTableData(statusLabel, table);
                    setStatus(statusLabel, "Plugin '" + id + "' promoted to STABLE.", TokyoNightTheme.SUCCESS);
                }
            };

            Runnable demoteAction = () -> {
                String id = getSelectedPluginId(table);
                if (id == null) {
                    setStatus(statusLabel, "No plugin selected.", TokyoNightTheme.WARNING);
                    return;
                }
                boolean ok = executeApiCall("PUT", "/api/v1/admin/plugins/" + id + "/channel?channel=NIGHTLY", null, statusLabel);
                if (ok) {
                    refreshTableData(statusLabel, table);
                    setStatus(statusLabel, "Plugin '" + id + "' demoted to NIGHTLY.", TokyoNightTheme.WARNING);
                }
            };

            Runnable toggleActiveAction = () -> {
                String id = getSelectedPluginId(table);
                if (id == null) {
                    setStatus(statusLabel, "No plugin selected.", TokyoNightTheme.WARNING);
                    return;
                }
                boolean ok = executeApiCall("PUT", "/api/v1/admin/plugins/" + id + "/toggle-active", null, statusLabel);
                if (ok) {
                    refreshTableData(statusLabel, table);
                    setStatus(statusLabel, "Plugin '" + id + "' toggled.", TokyoNightTheme.ACCENT);
                }
            };

            Runnable viewCodeAction = () -> {
                String id = getSelectedPluginId(table);
                if (id == null) {
                    setStatus(statusLabel, "No plugin selected.", TokyoNightTheme.WARNING);
                    return;
                }
                viewPluginCode(id, textGUI, statusLabel);
            };

            Runnable deleteAction = () -> {
                String id = getSelectedPluginId(table);
                if (id == null) {
                    setStatus(statusLabel, "No plugin selected.", TokyoNightTheme.WARNING);
                    return;
                }

                BasicWindow confirmWindow = new BasicWindow("Confirm Deletion");
                confirmWindow.setHints(List.of(Window.Hint.CENTERED));

                Panel confirmPanel = new Panel(new LinearLayout(Direction.VERTICAL));
                confirmPanel.addComponent(new Label("Permanently delete plugin '" + id + "'?"));
                confirmPanel.addComponent(new Label("This will remove it from the DB and push removal to GitHub."));
                confirmPanel.addComponent(new EmptySpace(new TerminalSize(1, 1)));

                Panel confirmButtons = new Panel(new LinearLayout(Direction.HORIZONTAL));
                Button deleteBtn = new Button("Delete", () -> {
                    confirmWindow.close();
                    setStatus(statusLabel, "Deleting plugin '" + id + "' and syncing Git...", TokyoNightTheme.WARNING);
                    boolean ok = executeApiCall("DELETE", "/api/v1/admin/plugins/" + id, null, statusLabel);
                    if (ok) {
                        refreshTableData(statusLabel, table);
                        setStatus(statusLabel, "Plugin '" + id + "' deleted and synced to GitHub.", TokyoNightTheme.SUCCESS);
                    }
                });

                Button cancelBtn = new Button("Cancel", confirmWindow::close);

                confirmButtons.addComponent(deleteBtn);
                confirmButtons.addComponent(cancelBtn);
                confirmPanel.addComponent(confirmButtons);

                confirmWindow.setComponent(confirmPanel);

                confirmWindow.addWindowListener(new WindowListenerAdapter() {
                    @Override
                    public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliver) {
                        if (keyStroke.getKeyType() == KeyType.Escape ||
                            (keyStroke.getKeyType() == KeyType.Character &&
                             (keyStroke.getCharacter() == 'q' || keyStroke.getCharacter() == 'Q' ||
                              keyStroke.getCharacter() == 'c' || keyStroke.getCharacter() == 'C'))) {
                            deliver.set(false);
                            confirmWindow.close();
                        }
                    }
                });

                textGUI.addWindow(confirmWindow);
                cancelBtn.takeFocus();
            };

            Runnable gitSyncAction = () -> {
                setStatus(statusLabel, "Syncing Git repository...", TokyoNightTheme.CYAN);
                boolean ok = executeApiCall("POST", "/api/v1/admin/git/sync", null, statusLabel);
                if (ok) {
                    refreshTableData(statusLabel, table);
                    setStatus(statusLabel, "Git repository synced.", TokyoNightTheme.SUCCESS);
                }
            };

            Runnable refreshAction = () -> {
                refreshTableData(statusLabel, table);
            };

            table.setSelectAction(viewCodeAction);

            Panel buttonPanel = new Panel(new LinearLayout(Direction.HORIZONTAL));
            buttonPanel.addComponent(new Button("[S] Promote", promoteAction));
            buttonPanel.addComponent(new Button("[N] Demote", demoteAction));
            buttonPanel.addComponent(new Button("[T] Toggle", toggleActiveAction));
            buttonPanel.addComponent(new Button("[V] Code", viewCodeAction));
            buttonPanel.addComponent(new Button("[X] Delete", deleteAction));
            buttonPanel.addComponent(new Button("[P] Sync", gitSyncAction));
            buttonPanel.addComponent(new Button("[R] Refresh", refreshAction));
            buttonPanel.addComponent(new Button("[Q] Quit", window::close));
            mainPanel.addComponent(buttonPanel);

            window.setComponent(mainPanel);

            Map<Character, Runnable> hotkeys = new HashMap<>();
            hotkeys.put('S', promoteAction);
            hotkeys.put('N', demoteAction);
            hotkeys.put('T', toggleActiveAction);
            hotkeys.put('D', toggleActiveAction);
            hotkeys.put(' ', toggleActiveAction);
            hotkeys.put('V', viewCodeAction);
            hotkeys.put('X', deleteAction);
            hotkeys.put('P', gitSyncAction);
            hotkeys.put('R', refreshAction);
            hotkeys.put('Q', window::close);

            hotkeys.put('J', () -> {
                table.takeFocus();
                int cur = table.getSelectedRow();
                int total = table.getTableModel().getRowCount();
                if (cur < total - 1) {
                    table.setSelectedRow(cur + 1);
                    updateSelectedRowStatus(table, statusLabel);
                }
            });
            hotkeys.put('K', () -> {
                table.takeFocus();
                int cur = table.getSelectedRow();
                if (cur > 0) {
                    table.setSelectedRow(cur - 1);
                    updateSelectedRowStatus(table, statusLabel);
                }
            });

            window.addWindowListener(new WindowListenerAdapter() {
                @Override
                public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliver) {
                    if (keyStroke.isCtrlDown() && (keyStroke.getCharacter() == 'c' || keyStroke.getCharacter() == 'C')) {
                        deliver.set(false);
                        window.close();
                        return;
                    }

                    KeyType type = keyStroke.getKeyType();
                    if (type == KeyType.Escape) {
                        deliver.set(false);
                        window.close();
                        return;
                    }
                    if (type == KeyType.Delete) {
                        deliver.set(false);
                        deleteAction.run();
                        return;
                    }

                    Interactable focused = basePane.getFocusedInteractable();
                    boolean isEditableText = (focused instanceof TextBox) && !((TextBox) focused).isReadOnly();

                    if (type == KeyType.ArrowDown || type == KeyType.ArrowUp) {
                        if (focused != table) {
                            table.takeFocus();
                        }
                    }

                    if (!isEditableText) {
                        Character c = null;
                        if (type == KeyType.Character && keyStroke.getCharacter() != null) {
                            c = Character.toUpperCase(keyStroke.getCharacter());
                        }
                        if (c != null && hotkeys.containsKey(c)) {
                            deliver.set(false);
                            hotkeys.get(c).run();
                            return;
                        }
                    }
                }

                @Override
                public void onUnhandledInput(Window basePane, KeyStroke keyStroke, AtomicBoolean hasBeenHandled) {
                    KeyType type = keyStroke.getKeyType();
                    if (type == KeyType.ArrowDown || type == KeyType.ArrowUp) {
                        updateSelectedRowStatus(table, statusLabel);
                    }
                }
            });

            refreshTableData(statusLabel, table);
            table.takeFocus();

            textGUI.addWindowAndWait(window);

        } catch (Exception e) {
            System.err.println("TUI error: " + e.getMessage());
        } finally {
            cleanupTerminal(screen);
        }
    }

    private void setStatus(Label statusLabel, String msg, TextColor color) {
        if (statusLabel != null) {
            statusLabel.setText("  " + msg + "  ");
            statusLabel.setForegroundColor(color != null ? color : TokyoNightTheme.FG);
        }
    }

    private void updateSelectedRowStatus(Table<String> table, Label statusLabel) {
        int row = table.getSelectedRow();
        int total = table.getTableModel().getRowCount();
        if (row >= 0 && row < total) {
            String id = table.getTableModel().getCell(0, row);
            String name = table.getTableModel().getCell(1, row);
            String channel = table.getTableModel().getCell(2, row);
            String status = table.getTableModel().getCell(7, row);
            setStatus(statusLabel, "[" + (row + 1) + "/" + total + "] " + id + " (" + name + ") | " + channel + " | " + status, TokyoNightTheme.CYAN);
        }
    }

    private void refreshTableData(Label statusLabel, Table<String> table) {
        int previousSelectedRow = table.getSelectedRow();
        table.getTableModel().clear();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/admin/plugins"))
                    .header("X-Admin-Token", adminToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                setStatus(statusLabel, "Server error HTTP " + response.statusCode() + ": " + response.body(), TokyoNightTheme.ERROR);
                return;
            }

            List<Map<String, Object>> plugins = objectMapper.readValue(response.body(), new TypeReference<>() {});
            for (Map<String, Object> p : plugins) {
                String id = String.valueOf(p.get("id"));
                String name = String.valueOf(p.get("name"));
                String channel = String.valueOf(p.get("channel"));
                String version = String.valueOf(p.get("version"));
                int likes = ((Number) p.getOrDefault("likes", 0)).intValue();
                int dislikes = ((Number) p.getOrDefault("dislikes", 0)).intValue();
                boolean isActive = Boolean.TRUE.equals(p.get("isActive"));

                int total = likes + dislikes;
                double ratio = total > 0 ? (dislikes * 100.0 / total) : 0.0;

                table.getTableModel().addRow(
                        id,
                        name,
                        channel,
                        version,
                        String.valueOf(likes),
                        String.valueOf(dislikes),
                        String.format("%.1f%%", ratio),
                        isActive ? "ACTIVE" : "DISABLED"
                );
            }

            int count = plugins.size();
            if (count > 0) {
                int rowToSelect = Math.max(0, Math.min(previousSelectedRow >= 0 ? previousSelectedRow : 0, count - 1));
                table.setSelectedRow(rowToSelect);
            }
            table.takeFocus();
            updateSelectedRowStatus(table, statusLabel);

        } catch (Exception e) {
            setStatus(statusLabel, "Connection Error: " + e.getMessage(), TokyoNightTheme.ERROR);
        }
    }

    private String getSelectedPluginId(Table<String> table) {
        int row = table.getSelectedRow();
        if (row >= 0 && row < table.getTableModel().getRowCount()) {
            return table.getTableModel().getCell(0, row);
        }
        return null;
    }

    private boolean executeApiCall(String method, String path, String jsonBody, Label statusLabel) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("X-Admin-Token", adminToken);

            if ("PUT".equalsIgnoreCase(method)) {
                builder.PUT(HttpRequest.BodyPublishers.noBody());
            } else if ("POST".equalsIgnoreCase(method)) {
                builder.POST(jsonBody != null ? HttpRequest.BodyPublishers.ofString(jsonBody) : HttpRequest.BodyPublishers.noBody());
            } else if ("DELETE".equalsIgnoreCase(method)) {
                builder.DELETE();
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            } else {
                setStatus(statusLabel, "API Error (" + response.statusCode() + "): " + response.body(), TokyoNightTheme.ERROR);
                return false;
            }
        } catch (Exception e) {
            setStatus(statusLabel, "Network Error: " + e.getMessage(), TokyoNightTheme.ERROR);
            return false;
        }
    }

    private void viewPluginCode(String pluginId, WindowBasedTextGUI textGUI, Label statusLabel) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/plugins/" + pluginId + "/download"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                setStatus(statusLabel, "Could not fetch code: " + response.body(), TokyoNightTheme.ERROR);
                return;
            }

            BasicWindow codeWindow = new BasicWindow("Plugin Code: " + pluginId + ".js");
            codeWindow.setHints(List.of(Window.Hint.CENTERED));

            Panel panel = new Panel(new LinearLayout(Direction.VERTICAL));
            TextBox codeBox = new TextBox(new TerminalSize(88, 20), response.body(), TextBox.Style.MULTI_LINE);
            codeBox.setReadOnly(true);
            panel.addComponent(codeBox.withBorder(Borders.singleLine("Source")));

            panel.addComponent(new Button("[Q / ESC] Close", codeWindow::close));
            codeWindow.setComponent(panel);

            codeWindow.addWindowListener(new WindowListenerAdapter() {
                @Override
                public void onInput(Window basePane, KeyStroke keyStroke, AtomicBoolean deliver) {
                    if (keyStroke.getKeyType() == KeyType.Escape ||
                        (keyStroke.getKeyType() == KeyType.Character &&
                         (keyStroke.getCharacter() == 'q' || keyStroke.getCharacter() == 'Q' ||
                          keyStroke.getCharacter() == 'c' || keyStroke.getCharacter() == 'C'))) {
                        deliver.set(false);
                        basePane.close();
                    }
                }
            });

            textGUI.addWindow(codeWindow);
            codeBox.takeFocus();

        } catch (Exception e) {
            setStatus(statusLabel, "Failed to fetch plugin code: " + e.getMessage(), TokyoNightTheme.ERROR);
        }
    }

    private static void cleanupTerminal(Screen screen) {
        if (screen != null) {
            try {
                screen.stopScreen();
            } catch (Exception ignored) {}
        }
        try {
            new ProcessBuilder("stty", "sane")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor();
        } catch (Exception ignored) {}
        System.out.print("\033[?1000l\033[?1002l\033[?1006l\033[?25h\033[0m");
        System.out.flush();
    }
}
