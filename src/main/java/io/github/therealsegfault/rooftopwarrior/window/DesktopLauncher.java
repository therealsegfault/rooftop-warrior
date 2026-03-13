package io.github.therealsegfault.rooftopwarrior.window;

import com.jme3.system.AppSettings;
import io.github.therealsegfault.rooftopwarrior.game.PWLGame;

public class DesktopLauncher {
    public static void main(String[] args) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Rooftop Warrior (Chord Cutter Games)");
        settings.setResolution(960, 480);
        settings.setFrameRate(60);
        settings.setSamples(4); // MSAA
        settings.setVSync(true);

        PWLGame app = new PWLGame();
        app.setSettings(settings);
        app.setShowSettings(false); // skip jME settings dialog
        app.start();
    }
}