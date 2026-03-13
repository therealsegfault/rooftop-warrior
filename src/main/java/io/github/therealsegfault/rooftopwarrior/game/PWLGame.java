package io.github.therealsegfault.rooftopwarrior.game;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.RenderManager;

public class PWLGame extends SimpleApplication {

    private SceneManager sceneManager;

    @Override
    public void simpleInitApp() {
        // Disable default jME camera controls — we manage the camera ourselves
        flyCam.setEnabled(false);

        // Black background by default; scenes override via viewport
        viewPort.setBackgroundColor(ColorRGBA.Black);

        // Hide default stats and FPS display
        setDisplayStatView(false);
        setDisplayFps(false);

        sceneManager = new SceneManager(this);
        sceneManager.init();
    }

    @Override
    public void simpleUpdate(float tpf) {
        sceneManager.update(tpf);
    }

    @Override
    public void simpleRender(RenderManager rm) {
        // jME handles rendering via the scene graph automatically.
        // SceneManager manipulates the scene graph in update().
    }
}