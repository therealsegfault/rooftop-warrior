package io.github.therealsegfault.rooftopwarrior.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.List;

public class PWLGame extends ApplicationAdapter {

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Texture waveTexture;

    // Wave screen position is fixed — world scrolls
    private static final float WAVE_SCREEN_X = 200f;
    private float waveY        = GROUND_Y;
    private float velocityY    = 0f;
    private float velocityX    = 0f;
    private boolean isGrounded    = true;
    private boolean hasDoubleJump = true;
    private boolean isDashing     = false;
    private float dashTimer    = 0f;
    private float dashCooldown = 0f;

    // World scroll offset — how far the world has moved
    private float worldOffset = 0f;

    // Parallax offsets
    private float skyOffset    = 0f;
    private float farOffset    = 0f;
    private float midOffset    = 0f;
    private float detailOffset = 0f;

    // Platforms
    private List<Platform> platforms = new ArrayList<>();

    // Constants
    private static final float WALK_SPEED    = 40f;
    private static final float SPRINT_SPEED  = 90f;
    private static final float JUMP_FORCE    = 420f;
    private static final float BACKFLIP_X    = -300f;
    private static final float BACKFLIP_Y    = 500f;
    private static final float DASH_SPEED    = 500f;
    private static final float DASH_DURATION = 0.15f;
    private static final float DASH_COOLDOWN = 0.5f;
    private static final float GRAVITY       = -900f;
    private static final float GROUND_Y      = 80f;
    private static final float WAVE_W        = 64f;
    private static final float WAVE_H        = 64f;

    private static final float SKY_SPEED    = 0.05f;
    private static final float FAR_SPEED    = 0.2f;
    private static final float MID_SPEED    = 0.5f;
    private static final float DETAIL_SPEED = 0.95f;

    private static final float SW = 960f;
    private static final float SH = 480f;

    @Override
    public void create() {
        batch  = new SpriteBatch();
        shapes = new ShapeRenderer();
        font   = new BitmapFont();
        waveTexture = new Texture(Gdx.files.internal("sprites/wave.png"));
        loadLevel();
    }

    private void loadLevel() {
        Json json = new Json();
        JsonValue root = new com.badlogic.gdx.utils.JsonReader()
                .parse(Gdx.files.internal("levels/demo.json"));

        for (JsonValue p : root.get("platforms")) {
            platforms.add(new Platform(
                p.getFloat("x"),
                p.getFloat("y"),
                p.getFloat("w"),
                p.getFloat("h")
            ));
        }
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        update(delta);
        draw();
    }

    private void update(float delta) {
        boolean sprinting = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT);
        float speed = sprinting ? SPRINT_SPEED : WALK_SPEED;

        if (dashCooldown > 0) dashCooldown -= delta;
        if (dashTimer > 0) {
            dashTimer -= delta;
            if (dashTimer <= 0) isDashing = false;
        }

        // Dash
        if (Gdx.input.isKeyJustPressed(Keys.SHIFT_LEFT)
                && isGrounded && dashCooldown <= 0) {
            isDashing    = true;
            dashTimer    = DASH_DURATION;
            dashCooldown = DASH_COOLDOWN;
            velocityX    = DASH_SPEED;
        }

        // Scroll speed this frame
        float scrollSpeed = 0f;
        if (isDashing) {
            scrollSpeed = velocityX;
        } else {
            if (Gdx.input.isKeyPressed(Keys.D)) scrollSpeed =  speed;
            if (Gdx.input.isKeyPressed(Keys.A)) scrollSpeed = -speed;
        }

        // Apply scroll
        if (scrollSpeed != 0) {
            float base = scrollSpeed * delta;
            worldOffset  += scrollSpeed * delta;
            skyOffset    += base * SKY_SPEED;
            farOffset    += base * FAR_SPEED;
            midOffset    += base * MID_SPEED;
            detailOffset += base * DETAIL_SPEED;
        }

        // Clamp world so Wave can't scroll left past start
        if (worldOffset < 0) worldOffset = 0;

        // Backflip
        boolean aHeld = Gdx.input.isKeyPressed(Keys.A);
        boolean spaceJustPressed = Gdx.input.isKeyJustPressed(Keys.SPACE);

        if (spaceJustPressed && isGrounded && aHeld) {
            velocityX     = BACKFLIP_X;
            velocityY     = BACKFLIP_Y;
            isGrounded    = false;
            hasDoubleJump = true;
        } else if (spaceJustPressed) {
            if (isGrounded) {
                velocityY     = JUMP_FORCE;
                isGrounded    = false;
                hasDoubleJump = true;
            } else if (hasDoubleJump) {
                velocityY     = JUMP_FORCE;
                hasDoubleJump = false;
            }
        }

        // Gravity
        if (!isGrounded) {
            velocityY += GRAVITY * delta;
            waveY     += velocityY * delta;

            // Backflip world scroll bleed
            if (velocityX != 0) {
                float base = velocityX * delta;
                worldOffset  += velocityX * delta;
                skyOffset    += base * SKY_SPEED;
                farOffset    += base * FAR_SPEED;
                midOffset    += base * MID_SPEED;
                detailOffset += base * DETAIL_SPEED;
                velocityX = approach(velocityX, 0, 600f * delta);
            }
        }

        // Ground check
        if (waveY <= GROUND_Y) {
            waveY         = GROUND_Y;
            velocityY     = 0f;
            velocityX     = 0f;
            isGrounded    = true;
            hasDoubleJump = true;
            isDashing     = false;
        }

        // Platform collision — land on top only
        for (Platform p : platforms) {
            float px = p.screenX(worldOffset);

            // Wave's feet position
            float waveLeft  = WAVE_SCREEN_X;
            float waveRight = WAVE_SCREEN_X + WAVE_W;
            float waveFeet  = waveY;
            float waveHead  = waveY + WAVE_H;

            boolean overlapsX = waveRight > px && waveLeft < px + p.w;
            boolean fallingThrough = velocityY <= 0; // only land when falling
            boolean feetNearTop = waveFeet >= p.y && waveFeet <= p.y + p.h + 10f;

            if (overlapsX && fallingThrough && feetNearTop) {
                waveY         = p.y + p.h;
                velocityY     = 0f;
                isGrounded    = true;
                hasDoubleJump = true;
                isDashing     = false;
            }
        }
    }

    private void draw() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Sky
        shapes.setColor(0.72f, 0.93f, 1f, 1f);
        shapes.rect(0, 0, SW, SH);

        // Far buildings
        shapes.setColor(0.55f, 0.65f, 0.75f, 1f);
        drawBuildingRow(shapes, farOffset, 200f, 50f, 100f, 15f);

        // Mid buildings
        shapes.setColor(0.25f, 0.72f, 0.72f, 1f);
        drawBuildingRow(shapes, midOffset, 140f, 80f, 140f, 25f);

        // Ground
        shapes.setColor(0.95f, 0.91f, 0.86f, 1f);
        shapes.rect(0, 0, SW, GROUND_Y);

        // Ground line
        shapes.setColor(0.75f, 0.70f, 0.62f, 1f);
        shapes.rect(0, GROUND_Y - 5f, SW, 5f);

        shapes.end();

        // Details
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawDetailRow(shapes, detailOffset);
        shapes.end();

        // Platforms
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.25f, 0.55f, 0.60f, 1f); // darker teal
        for (Platform p : platforms) {
            float px = p.screenX(worldOffset);
            if (px > -p.w && px < SW) { // only draw if on screen
                shapes.rect(px, p.y, p.w, p.h);
            }
        }
        shapes.end();

        // Wave
        batch.begin();
        batch.draw(waveTexture, WAVE_SCREEN_X, waveY, WAVE_W, WAVE_H);

        // Layer labels
        font.setColor(0.2f, 0.2f, 0.2f, 0.5f);
        font.draw(batch, "SKY",     SW - 80, SH - 10);
        font.draw(batch, "FAR",     SW - 80, SH - 60);
        font.draw(batch, "MID",     SW - 80, SH - 130);
        font.draw(batch, "DETAILS", SW - 80, GROUND_Y + 100);
        font.draw(batch, "GROUND",  SW - 80, GROUND_Y - 10);

        batch.end();
    }

    private void drawBuildingRow(ShapeRenderer s, float offset,
                                  float baseH, float minW, float maxW, float gap) {
        float tileW = maxW + gap;
        float x = -((offset % (tileW * 6)) % (tileW * 6));
        int i = 0;
        while (x < SW + maxW) {
            float h = baseH + (i % 5) * 25f;
            float w = minW  + (i % 3) * 20f;
            s.rect(x, GROUND_Y, w, h);
            x += w + gap;
            i++;
        }
    }

    private void drawDetailRow(ShapeRenderer s, float offset) {
        float tileW = 220f;
        float x = -((offset % (tileW * 4)) % (tileW * 4));
        int i = 0;
        while (x < SW + tileW) {
            s.setColor(0.10f, 0.10f, 0.14f, 1f);
            s.rect(x, GROUND_Y + 130f + (i % 3) * 25f, 140f, 5f);
            s.rect(x + 140f, GROUND_Y + 110f + (i % 3) * 25f, 80f, 5f);
            s.setColor(0.20f, 0.20f, 0.25f, 1f);
            s.rect(x + 10f, GROUND_Y, 8f, 80f + (i % 2) * 30f);
            s.setColor(0.85f, 0.25f, 0.45f, 1f);
            s.rect(x + 18f, GROUND_Y + 50f + (i % 2) * 20f, 90f, 28f);
            x += tileW;
            i++;
        }
    }

    private float approach(float current, float target, float step) {
        if (current < target) return Math.min(current + step, target);
        if (current > target) return Math.max(current - step, target);
        return target;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
        waveTexture.dispose();
    }
}
