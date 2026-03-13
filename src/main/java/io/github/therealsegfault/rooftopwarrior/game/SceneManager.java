package io.github.therealsegfault.rooftopwarrior.game;

import com.jme3.app.SimpleApplication;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;

/**
 * SceneManager owns all scenes and drives transitions.
 *
 * In jME the scene graph is the rendering pipeline.
 * Each scene owns a Node subtree. On transition we detach the
 * current scene's node and attach the next one.
 *
 * Fade transitions work by tinting the viewport background and
 * driving a full-screen quad alpha — same 0.4s out / 0.7s hold /
 * 0.4s in timing as before.
 */
public class SceneManager {

    private final SimpleApplication app;
    private final Node rootNode;

    private GameState currentState = GameState.BACKSTAGE;

    // Scenes
    private BackstageScene backstageScene;
    // TODO: add remaining scenes as they are ported

    // Transition
    private boolean   transitioning   = false;
    private float     transitionTimer = 0f;
    private GameState pendingState    = null;
    private float     alpha           = 0f;

    private static final float FADE_OUT_END = 0.4f;
    private static final float HOLD_END     = 1.1f;
    private static final float FADE_IN_END  = 1.5f;

    // Fade overlay — full-screen quad driven by alpha
    private FadeOverlay fadeOverlay;

    public SceneManager(SimpleApplication app) {
        this.app      = app;
        this.rootNode = app.getRootNode();
    }

    public void init() {
        fadeOverlay    = new FadeOverlay(app);

        backstageScene = new BackstageScene(app, this);

        // Attach starting scene
        activateScene(currentState);
    }

    private void activateScene(GameState state) {
        rootNode.detachAllChildren();
        switch (state) {
            case BACKSTAGE:
                backstageScene.attach(rootNode);
                backstageScene.enter();
                app.getViewPort().setBackgroundColor(new ColorRGBA(0.08f, 0.06f, 0.10f, 1f));
                break;
            // TODO: remaining cases as scenes are ported
            default:
                break;
        }
    }

    public void update(float tpf) {
        if (transitioning) {
            transitionTimer += tpf;

            if (transitionTimer <= FADE_OUT_END) {
                alpha = transitionTimer / FADE_OUT_END;
            } else if (transitionTimer <= HOLD_END) {
                alpha = 1f;
                if (pendingState != null) {
                    currentState = pendingState;
                    activateScene(currentState);
                    pendingState = null;
                }
            } else if (transitionTimer <= FADE_IN_END) {
                alpha = 1f - ((transitionTimer - HOLD_END) / (FADE_IN_END - HOLD_END));
            } else {
                alpha           = 0f;
                transitioning   = false;
                transitionTimer = 0f;
            }

            fadeOverlay.setAlpha(alpha);
            return;
        }

        switch (currentState) {
            case BACKSTAGE: backstageScene.update(tpf); break;
            default: break;
        }
    }

    public void transitionTo(GameState state) {
        if (transitioning) return;
        transitioning   = true;
        transitionTimer = 0f;
        pendingState    = state;
        alpha           = 0f;
    }

    public void setState(GameState state) {
        currentState = state;
    }

    public GameState getCurrentState() { return currentState; }
    public SimpleApplication getApp()  { return app; }
}