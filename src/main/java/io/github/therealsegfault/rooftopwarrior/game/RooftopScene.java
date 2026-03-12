package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class RooftopScene {

    private SpriteBatch   batch;
    private ShapeRenderer shapes;
    private BitmapFont    font;
    private SceneManager  sceneManager;
    private Texture waveTexture;
    private Texture skyTexture;

    private static final float SW    = 960f;
    private static final float SH    = 480f;
    private static final float SKY_W = 1920f;
    private static final float SKY_H = 1080f;

    private static final float GROUND_Y      = 120f;
    private static final float WAVE_SCREEN_X = 200f;
    private static final float WAVE_W        = 64f;
    private static final float WAVE_H        = 64f;

    // Movement
    private static final float WALK_SPEED    = 160f;
    private static final float SPRINT_SPEED  = 320f;
    private static final float JUMP_FORCE    = 420f;
    private static final float BACKFLIP_VX   = -300f;
    private static final float BACKFLIP_VY   = 500f;
    private static final float DASH_SPEED    = 600f;
    private static final float DASH_DURATION = 0.15f;
    private static final float DASH_COOLDOWN = 0.5f;
    private static final float GRAVITY       = -900f;

    // Parallax
    private static final float SKY_MULT    = 0.05f;
    private static final float MID_MULT    = 0.40f;
    private static final float DETAIL_MULT = 0.95f;

    // Wave world position
    private float waveWorldX = 0f;
    private float waveY      = GROUND_Y;
    private float velX       = 0f;
    private float velY       = 0f;
    private boolean isGrounded      = true;
    private boolean hasDoubleJump   = true;
    private boolean isDashing       = false;
    private boolean droppingThrough = false;
    private float dashTimer    = 0f;
    private float dashCooldown = 0f;

    // Ladder down
    private boolean onLadderDown   = false;
    private boolean autoDescending = false;
    private static final float LADDER_DOWN_WORLD_X = 2600f;
    private static final float LADDER_W            = 24f;
    private static final float LADDER_H            = 200f;
    private static final float CLIMB_SPEED         = 220f;

    private static final float PLATFORM_H = 16f;
    private static final float LEG_W      = 8f;

    // { worldX, topSurfaceY, width }
    private static final float[][] PLATFORMS = {
        {   0f, GROUND_Y,        400f },
        { 600f, GROUND_Y + 50f,  350f },
        { 800f, GROUND_Y + 100f, 120f },
        {1100f, GROUND_Y + 10f,  300f },
        {1200f, GROUND_Y + 10f,   60f },
        {1550f, GROUND_Y + 70f,  400f },
        {1680f, GROUND_Y + 120f, 120f },
        {1850f, GROUND_Y + 170f, 120f },
        {2100f, GROUND_Y + 90f,  500f },
    };

    private OrthographicCamera screenCam;

    public RooftopScene(SpriteBatch batch, ShapeRenderer shapes,
                        BitmapFont font, SceneManager sceneManager) {
        this.batch        = batch;
        this.shapes       = shapes;
        this.font         = font;
        this.sceneManager = sceneManager;
        waveTexture = new Texture(Gdx.files.internal("sprites/wave.png"));
        skyTexture  = new Texture(Gdx.files.internal("sprites/sunset_sky_sunless.png"));
        skyTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);

        screenCam = new OrthographicCamera();
        screenCam.setToOrtho(false, SW, SH);
    }

    public void enter() {
        waveWorldX = 0f; waveY = GROUND_Y;
        velX = 0f; velY = 0f;
        isGrounded = true; onLadderDown = false;
        autoDescending = false; droppingThrough = false;
    }

    // World X of the left edge of the viewport
    private float camLeft() { return waveWorldX - WAVE_SCREEN_X; }

    public void update(float delta) {
        if (onLadderDown) { updateLadderDown(delta); return; }

        boolean sprinting = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT);
        float speed = sprinting ? SPRINT_SPEED : WALK_SPEED;

        if (dashCooldown > 0) dashCooldown -= delta;
        if (dashTimer > 0) {
            dashTimer -= delta;
            if (dashTimer <= 0) { isDashing = false; velX = 0f; }
        }

        if (Gdx.input.isKeyJustPressed(Keys.SHIFT_LEFT) && isGrounded && dashCooldown <= 0) {
            isDashing = true; dashTimer = DASH_DURATION;
            dashCooldown = DASH_COOLDOWN; velX = DASH_SPEED;
        }

        if (Gdx.input.isKeyJustPressed(Keys.S) && isGrounded) {
            droppingThrough = true; isGrounded = false; velY = -50f;
        }
        if (droppingThrough && velY < -100f) droppingThrough = false;

        if (!isDashing) {
            velX = 0f;
            if (Gdx.input.isKeyPressed(Keys.D)) velX =  speed;
            if (Gdx.input.isKeyPressed(Keys.A)) velX = -speed;
        }

        boolean aHeld = Gdx.input.isKeyPressed(Keys.A);
        if (Gdx.input.isKeyJustPressed(Keys.SPACE) && isGrounded && aHeld) {
            velX = BACKFLIP_VX; velY = BACKFLIP_VY;
            isGrounded = false; hasDoubleJump = true;
            isDashing = false; droppingThrough = false;
        } else if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            if (isGrounded) {
                velY = JUMP_FORCE; isGrounded = false;
                hasDoubleJump = true; droppingThrough = false;
            } else if (hasDoubleJump) {
                velY = JUMP_FORCE; hasDoubleJump = false; droppingThrough = false;
            }
        }

        waveWorldX += velX * delta;
        if (waveWorldX < WAVE_SCREEN_X) waveWorldX = WAVE_SCREEN_X;

        if (!isGrounded) { velY += GRAVITY * delta; waveY += velY * delta; }
        if (waveY <= GROUND_Y) {
            waveY = GROUND_Y; velY = 0f; isGrounded = true;
            hasDoubleJump = true; droppingThrough = false;
            if (!isDashing) velX = 0f;
        }

        // Platform collision — all in world space
        if (!droppingThrough) {
            for (float[] p : PLATFORMS) {
                float pWorldX    = p[0];
                float topSurface = p[1];
                float pw         = p[2];
                boolean overlapsX   = waveWorldX + WAVE_W - 6f > pWorldX
                                   && waveWorldX + 6f < pWorldX + pw;
                boolean falling     = velY <= 0;
                boolean nearSurface = waveY >= topSurface - Math.abs(velY * delta) - 2f
                                   && waveY <= topSurface + 4f;
                if (overlapsX && falling && nearSurface) {
                    waveY = topSurface; velY = 0f; velX = 0f;
                    isGrounded = true; hasDoubleJump = true; isDashing = false;
                }
            }
        }

        // Ladder down — world space
        boolean overLadder = waveWorldX + WAVE_W > LADDER_DOWN_WORLD_X
                          && waveWorldX < LADDER_DOWN_WORLD_X + LADDER_W;
        if (overLadder && isGrounded && Gdx.input.isKeyJustPressed(Keys.S)) {
            onLadderDown = true; autoDescending = true;
        }

        // Sanity check — if Wave thinks she's grounded but isn't on any surface, drop her
        if (isGrounded && waveY > GROUND_Y) {
            boolean onSurface = false;
            for (float[] p : PLATFORMS) {
                float pWorldX    = p[0];
                float topSurface = p[1];
                float pw         = p[2];
                boolean overlapsX = waveWorldX + WAVE_W - 6f > pWorldX
                                 && waveWorldX + 6f < pWorldX + pw;
                if (overlapsX && Math.abs(waveY - topSurface) < 4f) {
                    onSurface = true;
                    break;
                }
            }
            if (!onSurface) {
                isGrounded = false;
            }
        }
    }

    private void updateLadderDown(float delta) {
        if (autoDescending) {
            waveY -= CLIMB_SPEED * delta;
            if (waveY <= GROUND_Y - LADDER_H) {
                autoDescending = false; onLadderDown = false;
                sceneManager.transitionTo(GameState.CHASE);
            }
        }
    }

    public void draw() {
        Gdx.gl.glClearColor(0.55f, 0.80f, 0.95f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float cam = camLeft();

        batch.setProjectionMatrix(screenCam.combined);
        shapes.setProjectionMatrix(screenCam.combined);

        // ── Sky — tiled, very slow parallax ───────────────────────────────
        batch.begin();
        float skyScale = SH / SKY_H;
        float skyDrawW = SKY_W * skyScale; // ~170px per tile at 480p
        float skyOff   = (cam * SKY_MULT) % skyDrawW;
        for (float x = -skyOff - skyDrawW; x < SW + skyDrawW; x += skyDrawW)
            batch.draw(skyTexture, x, 0, skyDrawW, SH);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Distant rooftop silhouettes
        shapes.setColor(0.15f, 0.15f, 0.18f, 1f);
        float midOff = (cam * MID_MULT) % (220f * 8);
        float midX = -midOff;
        int i = 0;
        while (midX < SW + 220f) {
            float h = 50f + (i % 4) * 20f;
            float w = 70f + (i % 3) * 25f;
            shapes.rect(midX, GROUND_Y + 10f, w, h);
            midX += w + 30f; i++;
        }

        // Rooftop surface
        shapes.setColor(0.28f, 0.28f, 0.32f, 1f);
        shapes.rect(0, 0, SW, GROUND_Y);
        shapes.setColor(0.20f, 0.20f, 0.24f, 1f);
        shapes.rect(0, GROUND_Y - 6f, SW, 6f);

        // Platforms — world → screen via cam
        for (float[] p : PLATFORMS) {
            float px         = p[0] - cam;
            float topSurface = p[1];
            float pw         = p[2];
            float boxBottom  = topSurface - PLATFORM_H;
            if (px + pw < 0 || px > SW) continue;

            float legH = boxBottom - GROUND_Y;
            if (legH > 0) {
                shapes.setColor(0.22f, 0.22f, 0.26f, 1f);
                shapes.rect(px + 10f,      GROUND_Y, LEG_W, legH);
                shapes.rect(px + pw - 18f, GROUND_Y, LEG_W, legH);
                if (pw > 150f) shapes.rect(px + pw / 2f - 4f, GROUND_Y, LEG_W, legH);
            }
            drawHeatingBox(shapes, px, boxBottom, pw, PLATFORM_H);
        }

        // AC units
        float detailOff = (cam * DETAIL_MULT) % 300f;
        float dx = -detailOff;
        while (dx < SW + 300f) {
            shapes.setColor(0.38f, 0.38f, 0.42f, 1f);
            shapes.rect(dx + 20f, GROUND_Y, 40f, 20f);
            shapes.rect(dx + 80f, GROUND_Y, 15f, 35f);
            shapes.rect(dx + 120f, GROUND_Y, 25f, 15f);
            dx += 300f;
        }

        // Ladder down
        float ladderSX = LADDER_DOWN_WORLD_X - cam;
        if (ladderSX > -LADDER_W && ladderSX < SW) {
            shapes.setColor(0.60f, 0.45f, 0.20f, 1f);
            shapes.rect(ladderSX, GROUND_Y - LADDER_H + 30f, LADDER_W, LADDER_H - 30f);
            shapes.setColor(0.75f, 0.60f, 0.30f, 1f);
            for (float ry = GROUND_Y - LADDER_H + 40f; ry < GROUND_Y + 30f; ry += 20f)
                shapes.rect(ladderSX, ry, LADDER_W, 4f);
        }

        shapes.end();

        batch.begin();
        batch.draw(waveTexture, WAVE_SCREEN_X, waveY, WAVE_W, WAVE_H);
        font.setColor(0.2f, 0.2f, 0.2f, 0.5f);
        font.draw(batch, "ROOFTOP", SW - 90, SH - 10);
        batch.end();
    }

    private void drawHeatingBox(ShapeRenderer s, float x, float y, float w, float h) {
        s.setColor(0.35f, 0.35f, 0.38f, 1f);
        s.rect(x, y, w, h);
        s.setColor(0.45f, 0.45f, 0.48f, 1f);
        s.rect(x + 2f, y + h - 4f, w - 4f, 4f);
        s.setColor(0.20f, 0.20f, 0.22f, 1f);
        for (float vx = x + 4f; vx < x + w - 4f; vx += 8f)
            s.rect(vx, y + 4f, 4f, h - 8f);
        s.setColor(0.50f, 0.50f, 0.52f, 1f);
        s.rect(x + 2f,     y + 2f,     3f, 3f);
        s.rect(x + w - 5f, y + 2f,     3f, 3f);
        s.rect(x + 2f,     y + h - 5f, 3f, 3f);
        s.rect(x + w - 5f, y + h - 5f, 3f, 3f);
    }

    public void dispose() {
        waveTexture.dispose();
        skyTexture.dispose();
    }
}