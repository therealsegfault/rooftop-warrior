package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class SceneManager {

    private GameState currentState = GameState.STREET;

    // Transition state
    private boolean transitioning   = false;
    private float   transitionTimer = 0f;
    private float   alpha           = 0f;
    private GameState pendingState  = null;

    // Timing: 0.4s fade out, 0.7s hold, 0.4s fade in = 1.5s total
    private static final float FADE_OUT_END = 0.4f;
    private static final float HOLD_END     = 1.1f;
    private static final float FADE_IN_END  = 1.5f;

    private StreetScene     streetScene;
    private RooftopScene    rooftopScene;
    private LadderDownScene ladderDownScene;
    private ChaseScene      chaseScene;
    private LateScene       lateScene;

    public SceneManager(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        streetScene     = new StreetScene(batch, shapes, font, this);
        rooftopScene    = new RooftopScene(batch, shapes, font, this);
        ladderDownScene = new LadderDownScene(batch, shapes, font, this);
        chaseScene      = new ChaseScene(batch, shapes, font, this);
        lateScene       = new LateScene(batch, shapes, font, this);
    }

    public void update(float delta) {
        if (transitioning) {
            transitionTimer += delta;

            if (transitionTimer <= FADE_OUT_END) {
                alpha = transitionTimer / FADE_OUT_END;
            } else if (transitionTimer <= HOLD_END) {
                alpha = 1f;
                if (pendingState != null) {
                    currentState = pendingState;
                    if (currentState == GameState.ROOFTOP)     rooftopScene.enter();
                    if (currentState == GameState.LADDER_DOWN) ladderDownScene.enter();
                    if (currentState == GameState.CHASE)       chaseScene.enter();
                    if (currentState == GameState.LATE)        lateScene.enter();
                    pendingState = null;
                }
            } else if (transitionTimer <= FADE_IN_END) {
                alpha = 1f - ((transitionTimer - HOLD_END) / (FADE_IN_END - HOLD_END));
            } else {
                alpha           = 0f;
                transitioning   = false;
                transitionTimer = 0f;
            }
            return;
        }

        switch (currentState) {
            case STREET:
            case LADDER:
                streetScene.update(delta);
                break;
            case ROOFTOP:
                rooftopScene.update(delta);
                break;
            case LADDER_DOWN:
                ladderDownScene.update(delta);
                break;
            case CHASE:
                chaseScene.update(delta);
                break;
            case LATE:
                lateScene.update(delta);
                break;
        }
    }

    public void draw(SpriteBatch batch, ShapeRenderer shapes) {
        switch (currentState) {
            case STREET:
            case LADDER:
                streetScene.draw();
                break;
            case ROOFTOP:
                rooftopScene.draw();
                break;
            case LADDER_DOWN:
                ladderDownScene.draw();
                break;
            case CHASE:
                chaseScene.draw();
                break;
            case LATE:
                lateScene.draw();
                break;
        }

        // Fade overlay
        if (transitioning && alpha > 0f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, alpha);
            shapes.rect(0, 0, 960f, 480f);
            shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    public void transitionTo(GameState state) {
        if (transitioning) return;
        transitioning   = true;
        transitionTimer = 0f;
        pendingState    = state;
        alpha           = 0f;
    }

    public GameState getCurrentState() { return currentState; }
    public void setState(GameState state) { this.currentState = state; }
}