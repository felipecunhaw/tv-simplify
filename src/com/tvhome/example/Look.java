package com.tvhome.example;

// The two looks of the home screen: night from 19:00 to 06:00, morning the rest of the day.
final class Look {
    static final Look NIGHT = new Look(true, 0xFFEEF2FB, 0xADE2E9F8, 0x16FFFFFF, 0x29FFFFFF, 0x33FFFFFF, 0xFFC9D6FF, 0xF70F1420);
    static final Look MORNING = new Look(false, 0xFF14203A, 0xA814203A, 0x66FFFFFF, 0xBFFFFFFF, 0xA8FFFFFF, 0xFF2C3F9A, 0xF7EEF2FA);

    final boolean night;
    final int fg, fgMute, glass, glassEdge, glassOn, focus, panel;

    private Look(boolean night, int fg, int fgMute, int glass, int glassEdge, int glassOn, int focus, int panel) {
        this.night = night;
        this.fg = fg;
        this.fgMute = fgMute;
        this.glass = glass;
        this.glassEdge = glassEdge;
        this.glassOn = glassOn;
        this.focus = focus;
        this.panel = panel;
    }

    static Look at(int hour) {
        return hour >= 19 || hour < 6 ? NIGHT : MORNING;
    }
}
