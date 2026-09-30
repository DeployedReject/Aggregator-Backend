package com.github.deployedreject.Aggregator_backend.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.gui2.Border;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.table.Table;

/**
 * 24-bit TrueColor Tokyo Night Theme inspired by LazyVim and murces.
 */
public class TokyoNightTheme {

    // Tokyo Night Authentic Color Palette
    public static final TextColor BG = new TextColor.RGB(26, 27, 38);          // #1a1b26 (Night background)
    public static final TextColor SURFACE = new TextColor.RGB(36, 40, 59);     // #24283b (Panel / Card surface)
    public static final TextColor FG = new TextColor.RGB(192, 202, 245);       // #c0caf5 (Crisp foreground)
    public static final TextColor SELECTION = new TextColor.RGB(51, 70, 124);  // #33467c (Rich selection blue)
    public static final TextColor ACCENT = new TextColor.RGB(122, 162, 247);   // #7aa2f7 (Tokyo Night vibrant blue)
    public static final TextColor SUCCESS = new TextColor.RGB(158, 206, 106);  // #9ece6a (Spring green)
    public static final TextColor WARNING = new TextColor.RGB(224, 175, 104);  // #e0af68 (Amber yellow)
    public static final TextColor ERROR = new TextColor.RGB(247, 118, 142);    // #f7768e (Coral red)
    public static final TextColor BORDER = new TextColor.RGB(122, 162, 247);   // #7aa2f7 (Accent border)
    public static final TextColor MUTED = new TextColor.RGB(86, 95, 137);      // #565f89 (Comments / Muted gray)
    public static final TextColor CYAN = new TextColor.RGB(125, 207, 255);     // #7dcfff (Cyan highlight)
    public static final TextColor PURPLE = new TextColor.RGB(187, 154, 247);   // #bb9af7 (Purple highlight)

    public static Theme createTheme() {
        SimpleTheme theme = new SimpleTheme(FG, BG);

        // Default definition
        theme.getDefaultDefinition()
                .setSelected(FG, SELECTION, SGR.BOLD)
                .setActive(BG, ACCENT, SGR.BOLD)
                .setPreLight(FG, SELECTION)
                .setInsensitive(MUTED, BG);

        // Fill all panels, windows, and borders with uniform dark background
        theme.addOverride(Panel.class, FG, BG);
        theme.addOverride(Window.class, FG, BG);
        theme.addOverride(Border.class, BORDER, BG);

        // Buttons: Accent on Surface; when selected/focused: dark BG on glowing ACCENT
        theme.addOverride(Button.class, ACCENT, SURFACE)
                .setSelected(BG, ACCENT, SGR.BOLD)
                .setActive(BG, ACCENT, SGR.BOLD)
                .setPreLight(FG, SELECTION);

        // Table: FG on BG; selected row: crisp white on SELECTION blue
        theme.addOverride(Table.class, FG, BG)
                .setSelected(new TextColor.RGB(255, 255, 255), SELECTION, SGR.BOLD)
                .setPreLight(FG, SELECTION);

        // TextBox (Code Viewer / Inputs)
        theme.addOverride(TextBox.class, FG, SURFACE)
                .setSelected(new TextColor.RGB(255, 255, 255), SELECTION, SGR.BOLD);

        return theme;
    }
}
