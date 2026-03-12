package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * ChaseScene — IGR cutscene, no player input during chase.
 *
 * Beat timeline:
 *   0.0 – 2.0s  ALLEY     Wave auto-sprints, train visible ahead
 *   2.0 – 6.0s  CHASE     Wave sprints, train slowly pulls away
 *   6.0 – 9.0s  SLIP      Wave decelerates, train escapes off right
 *   9.0 – 12.0s WATCH     Wave slows to a stop, watches train go
 *  12.0 – 14.0s SHOUTBOX  "...seriously?" floats above Wave
 *  14.0+        any key   fade → LATE
 */
public class ChaseScene {

    private SpriteBatch   batch;
    private ShapeRenderer shapes;
    private BitmapFont    font;
    private SceneManager  sceneManager;
    private Texture waveTexture;
    private Texture bgTexture;

    private static final float SW   = 960f;
    private static final float SH   = 480f;
    private static final float BG_W = 480f;
    private static final float BG_H = 270f;

    private static final float GROUND_Y      = 80f;
    private static final float WAVE_SCREEN_X = 200f;
    private static final float WAVE_W        = 64f;
    private static final float WAVE_H        = 64f;

    private static final float TRAIN_W = 600f;
    private static final float TRAIN_H = 120f;
    private static final float TRAIN_Y = GROUND_Y;

    // Beat timestamps
    private static final float T_ALLEY    = 0f;
    private static final float T_CHASE    = 1.5f;
    private static final float T_SLIP     = 3.5f;
    private static final float T_WATCH    = 5.0f;
    private static final float T_SHOUTBOX = 6.5f;
    private static final float T_DISMISSABLE = 8.0f;

    // Speeds
    private static final float WAVE_SPRINT = 680f;
    private static final float WAVE_WALK   = 200f;
    private static final float TRAIN_CHASE = 750f;  // just faster than wave sprint
    private static final float TRAIN_ESCAPE = 1200f; // clearly pulling away in SLIP

    // World state
    private float waveWorldX = 0f;
    private float trainRelX  = 0f; // train rear offset from waveWorldX, screen-space friendly

    private float sceneTimer = 0f;
    private boolean dismissed = false;
    private float fadeAlpha   = 0f;
    private boolean fading    = false;

    // Shoutbox
    private static final String SHOUTBOX_TEXT = "...seriously?";
    private float shoutboxAlpha = 0f;

    private OrthographicCamera screenCam;

    public ChaseScene(SpriteBatch batch, ShapeRenderer shapes,
                      BitmapFont font, SceneManager sceneManager) {
        this.batch        = batch;
        this.shapes       = shapes;
        this.font         = font;
        this.sceneManager = sceneManager;
        waveTexture = new Texture(Gdx.files.internal("sprites/wave.png"));
        bgTexture   = new Texture(Gdx.files.internal("sprites/bg.png"));
        screenCam = new OrthographicCamera();
        screenCam.setToOrtho(false, SW, SH);
    }

    public void enter() {
        waveWorldX  = 0f;
        // Train starts 250px ahead on screen
        trainRelX   = 250f;
        sceneTimer  = 0f;
        dismissed   = false;
        fading      = false;
        fadeAlpha   = 0f;
        shoutboxAlpha = 0f;
    }

    private float camLeft() { return waveWorldX - WAVE_SCREEN_X; }

    public void update(float delta) {
        sceneTimer += delta;

        // ── Wave speed by beat ─────────────────────────────────────────
        float waveSpeed;
        if (sceneTimer < T_CHASE) {
            waveSpeed = WAVE_SPRINT;                          // alley run-out
        } else if (sceneTimer < T_SLIP) {
            waveSpeed = WAVE_SPRINT;                          // chasing hard
        } else if (sceneTimer < T_WATCH) {
            // decelerate from sprint to walk over SLIP duration
            float t = (sceneTimer - T_SLIP) / (T_WATCH - T_SLIP);
            waveSpeed = lerp(WAVE_SPRINT, WAVE_WALK, Math.min(t, 1f));
        } else if (sceneTimer < T_SHOUTBOX) {
            // decelerate to stop
            float t = (sceneTimer - T_WATCH) / (T_SHOUTBOX - T_WATCH);
            waveSpeed = lerp(WAVE_WALK, 0f, Math.min(t, 1f));
        } else {
            waveSpeed = 0f;
        }

        waveWorldX += waveSpeed * delta;

        // ── Train speed by beat ────────────────────────────────────────
        float trainSpeed;
        if (sceneTimer < T_CHASE) {
            trainSpeed = TRAIN_CHASE;   // ahead but visible
        } else if (sceneTimer < T_SLIP) {
            trainSpeed = TRAIN_CHASE;   // slowly opening the gap
        } else {
            trainSpeed = TRAIN_ESCAPE;  // clearly escaping
        }

        // Train moves in world space; we track relative offset for drawing
        float trainWorldX = waveWorldX + trainRelX;
        trainWorldX += trainSpeed * delta;
        trainRelX    = trainWorldX - waveWorldX;

        // ── Shoutbox fade in ───────────────────────────────────────────
        if (sceneTimer >= T_SHOUTBOX) {
            shoutboxAlpha = Math.min(1f, shoutboxAlpha + delta * 2f);
        }

        // ── Dismiss on any key after T_DISMISSABLE ─────────────────────
        if (sceneTimer >= T_DISMISSABLE && !dismissed) {
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ANY_KEY)) {
                dismissed = true;
                fading    = true;
            }
        }

        if (fading) {
            fadeAlpha += delta * 2f;
            if (fadeAlpha >= 1f) {
                fadeAlpha = 1f;
                sceneManager.transitionTo(GameState.LATE);
            }
        }
    }

    public void draw() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cam     = camLeft();
        float trainSX = trainRelX + WAVE_SCREEN_X;

        batch.setProjectionMatrix(screenCam.combined);
        shapes.setProjectionMatrix(screenCam.combined);

        // BG
        batch.begin();
        float bgScale = SH / BG_H;
        float bgDrawW = BG_W * bgScale;
        float bgOff   = (cam * 0.6f) % bgDrawW;
        for (float x = -bgOff - bgDrawW; x < SW + bgDrawW; x += bgDrawW)
            batch.draw(bgTexture, x, 0, bgDrawW, SH);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Ground
        shapes.setColor(0.95f, 0.91f, 0.86f, 1f);
        shapes.rect(0, 0, SW, GROUND_Y);
        shapes.setColor(0.75f, 0.70f, 0.62f, 1f);
        shapes.rect(0, GROUND_Y - 5f, SW, 5f);

        // Train — only draw if on screen
        if (trainSX < SW + TRAIN_W && trainSX + TRAIN_W > 0) {
            shapes.setColor(0.15f, 0.15f, 0.20f, 1f);
            shapes.rect(trainSX, TRAIN_Y, TRAIN_W, TRAIN_H);

            shapes.setColor(0.40f, 0.80f, 0.90f, 1f);
            for (float wx = trainSX + 20f; wx < trainSX + TRAIN_W - 60f; wx += 80f)
                shapes.rect(wx, TRAIN_Y + 40f, 50f, 40f);

            shapes.setColor(0.30f, 0.30f, 0.35f, 1f);
            shapes.rect(trainSX, TRAIN_Y + 10f, 30f, TRAIN_H - 20f);
            shapes.setColor(0.70f, 0.70f, 0.75f, 1f);
            shapes.rect(trainSX + 8f,  TRAIN_Y + 30f, 6f, 6f);
            shapes.rect(trainSX + 18f, TRAIN_Y + 30f, 6f, 6f);
            shapes.rect(trainSX + 13f, TRAIN_Y + 42f, 6f, 6f);

            shapes.setColor(0.20f, 0.20f, 0.25f, 1f);
            shapes.rect(trainSX + TRAIN_W / 2 - 40f, TRAIN_Y + TRAIN_H, 80f, 30f);
            shapes.setColor(0.10f, 0.10f, 0.14f, 1f);
            shapes.rect(trainSX + TRAIN_W / 2 - 20f, TRAIN_Y + TRAIN_H + 5f, 40f, 20f);
        }

        shapes.end();

        // Wave
        batch.begin();
        batch.draw(waveTexture, WAVE_SCREEN_X, GROUND_Y, WAVE_W, WAVE_H);

        // Shoutbox — small bubble above Wave's head
        if (shoutboxAlpha > 0f) {
            font.setColor(1f, 1f, 1f, shoutboxAlpha);
            font.draw(batch, SHOUTBOX_TEXT,
                    WAVE_SCREEN_X + WAVE_W + 8f,
                    GROUND_Y + WAVE_H + 14f);

            // Prompt to continue, fades in a beat after the text
            if (sceneTimer >= T_DISMISSABLE) {
                float promptAlpha = Math.min(1f, (sceneTimer - T_DISMISSABLE) * 2f);
                font.setColor(0.7f, 0.7f, 0.7f, promptAlpha);
                font.draw(batch, "[ any key ]",
                        WAVE_SCREEN_X + WAVE_W + 8f,
                        GROUND_Y + WAVE_H - 4f);
            }
        }

        // Fade overlay
        if (fadeAlpha > 0f) {
            batch.end();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, fadeAlpha);
            shapes.rect(0, 0, SW, SH);
            shapes.end();
            batch.begin();
        }

        batch.end();
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public void dispose() {
        waveTexture.dispose();
        bgTexture.dispose();
    }
}