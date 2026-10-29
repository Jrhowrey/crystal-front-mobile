package com.crystalfront.mobilecommand;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Procedural 3D Mesh Builder for Native OpenGL ES 3.0 Renderer.
 * Vertex format: 10 floats per vertex [x, y, z, nx, ny, nz, r, g, b, a]
 * where X and Z are horizontal battlefield coordinates and Y is vertical elevation.
 */
public class MeshBuilder3D {
    public static final int FLOATS_PER_VERTEX = 10;
    public static final int STRIDE_BYTES = FLOATS_PER_VERTEX * 4;

    public static class Mesh3D {
        public final FloatBuffer vertexBuffer;
        public final int vertexCount;

        public Mesh3D(float[] data) {
            this.vertexCount = data.length / FLOATS_PER_VERTEX;
            ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
            bb.order(ByteOrder.nativeOrder());
            this.vertexBuffer = bb.asFloatBuffer();
            this.vertexBuffer.put(data);
            this.vertexBuffer.position(0);
        }
    }

    private final List<Float> verts = new ArrayList<>(4096);

    public void clear() {
        verts.clear();
    }

    public void addVertex(float x, float y, float z,
                          float nx, float ny, float nz,
                          float r, float g, float b, float a) {
        verts.add(x); verts.add(y); verts.add(z);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(r); verts.add(g); verts.add(b); verts.add(a);
    }

    public void addTri(float x1, float y1, float z1,
                       float x2, float y2, float z2,
                       float x3, float y3, float z3,
                       float r, float g, float b, float a) {
        float ux = x2 - x1, uy = y2 - y1, uz = z2 - z1;
        float vx = x3 - x1, vy = y3 - y1, vz = z3 - z1;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-5f) {
            nx /= len; ny /= len; nz /= len;
        } else {
            nx = 0f; ny = 1f; nz = 0f;
        }
        addVertex(x1, y1, z1, nx, ny, nz, r, g, b, a);
        addVertex(x2, y2, z2, nx, ny, nz, r, g, b, a);
        addVertex(x3, y3, z3, nx, ny, nz, r, g, b, a);
    }

    public void addQuad(float x1, float y1, float z1,
                        float x2, float y2, float z2,
                        float x3, float y3, float z3,
                        float x4, float y4, float z4,
                        float r, float g, float b, float a) {
        addTri(x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a);
        addTri(x1, y1, z1, x3, y3, z3, x4, y4, z4, r, g, b, a);
    }

    public void addBox(float cx, float cy, float cz,
                       float sx, float sy, float sz,
                       float r, float g, float b, float a) {
        float x0 = cx - sx * 0.5f, x1 = cx + sx * 0.5f;
        float y0 = cy - sy * 0.5f, y1 = cy + sy * 0.5f;
        float z0 = cz - sz * 0.5f, z1 = cz + sz * 0.5f;

        // Top (+Y)
        addQuad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r * 1.12f, g * 1.12f, b * 1.12f, a);
        // Bottom (-Y)
        addQuad(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, r * 0.55f, g * 0.55f, b * 0.55f, a);
        // Front (+Z)
        addQuad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r * 0.92f, g * 0.92f, b * 0.92f, a);
        // Back (-Z)
        addQuad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r * 0.72f, g * 0.72f, b * 0.72f, a);
        // Left (-X)
        addQuad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r * 0.78f, g * 0.78f, b * 0.78f, a);
        // Right (+X)
        addQuad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r * 0.85f, g * 0.85f, b * 0.85f, a);
    }

    public void addPyramid(float cx, float baseY, float cz,
                           float baseW, float height, float baseD,
                           float r, float g, float b, float a) {
        float x0 = cx - baseW * 0.5f, x1 = cx + baseW * 0.5f;
        float z0 = cz - baseD * 0.5f, z1 = cz + baseD * 0.5f;
        float topY = baseY + height;
        addTri(x0, baseY, z1, x1, baseY, z1, cx, topY, cz, r, g, b, a);
        addTri(x1, baseY, z1, x1, baseY, z0, cx, topY, cz, r * 0.9f, g * 0.9f, b * 0.9f, a);
        addTri(x1, baseY, z0, x0, baseY, z0, cx, topY, cz, r * 0.78f, g * 0.78f, b * 0.78f, a);
        addTri(x0, baseY, z0, x0, baseY, z1, cx, topY, cz, r * 0.84f, g * 0.84f, b * 0.84f, a);
    }

    public Mesh3D build() {
        float[] arr = new float[verts.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = verts.get(i);
        }
        return new Mesh3D(arr);
    }

    // --- PROCEDURAL 3D MULTI-PART UNIT & BUILDING MESHES ---

    public static Mesh3D createTankHull(float r, float g, float b) {
        MeshBuilder3D mb = new MeshBuilder3D();
        // Left & Right Armored Track Pods
        mb.addBox(0f, 3.5f, -10f, 32f, 7f, 6.5f, 0.16f, 0.18f, 0.20f, 1f);
        mb.addBox(0f, 3.5f,  10f, 32f, 7f, 6.5f, 0.16f, 0.18f, 0.20f, 1f);
        // Main Sloped Armored Chassis
        mb.addBox(0f, 6.5f, 0f, 28f, 7.5f, 15f, r, g, b, 1f);
        // Front Glacis Armor Plate
        mb.addBox(12f, 6.0f, 0f, 6f, 5.5f, 14f, r * 0.85f, g * 0.85f, b * 0.85f, 1f);
        // Rear Engine Deck Grill
        mb.addBox(-10f, 10.6f, 0f, 7f, 1.2f, 11f, 0.12f, 0.14f, 0.16f, 1f);
        return mb.build();
    }

    public static Mesh3D createTankTurret(float r, float g, float b, boolean mammothDual) {
        MeshBuilder3D mb = new MeshBuilder3D();
        if (mammothDual) {
            mb.addBox(-2f, 14.5f, 0f, 18f, 7.5f, 16f, r * 1.05f, g * 1.05f, b * 1.05f, 1f);
            // Dual Heavy 120mm Cannon Barrels
            mb.addBox(14f, 14.8f, -4.2f, 22f, 2.6f, 2.6f, 0.28f, 0.32f, 0.36f, 1f);
            mb.addBox(14f, 14.8f,  4.2f, 22f, 2.6f, 2.6f, 0.28f, 0.32f, 0.36f, 1f);
            // Side Missile Pods
            mb.addBox(-3f, 16.5f, -9.5f, 9f, 5f, 3.5f, 0.22f, 0.25f, 0.28f, 1f);
            mb.addBox(-3f, 16.5f,  9.5f, 9f, 5f, 3.5f, 0.22f, 0.25f, 0.28f, 1f);
        } else {
            mb.addBox(-1f, 13.5f, 0f, 14f, 6.2f, 12f, r * 1.05f, g * 1.05f, b * 1.05f, 1f);
            // Single High-Velocity Cannon Barrel
            mb.addBox(12f, 13.8f, 0f, 19f, 2.3f, 2.3f, 0.26f, 0.30f, 0.34f, 1f);
        }
        return mb.build();
    }

    public static Mesh3D createHarvesterMesh(float r, float g, float b) {
        MeshBuilder3D mb = new MeshBuilder3D();
        // Heavy Track Base
        mb.addBox(0f, 4.5f, -12f, 36f, 9f, 7f, 0.15f, 0.17f, 0.19f, 1f);
        mb.addBox(0f, 4.5f,  12f, 36f, 9f, 7f, 0.15f, 0.17f, 0.19f, 1f);
        // Forward Armored Cab
        mb.addBox(8f, 10.5f, 0f, 16f, 11f, 18f, r, g, b, 1f);
        // Cockpit Visor (Glowing Cyan)
        mb.addBox(15.5f, 12.0f, 0f, 2.5f, 4.0f, 12f, 0.15f, 0.78f, 0.95f, 1f);
        // Rear Xenocite Containment Hopper
        mb.addBox(-9f, 11.0f, 0f, 18f, 12f, 20f, r * 0.78f, g * 0.78f, b * 0.78f, 1f);
        // Front Crystal Drill Claws
        mb.addPyramid(20f, 4f, -6f, 6f, 8f, 5f, 0.35f, 0.40f, 0.45f, 1f);
        mb.addPyramid(20f, 4f,  6f, 6f, 8f, 5f, 0.35f, 0.40f, 0.45f, 1f);
        return mb.build();
    }

    public static Mesh3D createInfantryMesh(float r, float g, float b) {
        MeshBuilder3D mb = new MeshBuilder3D();
        // Boots / Legs
        mb.addBox(0f, 3.0f, -2.2f, 3.5f, 6.0f, 3.0f, 0.18f, 0.20f, 0.24f, 1f);
        mb.addBox(0f, 3.0f,  2.2f, 3.5f, 6.0f, 3.0f, 0.18f, 0.20f, 0.24f, 1f);
        // Armored Exoskeleton Torso
        mb.addBox(0f, 9.0f, 0f, 5.5f, 7.0f, 7.5f, r, g, b, 1f);
        // Helmet & Visor
        mb.addBox(0f, 14.2f, 0f, 4.5f, 4.2f, 4.5f, r * 1.1f, g * 1.1f, b * 1.1f, 1f);
        mb.addBox(2.2f, 14.4f, 0f, 1.2f, 1.8f, 3.4f, 0.2f, 0.9f, 1.0f, 1f);
        // Pulse Rifle / Launcher
        mb.addBox(4.5f, 9.5f, 2.8f, 8.5f, 2.0f, 1.8f, 0.22f, 0.25f, 0.28f, 1f);
        return mb.build();
    }

    public static Mesh3D createBuildingMesh(String type, float w, float d, float r, float g, float b) {
        MeshBuilder3D mb = new MeshBuilder3D();
        // 1. Concrete Foundation Pad Skirt
        mb.addBox(0f, 1.5f, 0f, w * 1.12f, 3.0f, d * 1.12f, 0.22f, 0.24f, 0.27f, 1f);

        if ("conyard".equals(type)) {
            mb.addBox(-w * 0.12f, 16f, 0f, w * 0.72f, 29f, d * 0.82f, r, g, b, 1f);
            mb.addBox(w * 0.22f, 26f, -d * 0.18f, w * 0.32f, 48f, d * 0.36f, r * 1.12f, g * 1.12f, b * 1.12f, 1f);
            // Glowing Command Bridge Window
            mb.addBox(w * 0.22f, 42f, -d * 0.18f, w * 0.35f, 5f, d * 0.38f, 0.20f, 0.85f, 0.98f, 1f);
        } else if ("power".equals(type)) {
            mb.addBox(0f, 12f, 0f, w * 0.84f, 21f, d * 0.84f, r, g, b, 1f);
            // Twin Reactor Cooling Towers + Glowing Xenocite Plasma Cores
            mb.addBox(-w * 0.22f, 26f, -d * 0.18f, w * 0.32f, 34f, d * 0.32f, 0.28f, 0.32f, 0.36f, 1f);
            mb.addBox( w * 0.22f, 26f,  d * 0.18f, w * 0.32f, 34f, d * 0.32f, 0.28f, 0.32f, 0.36f, 1f);
            mb.addBox(-w * 0.22f, 44f, -d * 0.18f, w * 0.22f, 6f, d * 0.22f, 0.15f, 0.95f, 0.45f, 1f);
            mb.addBox( w * 0.22f, 44f,  d * 0.18f, w * 0.22f, 6f, d * 0.22f, 0.15f, 0.95f, 0.45f, 1f);
        } else if ("refinery".equals(type)) {
            mb.addBox(-w * 0.15f, 15f, 0f, w * 0.62f, 27f, d * 0.86f, r, g, b, 1f);
            // Harvester Unloading Bay Ramp
            mb.addBox(w * 0.26f, 4f, 0f, w * 0.38f, 6f, d * 0.68f, 0.32f, 0.34f, 0.38f, 1f);
            // Smelter Silos
            mb.addBox(-w * 0.26f, 34f, -d * 0.24f, w * 0.24f, 42f, d * 0.24f, 0.35f, 0.38f, 0.42f, 1f);
            mb.addBox(-w * 0.26f, 34f,  d * 0.24f, w * 0.24f, 42f, d * 0.24f, 0.35f, 0.38f, 0.42f, 1f);
        } else if ("barracks".equals(type)) {
            mb.addBox(0f, 13f, 0f, w * 0.84f, 23f, d * 0.84f, r, g, b, 1f);
            mb.addPyramid(0f, 24.5f, 0f, w * 0.72f, 14f, d * 0.72f, r * 1.15f, g * 1.15f, b * 1.15f, 1f);
        } else if ("factory".equals(type)) {
            mb.addBox(0f, 18f, 0f, w * 0.88f, 33f, d * 0.88f, r, g, b, 1f);
            // Roof Monitor Skylights
            mb.addBox(0f, 36f, -d * 0.22f, w * 0.72f, 6f, d * 0.18f, 0.25f, 0.82f, 0.96f, 1f);
            mb.addBox(0f, 36f,  d * 0.22f, w * 0.72f, 6f, d * 0.18f, 0.25f, 0.82f, 0.96f, 1f);
        } else if ("obelisk".equals(type)) {
            mb.addBox(0f, 6f, 0f, w * 0.75f, 10f, d * 0.75f, 0.18f, 0.18f, 0.22f, 1f);
            // Towering Crimson Prism Obelisk Monolith
            mb.addPyramid(0f, 10f, 0f, w * 0.52f, 58f, d * 0.52f, 0.15f, 0.12f, 0.16f, 1f);
            // Glowing Ruby Focusing Crystal Tip
            mb.addPyramid(0f, 56f, 0f, w * 0.28f, 18f, d * 0.28f, 1.0f, 0.15f, 0.22f, 1f);
        } else {
            // Fortified Defense Turret Base
            mb.addBox(0f, 8f, 0f, w * 0.78f, 14f, d * 0.78f, r, g, b, 1f);
        }
        return mb.build();
    }

    public static Mesh3D createCrystalCluster(boolean isBlue) {
        MeshBuilder3D mb = new MeshBuilder3D();
        float r = isBlue ? 0.22f : 0.13f;
        float g = isBlue ? 0.74f : 0.86f;
        float b = isBlue ? 0.98f : 0.42f;
        mb.addPyramid(0f, 0f, 0f, 10f, 24f, 10f, r, g, b, 1f);
        mb.addPyramid(-6f, 0f, 4f, 7f, 16f, 7f, r * 0.85f, g * 0.95f, b, 1f);
        mb.addPyramid( 6f, 0f, -4f, 7f, 17f, 7f, r * 1.1f, g, b * 0.9f, 1f);
        mb.addPyramid( 3f, 0f, 6f, 6f, 13f, 6f, r, g * 1.05f, b, 1f);
        return mb.build();
    }

    public static Mesh3D createTerrainMesh(int worldW, int worldH, String biome, int seed) {
        MeshBuilder3D mb = new MeshBuilder3D();
        int step = 120;
        int cols = worldW / step;
        int rows = worldH / step;

        float baseR = 0.22f, baseG = 0.16f, baseB = 0.11f;
        if ("wasteland".equals(biome)) {
            baseR = 0.10f; baseG = 0.18f; baseB = 0.13f;
        } else if ("volcanic".equals(biome)) {
            baseR = 0.14f; baseG = 0.11f; baseB = 0.12f;
        }

        float[][] heights = new float[cols + 1][rows + 1];
        for (int gx = 0; gx <= cols; gx++) {
            for (int gz = 0; gz <= rows; gz++) {
                float wx = gx * step;
                float wz = gz * step;
                // Keep spawn corners flat for base building
                float dPlayer = (float) Math.hypot(wx - 420f, wz - (worldH - 420f));
                float dEnemy = (float) Math.hypot(wx - (worldW - 420f), wz - 420f);
                if (dPlayer < 520f || dEnemy < 520f) {
                    heights[gx][gz] = 0f;
                } else {
                    double n = Math.sin(wx * 0.0045 + seed) * Math.cos(wz * 0.0045 - seed * 0.5)
                             + 0.5 * Math.sin((wx + wz) * 0.009 + seed * 1.7);
                    heights[gx][gz] = (n > 0.62) ? (float) ((n - 0.62) * 68.0) : (float) (n * 4.5);
                }
            }
        }

        for (int gx = 0; gx < cols; gx++) {
            for (int gz = 0; gz < rows; gz++) {
                float x0 = gx * step, x1 = (gx + 1) * step;
                float z0 = gz * step, z1 = (gz + 1) * step;
                float y00 = heights[gx][gz];
                float y10 = heights[gx + 1][gz];
                float y11 = heights[gx + 1][gz + 1];
                float y01 = heights[gx][gz + 1];

                float avgY = (y00 + y10 + y11 + y01) * 0.25f;
                float elevTint = Math.min(0.22f, Math.max(-0.04f, avgY * 0.0045f));
                float check = ((gx + gz) % 2 == 0) ? 0.012f : -0.012f;
                float r = Math.min(1f, Math.max(0f, baseR + elevTint + check));
                float g = Math.min(1f, Math.max(0f, baseG + elevTint * 0.85f + check));
                float b = Math.min(1f, Math.max(0f, baseB + elevTint * 0.65f + check));

                mb.addQuad(
                        x0, y00, z0,
                        x0, y01, z1,
                        x1, y11, z1,
                        x1, y10, z0,
                        r, g, b, 1f
                );
            }
        }
        return mb.build();
    }
}
