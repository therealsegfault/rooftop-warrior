package io.github.therealsegfault.rooftopwarrior.game;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;

/**
 * Full-screen black quad rendered on the GUI node (always on top).
 * Drive alpha to fade in/out between scenes.
 */
public class FadeOverlay {

    private final Geometry quad;
    private final Material mat;

    private static final float SW = 960f;
    private static final float SH = 480f;

    public FadeOverlay(SimpleApplication app) {
        Quad mesh = new Quad(SW, SH);
        quad = new Geometry("FadeOverlay", mesh);

        mat = new Material(app.getAssetManager(),
                "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0f, 0f, 0f, 0f));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        quad.setMaterial(mat);
        quad.setLocalTranslation(0f, 0f, 10f); // in front of everything

        // GUI node renders in screen space, always on top of 3D scene
        app.getGuiNode().attachChild(quad);
    }

    public void setAlpha(float alpha) {
        mat.setColor("Color", new ColorRGBA(0f, 0f, 0f, alpha));
    }
}