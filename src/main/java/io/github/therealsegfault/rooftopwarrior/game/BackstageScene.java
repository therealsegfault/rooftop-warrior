package io.github.therealsegfault.rooftopwarrior.game;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

/**
 * BackstageScene — tutorial scene before STREET.
 *
 * jME translation notes:
 *   - All geometry is flat Quads placed in 2.5D space (Z=0 for world, Z=1+ for UI)
 *   - Camera is orthographic, locked — no flyCam
 *   - GuiNode hosts all screen-space UI (conversation box, prompts)
 *   - RootNode hosts world geometry (Wave, environment)
 *   - Lighting: single AmbientLight for now; DirectionalLight added in lighting pass
 *
 * TODO: replace placeholder box geometry with actual backstage art
 * TODO: replace BitmapFont with voice-of-god tutorial font
 * TODO: add Wave portrait texture to conversation box
 */
public class BackstageScene implements ActionListener {

    private final SimpleApplication app;
    private final AssetManager      assets;
    private final InputManager      input;
    private final SceneManager      sceneManager;

    // Scene graph roots
    private Node sceneNode; // world geometry — attached to guiNode on enter
    private Node guiNode;   // reference to app.getGuiNode()

    // World constants — 2.5D orthographic, same pixel dimensions as before
    private static final float SW = 960f;
    private static final float SH = 480f;
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
    private static final float RIGHT_BOUNDARY = 900f;

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

    // Input state (polled via ActionListener flags)
    private boolean keyD      = false;
    private boolean keyA      = false;
    private boolean keyShift  = false;
    private boolean keySpace  = false; // just-pressed, cleared after use
    private boolean keyEnter  = false;

    // Tutorial stages
    private enum Stage {
        INTRO, WALK, SPRINT, JUMP, DOUBLE_JUMP, DASH, BACKFLIP, OUTRO, EXIT
    }
    private Stage stage = Stage.INTRO;

    private static final String[][] INTRO_LINES = {
        { "Wave", "[PLACEHOLDER] Hey." },
        { "Wave", "[PLACEHOLDER] You're the one playing, right?" },
        { "Wave", "[PLACEHOLDER] Cool. Let's make sure you know what I can do." },
    };
    private static final String[][] OUTRO_LINES = {
        { "Wave", "[PLACEHOLDER] That's everything. Keep up." },
        { "Wave", "[PLACEHOLDER] Show's waiting." },
    };
    private int     convoIndex    = 0;
    private boolean stageDone     = false;
    private boolean firstJumpDone = false;
    private float   promptAlpha   = 0f;
    private float   fadeAlpha     = 0f;
    private boolean fading        = false;

    // Scene graph nodes for dynamic elements
    private Geometry waveGeom;
    private Node     convoBoxNode;
    private Node     promptNode;
    private BitmapText speakerText;
    private BitmapText dialogueText;
    private BitmapText advanceText;
    private BitmapText promptKeysText;
    private BitmapText promptActionText;

    public BackstageScene(SimpleApplication app, SceneManager sceneManager) {
        this.app          = app;
        this.assets       = app.getAssetManager();
        this.input        = app.getInputManager();
        this.sceneManager = sceneManager;
        this.guiNode      = app.getGuiNode();

        buildSceneGraph();
        registerInput();
    }

    // ── Scene graph construction ───────────────────────────────────────────

    private void buildSceneGraph() {
        sceneNode = new Node("Backstage");

        // Environment
        sceneNode.attachChild(makeBox("BG", 0, 0, -1f, SW, SH,
                new ColorRGBA(0.08f, 0.06f, 0.10f, 1f)));
        sceneNode.attachChild(makeBox("BackWall", 0, GROUND_Y, -0.5f, SW, SH - GROUND_Y,
                new ColorRGBA(0.12f, 0.10f, 0.14f, 1f)));
        sceneNode.attachChild(makeBox("Ground", 0, 0, 0f, SW, GROUND_Y,
                new ColorRGBA(0.14f, 0.12f, 0.16f, 1f)));
        sceneNode.attachChild(makeBox("GroundLine", 0, GROUND_Y - 4f, 0.1f, SW, 4f,
                new ColorRGBA(0.10f, 0.08f, 0.12f, 1f)));

        // Stage door (left side)
        sceneNode.attachChild(makeBox("Door", -10f, GROUND_Y, 0.2f, 60f, 120f,
                new ColorRGBA(0.20f, 0.16f, 0.22f, 1f)));
        sceneNode.attachChild(makeBox("DoorFrameL", -12f, GROUND_Y, 0.3f, 4f, 124f,
                new ColorRGBA(0.60f, 0.50f, 0.20f, 1f)));
        sceneNode.attachChild(makeBox("DoorFrameR", 48f, GROUND_Y, 0.3f, 4f, 124f,
                new ColorRGBA(0.60f, 0.50f, 0.20f, 1f)));
        sceneNode.attachChild(makeBox("DoorFrameTop", -12f, GROUND_Y + 120f, 0.3f, 68f, 4f,
                new ColorRGBA(0.60f, 0.50f, 0.20f, 1f)));
        sceneNode.attachChild(makeBox("DoorGlow", -10f, GROUND_Y, 0.4f, 60f, 6f,
                new ColorRGBA(0.90f, 0.65f, 0.20f, 0.15f)));

        // Equipment cases (right side, static)
        sceneNode.attachChild(makeBox("Case1", 700f, GROUND_Y, 0.2f, 80f, 50f,
                new ColorRGBA(0.22f, 0.20f, 0.25f, 1f)));
        sceneNode.attachChild(makeBox("Case2", 710f, GROUND_Y + 50f, 0.2f, 60f, 40f,
                new ColorRGBA(0.22f, 0.20f, 0.25f, 1f)));
        sceneNode.attachChild(makeBox("CaseLid", 702f, GROUND_Y + 2f, 0.3f, 76f, 4f,
                new ColorRGBA(0.35f, 0.30f, 0.40f, 1f)));

        // Wave sprite — loaded from sprites/wave.png
        waveGeom = makeTexturedBox("Wave", WAVE_SCREEN_X, GROUND_Y, 1f, WAVE_W, WAVE_H,
                "sprites/wave.png");
        sceneNode.attachChild(waveGeom);

        // ── GUI (screen-space, on guiNode) ────────────────────────────
        BitmapFont font = assets.loadFont("Interface/Fonts/Default.fnt");

        // Conversation box (hidden until INTRO/OUTRO)
        convoBoxNode = new Node("ConvoBox");
        convoBoxNode.attachChild(makeGuiBox("ConvoBG", 40f, 20f, 5f, SW - 80f, 100f,
                new ColorRGBA(0.05f, 0.04f, 0.07f, 0.92f)));
        convoBoxNode.attachChild(makeGuiBox("ConvoAccent", 40f, 116f, 5.1f, SW - 80f, 4f,
                new ColorRGBA(0.50f, 0.70f, 0.90f, 1f)));
        convoBoxNode.attachChild(makeGuiBox("Portrait", 48f, 28f, 5.1f, 80f, 80f,
                new ColorRGBA(0.15f, 0.13f, 0.18f, 1f)));

        speakerText = makeText(font, "", new ColorRGBA(0.50f, 0.80f, 1.00f, 1f), 140f, 106f, 6f);
        dialogueText = makeText(font, "", ColorRGBA.White, 140f, 82f, 6f);
        advanceText  = makeText(font, "Space / Enter",
                new ColorRGBA(0.50f, 0.50f, 0.55f, 0.7f), SW - 140f, 36f, 6f);
        convoBoxNode.attachChild(speakerText);
        convoBoxNode.attachChild(dialogueText);
        convoBoxNode.attachChild(advanceText);

        // Tutorial prompt chip (hidden until movement stages)
        promptNode = new Node("Prompt");
        promptNode.attachChild(makeGuiBox("PromptBG",
                SW / 2f - 160f, SH - 80f, 5f, 320f, 50f,
                new ColorRGBA(0.10f, 0.08f, 0.14f, 0.85f)));
        promptNode.attachChild(makeGuiBox("PromptLine",
                SW / 2f - 160f, SH - 32f, 5.1f, 320f, 2f,
                new ColorRGBA(0.50f, 0.70f, 0.90f, 1f)));

        promptKeysText   = makeText(font, "", new ColorRGBA(1f, 0.95f, 0.70f, 1f),
                SW / 2f - 100f, SH - 44f, 6f);
        promptActionText = makeText(font, "", new ColorRGBA(0.70f, 0.70f, 0.75f, 0.8f),
                SW / 2f - 100f, SH - 62f, 6f);
        promptNode.attachChild(promptKeysText);
        promptNode.attachChild(promptActionText);
    }

    private void registerInput() {
        input.addMapping("MoveRight", new KeyTrigger(KeyInput.KEY_D));
        input.addMapping("MoveLeft",  new KeyTrigger(KeyInput.KEY_A));
        input.addMapping("Sprint",    new KeyTrigger(KeyInput.KEY_LSHIFT));
        input.addMapping("Jump",      new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping("Advance",   new KeyTrigger(KeyInput.KEY_RETURN));
        input.addListener(this, "MoveRight", "MoveLeft", "Sprint", "Jump", "Advance");
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        System.out.println("onAction: " + name + " pressed=" + isPressed);
        switch (name) {
            case "MoveRight": keyD     = isPressed; break;
            case "MoveLeft":  keyA     = isPressed; break;
            case "Sprint":    keyShift = isPressed; break;
            case "Jump":      if (isPressed) keySpace = true; break;
            case "Advance":   if (isPressed) keyEnter = true; break;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public void attach(Node root) {
        // Everything on guiNode — screen space, pixel coords, no camera math needed.
        guiNode.attachChild(sceneNode);
        guiNode.attachChild(convoBoxNode);
        guiNode.attachChild(promptNode);
    }

    public void detach(Node root) {
        guiNode.detachChild(sceneNode);
        guiNode.detachChild(convoBoxNode);
        guiNode.detachChild(promptNode);
    }

    public void enter() {
        waveWorldX  = 0f;
        waveY       = GROUND_Y;
        velX        = 0f; velY = 0f;
        isGrounded  = true; hasDoubleJump = true;
        isDashing   = false; dashTimer = 0f; dashCooldown = 0f;
        stage       = Stage.INTRO;
        convoIndex  = 0;
        stageDone   = false; firstJumpDone = false;
        promptAlpha = 0f; fadeAlpha = 0f; fading = false;
        keySpace    = false; keyEnter = false;

        showConvoBox(true);
        showPrompt(false);
        updateConvoBox(INTRO_LINES, 0);
    }

    // ── Update ────────────────────────────────────────────────────────────

    public void update(float tpf) {
        if (fading) {
            fadeAlpha = Math.min(1f, fadeAlpha + tpf * 2f);
            // FadeOverlay is driven by SceneManager, not here.
            // Signal transition at full fade.
            if (fadeAlpha >= 1f) sceneManager.transitionTo(GameState.STREET);
            consumeKeys();
            return;
        }

        promptAlpha = Math.min(1f, promptAlpha + tpf * 3f);

        switch (stage) {
            case INTRO:  updateConvo(INTRO_LINES, Stage.WALK);  break;
            case OUTRO:  updateConvo(OUTRO_LINES, Stage.EXIT);  break;
            case EXIT:   fading = true;                          break;
            default:     updateMovement(tpf);                    break;
        }

        // Update Wave geometry position
        waveGeom.setLocalTranslation(waveWorldX, waveY, 1f);

        consumeKeys();
    }

    private void updateConvo(String[][] lines, Stage next) {
        if (keySpace || keyEnter) {
            convoIndex++;
            if (convoIndex >= lines.length) {
                convoIndex  = 0;
                promptAlpha = 0f;
                stage       = next;
                if (next == Stage.EXIT) {
                    showConvoBox(false);
                } else if (isMovementStage(next)) {
                    showConvoBox(false);
                    showPrompt(true);
                    setPrompt(next);
                } else if (next == Stage.OUTRO) {
                    showPrompt(false);
                    showConvoBox(true);
                    updateConvoBox(OUTRO_LINES, 0);
                }
            } else {
                updateConvoBox(lines, convoIndex);
            }
        }
    }

    private void updateMovement(float tpf) {
        if (dashCooldown > 0) dashCooldown -= tpf;
        if (dashTimer > 0) {
            dashTimer -= tpf;
            if (dashTimer <= 0) { isDashing = false; velX = 0f; }
        }

        // Dash
        if (keyShift && isGrounded && dashCooldown <= 0 && !isDashing) {
            isDashing = true; dashTimer = DASH_DURATION;
            dashCooldown = DASH_COOLDOWN; velX = DASH_SPEED;
            if (stage == Stage.DASH) stageDone = true;
        }

        // Backflip
        if (keySpace && isGrounded && keyA) {
            velX = BACKFLIP_VX; velY = BACKFLIP_VY;
            isGrounded = false; hasDoubleJump = true; isDashing = false;
            if (stage == Stage.BACKFLIP) stageDone = true;
        } else if (keySpace) {
            if (isGrounded) {
                velY = JUMP_FORCE; isGrounded = false; hasDoubleJump = true;
                if (stage == Stage.JUMP) stageDone = true;
                if (stage == Stage.DOUBLE_JUMP) firstJumpDone = true;
            } else if (hasDoubleJump) {
                velY = JUMP_FORCE; hasDoubleJump = false;
                if (stage == Stage.DOUBLE_JUMP && firstJumpDone) stageDone = true;
            }
        }

        if (!isDashing) {
            velX = 0f;
            if (keyD) {
                velX = keyShift ? SPRINT_SPEED : WALK_SPEED;
                if (stage == Stage.WALK   && !keyShift) stageDone = true;
                if (stage == Stage.SPRINT &&  keyShift) stageDone = true;
            }
        }

        waveWorldX = Math.max(0f, Math.min(waveWorldX + velX * tpf, RIGHT_BOUNDARY));

        if (!isGrounded) { velY += GRAVITY * tpf; waveY += velY * tpf; }
        if (waveY <= GROUND_Y) {
            waveY = GROUND_Y; velY = 0f; isGrounded = true; hasDoubleJump = true;
            if (!isDashing) velX = 0f;
        }

        if (stageDone) advanceTutorialStage();
    }

    private void advanceTutorialStage() {
        stageDone = false; firstJumpDone = false; promptAlpha = 0f;
        Stage next;
        switch (stage) {
            case WALK:        next = Stage.SPRINT;      break;
            case SPRINT:      next = Stage.JUMP;        break;
            case JUMP:        next = Stage.DOUBLE_JUMP; break;
            case DOUBLE_JUMP: next = Stage.DASH;        break;
            case DASH:        next = Stage.BACKFLIP;    break;
            case BACKFLIP:
                next = Stage.OUTRO;
                showPrompt(false);
                showConvoBox(true);
                updateConvoBox(OUTRO_LINES, 0);
                convoIndex = 0;
                break;
            default: next = stage; break;
        }
        stage = next;
        if (isMovementStage(next)) setPrompt(next);
    }

    private void consumeKeys() {
        keySpace = false;
        keyEnter = false;
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private void showConvoBox(boolean visible) {
        convoBoxNode.setCullHint(visible
                ? com.jme3.scene.Spatial.CullHint.Never
                : com.jme3.scene.Spatial.CullHint.Always);
    }

    private void showPrompt(boolean visible) {
        promptNode.setCullHint(visible
                ? com.jme3.scene.Spatial.CullHint.Never
                : com.jme3.scene.Spatial.CullHint.Always);
    }

    private void updateConvoBox(String[][] lines, int idx) {
        if (idx >= lines.length) return;
        speakerText.setText(lines[idx][0]);
        dialogueText.setText(lines[idx][1]);
    }

    private void setPrompt(Stage s) {
        switch (s) {
            case WALK:        promptKeysText.setText("D");
                              promptActionText.setText("walk"); break;
            case SPRINT:      promptKeysText.setText("Shift + D");
                              promptActionText.setText("sprint"); break;
            case JUMP:        promptKeysText.setText("Space");
                              promptActionText.setText("jump"); break;
            case DOUBLE_JUMP: promptKeysText.setText(firstJumpDone ? "Space  (again)" : "Space  \u2192  Space");
                              promptActionText.setText("double jump"); break;
            case DASH:        promptKeysText.setText("Shift  (tap)");
                              promptActionText.setText("dash"); break;
            case BACKFLIP:    promptKeysText.setText("A + Space");
                              promptActionText.setText("backflip"); break;
            default: break;
        }
    }

    private boolean isMovementStage(Stage s) {
        return s == Stage.WALK || s == Stage.SPRINT || s == Stage.JUMP
            || s == Stage.DOUBLE_JUMP || s == Stage.DASH || s == Stage.BACKFLIP;
    }

    // ── Geometry factories ────────────────────────────────────────────────

    /**
     * Flat quad in world space (attached to sceneNode / rootNode).
     * Z controls draw order — higher Z is in front.
     */
    private Geometry makeBox(String name, float x, float y, float z,
                              float w, float h, ColorRGBA color) {
        Geometry g = new Geometry(name, new Quad(w, h));
        Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        if (color.a < 1f) {
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        }
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
        return g;
    }

    /**
     * Flat quad in screen space (attached to guiNode).
     * jME guiNode uses screen coordinates: (0,0) = bottom-left.
     */
    private Geometry makeGuiBox(String name, float x, float y, float z,
                                 float w, float h, ColorRGBA color) {
        Geometry g = new Geometry(name, new Quad(w, h));
        Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        if (color.a < 1f) {
            m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        }
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
        return g;
    }

    /**
     * Textured quad in world space.
     */
    private Geometry makeTexturedBox(String name, float x, float y, float z,
                                      float w, float h, String texturePath) {
        Geometry g = new Geometry(name, new Quad(w, h));
        Material m = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        Texture tex = assets.loadTexture(texturePath);
        m.setTexture("ColorMap", tex);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        g.setMaterial(m);
        g.setLocalTranslation(x, y, z);
        g.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Gui);
        return g;
    }

    /**
     * BitmapText label on the GUI node.
     */
    private BitmapText makeText(BitmapFont font, String text, ColorRGBA color,
                                 float x, float y, float z) {
        BitmapText t = new BitmapText(font, false);
        t.setText(text);
        t.setColor(color);
        t.setLocalTranslation(x, y, z);
        return t;
    }

    public void dispose() {
        input.removeListener(this);
    }
}