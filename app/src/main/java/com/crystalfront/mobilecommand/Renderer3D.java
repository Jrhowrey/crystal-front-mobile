package com.crystalfront.mobilecommand;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.util.HashMap;
import java.util.Map;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Native Android OpenGL ES 3.0 3D Hardware Renderer for Crystal Front: Mobile Command.
 * Implements:
 *  - Seamless Zoom-to-3D Perspective Camera (Top-Down RTS at low zoom <-> 26-deg 3D Ground View at high zoom)
 *  - GLSL ES 3.0 Directional Sun + Hemisphere Ambient + Blinn-Phong Specular + Emissive Shader
 *  - Multi-Part 3D Hierarchical Rendering (Hull + Independent Rotating Turret + Recoiling Barrels)
 *  - Real-time 3D Cinematic Intro Cutscene Renderer
 */
public class Renderer3D implements GLSurfaceView.Renderer {
    private final GameState state;
    private int shaderProgram = 0;

    private int uMVPMatrixLoc;
    private int uModelMatrixLoc;
    private int uLightDirLoc;
    private int uTintLoc;
    private int uEmissiveLoc;

    private final float[] projMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] vpMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    private MeshBuilder3D.Mesh3D terrainMesh;
    private MeshBuilder3D.Mesh3D greenCrystalMesh;
    private MeshBuilder3D.Mesh3D blueCrystalMesh;
    private MeshBuilder3D.Mesh3D unitBoxMesh;
    private MeshBuilder3D.Mesh3D shadowDiscMesh;

    private final Map<String, MeshBuilder3D.Mesh3D> playerMeshes = new HashMap<>();
    private final Map<String, MeshBuilder3D.Mesh3D> enemyMeshes = new HashMap<>();

    private long lastFrameNs = System.nanoTime();

    private static final String VERTEX_SHADER_300 =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "layout(location = 0) in vec3 aPosition;\n" +
            "layout(location = 1) in vec3 aNormal;\n" +
            "layout(location = 2) in vec4 aColor;\n" +
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uModelMatrix;\n" +
            "out mediump vec3 vNormal;\n" +
            "out mediump vec4 vColor;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
            "  vNormal = normalize(mat3(uModelMatrix) * aNormal);\n" +
            "  vColor = aColor;\n" +
            "}\n";

    // Adreno Dual-Rate FP16 mediump Fragment Lighting Shader
    private static final String FRAGMENT_SHADER_300 =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in mediump vec3 vNormal;\n" +
            "in mediump vec4 vColor;\n" +
            "uniform mediump vec3 uLightDir;\n" +
            "uniform mediump vec4 uTint;\n" +
            "uniform mediump float uEmissive;\n" +
            "out mediump vec4 fragColor;\n" +
            "void main() {\n" +
            "  mediump vec3 N = normalize(vNormal);\n" +
            "  mediump float ndotl = max(dot(N, uLightDir), 0.0);\n" +
            "  mediump float hemi = 0.38 + 0.22 * N.y;\n" +
            "  mediump vec3 H = normalize(uLightDir + vec3(0.0, 0.75, 0.65));\n" +
            "  mediump float spec = pow(max(dot(N, H), 0.0), 16.0) * 0.24;\n" +
            "  mediump vec3 base = vColor.rgb * uTint.rgb;\n" +
            "  mediump vec3 lit = base * (hemi + ndotl * 0.78) + vec3(spec) + base * uEmissive;\n" +
            "  fragColor = vec4(clamp(lit, 0.0, 1.0), vColor.a * uTint.a);\n" +
            "}\n";

    public Renderer3D(GameState state) {
        this.state = state;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES30.glClearColor(0.04f, 0.06f, 0.09f, 1.0f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        int vs = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_300);
        int fs = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_300);
        shaderProgram = GLES30.glCreateProgram();
        GLES30.glAttachShader(shaderProgram, vs);
        GLES30.glAttachShader(shaderProgram, fs);
        GLES30.glLinkProgram(shaderProgram);

        uMVPMatrixLoc = GLES30.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        uModelMatrixLoc = GLES30.glGetUniformLocation(shaderProgram, "uModelMatrix");
        uLightDirLoc = GLES30.glGetUniformLocation(shaderProgram, "uLightDir");
        uTintLoc = GLES30.glGetUniformLocation(shaderProgram, "uTint");
        uEmissiveLoc = GLES30.glGetUniformLocation(shaderProgram, "uEmissive");

        buildUnitAndBuildingMeshes();
    }

    private void buildUnitAndBuildingMeshes() {
        greenCrystalMesh = MeshBuilder3D.createCrystalCluster(false);
        blueCrystalMesh = MeshBuilder3D.createCrystalCluster(true);

        MeshBuilder3D boxMb = new MeshBuilder3D();
        boxMb.addBox(0f, 0.5f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f);
        unitBoxMesh = boxMb.build();

        MeshBuilder3D shadowMb = new MeshBuilder3D();
        shadowMb.addBox(0f, 0.2f, 0f, 1f, 0.2f, 1f, 0.02f, 0.02f, 0.03f, 0.55f);
        shadowDiscMesh = shadowMb.build();

        // Player (Gold/Amber GDI or Crimson NOD) & Enemy Meshes
        buildTeamMeshSet(playerMeshes, 0.88f, 0.62f, 0.14f);
        buildTeamMeshSet(enemyMeshes, 0.86f, 0.20f, 0.22f);
    }

    private void buildTeamMeshSet(Map<String, MeshBuilder3D.Mesh3D> map, float r, float g, float b) {
        map.put("hull_tank", MeshBuilder3D.createTankHull(r, g, b));
        map.put("turret_tank", MeshBuilder3D.createTankTurret(r, g, b, false));
        map.put("turret_mammoth", MeshBuilder3D.createTankTurret(r, g, b, true));
        map.put("harvester", MeshBuilder3D.createHarvesterMesh(r, g, b));
        map.put("infantry", MeshBuilder3D.createInfantryMesh(r, g, b));

        map.put("b_conyard", MeshBuilder3D.createBuildingMesh("conyard", 136f, 136f, r, g, b));
        map.put("b_power", MeshBuilder3D.createBuildingMesh("power", 96f, 96f, r, g, b));
        map.put("b_refinery", MeshBuilder3D.createBuildingMesh("refinery", 136f, 108f, r, g, b));
        map.put("b_barracks", MeshBuilder3D.createBuildingMesh("barracks", 104f, 104f, r, g, b));
        map.put("b_factory", MeshBuilder3D.createBuildingMesh("factory", 144f, 124f, r, g, b));
        map.put("b_turret", MeshBuilder3D.createBuildingMesh("turret", 68f, 68f, r, g, b));
        map.put("b_obelisk", MeshBuilder3D.createBuildingMesh("obelisk", 76f, 76f, r, g, b));
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        state.viewportW = Math.max(1, width);
        state.viewportH = Math.max(1, height);
        float aspect = (float) width / Math.max(1, height);
        Matrix.perspectiveM(projMatrix, 0, 46.0f, aspect, 25.0f, 6500.0f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.08f, (nowNs - lastFrameNs) / 1_000_000_000.0f);
        lastFrameNs = nowNs;

        state.update(dt);

        if (state.terrainDirty || terrainMesh == null) {
            state.terrainDirty = false;
            terrainMesh = MeshBuilder3D.createTerrainMesh(state.worldW, state.worldH, state.biome, state.mapSeed);
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        GLES30.glUseProgram(shaderProgram);
        GLES30.glUniform3f(uLightDirLoc, 0.45f, 0.78f, 0.42f);

        if (state.inIntroCutscene) {
            renderIntroCutscene3D(state.introTimeSec);
            return;
        }

        // Compute 3D Hybrid Camera Matrix (Zoomed-Out Top-Down RTS <-> Zoomed-In 3D Ground Camera)
        float pitchRad = (float) Math.toRadians(state.getCameraPitchDeg());
        float yawRad = (float) Math.toRadians(state.cameraYawDeg);
        float dist = 1350.0f / Math.max(0.35f, state.cameraZoom);

        float shakeX = (state.cameraShake > 0.1f) ? (float) (Math.sin(nowNs * 0.00005) * state.cameraShake) : 0f;
        float shakeZ = (state.cameraShake > 0.1f) ? (float) (Math.cos(nowNs * 0.00007) * state.cameraShake) : 0f;

        float targetX = state.cameraX + shakeX;
        float targetZ = state.cameraZ + shakeZ;

        float horizDist = (float) (dist * Math.cos(pitchRad));
        float eyeX = targetX + (float) (horizDist * Math.sin(yawRad));
        float eyeY = (float) (dist * Math.sin(pitchRad));
        float eyeZ = targetZ + (float) (horizDist * Math.cos(yawRad));

        // Up vector stabilized when near vertical 84-deg top-down view
        float upX = (float) (-Math.sin(yawRad) * Math.sin(pitchRad));
        float upY = (float) Math.cos(pitchRad);
        float upZ = (float) (-Math.cos(yawRad) * Math.sin(pitchRad));

        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, targetX, 0f, targetZ, upX, upY, upZ);
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0);

        synchronized (state) {
            System.arraycopy(vpMatrix, 0, state.currentVPMatrix, 0, 16);
            Matrix.invertM(state.currentInvVPMatrix, 0, vpMatrix, 0);
        }

        // 1. Render 3D Heightmap Terrain
        Matrix.setIdentityM(modelMatrix, 0);
        drawMesh(terrainMesh, modelMatrix, 1f, 1f, 1f, 1f, 0f);

        synchronized (state) {
            // 2. Render 3D Xenocite Crystal Clusters
            for (GameState.CrystalNode c : state.crystals) {
                if (c.amount <= 2f) continue;
                float scale = 0.55f + 0.65f * (c.amount / c.maxAmount);
                float pulse = 0.18f + 0.12f * (float) Math.sin(state.elapsedTime * 3.5f + c.pulseSeed);
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, c.x, 0f, c.z);
                Matrix.scaleM(modelMatrix, 0, scale, scale, scale);
                drawMesh(c.isBlue ? blueCrystalMesh : greenCrystalMesh, modelMatrix, 1f, 1f, 1f, 1f, pulse);
            }

            // 3. Render 3D Structures & Rotating Turrets
            for (GameState.Building b : state.buildings) {
                if (b.hp <= 0) continue;
                if ("enemy".equals(b.team) && !state.isVisible(b.x, b.z)) continue;

                Map<String, MeshBuilder3D.Mesh3D> teamSet = "player".equals(b.team) ? playerMeshes : enemyMeshes;
                MeshBuilder3D.Mesh3D bMesh = teamSet.get("b_" + b.type);
                if (bMesh == null) bMesh = teamSet.get("b_turret");

                // 3D Selection Highlight Pad
                if (b.selected) {
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, b.x, 1.8f, b.z);
                    Matrix.scaleM(modelMatrix, 0, b.w * 1.18f, 1.5f, b.d * 1.18f);
                    drawMesh(unitBoxMesh, modelMatrix, 0.22f, 0.96f, 0.48f, 0.65f, 0.45f);
                }

                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, b.x, 0f, b.z);
                drawMesh(bMesh, modelMatrix, 1f, 1f, 1f, 1f, 0.05f);

                if ("turret".equals(b.type)) {
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, b.x, 4f, b.z);
                    Matrix.rotateM(modelMatrix, 0, -b.turretYawDeg, 0f, 1f, 0f);
                    Matrix.scaleM(modelMatrix, 0, 1.25f, 1.25f, 1.25f);
                    drawMesh(teamSet.get("turret_tank"), modelMatrix, 1f, 1f, 1f, 1f, 0.08f);
                }
            }

            // 4. Render 3D Multi-Part Units (Chassis + Rotating 3D Turret + Recoil)
            for (GameState.Unit u : state.units) {
                if (u.hp <= 0) continue;
                if ("enemy".equals(u.team) && !state.isVisible(u.x, u.z)) continue;

                Map<String, MeshBuilder3D.Mesh3D> teamSet = "player".equals(u.team) ? playerMeshes : enemyMeshes;

                // Selection Ring
                if (u.selected) {
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, u.x, 1.2f, u.z);
                    Matrix.scaleM(modelMatrix, 0, u.radius * 2.2f, 1.0f, u.radius * 2.2f);
                    drawMesh(unitBoxMesh, modelMatrix, 0.25f, 0.98f, 0.52f, 0.72f, 0.50f);
                }

                // Ground Shadow
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, u.x + 4f, 0.6f, u.z + 4f);
                Matrix.scaleM(modelMatrix, 0, u.radius * 1.9f, 1f, u.radius * 1.6f);
                drawMesh(shadowDiscMesh, modelMatrix, 0.02f, 0.02f, 0.03f, 0.55f, 0f);

                if (u.spec.isInfantry) {
                    float bob = (float) Math.abs(Math.sin(u.walkPhase)) * 2.2f;
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, u.x, bob, u.z);
                    Matrix.rotateM(modelMatrix, 0, -u.turretYawDeg, 0f, 1f, 0f);
                    drawMesh(teamSet.get("infantry"), modelMatrix, 1f, 1f, 1f, 1f, 0.05f);
                } else if ("harvester".equals(u.type)) {
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, u.x, 0f, u.z);
                    Matrix.rotateM(modelMatrix, 0, -u.hullYawDeg, 0f, 1f, 0f);
                    drawMesh(teamSet.get("harvester"), modelMatrix, 1f, 1f, 1f, 1f, 0.05f);

                    // Render Glowing Xenocite Crystal Cargo inside Harvester Bed
                    if (u.cargo > 40f) {
                        float cargoScale = 0.45f + 0.55f * (u.cargo / u.maxCargo);
                        Matrix.setIdentityM(modelMatrix, 0);
                        Matrix.translateM(modelMatrix, 0, u.x, 12f, u.z);
                        Matrix.scaleM(modelMatrix, 0, cargoScale, cargoScale, cargoScale);
                        drawMesh(greenCrystalMesh, modelMatrix, 1f, 1f, 1f, 1f, 0.35f);
                    }
                } else {
                    float hullScale = "mammoth".equals(u.type) ? 1.32f : "buggy".equals(u.type) ? 0.78f : 1.0f;
                    // Draw 3D Hull
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, u.x, 0f, u.z);
                    Matrix.rotateM(modelMatrix, 0, -u.hullYawDeg, 0f, 1f, 0f);
                    Matrix.scaleM(modelMatrix, 0, hullScale, hullScale, hullScale);
                    drawMesh(teamSet.get("hull_tank"), modelMatrix, 1f, 1f, 1f, 1f, 0.05f);

                    // Draw Independently Rotating 3D Turret + Barrel Recoil
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, u.x, 0f, u.z);
                    Matrix.rotateM(modelMatrix, 0, -u.turretYawDeg, 0f, 1f, 0f);
                    Matrix.translateM(modelMatrix, 0, -u.recoil, 0f, 0f);
                    Matrix.scaleM(modelMatrix, 0, hullScale, hullScale, hullScale);
                    MeshBuilder3D.Mesh3D tMesh = "mammoth".equals(u.type)
                            ? teamSet.get("turret_mammoth") : teamSet.get("turret_tank");
                    drawMesh(tMesh, modelMatrix, 1f, 1f, 1f, 1f, 0.08f);
                }
            }

            // 5. Render 3D Projectiles & Laser Beams
            for (GameState.Projectile3D p : state.projectiles) {
                if (p.isLaser) {
                    float cx = (p.x + p.tx) * 0.5f;
                    float cy = (p.y + p.ty) * 0.5f;
                    float cz = (p.z + p.tz) * 0.5f;
                    float dx = p.tx - p.x, dy = p.ty - p.y, dz = p.tz - p.z;
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, cx, cy, cz);
                    Matrix.rotateM(modelMatrix, 0, -yaw, 0f, 1f, 0f);
                    Matrix.scaleM(modelMatrix, 0, len, 4.5f, 4.5f);
                    drawMesh(unitBoxMesh, modelMatrix, p.r, p.g, p.b, 0.95f, 0.85f);
                } else {
                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z);
                    Matrix.scaleM(modelMatrix, 0, 6.5f, 4.5f, 4.5f);
                    drawMesh(unitBoxMesh, modelMatrix, p.r, p.g, p.b, 1.0f, 0.75f);
                }
            }

            // 6. Render 3D Explosion Particles & Shockwaves
            for (GameState.Particle3D pt : state.particles) {
                float alpha = Math.max(0.1f, pt.life / pt.maxLife);
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, pt.x, pt.y, pt.z);
                if (pt.isRing) {
                    float s = pt.size * (1.0f - alpha * 0.6f);
                    Matrix.scaleM(modelMatrix, 0, s, 2.0f, s);
                } else {
                    Matrix.scaleM(modelMatrix, 0, pt.size, pt.size, pt.size);
                }
                drawMesh(unitBoxMesh, modelMatrix, pt.r, pt.g, pt.b, alpha, 0.75f);
            }
        }
    }

    /**
     * Real-Time OpenGL ES 3.0 3D Cinematic Intro Cutscene (4 Acts).
     */
    private void renderIntroCutscene3D(float t) {
        if (t < 4.5f) {
            // Act I: 3D Orbital Recon of Planet Ares-Prime
            Matrix.setLookAtM(viewMatrix, 0, 0f, 90f, 260f, 0f, 0f, 0f, 0f, 1f, 0f);
            Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0);

            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.rotateM(modelMatrix, 0, t * 28f, 0.2f, 1f, 0.1f);
            Matrix.scaleM(modelMatrix, 0, 85f, 85f, 85f);
            drawMesh(unitBoxMesh, modelMatrix, 0.15f, 0.55f, 0.85f, 0.9f, 0.25f);

            // Orbiting Satellite Ring
            for (int i = 0; i < 12; i++) {
                float ang = (float) (i * Math.PI / 6.0 + t * 1.5);
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, (float) Math.cos(ang) * 125f, (float) Math.sin(ang * 0.5) * 25f, (float) Math.sin(ang) * 125f);
                Matrix.scaleM(modelMatrix, 0, 8f, 4f, 8f);
                drawMesh(unitBoxMesh, modelMatrix, 0.2f, 0.95f, 0.55f, 1f, 0.6f);
            }
        } else if (t < 9.0f) {
            // Act II: 3D Assault Drop-Pods Atmospheric Re-Entry
            float localT = t - 4.5f;
            Matrix.setLookAtM(viewMatrix, 0, 0f, 40f, 190f, 0f, 0f, 0f, 0f, 1f, 0f);
            Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0);

            for (int p = -1; p <= 1; p++) {
                float px = p * 55f;
                float py = 90f - localT * 38f + p * 12f;
                // Pod Hull
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, px, py, 0f);
                Matrix.scaleM(modelMatrix, 0, 18f, 24f, 18f);
                drawMesh(unitBoxMesh, modelMatrix, 0.35f, 0.42f, 0.48f, 1f, 0.1f);
                // Plasma Flame Trail
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, px, py + 32f, 0f);
                Matrix.scaleM(modelMatrix, 0, 12f, 48f, 12f);
                drawMesh(unitBoxMesh, modelMatrix, 1.0f, 0.45f, 0.12f, 0.85f, 0.85f);
            }
        } else {
            // Act III & IV: 3D Canyon Tank Battle & Orbital Ion Strike
            float localT = t - 9.0f;
            float camAng = localT * 0.32f;
            float camX = 620f + (float) Math.cos(camAng) * 280f;
            float camZ = (state.worldH - 620f) + (float) Math.sin(camAng) * 280f;
            Matrix.setLookAtM(viewMatrix, 0, camX, 95f, camZ, 520f, 12f, state.worldH - 520f, 0f, 1f, 0f);
            Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0);

            Matrix.setIdentityM(modelMatrix, 0);
            drawMesh(terrainMesh, modelMatrix, 1f, 1f, 1f, 1f, 0f);

            // Render 3D Colossus Tank Squadron vs Viper Obelisk
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.translateM(modelMatrix, 0, 480f + localT * 14f, 0f, state.worldH - 520f);
            Matrix.scaleM(modelMatrix, 0, 1.6f, 1.6f, 1.6f);
            drawMesh(playerMeshes.get("hull_tank"), modelMatrix, 1f, 1f, 1f, 1f, 0.1f);
            drawMesh(playerMeshes.get("turret_mammoth"), modelMatrix, 1f, 1f, 1f, 1f, 0.15f);

            if (t >= 13.5f) {
                // Act IV: Orbital Ion Beam Column
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.translateM(modelMatrix, 0, 540f, 120f, state.worldH - 520f);
                Matrix.scaleM(modelMatrix, 0, 26f, 320f, 26f);
                drawMesh(unitBoxMesh, modelMatrix, 0.25f, 0.92f, 1.0f, 0.88f, 0.95f);
            }
        }
    }

    private void drawMesh(MeshBuilder3D.Mesh3D mesh, float[] model,
                          float r, float g, float b, float a, float emissive) {
        if (mesh == null) return;
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, model, 0);
        GLES30.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GLES30.glUniformMatrix4fv(uModelMatrixLoc, 1, false, model, 0);
        GLES30.glUniform4f(uTintLoc, r, g, b, a);
        GLES30.glUniform1f(uEmissiveLoc, emissive);

        mesh.vertexBuffer.position(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, MeshBuilder3D.STRIDE_BYTES, mesh.vertexBuffer);
        GLES30.glEnableVertexAttribArray(0);

        mesh.vertexBuffer.position(3);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, MeshBuilder3D.STRIDE_BYTES, mesh.vertexBuffer);
        GLES30.glEnableVertexAttribArray(1);

        mesh.vertexBuffer.position(6);
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, MeshBuilder3D.STRIDE_BYTES, mesh.vertexBuffer);
        GLES30.glEnableVertexAttribArray(2);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mesh.vertexCount);
    }

    private static int compileShader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        return shader;
    }
}
