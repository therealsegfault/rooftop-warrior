package io.github.therealsegfault.rooftopwarrior.window;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import io.github.therealsegfault.rooftopwarrior.game.PWLGame;

public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rooftop Warrior, A Project Wavelength Story");
        config.setWindowedMode(960, 480);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new PWLGame(), config);
    }
}
