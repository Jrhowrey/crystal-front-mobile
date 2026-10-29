package com.crystalfront.mobilecommand;

import android.opengl.Matrix;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Core Native 3D RTS Simulation & State Engine.
 * Manages 3D camera projection (Top-Down RTS <-> 3D Ground Perspective),
 * Structure-of-Arrays (SoA) broadphase targeting, economy, production, AI doctrines,
 * fog of war, and 3D raycast unprojection.
 */
public class GameState {
    public static class Spec {
        public final String id;
        public final String category; // "structures", "infantry", "vehicles", "support"
        public final String gdiName;
        public final String nodName;
        public final int cost;
        public final float buildTime;
        public final float maxHp;
        public final float speed;
        public final float range;
        public final float damage;
        public final float reload;
        public final int powerDelta;
        public final float sizeW;
        public final float sizeD;
        public final boolean isInfantry;

        public Spec(String id, String category, String gdiName, String nodName,
                    int cost, float buildTime, float maxHp, float speed,
                    float range, float damage, float reload, int powerDelta,
                    float sizeW, float sizeD, boolean isInfantry) {
            this.id = id;
            this.category = category;
            this.gdiName = gdiName;
            this.nodName = nodName;
            this.cost = cost;
            this.buildTime = buildTime;
            this.maxHp = maxHp;
            this.speed = speed;
            this.range = range;
            this.damage = damage;
            this.reload = reload;
            this.powerDelta = powerDelta;
            this.sizeW = sizeW;
            this.sizeD = sizeD;
            this.isInfantry = isInfantry;
        }
    }

    public static class Building {
        public int id;
        public String type;
        public Spec spec;
        public float x, z;
        public float w, d;
        public float hp, maxHp;
        public String team; // "player" or "enemy"
        public boolean selected;
        public boolean repairing;
        public float turretYawDeg;
        public float fireCooldown;
        public float rallyX, rallyZ;
    }

    public static class Unit {
        public int id;
        public String type;
        public Spec spec;
        public float x, z;
        public float radius;
        public float hp, maxHp;
        public String team;
        public boolean selected;
        public float hullYawDeg;
        public float turretYawDeg;
        public float recoil;
        public float walkPhase;
        public float fireCooldown;
        public Float moveTargetX, moveTargetZ;
        public boolean attackMove;
        public int targetEntityId = -1;
        public float cargo = 0f;
        public float maxCargo = 700f;
        public String stance = "guard";
        public int kills = 0;
    }

    public static class CrystalNode {
        public int id;
        public float x, z;
        public boolean isBlue;
        public float amount, maxAmount;
        public float pulseSeed;
    }

    public static class Projectile3D {
        public float x, y, z;
        public float tx, ty, tz;
        public float speed;
        public float damage;
        public float splash;
        public String team;
        public boolean isLaser;
        public float r, g, b;
    }

    public static class Particle3D {
        public float x, y, z;
        public float vx, vy, vz;
        public float size;
        public float life, maxLife;
        public float r, g, b;
        public boolean isRing;
    }

    // --- WORLD & CAMERA STATE ---
    public int worldW = 3840;
    public int worldH = 3840;
    public int tileSize = 64;
    public int gridW = 60;
    public int gridH = 60;
    public byte[] fogGrid; // 0 = shroud, 1 = explored, 2 = visible

    public String mapSize = "large"; // "standard", "large", "epic"
    public String biome = "redrock"; // "redrock", "wasteland", "volcanic"
    public String faction = "GDI";   // "GDI" (Aegis) or "NOD" (Viper)
    public String difficulty = "casual"; // "casual", "commander", "brutal"
    public String aiDoctrine = "adaptive";
    public String gfxQuality = "high"; // "low", "medium", "high", "ultra"
    public boolean sgsrEnabled = true;
    public int mapSeed = 48291;
    public volatile boolean terrainDirty = true;

    public boolean inTitleScreen = true;
    public boolean inIntroCutscene = false;
    public float introTimeSec = 0f;
    public boolean paused = false;
    public boolean gameOver = false;
    public boolean playerWon = false;

    public float elapsedTime = 0f;
    public float credits = 4500f;
    public float aiCredits = 1600f;
    public int powerOut = 100;
    public int powerDrain = 40;
    public String activeTab = "structures"; // "structures", "infantry", "vehicles", "support"
    public String orderMode = "normal";     // "normal", "attackMove", "repair", "sell", "place", "support"
    public Spec placingSpec = null;
    public Spec activeSupportSpec = null;

    // Production Queues
    public Spec structQueueSpec = null;
    public float structQueueProg = 0f;
    public boolean structReadyToPlace = false;

    public Spec infQueueSpec = null;
    public float infQueueProg = 0f;

    public Spec vehQueueSpec = null;
    public float vehQueueProg = 0f;

    public float cooldownAirstrike = 0f;
    public float cooldownIon = 0f;
    public float cooldownDrop = 0f;

    // 3D Hybrid Camera State (Focal point on ground X,Z + Zoom + Orbit Yaw)
    public float cameraX = 480f;
    public float cameraZ = 3300f;
    public float cameraZoom = 1.05f; // 0.35 (Top-Down RTS) <-> 2.40 (Full 3D Ground Perspective)
    public float cameraYawDeg = 0f;
    public float cameraShake = 0f;
    public int viewportW = 1280;
    public int viewportH = 720;

    public final float[] currentVPMatrix = new float[16];
    public final float[] currentInvVPMatrix = new float[16];

    public final List<Spec> catalog = new ArrayList<>();
    public final List<Building> buildings = new ArrayList<>();
    public final List<Unit> units = new ArrayList<>();
    public final List<CrystalNode> crystals = new ArrayList<>();
    public final List<Projectile3D> projectiles = new ArrayList<>();
    public final List<Particle3D> particles = new ArrayList<>();

    private int nextEntityId = 1;
    private float aiTimer = 0f;
    private float aiWaveTimer = 65f;
    private final SoundEngine sound;
    private final Random rng = new Random(48291);

    public GameState(SoundEngine sound) {
        this.sound = sound;
        initCatalog();
        initSkirmish();
    }

    private void initCatalog() {
        catalog.clear();
        // Structures
        catalog.add(new Spec("power", "structures", "Reactor Core", "Bio-Reactor", 300, 5f, 750f, 0f, 0f, 0f, 0f, 100, 96f, 96f, false));
        catalog.add(new Spec("refinery", "structures", "Ore Refinery", "Xenocite Hub", 900, 9f, 1200f, 0f, 0f, 0f, 0f, -30, 136f, 108f, false));
        catalog.add(new Spec("barracks", "structures", "Barracks", "Hand of Viper", 400, 6f, 900f, 0f, 0f, 0f, 0f, -20, 104f, 104f, false));
        catalog.add(new Spec("factory", "structures", "War Factory", "Mech Bay", 1100, 10f, 1500f, 0f, 0f, 0f, 0f, -35, 144f, 124f, false));
        catalog.add(new Spec("turret", "structures", "Guardian Turret", "Laser Turret", 450, 6f, 850f, 0f, 250f, 34f, 1.05f, -20, 68f, 68f, false));
        catalog.add(new Spec("obelisk", "structures", "Pulse Cannon", "Prism Obelisk", 950, 9f, 1150f, 0f, 340f, 135f, 2.5f, -45, 76f, 76f, false));

        // Infantry
        catalog.add(new Spec("rifle", "infantry", "Rifle Squad", "Viper Militia", 120, 3.0f, 150f, 72f, 145f, 16f, 0.65f, 0, 18f, 18f, true));
        catalog.add(new Spec("rocket", "infantry", "Missile Trooper", "Shock Trooper", 260, 4.5f, 190f, 62f, 210f, 44f, 1.35f, 0, 20f, 20f, true));
        catalog.add(new Spec("engineer", "infantry", "Combat Engineer", "Saboteur", 350, 5.0f, 160f, 68f, 90f, 85f, 1.50f, 0, 18f, 18f, true));

        // Vehicles
        catalog.add(new Spec("harvester", "vehicles", "Crystal Harvester", "Reaper Harvester", 750, 8.0f, 1150f, 64f, 0f, 0f, 0f, 0, 44f, 32f, false));
        catalog.add(new Spec("buggy", "vehicles", "Recon Striker", "Raider Buggy", 380, 5.0f, 440f, 118f, 175f, 24f, 0.55f, 0, 34f, 24f, false));
        catalog.add(new Spec("tank", "vehicles", "Predator Tank", "Scorpion Tank", 650, 7.0f, 820f, 82f, 225f, 62f, 1.35f, 0, 40f, 28f, false));
        catalog.add(new Spec("mammoth", "vehicles", "Colossus Mammoth", "Avatar Walker", 1250, 11.0f, 1650f, 58f, 265f, 128f, 1.65f, 0, 52f, 36f, false));

        // Support Superweapons
        catalog.add(new Spec("airstrike", "support", "Tactical Airstrike", "Venom Bomber", 500, 35f, 0f, 0f, 0f, 320f, 0f, 0, 0f, 0f, false));
        catalog.add(new Spec("ioncannon", "support", "Orbital Ion Beam", "Thermobaric Strike", 850, 50f, 0f, 0f, 0f, 680f, 0f, 0, 0f, 0f, false));
        catalog.add(new Spec("droppod", "support", "Airborne Drop Squad", "Shadow Insertion", 450, 30f, 0f, 0f, 0f, 0f, 0f, 0, 0f, 0f, false));
    }

    public Spec findSpec(String id) {
        for (Spec s : catalog) {
            if (s.id.equals(id)) return s;
        }
        return catalog.get(0);
    }

    public String getDisplayName(Spec s) {
        return "NOD".equals(faction) ? s.nodName : s.gdiName;
    }

    public synchronized void setMapSize(String sizeKey) {
        this.mapSize = sizeKey;
        if ("standard".equals(sizeKey)) {
            worldW = 2400; worldH = 2400;
        } else if ("epic".equals(sizeKey)) {
            worldW = 4800; worldH = 4800;
        } else {
            worldW = 3840; worldH = 3840;
        }
        gridW = worldW / tileSize;
        gridH = worldH / tileSize;
        fogGrid = new byte[gridW * gridH];
        terrainDirty = true;
    }

    public synchronized void initSkirmish() {
        setMapSize(this.mapSize);
        elapsedTime = 0f;
        gameOver = false;
        playerWon = false;
        credits = "casual".equals(difficulty) ? 5500f : "commander".equals(difficulty) ? 4000f : 3000f;
        aiCredits = "brutal".equals(difficulty) ? 3500f : "commander".equals(difficulty) ? 2000f : 1100f;
        orderMode = "normal";
        placingSpec = null;
        activeSupportSpec = null;
        structQueueSpec = null; structQueueProg = 0f; structReadyToPlace = false;
        infQueueSpec = null; infQueueProg = 0f;
        vehQueueSpec = null; vehQueueProg = 0f;
        cooldownAirstrike = 0f; cooldownIon = 8f; cooldownDrop = 0f;

        buildings.clear();
        units.clear();
        crystals.clear();
        projectiles.clear();
        particles.clear();
        for (int i = 0; i < fogGrid.length; i++) fogGrid[i] = 0;

        float px = 440f;
        float pz = worldH - 460f;
        float ex = worldW - 460f;
        float ez = 440f;

        cameraX = px + 80f;
        cameraZ = pz - 80f;

        // Spawn Crystal Fields across the 3D Map
        spawnCrystalField(px + 340f, pz - 60f, false, 26);
        spawnCrystalField(px + 120f, pz - 560f, false, 22);
        spawnCrystalField(ex - 340f, ez + 60f, false, 26);
        spawnCrystalField(ex - 120f, ez + 560f, false, 22);
        spawnCrystalField(worldW * 0.50f, worldH * 0.50f, true, 32);
        spawnCrystalField(worldW * 0.36f, worldH * 0.38f, true, 22);
        spawnCrystalField(worldW * 0.64f, worldH * 0.62f, true, 22);
        if (worldW >= 3840) {
            spawnCrystalField(worldW * 0.24f, worldH * 0.25f, false, 24);
            spawnCrystalField(worldW * 0.76f, worldH * 0.75f, false, 24);
        }

        // Player Base
        spawnBuilding("conyard", px, pz, "player");
        spawnBuilding("power", px - 150f, pz - 130f, "player");
        spawnBuilding("refinery", px + 175f, pz, "player");
        spawnBuilding("barracks", px - 10f, pz - 165f, "player");
        spawnBuilding("factory", px + 150f, pz - 175f, "player");
        spawnBuilding("turret", px + 260f, pz - 150f, "player");

        // Player Starting Strike Force
        spawnUnit("harvester", px + 230f, pz - 40f, "player");
        spawnUnit("mammoth", px + 120f, pz - 270f, "player");
        spawnUnit("tank", px + 190f, pz - 255f, "player");
        spawnUnit("tank", px + 55f, pz - 255f, "player");
        spawnUnit("buggy", px + 250f, pz - 240f, "player");
        spawnUnit("rifle", px - 40f, pz - 245f, "player");
        spawnUnit("rocket", px + 10f, pz - 245f, "player");

        // Enemy AI Base
        spawnBuilding("conyard", ex, ez, "enemy");
        spawnBuilding("power", ex + 150f, ez + 130f, "enemy");
        spawnBuilding("refinery", ex - 175f, ez, "enemy");
        spawnBuilding("barracks", ex + 10f, ez + 165f, "enemy");
        spawnBuilding("factory", ex - 150f, ez + 175f, "enemy");
        spawnBuilding("turret", ex - 250f, ez + 160f, "enemy");
        spawnBuilding("obelisk", ex - 110f, ez + 260f, "enemy");

        spawnUnit("harvester", ex - 230f, ez + 40f, "enemy");
        spawnUnit("tank", ex - 180f, ez + 260f, "enemy");
        spawnUnit("tank", ex - 90f, ez + 270f, "enemy");
        spawnUnit("buggy", ex - 240f, ez + 250f, "enemy");
        spawnUnit("rocket", ex - 40f, ez + 240f, "enemy");

        recalcPower();
        updateFogOfWar();
        terrainDirty = true;
    }

    private void spawnCrystalField(float cx, float cz, boolean isBlue, int count) {
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0 * i) / count + rng.nextDouble() * 0.5;
            float dist = 28f + rng.nextFloat() * 140f;
            CrystalNode node = new CrystalNode();
            node.id = nextEntityId++;
            node.x = Math.max(90f, Math.min(worldW - 90f, cx + (float) Math.cos(ang) * dist));
            node.z = Math.max(90f, Math.min(worldH - 90f, cz + (float) Math.sin(ang) * dist));
            node.isBlue = isBlue;
            node.maxAmount = isBlue ? 180f : 100f;
            node.amount = node.maxAmount;
            node.pulseSeed = rng.nextFloat() * 6.28f;
            crystals.add(node);
        }
    }

    public Building spawnBuilding(String type, float x, float z, String team) {
        Spec s = "conyard".equals(type)
                ? new Spec("conyard", "structures", "Command Center", "Temple Prime", 2000, 15f, 2600f, 0f, 0f, 0f, 0f, 60, 136f, 136f, false)
                : findSpec(type);
        Building b = new Building();
        b.id = nextEntityId++;
        b.type = type;
        b.spec = s;
        b.x = x;
        b.z = z;
        b.w = s.sizeW;
        b.d = s.sizeD;
        b.hp = s.maxHp;
        b.maxHp = s.maxHp;
        b.team = team;
        b.rallyX = x + ("player".equals(team) ? 140f : -140f);
        b.rallyZ = z + ("player".equals(team) ? -140f : 140f);
        buildings.add(b);
        if ("refinery".equals(type)) {
            spawnUnit("harvester", x + ("player".equals(team) ? 95f : -95f), z + 55f, team);
        }
        recalcPower();
        return b;
    }

    public Unit spawnUnit(String type, float x, float z, String team) {
        Spec s = findSpec(type);
        Unit u = new Unit();
        u.id = nextEntityId++;
        u.type = type;
        u.spec = s;
        u.x = x + (rng.nextFloat() - 0.5f) * 14f;
        u.z = z + (rng.nextFloat() - 0.5f) * 14f;
        u.radius = Math.max(12f, s.sizeW * 0.45f);
        u.hp = s.maxHp;
        u.maxHp = s.maxHp;
        u.team = team;
        u.hullYawDeg = "player".equals(team) ? -45f : 135f;
        u.turretYawDeg = u.hullYawDeg;
        units.add(u);
        return u;
    }

    public void recalcPower() {
        int out = 0, drain = 0;
        for (Building b : buildings) {
            if (b.hp > 0 && "player".equals(b.team)) {
                if (b.spec.powerDelta >= 0) out += b.spec.powerDelta;
                else drain += (-b.spec.powerDelta);
            }
        }
        this.powerOut = out;
        this.powerDrain = drain;
    }

    // --- 3D HYBRID PERSPECTIVE CAMERA MATH ---

    /**
     * Smoothly transitions between Top-Down RTS (84 deg pitch when zoomed out)
     * and Full 3D Ground-Level Tactical Perspective (26 deg pitch when zoomed in).
     */
    public float getCameraPitchDeg() {
        float t = Math.max(0f, Math.min(1f, (cameraZoom - 0.65f) / (2.15f - 0.65f)));
        // Smoothstep interpolation
        float smooth = t * t * (3f - 2f * t);
        return 84.0f - smooth * (84.0f - 26.0f);
    }

    public String getCameraModeLabel() {
        if (cameraZoom <= 0.72f) return "🛰️ TOP-DOWN RTS";
        if (cameraZoom < 1.40f) return "📐 3D ISOMETRIC";
        return "🎮 FULL 3D BATTLE";
    }

    public void setZoom(float newZoom) {
        this.cameraZoom = Math.max(0.35f, Math.min(2.40f, newZoom));
    }

    /**
     * Unprojects a 2D touch point (screenX, screenY) onto the 3D ground plane Y = 0
     * using the inverse View-Projection matrix. Works accurately at any 3D pitch or yaw!
     */
    public float[] screenToWorldXZ(float screenX, float screenY) {
        float nx = (2.0f * screenX) / Math.max(1, viewportW) - 1.0f;
        float ny = 1.0f - (2.0f * screenY) / Math.max(1, viewportH);

        float[] nearPt = {nx, ny, -1.0f, 1.0f};
        float[] farPt  = {nx, ny,  1.0f, 1.0f};
        float[] nearW  = new float[4];
        float[] farW   = new float[4];

        synchronized (this) {
            Matrix.multiplyMV(nearW, 0, currentInvVPMatrix, 0, nearPt, 0);
            Matrix.multiplyMV(farW, 0, currentInvVPMatrix, 0, farPt, 0);
        }

        if (Math.abs(nearW[3]) > 1e-6f) {
            nearW[0] /= nearW[3]; nearW[1] /= nearW[3]; nearW[2] /= nearW[3];
        }
        if (Math.abs(farW[3]) > 1e-6f) {
            farW[0] /= farW[3]; farW[1] /= farW[3]; farW[2] /= farW[3];
        }

        float dy = farW[1] - nearW[1];
        float t = (Math.abs(dy) > 1e-5f) ? (-nearW[1] / dy) : 0f;
        float wx = nearW[0] + t * (farW[0] - nearW[0]);
        float wz = nearW[2] + t * (farW[2] - nearW[2]);
        wx = Math.max(40f, Math.min(worldW - 40f, wx));
        wz = Math.max(40f, Math.min(worldH - 40f, wz));
        return new float[]{wx, wz};
    }

    // --- MAIN SIMULATION TICK ---

    public synchronized void update(float dt) {
        if (inIntroCutscene) {
            introTimeSec += dt;
            if (introTimeSec >= 18.0f) {
                inIntroCutscene = false;
                inTitleScreen = true;
            }
            return;
        }
        if (inTitleScreen || paused || gameOver) return;

        elapsedTime += dt;
        if (cameraShake > 0.05f) {
            cameraShake *= 0.88f;
            if (cameraShake < 0.05f) cameraShake = 0f;
        }

        // Cooldowns
        if (cooldownAirstrike > 0f) cooldownAirstrike = Math.max(0f, cooldownAirstrike - dt);
        if (cooldownIon > 0f) cooldownIon = Math.max(0f, cooldownIon - dt);
        if (cooldownDrop > 0f) cooldownDrop = Math.max(0f, cooldownDrop - dt);

        // Crystal regrow
        for (CrystalNode c : crystals) {
            if (c.amount < c.maxAmount) {
                c.amount = Math.min(c.maxAmount, c.amount + dt * 0.65f);
            }
        }

        // Production Queues
        float powerMult = (powerOut >= powerDrain) ? 1.0f : 0.55f;
        if (structQueueSpec != null && !structReadyToPlace) {
            float costRate = (structQueueSpec.cost / structQueueSpec.buildTime) * dt * powerMult;
            if (credits >= costRate) {
                credits -= costRate;
                structQueueProg += (dt * powerMult) / structQueueSpec.buildTime;
                if (structQueueProg >= 1.0f) {
                    structQueueProg = 1.0f;
                    structReadyToPlace = true;
                    sound.play("build");
                }
            }
        }
        if (infQueueSpec != null) {
            float costRate = (infQueueSpec.cost / infQueueSpec.buildTime) * dt * powerMult;
            if (credits >= costRate) {
                credits -= costRate;
                infQueueProg += (dt * powerMult) / infQueueSpec.buildTime;
                if (infQueueProg >= 1.0f) {
                    completeUnitProduction(infQueueSpec, "barracks");
                    infQueueSpec = null;
                    infQueueProg = 0f;
                }
            }
        }
        if (vehQueueSpec != null) {
            float costRate = (vehQueueSpec.cost / vehQueueSpec.buildTime) * dt * powerMult;
            if (credits >= costRate) {
                credits -= costRate;
                vehQueueProg += (dt * powerMult) / vehQueueSpec.buildTime;
                if (vehQueueProg >= 1.0f) {
                    completeUnitProduction(vehQueueSpec, "factory");
                    vehQueueSpec = null;
                    vehQueueProg = 0f;
                }
            }
        }

        updateBuildings(dt);
        updateUnits(dt);
        updateProjectilesAndParticles(dt);
        updateAiDirector(dt);
        updateFogOfWar();
        checkWinLoss();
    }

    private void completeUnitProduction(Spec s, String producerType) {
        for (Building b : buildings) {
            if (b.hp > 0 && "player".equals(b.team) && producerType.equals(b.type)) {
                Unit u = spawnUnit(s.id, b.x + 65f, b.z + 65f, "player");
                u.moveTargetX = b.rallyX;
                u.moveTargetZ = b.rallyZ;
                sound.play("build");
                return;
            }
        }
    }

    private void updateBuildings(float dt) {
        for (Building b : buildings) {
            if (b.hp <= 0) continue;
            if (b.repairing && b.hp < b.maxHp && credits >= 2f) {
                b.hp = Math.min(b.maxHp, b.hp + 28f * dt);
                credits -= 8f * dt;
                if (b.hp >= b.maxHp) b.repairing = false;
            }
            if (b.spec.range > 0f) {
                if (b.fireCooldown > 0f) b.fireCooldown -= dt;
                if ("player".equals(b.team) && "obelisk".equals(b.type) && powerOut < powerDrain) continue;
                Unit target = findNearestEnemyUnit(b.x, b.z, b.spec.range, b.team);
                if (target != null) {
                    b.turretYawDeg = (float) Math.toDegrees(Math.atan2(target.z - b.z, target.x - b.x));
                    if (b.fireCooldown <= 0f) {
                        b.fireCooldown = b.spec.reload;
                        boolean isObelisk = "obelisk".equals(b.type);
                        fireProjectile(b.x, 26f, b.z, target.x, 8f, target.z, b.spec.damage, b.team, isObelisk);
                        sound.play(isObelisk ? "laser" : "cannon");
                    }
                }
            }
        }
    }

    private void updateUnits(float dt) {
        for (Unit u : units) {
            if (u.hp <= 0) continue;
            if (u.recoil > 0f) u.recoil = Math.max(0f, u.recoil - dt * 16f);
            if (u.fireCooldown > 0f) u.fireCooldown -= dt;

            // Harvester AI
            if ("harvester".equals(u.type)) {
                updateHarvester(u, dt);
                continue;
            }

            // Combat Target Acquisition
            Unit enemyU = findNearestEnemyUnit(u.x, u.z, u.spec.range, u.team);
            Building enemyB = (enemyU == null) ? findNearestEnemyBuilding(u.x, u.z, u.spec.range, u.team) : null;

            float tx = 0f, tz = 0f;
            boolean hasTarget = false;
            if (enemyU != null) {
                tx = enemyU.x; tz = enemyU.z; hasTarget = true;
            } else if (enemyB != null) {
                tx = enemyB.x; tz = enemyB.z; hasTarget = true;
            }

            if (hasTarget) {
                u.turretYawDeg = (float) Math.toDegrees(Math.atan2(tz - u.z, tx - u.x));
                if (u.fireCooldown <= 0f) {
                    float vetMult = (u.kills >= 6) ? 1.35f : (u.kills >= 3) ? 1.15f : 1.0f;
                    u.fireCooldown = u.spec.reload / vetMult;
                    u.recoil = 4.5f;
                    fireProjectile(u.x, 12f, u.z, tx, 8f, tz, u.spec.damage * vetMult, u.team, false);
                    sound.play(u.spec.isInfantry ? "rifle" : "cannon");
                }
                if (u.moveTargetX == null || u.attackMove) {
                    continue;
                }
            }

            // Movement toward moveTarget
            if (u.moveTargetX != null && u.moveTargetZ != null) {
                float dx = u.moveTargetX - u.x;
                float dz = u.moveTargetZ - u.z;
                float dist = (float) Math.hypot(dx, dz);
                if (dist < 10f) {
                    u.moveTargetX = null;
                    u.moveTargetZ = null;
                    u.attackMove = false;
                } else {
                    float step = u.spec.speed * dt;
                    u.x += (dx / dist) * step;
                    u.z += (dz / dist) * step;
                    u.hullYawDeg = (float) Math.toDegrees(Math.atan2(dz, dx));
                    if (!hasTarget) u.turretYawDeg = u.hullYawDeg;
                    u.walkPhase += dt * 10f;
                }
            }
        }

        // Unit separation & cleanup
        for (int i = 0; i < units.size(); i++) {
            Unit a = units.get(i);
            if (a.hp <= 0) continue;
            for (int j = i + 1; j < units.size(); j++) {
                Unit b = units.get(j);
                if (b.hp <= 0) continue;
                float dx = b.x - a.x;
                float dz = b.z - a.z;
                float dist = (float) Math.hypot(dx, dz);
                float minD = a.radius + b.radius;
                if (dist > 0.1f && dist < minD) {
                    float push = (minD - dist) * 0.35f;
                    float nx = dx / dist, nz = dz / dist;
                    a.x -= nx * push; a.z -= nz * push;
                    b.x += nx * push; b.z += nz * push;
                }
            }
        }
        Iterator<Unit> uit = units.iterator();
        while (uit.hasNext()) {
            if (uit.next().hp <= 0) uit.remove();
        }
        Iterator<Building> bit = buildings.iterator();
        while (bit.hasNext()) {
            if (bit.next().hp <= 0) bit.remove();
        }
    }

    private void updateHarvester(Unit u, float dt) {
        if (u.moveTargetX != null && u.moveTargetZ != null) {
            float dx = u.moveTargetX - u.x, dz = u.moveTargetZ - u.z;
            float dist = (float) Math.hypot(dx, dz);
            if (dist < 14f) {
                u.moveTargetX = null; u.moveTargetZ = null;
            } else {
                u.x += (dx / dist) * u.spec.speed * dt;
                u.z += (dz / dist) * u.spec.speed * dt;
                u.hullYawDeg = (float) Math.toDegrees(Math.atan2(dz, dx));
                return;
            }
        }

        if (u.cargo < u.maxCargo) {
            CrystalNode best = null;
            float bestD = 999999f;
            for (CrystalNode c : crystals) {
                if (c.amount >= 5f) {
                    float d = (float) Math.hypot(c.x - u.x, c.z - u.z);
                    if (d < bestD) { bestD = d; best = c; }
                }
            }
            if (best != null) {
                if (bestD > 36f) {
                    float dx = best.x - u.x, dz = best.z - u.z;
                    u.x += (dx / bestD) * u.spec.speed * dt;
                    u.z += (dz / bestD) * u.spec.speed * dt;
                    u.hullYawDeg = (float) Math.toDegrees(Math.atan2(dz, dx));
                } else {
                    float take = Math.min(best.amount, 85f * dt);
                    best.amount -= take;
                    float valMult = best.isBlue ? 2.0f : 1.15f;
                    u.cargo = Math.min(u.maxCargo, u.cargo + take * valMult);
                }
                return;
            }
        }

        // Return to nearest Refinery
        Building ref = null;
        float bestD = 999999f;
        for (Building b : buildings) {
            if (b.hp > 0 && u.team.equals(b.team) && "refinery".equals(b.type)) {
                float d = (float) Math.hypot(b.x - u.x, b.z - u.z);
                if (d < bestD) { bestD = d; ref = b; }
            }
        }
        if (ref != null) {
            if (bestD > 95f) {
                float dx = ref.x - u.x, dz = ref.z - u.z;
                u.x += (dx / bestD) * u.spec.speed * dt;
                u.z += (dz / bestD) * u.spec.speed * dt;
                u.hullYawDeg = (float) Math.toDegrees(Math.atan2(dz, dx));
            } else if (u.cargo > 0f) {
                if ("player".equals(u.team)) {
                    credits += u.cargo;
                    sound.play("harvest");
                } else {
                    aiCredits += u.cargo;
                }
                u.cargo = 0f;
            }
        }
    }

    private Unit findNearestEnemyUnit(float x, float z, float range, String myTeam) {
        Unit best = null;
        float bestD = range;
        for (Unit u : units) {
            if (u.hp > 0 && !myTeam.equals(u.team)) {
                float d = (float) Math.hypot(u.x - x, u.z - z);
                if (d <= bestD) { bestD = d; best = u; }
            }
        }
        return best;
    }

    private Building findNearestEnemyBuilding(float x, float z, float range, String myTeam) {
        Building best = null;
        float bestD = range;
        for (Building b : buildings) {
            if (b.hp > 0 && !myTeam.equals(b.team)) {
                float d = (float) Math.hypot(b.x - x, b.z - z);
                if (d <= bestD) { bestD = d; best = b; }
            }
        }
        return best;
    }

    public void fireProjectile(float sx, float sy, float sz,
                               float tx, float ty, float tz,
                               float dmg, String team, boolean isLaser) {
        Projectile3D p = new Projectile3D();
        p.x = sx; p.y = sy; p.z = sz;
        p.tx = tx; p.ty = ty; p.tz = tz;
        p.speed = isLaser ? 1600f : 520f;
        p.damage = dmg;
        p.splash = isLaser ? 24f : 38f;
        p.team = team;
        p.isLaser = isLaser;
        if (isLaser) {
            p.r = 1.0f; p.g = 0.2f; p.b = 0.25f;
        } else if ("player".equals(team)) {
            p.r = 1.0f; p.g = 0.78f; p.b = 0.22f;
        } else {
            p.r = 1.0f; p.g = 0.35f; p.b = 0.18f;
        }
        projectiles.add(p);
    }

    private void updateProjectilesAndParticles(float dt) {
        Iterator<Projectile3D> pit = projectiles.iterator();
        while (pit.hasNext()) {
            Projectile3D p = pit.next();
            float dx = p.tx - p.x, dy = p.ty - p.y, dz = p.tz - p.z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float step = p.speed * dt;
            if (dist <= step || p.isLaser) {
                detonateAt(p.tx, p.tz, p.damage, p.splash, p.team);
                pit.remove();
            } else {
                p.x += (dx / dist) * step;
                p.y += (dy / dist) * step;
                p.z += (dz / dist) * step;
            }
        }

        Iterator<Particle3D> partIt = particles.iterator();
        while (partIt.hasNext()) {
            Particle3D pt = partIt.next();
            pt.life -= dt;
            if (pt.life <= 0f) {
                partIt.remove();
            } else {
                pt.x += pt.vx * dt;
                pt.y += pt.vy * dt;
                pt.z += pt.vz * dt;
                if (!pt.isRing) pt.vy -= 38f * dt;
                if (pt.y < 1f) pt.y = 1f;
            }
        }
    }

    public void detonateAt(float x, float z, float dmg, float splash, String sourceTeam) {
        if (splash >= 45f) {
            cameraShake = Math.min(20f, cameraShake + (splash >= 100f ? 14f : 5f));
        }
        for (Unit u : units) {
            if (u.hp > 0 && !sourceTeam.equals(u.team)) {
                float d = (float) Math.hypot(u.x - x, u.z - z);
                if (d <= splash) {
                    u.hp -= dmg * (1f - d / (splash * 1.4f));
                    if (u.hp <= 0) sound.play("explode");
                }
            }
        }
        for (Building b : buildings) {
            if (b.hp > 0 && !sourceTeam.equals(b.team)) {
                float d = (float) Math.hypot(b.x - x, b.z - z);
                if (d <= splash + b.w * 0.45f) {
                    b.hp -= dmg;
                    if (b.hp <= 0) {
                        sound.play("explode");
                        recalcPower();
                    }
                }
            }
        }
        // Spawn 3D explosion particles
        Particle3D ring = new Particle3D();
        ring.x = x; ring.y = 3f; ring.z = z;
        ring.size = splash * 1.3f;
        ring.life = 0.35f; ring.maxLife = 0.35f;
        ring.r = 1.0f; ring.g = 0.55f; ring.b = 0.15f;
        ring.isRing = true;
        particles.add(ring);

        int count = (splash >= 90f) ? 20 : 8;
        for (int i = 0; i < count && particles.size() < 260; i++) {
            Particle3D pt = new Particle3D();
            pt.x = x; pt.y = 6f; pt.z = z;
            double a = rng.nextDouble() * Math.PI * 2.0;
            float spd = 25f + rng.nextFloat() * 95f;
            pt.vx = (float) Math.cos(a) * spd;
            pt.vy = 30f + rng.nextFloat() * 75f;
            pt.vz = (float) Math.sin(a) * spd;
            pt.size = 4f + rng.nextFloat() * 6f;
            pt.life = 0.45f + rng.nextFloat() * 0.45f;
            pt.maxLife = pt.life;
            pt.r = 1.0f; pt.g = 0.35f + rng.nextFloat() * 0.5f; pt.b = 0.1f;
            particles.add(pt);
        }
    }

    public void executeSupportPower(Spec s, float wx, float wz) {
        if ("airstrike".equals(s.id) && cooldownAirstrike <= 0f && credits >= s.cost) {
            credits -= s.cost;
            cooldownAirstrike = s.buildTime;
            detonateAt(wx - 45f, wz - 25f, 320f, 110f, "player");
            detonateAt(wx, wz, 360f, 120f, "player");
            detonateAt(wx + 45f, wz + 25f, 320f, 110f, "player");
            sound.play("explode");
        } else if ("ioncannon".equals(s.id) && cooldownIon <= 0f && credits >= s.cost) {
            credits -= s.cost;
            cooldownIon = s.buildTime;
            fireProjectile(wx, 480f, wz, wx, 0f, wz, 680f, "player", true);
            detonateAt(wx, wz, 680f, 175f, "player");
            sound.play("ion");
        } else if ("droppod".equals(s.id) && cooldownDrop <= 0f && credits >= s.cost) {
            credits -= s.cost;
            cooldownDrop = s.buildTime;
            spawnUnit("rocket", wx - 28f, wz, "player").kills = 3;
            spawnUnit("rocket", wx + 28f, wz, "player").kills = 3;
            spawnUnit("rifle", wx, wz + 28f, "player").kills = 3;
            sound.play("build");
        }
        orderMode = "normal";
        activeSupportSpec = null;
    }

    private void updateAiDirector(float dt) {
        aiTimer += dt;
        aiWaveTimer -= dt;
        if (aiTimer >= 7.5f) {
            aiTimer = 0f;
            aiCredits += ("brutal".equals(difficulty) ? 260f : "commander".equals(difficulty) ? 160f : 95f);
            if (aiCredits >= 650f) {
                aiCredits -= 650f;
                Building fac = null;
                for (Building b : buildings) {
                    if (b.hp > 0 && "enemy".equals(b.team) && "factory".equals(b.type)) {
                        fac = b; break;
                    }
                }
                if (fac != null) {
                    String uType = (rng.nextFloat() < 0.28f) ? "mammoth" : (rng.nextFloat() < 0.65f) ? "tank" : "buggy";
                    spawnUnit(uType, fac.x - 80f, fac.z + 80f, "enemy");
                }
            }
        }
        if (aiWaveTimer <= 0f) {
            aiWaveTimer = "brutal".equals(difficulty) ? 45f : "commander".equals(difficulty) ? 65f : 85f;
            Building playerTarget = null;
            for (Building b : buildings) {
                if (b.hp > 0 && "player".equals(b.team)) { playerTarget = b; break; }
            }
            if (playerTarget != null) {
                for (Unit u : units) {
                    if (u.hp > 0 && "enemy".equals(u.team) && !"harvester".equals(u.type)) {
                        u.moveTargetX = playerTarget.x;
                        u.moveTargetZ = playerTarget.z;
                        u.attackMove = true;
                    }
                }
            }
        }
    }

    public void updateFogOfWar() {
        for (int i = 0; i < fogGrid.length; i++) {
            if (fogGrid[i] == 2) fogGrid[i] = 1;
        }
        for (Building b : buildings) {
            if (b.hp > 0 && "player".equals(b.team)) revealCircle(b.x, b.z, 380f);
        }
        for (Unit u : units) {
            if (u.hp > 0 && "player".equals(u.team)) revealCircle(u.x, u.z, 290f);
        }
    }

    private void revealCircle(float wx, float wz, float radius) {
        int gxC = (int) (wx / tileSize);
        int gzC = (int) (wz / tileSize);
        int rTiles = (int) Math.ceil(radius / tileSize);
        for (int gz = Math.max(0, gzC - rTiles); gz <= Math.min(gridH - 1, gzC + rTiles); gz++) {
            for (int gx = Math.max(0, gxC - rTiles); gx <= Math.min(gridW - 1, gxC + rTiles); gx++) {
                if ((gx - gxC) * (gx - gxC) + (gz - gzC) * (gz - gzC) <= rTiles * rTiles) {
                    fogGrid[gz * gridW + gx] = 2;
                }
            }
        }
    }

    public boolean isVisible(float wx, float wz) {
        int gx = Math.max(0, Math.min(gridW - 1, (int) (wx / tileSize)));
        int gz = Math.max(0, Math.min(gridH - 1, (int) (wz / tileSize)));
        return fogGrid[gz * gridW + gx] == 2;
    }

    private void checkWinLoss() {
        boolean hasPlayer = false, hasEnemy = false;
        for (Building b : buildings) {
            if (b.hp > 0) {
                if ("player".equals(b.team)) hasPlayer = true;
                else hasEnemy = true;
            }
        }
        for (Unit u : units) {
            if (u.hp > 0) {
                if ("player".equals(u.team)) hasPlayer = true;
                else hasEnemy = true;
            }
        }
        if (!hasEnemy) { gameOver = true; playerWon = true; }
        else if (!hasPlayer) { gameOver = true; playerWon = false; }
    }
}
