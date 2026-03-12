package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

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
    private static final float WAVE_SCREEN_X = 150f;
    private static final float WAVE_W        = 64f;
    private static final float WAVE_H        = 64f;

    // Wave movement
    private static final float WALK_SPEED    = 400f;
    private static final float SPRINT_SPEED  = 680f;
    private static final float JUMP_FORCE    = 420f;
    private static final float GRAVITY       = -900f;
    private static final float DASH_SPEED    = 900f;
    private static final float DASH_DURATION = 0.15f;
    private static final float DASH_COOLDOWN = 0.5f;

    // Wave state
    private float waveWorldX  = 0f;
    private float waveY       = GROUND_Y;
    private float velX        = 0f;
    private float velY        = 0f;
    private boolean isGrounded    = true;
    private boolean hasDoubleJump = true;
    private boolean isDashing     = false;
    private float dashTimer    = 0f;
    private float dashCooldown = 0f;

    // Train — lives in world space just like Wave
    // trainRelX = how far ahead of Wave the train is in world space
    // We control this gap directly so train behaviour is predictable
    private float trainRelX   = 0f; // world offset of train rear from waveWorldX
    private float trainWorldX = 0f; // absolute world X (updated from rel each frame)
    private float trainAbsSpeed = 0f;

    private static final float TRAIN_W     = 600f;
    private static final float TRAIN_H     = 120f;
    private static final float TRAIN_Y     = GROUND_Y;
    private static final float GRAB_ZONE_W = 60f;

    // Gap bounds in screen space — train rear stays within these screen X values
    // Wave is fixed at WAVE_SCREEN_X (150). Train rear at 150+GAP pixels.
    private static final float GAP_MIN_FAST = 200f; // fast phase: rear at least 200px ahead
    private static final float GAP_MAX_FAST = 500f; // fast phase: rear at most 500px ahead
    private static final float GAP_MIN_SLOW =  10f; // slow phase: can get very close
    private static final float GAP_MAX_SLOW = 250f; // slow phase: won't run too far

    // Chase phases
    private float chaseTimer       = 0f;
    private float speedChangeTimer = 0f;
    private boolean slowPhase      = false;
    private static final float CHASE_DURATION        = 60f;
    private static final float SPEED_CHANGE_INTERVAL = 5f;

    // These are absolute speeds, but the governor overrides them to enforce the gap
    private static final float TRAIN_FAST_SPEED = 720f;
    private static final float TRAIN_SLOW_SPEED = 460f;

    // Off-screen / fail
    private float offScreenTimer = 0f;
    private static final float OFFSCREEN_WARN = 1f;
    private static final float OFFSCREEN_FAIL = 5f;

    // Alley run-out intro
    private boolean alleyDone  = false;
    private float   alleyTimer = 0f;
    private static final float ALLEY_DURATION = 2f;

    // State
    private boolean grabbed  = false;
    private boolean canGrab  = false;
    private boolean retrying = false;
    private float retryTimer = 0f;
    private static final float RETRY_DELAY = 1.5f;

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
        waveWorldX    = 0f;
        waveY         = GROUND_Y;
        velX          = 0f; velY = 0f;
        isGrounded    = true;
        isDashing     = false;
        dashTimer     = 0f; dashCooldown = 0f;

        // Train starts GAP_MIN_FAST + TRAIN_W/2 ahead so it's nicely on screen
        trainRelX     = GAP_MIN_FAST + 50f;
        trainWorldX   = waveWorldX + trainRelX;
        trainAbsSpeed = TRAIN_FAST_SPEED;

        chaseTimer = 0f; speedChangeTimer = 0f; slowPhase = false;
        alleyDone = false; alleyTimer = 0f;
        grabbed = false; canGrab = false;
        retrying = false; retryTimer = 0f;
        offScreenTimer = 0f;
    }

    // World X of the left edge of the viewport — Wave always appears at WAVE_SCREEN_X
    private float camLeft() { return waveWorldX - WAVE_SCREEN_X; }

    public void update(float delta) {
        if (retrying) {
            retryTimer -= delta;
            if (retryTimer <= 0) enter();
            return;
        }

        // Alley intro — auto-sprint both Wave and train
        if (!alleyDone) {
            alleyTimer  += delta;
            waveWorldX  += SPRINT_SPEED * delta;
            trainWorldX += trainAbsSpeed * delta;
            trainRelX    = trainWorldX - waveWorldX;
            updateJump(delta);
            if (alleyTimer >= ALLEY_DURATION) alleyDone = true;
            return;
        }

        if (grabbed) {
            waveWorldX  += SPRINT_SPEED * delta;
            trainWorldX  = waveWorldX + trainRelX; // lock gap
            updateJump(delta);
            return;
        }

        // Chase timer
        chaseTimer       += delta;
        speedChangeTimer += delta;

        if (chaseTimer >= CHASE_DURATION / 2f && !slowPhase) {
            slowPhase        = true;
            trainAbsSpeed    = TRAIN_SLOW_SPEED;
            speedChangeTimer = 0f;
        }

        if (speedChangeTimer >= SPEED_CHANGE_INTERVAL) {
            speedChangeTimer = 0f;
            trainAbsSpeed = slowPhase ? TRAIN_SLOW_SPEED : TRAIN_FAST_SPEED;
        }

        // Wave movement
        if (dashCooldown > 0) dashCooldown -= delta;
        if (dashTimer > 0) {
            dashTimer -= delta;
            if (dashTimer <= 0) { isDashing = false; velX = 0f; }
        }
        if (Gdx.input.isKeyJustPressed(Keys.SHIFT_LEFT) && isGrounded && dashCooldown <= 0) {
            isDashing = true; dashTimer = DASH_DURATION;
            dashCooldown = DASH_COOLDOWN; velX = DASH_SPEED;
        }

        boolean sprinting = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT);
        if (!isDashing) {
            velX = 0f;
            if (Gdx.input.isKeyPressed(Keys.D)) velX = sprinting ? SPRINT_SPEED : WALK_SPEED;
            if (Gdx.input.isKeyPressed(Keys.A)) velX = sprinting ? -SPRINT_SPEED : -WALK_SPEED;
        }

        waveWorldX += velX * delta;
        updateJump(delta);

        // Train advances independently
        trainWorldX += trainAbsSpeed * delta;

        // Gap = how far the train rear is ahead of Wave in world space
        trainRelX = trainWorldX - waveWorldX;

        // Governor — enforce gap bounds in screen space
        // trainSX (screen X of train rear) = trainRelX + WAVE_SCREEN_X
        float trainSX = trainRelX + WAVE_SCREEN_X;
        float gapMin  = slowPhase ? GAP_MIN_SLOW : GAP_MIN_FAST;
        float gapMax  = slowPhase ? GAP_MAX_SLOW : GAP_MAX_FAST;

        if (trainSX > gapMax + WAVE_SCREEN_X) {
            // Train too far right — snap gap to max
            trainRelX   = gapMax;
            trainWorldX = waveWorldX + trainRelX;
        } else if (trainSX < gapMin + WAVE_SCREEN_X && !slowPhase) {
            // Train too close during fast phase — snap gap to min
            trainRelX   = gapMin;
            trainWorldX = waveWorldX + trainRelX;
        }

        // Recalculate trainSX after governor
        trainSX = trainRelX + WAVE_SCREEN_X;

        // Off-screen: train rear has gone past left edge
        boolean visible = trainSX + TRAIN_W > 0 && trainSX < SW;
        if (!visible) {
            offScreenTimer += delta;
            if (offScreenTimer >= OFFSCREEN_FAIL) { retrying = true; retryTimer = RETRY_DELAY; }
        } else {
            offScreenTimer = 0f;
        }

        // Grab zone — Wave's right edge (WAVE_SCREEN_X + WAVE_W) meets train rear door
        float waveRight = WAVE_SCREEN_X + WAVE_W;
        canGrab = waveRight >= trainSX && waveRight <= trainSX + GRAB_ZONE_W
               && waveY <= TRAIN_Y + TRAIN_H;

        if (canGrab && Gdx.input.isKeyJustPressed(Keys.E)) grabbed = true;
    }

    private void updateJump(float delta) {
        if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            if (isGrounded) {
                velY = JUMP_FORCE; isGrounded = false; hasDoubleJump = true;
            } else if (hasDoubleJump) {
                velY = JUMP_FORCE; hasDoubleJump = false;
            }
        }
        if (!isGrounded) { velY += GRAVITY * delta; waveY += velY * delta; }
        if (waveY <= GROUND_Y) { waveY = GROUND_Y; velY = 0f; isGrounded = true; }
    }

    public void draw() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cam     = camLeft();
        float trainSX = trainRelX + WAVE_SCREEN_X; // train rear in screen space

        batch.setProjectionMatrix(screenCam.combined);
        shapes.setProjectionMatrix(screenCam.combined);

        // BG parallax
        batch.begin();
        float bgScale  = SH / BG_H;
        float bgDrawW  = BG_W * bgScale;
        float bgOff    = (cam * 0.6f) % bgDrawW;
        for (float x = -bgOff - bgDrawW; x < SW + bgDrawW; x += bgDrawW)
            batch.draw(bgTexture, x, 0, bgDrawW, SH);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Ground
        shapes.setColor(0.95f, 0.91f, 0.86f, 1f);
        shapes.rect(0, 0, SW, GROUND_Y);
        shapes.setColor(0.75f, 0.70f, 0.62f, 1f);
        shapes.rect(0, GROUND_Y - 5f, SW, 5f);

        // Train body
        shapes.setColor(0.15f, 0.15f, 0.20f, 1f);
        shapes.rect(trainSX, TRAIN_Y, TRAIN_W, TRAIN_H);

        // Windows
        shapes.setColor(0.40f, 0.80f, 0.90f, 1f);
        for (float wx = trainSX + 20f; wx < trainSX + TRAIN_W - 60f; wx += 80f)
            shapes.rect(wx, TRAIN_Y + 40f, 50f, 40f);

        // Rear door
        shapes.setColor(0.30f, 0.30f, 0.35f, 1f);
        shapes.rect(trainSX, TRAIN_Y + 10f, 30f, TRAIN_H - 20f);
        shapes.setColor(0.70f, 0.70f, 0.75f, 1f);
        shapes.rect(trainSX + 8f,  TRAIN_Y + 30f, 6f, 6f);
        shapes.rect(trainSX + 18f, TRAIN_Y + 30f, 6f, 6f);
        shapes.rect(trainSX + 13f, TRAIN_Y + 42f, 6f, 6f);

        // Roof vent
        shapes.setColor(0.20f, 0.20f, 0.25f, 1f);
        shapes.rect(trainSX + TRAIN_W / 2 - 40f, TRAIN_Y + TRAIN_H, 80f, 30f);
        shapes.setColor(0.10f, 0.10f, 0.14f, 1f);
        shapes.rect(trainSX + TRAIN_W / 2 - 20f, TRAIN_Y + TRAIN_H + 5f, 40f, 20f);

        // Grab zone highlight
        shapes.setColor(canGrab ? 0.00f : 0.30f,
                        canGrab ? 1.00f : 0.30f,
                        canGrab ? 0.60f : 0.35f,
                        canGrab ? 0.40f : 0.20f);
        shapes.rect(trainSX, TRAIN_Y, GRAB_ZONE_W, TRAIN_H);

        shapes.end();

        // Wave
        batch.begin();
        batch.draw(waveTexture, WAVE_SCREEN_X, waveY, WAVE_W, WAVE_H);

        font.setColor(1f, 1f, 1f, 0.8f);
        if (!alleyDone)
            font.draw(batch, "...", WAVE_SCREEN_X + WAVE_W + 5f, waveY + WAVE_H + 10f);
        if (canGrab)
            font.draw(batch, "E to grab", SW / 2f - 35f, SH - 20f);
        if (grabbed)
            font.draw(batch, "GOT IT!", SW / 2f - 30f, SH / 2f);
        if (retrying)
            font.draw(batch, "LOST THE TRAIN", SW / 2f - 60f, SH / 2f);

        if (offScreenTimer > OFFSCREEN_WARN) {
            font.setColor(1f, 0.3f, 0.3f, Math.min(1f, offScreenTimer - OFFSCREEN_WARN));
            font.draw(batch, String.format("LOSING TRAIN — %.0fs", OFFSCREEN_FAIL - offScreenTimer),
                    SW / 2f - 70f, SH / 2f + 30f);
        }

        font.setColor(0.5f, 0.5f, 0.5f, 0.5f);
        font.draw(batch, slowPhase ? "SLOW" : String.format("%.0fs", chaseTimer), SW - 60f, SH - 10f);
        batch.end();
    }

    public void dispose() {
        waveTexture.dispose();
        bgTexture.dispose();
    }
}