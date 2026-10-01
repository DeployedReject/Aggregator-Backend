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

public class TokyoNightTheme {

    public static final TextColor BG = new TextColor.RGB(26, 27, 38);
    public static final TextColor SURFACE = new TextColor.RGB(36, 40, 59);
    public static final TextColor FG = new TextColor.RGB(192, 202, 245);
    public static final TextColor SELECTION = new TextColor.RGB(51, 70, 124);
    public static final TextColor ACCENT = new TextColor.RGB(122, 162, 247);
    public static final TextColor SUCCESS = new TextColor.RGB(158, 206, 106);
    public static final TextColor WARNING = new TextColor.RGB(224, 175, 104);
    public static final TextColor ERROR = new TextColor.RGB(247, 118, 142);
    public static final TextColor BORDER = new TextColor.RGB(122, 162, 247);
    public static final TextColor MUTED = new TextColor.RGB(86, 95, 137);
    public static final TextColor CYAN = new TextColor.RGB(125, 207, 255);
    public static final TextColor PURPLE = new TextColor.RGB(187, 154, 247);

    public static Theme createTheme() {
        SimpleTheme theme = new SimpleTheme(FG, BG);

        theme.getDefaultDefinition()
                .setSelected(FG, SELECTION, SGR.BOLD)
                .setActive(BG, ACCENT, SGR.BOLD)
                .setPreLight(FG, SELECTION)
                .setInsensitive(MUTED, BG);

        theme.addOverride(Panel.class, FG, BG);
        theme.addOverride(Window.class, FG, BG);
        theme.addOverride(Border.class, BORDER, BG);

        theme.addOverride(Button.class, ACCENT, SURFACE)
                .setSelected(BG, ACCENT, SGR.BOLD)
                .setActive(BG, ACCENT, SGR.BOLD)
                .setPreLight(FG, SELECTION);

        theme.addOverride(Table.class, FG, BG)
                .setSelected(new TextColor.RGB(255, 255, 255), SELECTION, SGR.BOLD)
                .setActive(new TextColor.RGB(255, 255, 255), SELECTION, SGR.BOLD)
                .setPreLight(FG, SELECTION);

        theme.addOverride(TextBox.class, FG, SURFACE)
                .setSelected(new TextColor.RGB(255, 255, 255), SELECTION, SGR.BOLD);

        return theme;
    }
}
