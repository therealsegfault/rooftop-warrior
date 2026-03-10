package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
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

    // Movement
    private static final float WALK_SPEED    = 400f;
    private static final float SPRINT_SPEED  = 680f;
    private static final float JUMP_FORCE    = 420f;
    private static final float GRAVITY       = -900f;
    private static final float DASH_SPEED    = 900f;
    private static final float DASH_DURATION = 0.15f;
    private static final float DASH_COOLDOWN = 0.5f;

    // Wave world state
    private float waveWorldX  = 0f;
    private float waveY       = GROUND_Y;
    private float velX        = 0f;
    private float velY        = 0f;
    private boolean isGrounded    = true;
    private boolean hasDoubleJump = true;
    private boolean isDashing     = false;
    private float dashTimer    = 0f;
    private float dashCooldown = 0f;

    // Train world state — moves independently
    private float trainWorldX  = 0f;
    private float trainAbsSpeed = 0f;

    // Camera
    // cameraX = waveWorldX - WAVE_SCREEN_X
    // but we clamp it so train stays on screen

    // Alley run-out
    private boolean alleyDone  = false;
    private float   alleyTimer = 0f;
    private static final float ALLEY_DURATION = 2f;

    // Chase timer
    private float chaseTimer       = 0f;
    private float speedChangeTimer = 0f;
    private boolean slowPhase      = false;

    // Train constants
    private static final float TRAIN_W     = 600f;
    private static final float TRAIN_H     = 120f;
    private static final float TRAIN_Y     = 80f;
    private static final float GRAB_ZONE_W = 60f;
    private static final float CHASE_DURATION        = 60f;
    private static final float SPEED_CHANGE_INTERVAL = 5f;

    // Train absolute world speeds
    // Fast: just faster than Wave sprint (680) — visibly pulls away
    private static final float TRAIN_FAST_MIN = 700f;
    private static final float TRAIN_FAST_MAX = 730f;
    // Slow: much slower — she can definitely close gap at sprint
    private static final float TRAIN_SLOW_MIN = 420f;
    private static final float TRAIN_SLOW_MAX = 460f;

    // Camera clamping — keep train within these screen X bounds at all times
    private static final float TRAIN_SCREEN_MIN = SW * 0.28f; // rear of train never left of this
    private static final float TRAIN_SCREEN_MAX = SW * 0.90f; // front of train never right of this

    // Off-screen fail
    private float offScreenTimer = 0f;
    private static final float OFFSCREEN_FAIL = 5f;

    // Viewport stretch
    private float stretchX = 1f;
    private static final float MAX_STRETCH   = 1.3f;
    private static final float STRETCH_SPEED = 2f;

    // State
    private boolean grabbed  = false;
    private boolean canGrab  = false;
    private boolean retrying = false;
    private float retryTimer = 0f;
    private static final float RETRY_DELAY = 1.5f;

    public ChaseScene(SpriteBatch batch, ShapeRenderer shapes,
                      BitmapFont font, SceneManager sceneManager) {
        this.batch        = batch;
        this.shapes       = shapes;
        this.font         = font;
        this.sceneManager = sceneManager;
        waveTexture = new Texture(Gdx.files.internal("sprites/wave.png"));
        bgTexture   = new Texture(Gdx.files.internal("sprites/bg.png"));
    }

    public void enter() {
        // Wave starts at world origin
        waveWorldX     = 0f;
        waveY          = GROUND_Y;
        velX           = 0f;
        velY           = 0f;
        isGrounded     = true;
        isDashing      = false;
        dashTimer      = 0f;
        dashCooldown   = 0f;

        // Train starts ahead of Wave on screen — world X = screen offset + desired screen pos
        // At start cameraX = waveWorldX - WAVE_SCREEN_X = -WAVE_SCREEN_X = -150
        // So trainWorldX = cameraX + desired screen X = -150 + SW*0.7
        trainWorldX    = -WAVE_SCREEN_X + SW * 0.55f;
        trainAbsSpeed  = TRAIN_FAST_MIN;

        chaseTimer       = 0f;
        speedChangeTimer = 0f;
        slowPhase        = false;
        alleyDone        = false;
        alleyTimer       = 0f;
        grabbed          = false;
        canGrab          = false;
        retrying         = false;
        retryTimer       = 0f;
        stretchX         = 1f;
        offScreenTimer   = 0f;
    }

    // Camera X that maps world X=0 to screen X=0
    // Normally follows Wave, but clamped to keep train visible
    private float cameraX() {
        return waveWorldX - WAVE_SCREEN_X;
    }

    public void update(float delta) {
        if (retrying) {
            retryTimer -= delta;
            if (retryTimer <= 0) enter();
            return;
        }

        // Alley run-out — Wave auto-sprints, train already on screen
        if (!alleyDone) {
            alleyTimer   += delta;
            waveWorldX   += SPRINT_SPEED * delta;
            trainWorldX  += trainAbsSpeed * delta;
            if (alleyTimer >= ALLEY_DURATION) alleyDone = true;
            updateJump(delta);
            return;
        }

        if (grabbed) {
            // World keeps moving
            waveWorldX  += SPRINT_SPEED * delta;
            trainWorldX += SPRINT_SPEED * delta;
            updateJump(delta);
            return;
        }

        // Chase timer
        chaseTimer       += delta;
        speedChangeTimer += delta;

        if (chaseTimer >= CHASE_DURATION / 2f && !slowPhase) {
            slowPhase        = true;
            trainAbsSpeed    = TRAIN_SLOW_MIN;
            speedChangeTimer = 0f;
        }

        if (speedChangeTimer >= SPEED_CHANGE_INTERVAL) {
            speedChangeTimer = 0f;
            if (!slowPhase) {
                trainAbsSpeed = TRAIN_FAST_MIN + (float)(Math.random()
                              * (TRAIN_FAST_MAX - TRAIN_FAST_MIN));
            } else {
                trainAbsSpeed = TRAIN_SLOW_MIN + (float)(Math.random()
                              * (TRAIN_SLOW_MAX - TRAIN_SLOW_MIN));
            }
        }

        // Train moves through world
        trainWorldX += trainAbsSpeed * delta;

        // Soft governor — keep train between 40% and 85% of screen during fast phase
        if (!slowPhase) {
            float cam_check = waveWorldX - WAVE_SCREEN_X;
            float trainSX_check = trainWorldX - cam_check;
            if (trainSX_check + TRAIN_W > SW * 0.85f) {
                // Too far right — slow down toward min
                trainAbsSpeed = Math.max(TRAIN_FAST_MIN, trainAbsSpeed - 400f * delta);
            } else if (trainSX_check < SW * 0.40f) {
                // Too far left — speed back up
                trainAbsSpeed = Math.min(TRAIN_FAST_MAX, trainAbsSpeed + 400f * delta);
            }
        }

        // Dash
        if (dashCooldown > 0) dashCooldown -= delta;
        if (dashTimer > 0) {
            dashTimer -= delta;
            if (dashTimer <= 0) { isDashing = false; velX = 0f; }
        }
        if (Gdx.input.isKeyJustPressed(Keys.SHIFT_LEFT) && isGrounded && dashCooldown <= 0) {
            isDashing    = true;
            dashTimer    = DASH_DURATION;
            dashCooldown = DASH_COOLDOWN;
            velX         = DASH_SPEED;
        }

        // Wave movement through world
        boolean sprinting = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT);
        if (!isDashing) {
            velX = 0f;
            if (Gdx.input.isKeyPressed(Keys.D)) velX = sprinting ? SPRINT_SPEED : WALK_SPEED;
            if (Gdx.input.isKeyPressed(Keys.A)) velX = sprinting ? -SPRINT_SPEED : -WALK_SPEED;
        }

        waveWorldX += velX * delta;

        updateJump(delta);

        // Compute camera and train screen position
        float cam      = cameraX();
        float trainSX  = trainWorldX - cam;

        // Off-screen check — only after alley done and not grabbed
        boolean visible = trainSX + TRAIN_W > 0 && trainSX < SW;
        if (!visible && alleyDone && !grabbed) {
            offScreenTimer += delta;
            if (offScreenTimer >= OFFSCREEN_FAIL) {
                retrying   = true;
                retryTimer = RETRY_DELAY;
            }
        } else {
            offScreenTimer = 0f;
        }

        // Viewport stretch
        float trainRight = trainSX + TRAIN_W;
        float targetStretch = 1f;
        if (trainRight > SW * 0.80f) {
            targetStretch = Math.min(MAX_STRETCH,
                1f + (trainRight - SW * 0.80f) / (SW * 0.6f));
        }
        stretchX = approach(stretchX, targetStretch, STRETCH_SPEED * delta);

        // Grab zone — Wave's right edge meets train's rear door
        float waveSX = WAVE_SCREEN_X; // Wave always at fixed screen X
        canGrab = waveSX + WAVE_W >= trainSX
               && waveSX + WAVE_W <= trainSX + GRAB_ZONE_W
               && waveY <= TRAIN_Y + TRAIN_H;

        if (canGrab && Gdx.input.isKeyJustPressed(Keys.E)) {
            grabbed = true;
        }
    }

    private void updateJump(float delta) {
        if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            if (isGrounded) {
                velY          = JUMP_FORCE;
                isGrounded    = false;
                hasDoubleJump = true;
            } else if (hasDoubleJump) {
                velY          = JUMP_FORCE;
                hasDoubleJump = false;
            }
        }
        if (!isGrounded) {
            velY  += GRAVITY * delta;
            waveY += velY * delta;
        }
        if (waveY <= GROUND_Y) {
            waveY      = GROUND_Y;
            velY       = 0f;
            isGrounded = true;
        }
    }

    public void draw() {
        float cam     = cameraX();
        float trainSX = trainWorldX - cam;
        float tx      = trainSX / stretchX; // position in stretched shape space

        // Reset transform matrices at start of every frame
        shapes.getTransformMatrix().idt();
        shapes.updateMatrices();
        batch.setTransformMatrix(batch.getTransformMatrix().idt());

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // BG scrolls with camera
        batch.begin();
        float bgScale  = SH / BG_H;
        float bgDrawW  = BG_W * bgScale;
        float bgScroll = (cam * 0.6f) % bgDrawW;
        float bgX = -bgScroll - bgDrawW;
        while (bgX < SW + bgDrawW) {
            batch.draw(bgTexture, bgX, 0, bgDrawW, SH);
            bgX += bgDrawW;
        }
        batch.end();

        // Stretch shapes for viewport
        shapes.getTransformMatrix().setToScaling(stretchX, 1f, 1f);
        shapes.updateMatrices();

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float invS = 1f / stretchX;

        // Ground
        shapes.setColor(0.95f, 0.91f, 0.86f, 1f);
        shapes.rect(0, 0, SW * invS, GROUND_Y);
        shapes.setColor(0.75f, 0.70f, 0.62f, 1f);
        shapes.rect(0, GROUND_Y - 5f, SW * invS, 5f);

        // Train
        shapes.setColor(0.15f, 0.15f, 0.20f, 1f);
        shapes.rect(tx, TRAIN_Y, TRAIN_W, TRAIN_H);

        shapes.setColor(0.40f, 0.80f, 0.90f, 1f);
        for (float wx = tx + 20f; wx < tx + TRAIN_W - 60f; wx += 80f)
            shapes.rect(wx, TRAIN_Y + 40f, 50f, 40f);

        shapes.setColor(0.30f, 0.30f, 0.35f, 1f);
        shapes.rect(tx + TRAIN_W - 30f, TRAIN_Y + 10f, 30f, TRAIN_H - 20f);
        shapes.setColor(0.70f, 0.70f, 0.75f, 1f);
        shapes.rect(tx + TRAIN_W - 22f, TRAIN_Y + 30f, 6f, 6f);
        shapes.rect(tx + TRAIN_W - 12f, TRAIN_Y + 30f, 6f, 6f);
        shapes.rect(tx + TRAIN_W - 17f, TRAIN_Y + 42f, 6f, 6f);

        shapes.setColor(0.20f, 0.20f, 0.25f, 1f);
        shapes.rect(tx + TRAIN_W / 2 - 40f, TRAIN_Y + TRAIN_H, 80f, 30f);
        shapes.setColor(0.10f, 0.10f, 0.14f, 1f);
        shapes.rect(tx + TRAIN_W / 2 - 20f, TRAIN_Y + TRAIN_H + 5f, 40f, 20f);

        shapes.setColor(canGrab ? 0.00f : 0.30f,
                        canGrab ? 1.00f : 0.30f,
                        canGrab ? 0.60f : 0.35f,
                        canGrab ? 0.40f : 0.60f);
        shapes.rect(tx, TRAIN_Y, GRAB_ZONE_W, TRAIN_H);

        shapes.end();

        shapes.getTransformMatrix().idt();
        shapes.updateMatrices();

        // Wave — always at WAVE_SCREEN_X
        batch.begin();
        batch.draw(waveTexture, WAVE_SCREEN_X, waveY, WAVE_W, WAVE_H);

        font.setColor(1f, 1f, 1f, 0.8f);
        if (!alleyDone)
            font.draw(batch, "...", WAVE_SCREEN_X + WAVE_W + 5f, waveY + WAVE_H + 10f);
        if (canGrab)
            font.draw(batch, "SHIFT + D + E", SW / 2f - 55f, SH - 20f);
        if (retrying)
            font.draw(batch, offScreenTimer >= OFFSCREEN_FAIL
                    ? "LOST THE TRAIN" : "MISSED — retrying...",
                    SW / 2f - 80f, SH / 2f);
        if (grabbed)
            font.draw(batch, "GOT IT!", SW / 2f - 30f, SH / 2f);

        // Off-screen warning
        if (offScreenTimer > 1f) {
            font.setColor(1f, 0.3f, 0.3f, Math.min(1f, offScreenTimer - 1f));
            font.draw(batch, String.format("LOSING TRAIN — %.0fs",
                    OFFSCREEN_FAIL - offScreenTimer),
                    SW / 2f - 70f, SH / 2f + 30f);
        }

        font.setColor(0.5f, 0.5f, 0.5f, 0.5f);
        font.draw(batch, slowPhase ? "SLOW"
                        : String.format("%.0fs", chaseTimer),
                  SW - 60f, SH - 10f);
        batch.end();
    }

    private float approach(float current, float target, float step) {
        if (current < target) return Math.min(current + step, target);
        if (current > target) return Math.max(current - step, target);
        return target;
    }

    public void dispose() {
        waveTexture.dispose();
        bgTexture.dispose();
    }
}