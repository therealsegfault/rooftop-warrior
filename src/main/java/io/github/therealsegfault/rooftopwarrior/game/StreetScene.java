package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class StreetScene {

    private SpriteBatch   batch;
    private ShapeRenderer shapes;
    private BitmapFont    font;
    private SceneManager  sceneManager;
    private Texture waveTexture;
    private Texture bgTexture;
    private Texture signTexture;

    private static final float SW = 960f;
    private static final float SH = 480f;
    private static final float BG_W = 480f;
    private static final float BG_H = 270f;

    private static final float GROUND_Y      = 80f;
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

    // Parallax — fraction of main camera movement each layer follows
    private static final float SKY_MULT    = 0.05f;
    private static final float FAR_MULT    = 0.20f;
    private static final float MID_MULT    = 0.50f;
    private static final float DETAIL_MULT = 0.95f;

    // Wave world position
    private float waveWorldX = 0f;
    private float waveY      = GROUND_Y;
    private float velX       = 0f;
    private float velY       = 0f;
    private boolean isGrounded    = true;
    private boolean hasDoubleJump = true;
    private boolean isDashing     = false;
    private float dashTimer    = 0f;
    private float dashCooldown = 0f;

    // Ladder
    private boolean onLadder     = false;
    private boolean autoClimbing = false;
    private static final float LADDER_WORLD_X = 800f;
    private static final float LADDER_W       = 24f;
    private static final float LADDER_H       = 200f;
    private static final float CLIMB_SPEED    = 220f;

    // Screen-space camera (identity) used for all rendering
    // We compute parallax offsets manually per layer, keeping everything in screen space
    private OrthographicCamera screenCam;

    private static final float[][] BUILDINGS = {
        {   0f, 280f, 1f },
        { 200f, 340f, 3f },
        { 420f, 200f, 1f },
        { 600f, 380f, 2f },
        { 850f, 260f, 1f },
        {1020f, 440f, 3f },
        {1260f, 300f, 2f },
        {1460f, 220f, 1f },
        {1640f, 360f, 3f },
        {1880f, 280f, 1f },
        {2060f, 420f, 2f },
        {2280f, 240f, 1f },
    };

    private static final float[][] SIGNS = {
        { 300f, 0f },
        { 700f, 0f },
        {1200f, 0f },
        {1800f, 0f },
    };

    public StreetScene(SpriteBatch batch, ShapeRenderer shapes,
                       BitmapFont font, SceneManager sceneManager) {
        this.batch        = batch;
        this.shapes       = shapes;
        this.font         = font;
        this.sceneManager = sceneManager;
        waveTexture = new Texture(Gdx.files.internal("sprites/wave.png"));
        bgTexture   = new Texture(Gdx.files.internal("sprites/bg.png"));
        signTexture = new Texture(Gdx.files.internal("sprites/streetside_sign.png"));

        screenCam = new OrthographicCamera();
        screenCam.setToOrtho(false, SW, SH);
    }

    // World X of the left edge of the screen — used for parallax math
    private float camLeft() {
        return waveWorldX - WAVE_SCREEN_X;
    }

    public void update(float delta) {
        if (onLadder) { updateLadder(delta); return; }
        updateStreet(delta);
    }

    private void updateLadder(float delta) {
        if (!autoClimbing && Gdx.input.isKeyJustPressed(Keys.Q)) autoClimbing = true;
        if (autoClimbing) {
            waveY += CLIMB_SPEED * delta;
            if (waveY >= GROUND_Y + LADDER_H) {
                waveY        = GROUND_Y + LADDER_H;
                autoClimbing = false;
                onLadder     = false;
                sceneManager.transitionTo(GameState.ROOFTOP);
            }
        }
        if (waveY < GROUND_Y) { waveY = GROUND_Y; onLadder = false; }
    }

    private void updateStreet(float delta) {
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

        if (!isDashing) {
            velX = 0f;
            if (Gdx.input.isKeyPressed(Keys.D)) velX =  speed;
            if (Gdx.input.isKeyPressed(Keys.A)) velX = -speed;
        }

        boolean aHeld = Gdx.input.isKeyPressed(Keys.A);
        if (Gdx.input.isKeyJustPressed(Keys.SPACE) && isGrounded && aHeld) {
            velX = BACKFLIP_VX; velY = BACKFLIP_VY;
            isGrounded = false; hasDoubleJump = true; isDashing = false;
        } else if (Gdx.input.isKeyJustPressed(Keys.SPACE)) {
            if (isGrounded) {
                velY = JUMP_FORCE; isGrounded = false; hasDoubleJump = true;
            } else if (hasDoubleJump) {
                velY = JUMP_FORCE; hasDoubleJump = false;
            }
        }

        waveWorldX += velX * delta;
        if (waveWorldX < WAVE_SCREEN_X) waveWorldX = WAVE_SCREEN_X;

        if (!isGrounded) { velY += GRAVITY * delta; waveY += velY * delta; }
        if (waveY <= GROUND_Y) {
            waveY = GROUND_Y; velY = 0f; isGrounded = true; hasDoubleJump = true;
            if (!isDashing) velX = 0f;
        }

        // Ladder grab — world space comparison
        if (waveWorldX + WAVE_W > LADDER_WORLD_X
                && waveWorldX < LADDER_WORLD_X + LADDER_W && isGrounded) {
            onLadder = true; autoClimbing = false; isGrounded = false;
            sceneManager.setState(GameState.LADDER);
        }
    }

    public void draw() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // cam = world X of left edge. Every layer scrolls at its own fraction of this.
        float cam = camLeft();

        batch.setProjectionMatrix(screenCam.combined);
        shapes.setProjectionMatrix(screenCam.combined);

        // ── Sky BG ─────────────────────────────────────────────────────────
        batch.begin();
        float bgScale = SH / BG_H;
        float bgDrawW = BG_W * bgScale;
        float skyOff  = (cam * SKY_MULT) % bgDrawW;
        for (float x = -skyOff - bgDrawW; x < SW + bgDrawW; x += bgDrawW)
            batch.draw(bgTexture, x, 0, bgDrawW, SH);
        batch.end();

        // ── Shape layers ───────────────────────────────────────────────────
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        shapes.setColor(0.45f, 0.55f, 0.65f, 1f);
        drawFarBuildings(shapes, cam * FAR_MULT);
        drawMidBuildings(shapes, cam * MID_MULT);

        shapes.setColor(0.95f, 0.91f, 0.86f, 1f);
        shapes.rect(0, 0, SW, GROUND_Y);
        shapes.setColor(0.75f, 0.70f, 0.62f, 1f);
        shapes.rect(0, GROUND_Y - 5f, SW, 5f);

        drawDetailRow(shapes, cam * DETAIL_MULT);

        // Ladder — world → screen
        float ladderSX = LADDER_WORLD_X - cam;
        if (ladderSX > -LADDER_W && ladderSX < SW) {
            shapes.setColor(0.60f, 0.45f, 0.20f, 1f);
            shapes.rect(ladderSX, GROUND_Y, LADDER_W, LADDER_H);
            shapes.setColor(0.75f, 0.60f, 0.30f, 1f);
            for (float ry = GROUND_Y + 10f; ry < GROUND_Y + LADDER_H; ry += 20f)
                shapes.rect(ladderSX, ry, LADDER_W, 4f);
        }

        shapes.end();

        // ── Sprites ────────────────────────────────────────────────────────
        batch.begin();
        for (float[] s : SIGNS) {
            float sx = s[0] - cam;
            if (sx > -160f && sx < SW + 160f)
                batch.draw(signTexture, sx, GROUND_Y, 80f, 80f);
        }
        batch.draw(waveTexture, WAVE_SCREEN_X, waveY, WAVE_W, WAVE_H);

        font.setColor(0.2f, 0.2f, 0.2f, 0.5f);
        font.draw(batch, "STREET", SW - 80, SH - 10);
        if (onLadder && !autoClimbing) {
            font.setColor(1f, 1f, 1f, 0.8f);
            font.draw(batch, "Q = climb up", SW / 2 - 45, SH - 20);
        }
        batch.end();
    }

    private void drawFarBuildings(ShapeRenderer s, float scrollX) {
        float tileW = 80f;
        float x = -(scrollX % (tileW * 10));
        int i = 0;
        while (x < SW + tileW) {
            float h = 80f + (i % 5) * 28f;
            float w = 50f + (i % 3) * 18f;
            s.rect(x, GROUND_Y, w, h);
            x += w + 12f;
            i++;
        }
    }

    private void drawMidBuildings(ShapeRenderer s, float scrollX) {
        for (float[] b : BUILDINGS) {
            float bx = b[0] - scrollX;
            float totalH = b[1];
            int steps = (int) b[2];
            if (bx + 200f < 0 || bx > SW) continue;
            float baseW = 80f + ((int)b[0] % 3) * 20f;
            if (steps == 1) {
                drawCubeStack(s, bx, GROUND_Y, baseW, totalH);
            } else {
                float stepH = totalH / steps;
                float w = baseW, y = GROUND_Y;
                for (int i = 0; i < steps; i++) {
                    drawCubeStack(s, bx + (baseW - w) / 2f, y, w, stepH);
                    y += stepH; w *= 0.75f;
                }
            }
        }
    }

    private void drawCubeStack(ShapeRenderer s, float x, float y, float w, float h) {
        float cubeH = 30f, curY = y; int idx = 0;
        while (curY < y + h) {
            float thisH = Math.min(cubeH, (y + h) - curY);
            float shade = (idx % 2 == 0) ? 0.72f : 0.65f;
            s.setColor(0.25f * shade, 0.72f * shade, 0.72f * shade, 1f);
            s.rect(x, curY, w, thisH);
            s.setColor(0.15f, 0.50f, 0.50f, 1f);
            s.rect(x, curY + thisH - 2f, w, 2f);
            curY += cubeH; idx++;
        }
    }

    private void drawDetailRow(ShapeRenderer s, float scrollX) {
        float tileW = 220f;
        float x = -(scrollX % (tileW * 6));
        int i = 0;
        while (x < SW + tileW) {
            float postH1 = 160f + (i % 2) * 40f;
            float postH2 = 140f + (i % 2) * 40f;
            s.setColor(0.20f, 0.20f, 0.25f, 1f);
            s.rect(x + 10f,  GROUND_Y, 8f, postH1);
            s.rect(x + 160f, GROUND_Y, 8f, postH2);
            float x1 = x + 14f, y1 = GROUND_Y + postH1 + 4f;
            float x2 = x + 164f, y2 = GROUND_Y + postH2 + 4f;
            for (int seg = 0; seg < 14; seg++) {
                float t0 = (float) seg / 14f, t1 = (float)(seg + 1) / 14f;
                float wx0 = x1 + (x2 - x1) * t0, wx1 = x1 + (x2 - x1) * t1;
                float sag = 22f;
                float wy0 = lerp(y1, y2, t0) - sag * 4f * t0 * (1f - t0);
                float wy1 = lerp(y1, y2, t1) - sag * 4f * t1 * (1f - t1);
                s.setColor(0.10f, 0.10f, 0.14f, 1f);
                s.rectLine(wx0, wy0, wx1, wy1, 2f);
            }
            x += tileW; i++;
        }
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public void dispose() {
        waveTexture.dispose();
        bgTexture.dispose();
        signTexture.dispose();
    }
}