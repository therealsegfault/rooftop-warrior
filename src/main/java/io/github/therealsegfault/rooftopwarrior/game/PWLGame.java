package io.github.therealsegfault.rooftopwarrior.game;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.RenderManager;

public class PWLGame extends SimpleApplication {

    private SceneManager sceneManager;

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        setDisplayStatView(false);
        setDisplayFps(false);
        viewPort.setBackgroundColor(new ColorRGBA(0.08f, 0.06f, 0.10f, 1f));

        // Orthographic camera — pixel space, (0,0) = bottom-left
        cam.setParallelProjection(true);
        cam.setFrustumNear(-1000f);
        cam.setFrustumFar(1000f);
        cam.setFrustumLeft(0f);
        cam.setFrustumRight(960f);
        cam.setFrustumBottom(0f);
        cam.setFrustumTop(480f);
        cam.setLocation(new com.jme3.math.Vector3f(0f, 0f, 500f));
        cam.lookAt(new com.jme3.math.Vector3f(0f, 0f, 0f), com.jme3.math.Vector3f.UNIT_Y);

        System.out.println("PWLGame cam frustum L/R: " + cam.getFrustumLeft() + " / " + cam.getFrustumRight());
        System.out.println("PWLGame cam frustum B/T: " + cam.getFrustumBottom() + " / " + cam.getFrustumTop());
        System.out.println("PWLGame cam parallel: " + cam.isParallelProjection());

        // NUCLEAR DEBUG — bypass everything, attach directly to guiNode
        com.jme3.scene.shape.Quad q = new com.jme3.scene.shape.Quad(200f, 200f);
        com.jme3.scene.Geometry g = new com.jme3.scene.Geometry("NUKE", q);
        com.jme3.material.Material m = new com.jme3.material.Material(assetManager,
                "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", com.jme3.math.ColorRGBA.Red);
        g.setMaterial(m);
        g.setLocalTranslation(100f, 100f, 0f);
        guiNode.attachChild(g);
        System.out.println("NUCLEAR quad attached to guiNode");

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