package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class LadderDownScene {

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private SceneManager sceneManager;
    private Texture waveTexture;

    private float waveY           = TOP_Y;
    private static final float TOP_Y    = 360f;
    private static final float BOTTOM_Y = 80f;
    private static final float LADDER_X = 480f; // center screen
    private static final float LADDER_W = 24f;
    private static final float CLIMB_SPEED = 80f;
    private static final float WAVE_W   = 64f;
    private static final float WAVE_H   = 64f;
    private static final float SW       = 960f;
    private static final float SH       = 480f;

    public LadderDownScene(SpriteBatch batch, ShapeRenderer shapes,
                            BitmapFont font, SceneManager sceneManager) {
        this.batch        = batch;
        this.shapes       = shapes;
        this.font         = font;
        this.sceneManager = sceneManager;
        waveTexture = new Texture(Gdx.files.internal("sprites/wave.png"));
    }

    public void enter() {
        waveY = TOP_Y;
    }

    public void update(float delta) {
        if (Gdx.input.isKeyPressed(Keys.S)) {
            waveY -= CLIMB_SPEED * delta;
        }
        if (Gdx.input.isKeyPressed(Keys.W)) {
            waveY += CLIMB_SPEED * delta;
        }

        // Cap at top
        if (waveY > TOP_Y) waveY = TOP_Y;

        // Reach bottom = chase
        if (waveY <= BOTTOM_Y) {
            sceneManager.transitionTo(GameState.CHASE);
        }
    }

    public void draw() {
        Gdx.gl.glClearColor(0.10f, 0.10f, 0.12f, 1f); // dark alley
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Alley walls
        shapes.setColor(0.20f, 0.20f, 0.22f, 1f);
        shapes.rect(0,         0, LADDER_X - 60f, SH); // left wall
        shapes.rect(LADDER_X + 60f, 0, SW - LADDER_X - 60f, SH); // right wall

        // Ground
        shapes.setColor(0.15f, 0.15f, 0.18f, 1f);
        shapes.rect(0, 0, SW, BOTTOM_Y);

        // Ladder
        shapes.setColor(0.60f, 0.45f, 0.20f, 1f);
        shapes.rect(LADDER_X, BOTTOM_Y, LADDER_W, TOP_Y - BOTTOM_Y);
        shapes.setColor(0.75f, 0.60f, 0.30f, 1f);
        for (float ry = BOTTOM_Y + 10f; ry < TOP_Y; ry += 20f) {
            shapes.rect(LADDER_X, ry, LADDER_W, 4f);
        }

        // Light from above — pale circle at top
        shapes.setColor(0.70f, 0.85f, 0.95f, 0.15f);
        shapes.rect(LADDER_X - 40f, TOP_Y, 100f, 60f);

        shapes.end();

        batch.begin();
        batch.draw(waveTexture, LADDER_X - WAVE_W / 2f, waveY, WAVE_W, WAVE_H);
        font.setColor(1f, 1f, 1f, 0.6f);
        font.draw(batch, "S = descend", SW / 2f - 40f, SH - 20f);
        batch.end();
    }

    public void dispose() {
        waveTexture.dispose();
    }
}