package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * LateScene — Wave runs through the hub city to the venue.
 *
 * Purely exploratory, no fail state. Uses heelys momentum model —
 * Wave carries speed and can't stop instantly. Dodge QTEs interrupt
 * the run as she weaves through pedestrians and traffic.
 *
 * Controls:
 *   D / Shift+D  roll / sprint-roll
 *   A            brake / slow
 *   Q            dodge (QTE)
 *   W            push past (QTE)
 *   Space        jump
 *
 * Scene ends when Wave reaches the venue driveway (VENUE_WORLD_X).
 * Camera pulls back for establishing shot, then cinecut to SHOW.
 *
 * TODO: pedestrian spawning, car obstacles, venue establishing shot,
 *       hub city palette (warm gold, each member's colour in their block)
 */
public class LateScene {

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

    // Heelys momentum model
    private static final float ROLL_ACCEL    = 400f;  // acceleration when holding D
    private static final float BRAKE_DECEL   = 600f;  // deceleration when holding A
    private static final float COAST_DECEL   = 80f;   // passive slowdown
    private static final float MAX_ROLL      = 320f;  // normal roll cap
    private static final float MAX_SPRINT    = 560f;  // shift+D sprint cap
    private static final float JUMP_FORCE    = 420f;
    private static final float GRAVITY       = -900f;

    // Venue — scene ends here
    private static final float VENUE_WORLD_X = 4000f;

    // Wave state
    private float waveWorldX = 0f;
    private float waveY      = GROUND_Y;
    private float velX       = 0f;
    private float velY       = 0f;
    private boolean isGrounded = true;
    private boolean hasDoubleJump = true;

    // Parallax
    private static final float SKY_MULT    = 0.05f;
    private static final float FAR_MULT    = 0.20f;
    private static final float MID_MULT    = 0.50f;
    private static final float DETAIL_MULT = 0.95f;

    // QTE state
    private boolean qteActive    = false;
    private String  qtePrompt    = "";
    private float   qteTimer     = 0f;
    private float   qteWindow    = 0.6f; // seconds to respond
    private boolean qteMissed    = false;
    private float   qteFeedback  = 0f;

    // Establishing shot
    private boolean atVenue         = false;
    private float   establishTimer  = 0f;
    private static final float ESTABLISH_DURATION = 2.5f;
    private float   establishZoom   = 1f; // camera pulls back from 1x to 0.6x

    // Fade out
    private float fadeAlpha = 0f;
    private boolean fading  = false;

    private OrthographicCamera screenCam;

    public LateScene(SpriteBatch batch, ShapeRenderer shapes,
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
        waveWorldX    = 0f;
        waveY         = GROUND_Y;
        velX          = 80f; // starts already rolling
        velY          = 0f;
        isGrounded    = true;
        hasDoubleJump = true;
        qteActive     = false;
        atVenue       = false;
        establishTimer= 0f;
        establishZoom = 1f;
        fadeAlpha     = 0f;
        fading        = false;
    }

    private float camLeft() { return waveWorldX - WAVE_SCREEN_X; }

    public void update(float delta) {
        if (fading) {
            fadeAlpha += delta * 1.5f;
            if (fadeAlpha >= 1f) {
                // TODO: transition to SHOW when ShowScene exists
                // sceneManager.transitionTo(GameState.SHOW);
            }
            return;
        }

        if (atVenue) {
            establishTimer += delta;
            establishZoom = lerp(1f, 0.6f, Math.min(establishTimer / ESTABLISH_DURATION, 1f));
            if (establishTimer >= ESTABLISH_DURATION) {
                fading = true;
            }
            return;
        }

        // ── QTE window ────────────────────────────────────────────────
        if (qteActive) {
            qteTimer -= delta;
            if (Gdx.input.isKeyJustPressed(Keys.Q) && qtePrompt.equals("Q")) {
                qteActive   = false;
                qteFeedback = 0.5f;
                qteMissed   = false;
            } else if (Gdx.input.isKeyJustPressed(Keys.W) && qtePrompt.equals("W")) {
                qteActive   = false;
                qteFeedback = 0.5f;
                qteMissed   = false;
            } else if (qteTimer <= 0f) {
                qteActive   = false;
                qteMissed   = true;
                qteFeedback = 0.8f;
                velX *= 0.5f; // stumble — lose momentum on miss
            }
        }

        if (qteFeedback > 0f) qteFeedback -= delta;

        // ── Heelys movement ───────────────────────────────────────────
        boolean sprinting = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT);
        float maxV = sprinting ? MAX_SPRINT : MAX_ROLL;

        if (Gdx.input.isKeyPressed(Keys.D)) {
            velX = Math.min(velX + ROLL_ACCEL * delta, maxV);
        } else if (Gdx.input.isKeyPressed(Keys.A)) {
            velX = Math.max(velX - BRAKE_DECEL * delta, 0f); // can't roll backwards
        } else {
            velX = Math.max(velX - COAST_DECEL * delta, 0f); // passive coast-down
        }

        // Jump
        if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            if (isGrounded) {
                velY = JUMP_FORCE; isGrounded = false; hasDoubleJump = true;
            } else if (hasDoubleJump) {
                velY = JUMP_FORCE; hasDoubleJump = false;
            }
        }

        waveWorldX += velX * delta;

        if (!isGrounded) { velY += GRAVITY * delta; waveY += velY * delta; }
        if (waveY <= GROUND_Y) {
            waveY = GROUND_Y; velY = 0f; isGrounded = true; hasDoubleJump = true;
        }

        // ── Venue arrival ─────────────────────────────────────────────
        if (waveWorldX >= VENUE_WORLD_X) {
            waveWorldX = VENUE_WORLD_X;
            velX       = 0f;
            atVenue    = true;
        }

        // ── TODO: spawn pedestrians and cars, trigger QTEs ────────────
        // spawnObstacles(delta);
    }

    public void draw() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cam = camLeft();

        // Establishing shot zoom — expand camera viewport
        if (atVenue) {
            float z = 1f / establishZoom;
            screenCam.setToOrtho(false, SW * z, SH * z);
            screenCam.position.set(SW * z / 2f, SH * z / 2f, 0);
            screenCam.update();
        } else {
            screenCam.setToOrtho(false, SW, SH);
        }

        batch.setProjectionMatrix(screenCam.combined);
        shapes.setProjectionMatrix(screenCam.combined);

        // BG
        batch.begin();
        float bgScale = SH / BG_H;
        float bgDrawW = BG_W * bgScale;
        float bgOff   = (cam * SKY_MULT) % bgDrawW;
        for (float x = -bgOff - bgDrawW; x < SW + bgDrawW; x += bgDrawW)
            batch.draw(bgTexture, x, 0, bgDrawW, SH);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // TODO: full hub city parallax layers (warm gold palette)
        // For now, placeholder buildings
        shapes.setColor(0.45f, 0.40f, 0.35f, 1f);
        float farOff = (cam * FAR_MULT) % (60f * 10);
        float bx = -farOff;
        int i = 0;
        while (bx < SW + 60f) {
            float h = 80f + (i % 5) * 30f;
            float w = 45f + (i % 3) * 15f;
            shapes.rect(bx, GROUND_Y, w, h);
            bx += w + 10f; i++;
        }

        // Ground — warm pavement
        shapes.setColor(0.88f, 0.82f, 0.72f, 1f);
        shapes.rect(0, 0, SW, GROUND_Y);
        shapes.setColor(0.70f, 0.64f, 0.55f, 1f);
        shapes.rect(0, GROUND_Y - 5f, SW, 5f);

        // Venue marker
        float venueSX = VENUE_WORLD_X - cam;
        if (venueSX > -20f && venueSX < SW + 20f) {
            shapes.setColor(0.95f, 0.75f, 0.20f, 1f);
            shapes.rect(venueSX, GROUND_Y, 20f, 180f);
            shapes.setColor(0.80f, 0.55f, 0.10f, 1f);
            shapes.rect(venueSX - 60f, GROUND_Y + 160f, 80f, 40f);
        }

        shapes.end();

        batch.begin();
        batch.draw(waveTexture, WAVE_SCREEN_X, waveY, WAVE_W, WAVE_H);

        // QTE prompt
        if (qteActive) {
            float pulse = 0.7f + 0.3f * (float)Math.sin(qteTimer * 20f);
            font.setColor(1f, 0.9f, 0.2f, pulse);
            font.draw(batch,
                    qtePrompt.equals("Q") ? "Q  dodge!" : "W  push past!",
                    SW / 2f - 45f, SH - 30f);
        }

        // QTE feedback
        if (qteFeedback > 0f) {
            float a = Math.min(1f, qteFeedback * 2f);
            font.setColor(qteMissed ? 1f : 0.3f,
                          qteMissed ? 0.3f : 1f,
                          0.3f, a);
            font.draw(batch, qteMissed ? "miss" : "nice",
                    WAVE_SCREEN_X + WAVE_W + 6f, waveY + WAVE_H + 10f);
        }

        // Venue label
        if (venueSX > 0 && venueSX < SW) {
            font.setColor(1f, 1f, 1f, 0.6f);
            font.draw(batch, "VENUE", venueSX - 10f, GROUND_Y + 210f);
        }

        font.setColor(0.4f, 0.4f, 0.4f, 0.4f);
        font.draw(batch, "LATE", SW - 70f, SH - 10f);

        // Establishing shot text
        if (atVenue && establishTimer > 0.5f) {
            float a = Math.min(1f, (establishTimer - 0.5f) * 2f);
            font.setColor(1f, 1f, 1f, a);
            font.draw(batch, "[venue name]", SW / 2f - 55f, SH / 2f + 20f);
        }

        // Fade overlay
        if (fadeAlpha > 0f) {
            batch.end();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, fadeAlpha);
            shapes.rect(0, 0, SW * 2f, SH * 2f); // oversized to cover zoom
            shapes.end();
            batch.begin();
        }

        batch.end();
    }

    // Called externally to trigger a QTE (will be used by obstacle spawner)
    public void triggerQTE(boolean isDodge) {
        if (qteActive) return;
        qteActive = true;
        qtePrompt = isDodge ? "Q" : "W";
        qteTimer  = qteWindow;
        qteMissed = false;
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public void dispose() {
        waveTexture.dispose();
        bgTexture.dispose();
    }
}