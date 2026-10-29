package com.crystalfront.mobilecommand;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Native Android Hardware-Accelerated DPI-Scaled Tactical HUD & Multi-Touch 3D Controller.
 * Renders over the OpenGL ES 3.0 GLSurfaceView at 120Hz and translates screen touches
 * into 3D world raycasts, pinch-to-3D-zoom transitions, and production commands.
 */
public class HudOverlayView extends View {
    private final GameState state;
    private final SoundEngine sound;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;

    private float dp = 1f;
    private float downX, downY;
    private float lastPanX, lastPanY;
    private boolean isDraggingMap = false;

    private static class HitButton {
        final String id;
        final RectF rect;
        HitButton(String id, float l, float t, float r, float b) {
            this.id = id;
            this.rect = new RectF(l, t, r, b);
        }
    }

    private final List<HitButton> buttons = new ArrayList<>();
    private final RectF minimapRect = new RectF();

    public HudOverlayView(Context context, final GameState state, final SoundEngine sound) {
        super(context);
        this.state = state;
        this.sound = sound;
        this.dp = context.getResources().getDisplayMetrics().density;
        this.textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        this.scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                synchronized (state) {
                    state.setZoom(state.cameraZoom * detector.getScaleFactor());
                }
                return true;
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        buttons.clear();
        int w = getWidth();
        int h = getHeight();

        synchronized (state) {
            if (state.inIntroCutscene) {
                drawIntroCutsceneHud(canvas, w, h);
                postInvalidateOnAnimation();
                return;
            }

            drawTopCommandBar(canvas, w);
            drawMinimapRadar(canvas, w, h);
            drawBottomProductionDeck(canvas, w, h);

            if (state.inTitleScreen) {
                drawTitleScreenModal(canvas, w, h);
            } else if (state.gameOver) {
                drawGameOverModal(canvas, w, h);
            }
        }

        postInvalidateOnAnimation();
    }

    private void drawIntroCutsceneHud(Canvas canvas, int w, int h) {
        int barH = (int) (54 * dp);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(235, 4, 7, 12));
        canvas.drawRect(0, 0, w, barH, paint);
        canvas.drawRect(0, h - barH, w, h, paint);

        textPaint.setColor(Color.rgb(248, 113, 113));
        textPaint.setTextSize(12 * dp);
        canvas.drawText("● REC // ORBITAL 3D TELEMETRY DOWNLINK — ARES-PRIME", 16 * dp, 32 * dp, textPaint);

        String sub;
        if (state.introTimeSec < 4.5f) {
            sub = "ACT I: ORBITAL SATELLITE OMEGA-7 SCANNING XENOCITE RADIATION ANOMALY...";
        } else if (state.introTimeSec < 9.0f) {
            sub = "ACT II: AEGIS ASSAULT DROP-PODS ENTERING ATMOSPHERE AT MACH 14...";
        } else if (state.introTimeSec < 13.5f) {
            sub = "ACT III: 3D SURFACE CONTACT — COLOSSUS ARMOR ENGAGING VIPER OBELISKS!";
        } else {
            sub = "ACT IV: ORBITAL ION CANNON DETONATION // CRYSTAL FRONT: 3D COMMAND READY.";
        }
        textPaint.setColor(Color.rgb(253, 224, 71));
        textPaint.setTextSize(13 * dp);
        canvas.drawText(sub, 16 * dp, h - 20 * dp, textPaint);

        drawBtn(canvas, "skip_intro", "⚡ SKIP 3D INTRO", w - 155 * dp, 10 * dp, w - 14 * dp, 44 * dp,
                Color.argb(220, 30, 41, 59), Color.rgb(251, 191, 36));
    }

    private void drawTopCommandBar(Canvas canvas, int w) {
        float barH = 44 * dp;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(225, 11, 16, 24));
        canvas.drawRect(0, 0, w, barH, paint);
        paint.setColor(Color.rgb(38, 53, 71));
        canvas.drawRect(0, barH - 2 * dp, w, barH, paint);

        // Credits & Power
        textPaint.setTextSize(13 * dp);
        textPaint.setColor(Color.rgb(251, 191, 36));
        canvas.drawText("$" + (int) state.credits, 12 * dp, 27 * dp, textPaint);

        boolean lowPower = state.powerOut < state.powerDrain;
        textPaint.setColor(lowPower ? Color.rgb(248, 113, 113) : Color.rgb(52, 211, 153));
        canvas.drawText("⚡" + state.powerOut + "/" + state.powerDrain, 92 * dp, 27 * dp, textPaint);

        // 3D Camera Perspective Mode Badge + Zoom & Yaw Controls
        float cx = 185 * dp;
        int modeBg = (state.cameraZoom <= 0.72f) ? Color.argb(200, 16, 95, 68)
                : (state.cameraZoom >= 1.40f) ? Color.argb(215, 146, 64, 14)
                : Color.argb(200, 14, 78, 118);
        drawBtn(canvas, "cam_reset", state.getCameraModeLabel() + " " + Math.round(state.cameraZoom * 100) + "%",
                cx, 6 * dp, cx + 165 * dp, 38 * dp, modeBg, Color.WHITE);

        cx += 170 * dp;
        drawBtn(canvas, "zoom_out", "🔍−", cx, 6 * dp, cx + 42 * dp, 38 * dp,
                Color.rgb(22, 32, 46), Color.rgb(125, 211, 252));
        cx += 46 * dp;
        drawBtn(canvas, "zoom_in", "🔍+", cx, 6 * dp, cx + 42 * dp, 38 * dp,
                Color.rgb(22, 32, 46), Color.rgb(125, 211, 252));
        cx += 46 * dp;
        drawBtn(canvas, "yaw_left", "⟲3D", cx, 6 * dp, cx + 44 * dp, 38 * dp,
                Color.rgb(22, 32, 46), Color.rgb(253, 224, 71));
        cx += 48 * dp;
        drawBtn(canvas, "yaw_right", "⟳3D", cx, 6 * dp, cx + 44 * dp, 38 * dp,
                Color.rgb(22, 32, 46), Color.rgb(253, 224, 71));

        // Right-side Mode & Menu Buttons
        float rx = w - 10 * dp;
        drawBtn(canvas, "open_title", "⚙ MENU / 3D", rx - 98 * dp, 6 * dp, rx, 38 * dp,
                Color.rgb(30, 41, 59), Color.rgb(251, 191, 36));
        rx -= 104 * dp;
        drawBtn(canvas, "mode_sell", "💲 SELL", rx - 66 * dp, 6 * dp, rx, 38 * dp,
                "sell".equals(state.orderMode) ? Color.rgb(153, 27, 27) : Color.rgb(22, 32, 46),
                Color.rgb(252, 165, 165));
        rx -= 72 * dp;
        drawBtn(canvas, "mode_repair", "🔧 FIX", rx - 66 * dp, 6 * dp, rx, 38 * dp,
                "repair".equals(state.orderMode) ? Color.rgb(3, 105, 161) : Color.rgb(22, 32, 46),
                Color.rgb(125, 211, 252));
    }

    private void drawMinimapRadar(Canvas canvas, int w, int h) {
        float mapSz = Math.min(w, h) * 0.24f;
        mapSz = Math.max(96 * dp, Math.min(145 * dp, mapSz));
        float left = 10 * dp;
        float top = 52 * dp;
        minimapRect.set(left, top, left + mapSz, top + mapSz);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(215, 8, 13, 20));
        canvas.drawRoundRect(minimapRect, 8 * dp, 8 * dp, paint);

        float sx = mapSz / state.worldW;
        float sz = mapSz / state.worldH;

        // Crystals
        for (GameState.CrystalNode c : state.crystals) {
            if (c.amount <= 2f) continue;
            paint.setColor(c.isBlue ? Color.rgb(56, 189, 248) : Color.rgb(34, 197, 94));
            canvas.drawRect(left + c.x * sx - 1.5f, top + c.z * sz - 1.5f,
                    left + c.x * sx + 1.5f, top + c.z * sz + 1.5f, paint);
        }

        // Buildings
        for (GameState.Building b : state.buildings) {
            if (b.hp <= 0) continue;
            if ("enemy".equals(b.team) && !state.isVisible(b.x, b.z)) continue;
            paint.setColor("player".equals(b.team) ? Color.rgb(251, 191, 36) : Color.rgb(239, 68, 68));
            canvas.drawRect(left + b.x * sx - 3 * dp, top + b.z * sz - 3 * dp,
                    left + b.x * sx + 3 * dp, top + b.z * sz + 3 * dp, paint);
        }

        // Units
        for (GameState.Unit u : state.units) {
            if (u.hp <= 0) continue;
            if ("enemy".equals(u.team) && !state.isVisible(u.x, u.z)) continue;
            paint.setColor("player".equals(u.team) ? Color.rgb(74, 222, 128) : Color.rgb(248, 113, 113));
            canvas.drawCircle(left + u.x * sx, top + u.z * sz, 2.2f * dp, paint);
        }

        // Camera Focal Reticle
        float camRx = left + state.cameraX * sx;
        float camRz = top + state.cameraZ * sz;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * dp);
        paint.setColor(Color.WHITE);
        float boxR = Math.max(8 * dp, (18 * dp) / state.cameraZoom);
        canvas.drawRect(camRx - boxR, camRz - boxR, camRx + boxR, camRz + boxR, paint);

        paint.setColor(Color.rgb(56, 189, 248));
        canvas.drawRoundRect(minimapRect, 8 * dp, 8 * dp, paint);
    }

    private void drawBottomProductionDeck(Canvas canvas, int w, int h) {
        float deckH = 92 * dp;
        float top = h - deckH;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(230, 10, 15, 23));
        canvas.drawRect(0, top, w, h, paint);
        paint.setColor(Color.rgb(38, 53, 71));
        canvas.drawRect(0, top, w, top + 2 * dp, paint);

        // Left Category Tabs + Tactical Stances
        String[] tabs = {"structures", "infantry", "vehicles", "support"};
        String[] labels = {"🏗️ BASE", "🪖 INF", "🚜 ARMOR", "☢️ SUPER"};
        float tabW = 74 * dp;
        for (int i = 0; i < tabs.length; i++) {
            float tx = 8 * dp + (i % 2) * (tabW + 4 * dp);
            float ty = top + 6 * dp + (i / 2) * (38 * dp + 4 * dp);
            boolean active = tabs[i].equals(state.activeTab);
            drawBtn(canvas, "tab_" + tabs[i], labels[i], tx, ty, tx + tabW, ty + 38 * dp,
                    active ? Color.rgb(217, 119, 6) : Color.rgb(22, 32, 46),
                    active ? Color.BLACK : Color.rgb(226, 232, 240));
        }

        // Production Cards for Active Category
        float startX = 166 * dp;
        float cardW = Math.min(118 * dp, (w - startX - 150 * dp) / 4.2f);
        float cx = startX;
        for (GameState.Spec s : state.catalog) {
            if (!s.category.equals(state.activeTab)) continue;
            if (cx + cardW > w - 135 * dp) break;

            int bg = Color.rgb(20, 29, 42);
            String sub = "$" + s.cost;
            if (s == state.structQueueSpec) {
                sub = state.structReadyToPlace ? "PLACE NOW!" : Math.round(state.structQueueProg * 100) + "%";
                bg = state.structReadyToPlace ? Color.rgb(21, 128, 61) : Color.rgb(146, 64, 14);
            } else if (s == state.infQueueSpec) {
                sub = Math.round(state.infQueueProg * 100) + "%";
                bg = Color.rgb(146, 64, 14);
            } else if (s == state.vehQueueSpec) {
                sub = Math.round(state.vehQueueProg * 100) + "%";
                bg = Color.rgb(146, 64, 14);
            }

            RectF r = new RectF(cx, top + 8 * dp, cx + cardW, h - 8 * dp);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(bg);
            canvas.drawRoundRect(r, 8 * dp, 8 * dp, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * dp);
            paint.setColor(Color.rgb(71, 85, 105));
            canvas.drawRoundRect(r, 8 * dp, 8 * dp, paint);

            textPaint.setTextSize(10.5f * dp);
            textPaint.setColor(Color.WHITE);
            String title = state.getDisplayName(s);
            if (title.length() > 13) title = title.substring(0, 13);
            canvas.drawText(title, cx + 6 * dp, top + 34 * dp, textPaint);

            textPaint.setTextSize(11 * dp);
            textPaint.setColor(Color.rgb(253, 224, 71));
            canvas.drawText(sub, cx + 6 * dp, top + 64 * dp, textPaint);

            buttons.add(new HitButton("prod_" + s.id, r.left, r.top, r.right, r.bottom));
            cx += cardW + 6 * dp;
        }

        // Right-side Quick Unit Commands
        float cmdX = w - 132 * dp;
        drawBtn(canvas, "cmd_select_all", "🎯 ALL COMBAT", cmdX, top + 6 * dp, w - 8 * dp, top + 42 * dp,
                Color.rgb(21, 94, 117), Color.WHITE);
        drawBtn(canvas, "cmd_atk_move", "⚔️ ATK-MOVE", cmdX, top + 48 * dp, w - 8 * dp, h - 6 * dp,
                "attackMove".equals(state.orderMode) ? Color.rgb(185, 28, 28) : Color.rgb(30, 41, 59),
                Color.rgb(252, 165, 165));
    }

    private void drawTitleScreenModal(Canvas canvas, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(210, 4, 8, 15));
        canvas.drawRect(0, 0, w, h, paint);

        float mw = Math.min(w - 28 * dp, 560 * dp);
        float mh = Math.min(h - 24 * dp, 370 * dp);
        float left = (w - mw) * 0.5f;
        float top = (h - mh) * 0.5f;

        RectF card = new RectF(left, top, left + mw, top + mh);
        paint.setColor(Color.rgb(15, 23, 36));
        canvas.drawRoundRect(card, 14 * dp, 14 * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(Color.rgb(245, 158, 11));
        canvas.drawRoundRect(card, 14 * dp, 14 * dp, paint);

        textPaint.setTextSize(17 * dp);
        textPaint.setColor(Color.rgb(253, 224, 71));
        canvas.drawText("CRYSTAL FRONT: 3D NATIVE COMMAND", left + 18 * dp, top + 32 * dp, textPaint);

        textPaint.setTextSize(10.5f * dp);
        textPaint.setColor(Color.rgb(148, 163, 184));
        canvas.drawText("100% Native Android OpenGL ES 3.0 3D Engine • Zoom-In 3D <-> Zoom-Out RTS", left + 18 * dp, top + 50 * dp, textPaint);

        // Faction Row
        float y = top + 64 * dp;
        float halfW = (mw - 44 * dp) * 0.5f;
        drawBtn(canvas, "fac_GDI", "🦅 AEGIS DEFENSE FORCE", left + 18 * dp, y, left + 18 * dp + halfW, y + 38 * dp,
                "GDI".equals(state.faction) ? Color.rgb(180, 83, 9) : Color.rgb(30, 41, 59), Color.WHITE);
        drawBtn(canvas, "fac_NOD", "🦂 VIPER SYNDICATE", left + 26 * dp + halfW, y, left + mw - 18 * dp, y + 38 * dp,
                "NOD".equals(state.faction) ? Color.rgb(153, 27, 27) : Color.rgb(30, 41, 59), Color.WHITE);

        // Map Size Row
        y += 48 * dp;
        float thirdW = (mw - 52 * dp) / 3f;
        drawBtn(canvas, "map_standard", "MAP: STANDARD (2400)", left + 18 * dp, y, left + 18 * dp + thirdW, y + 36 * dp,
                "standard".equals(state.mapSize) ? Color.rgb(14, 116, 144) : Color.rgb(22, 32, 46), Color.WHITE);
        drawBtn(canvas, "map_large", "MAP: LARGE (3840)", left + 26 * dp + thirdW, y, left + 26 * dp + thirdW * 2, y + 36 * dp,
                "large".equals(state.mapSize) ? Color.rgb(14, 116, 144) : Color.rgb(22, 32, 46), Color.WHITE);
        drawBtn(canvas, "map_epic", "MAP: EPIC (4800)", left + 34 * dp + thirdW * 2, y, left + mw - 18 * dp, y + 36 * dp,
                "epic".equals(state.mapSize) ? Color.rgb(14, 116, 144) : Color.rgb(22, 32, 46), Color.WHITE);

        // Biome Row
        y += 46 * dp;
        drawBtn(canvas, "biome_redrock", "🏜️ REDROCK CANYON", left + 18 * dp, y, left + 18 * dp + thirdW, y + 36 * dp,
                "redrock".equals(state.biome) ? Color.rgb(146, 64, 14) : Color.rgb(22, 32, 46), Color.WHITE);
        drawBtn(canvas, "biome_wasteland", "☣️ XENO WASTELAND", left + 26 * dp + thirdW, y, left + 26 * dp + thirdW * 2, y + 36 * dp,
                "wasteland".equals(state.biome) ? Color.rgb(21, 128, 61) : Color.rgb(22, 32, 46), Color.WHITE);
        drawBtn(canvas, "biome_volcanic", "🌋 VOLCANIC CALDERA", left + 34 * dp + thirdW * 2, y, left + mw - 18 * dp, y + 36 * dp,
                "volcanic".equals(state.biome) ? Color.rgb(153, 27, 27) : Color.rgb(22, 32, 46), Color.WHITE);

        // Audio & Difficulty Row
        y += 46 * dp;
        drawBtn(canvas, "toggle_sfx", sound.isSfxEnabled() ? "🔊 SFX: ON" : "🔇 SFX: OFF",
                left + 18 * dp, y, left + 18 * dp + thirdW, y + 36 * dp, Color.rgb(30, 41, 59), Color.rgb(125, 211, 252));
        drawBtn(canvas, "toggle_music", sound.isMusicEnabled() ? "🎵 MUSIC: ON" : "🎵 MUSIC: OFF",
                left + 26 * dp + thirdW, y, left + 26 * dp + thirdW * 2, y + 36 * dp, Color.rgb(30, 41, 59), Color.rgb(125, 211, 252));
        drawBtn(canvas, "cycle_diff", "AI: " + state.difficulty.toUpperCase(),
                left + 34 * dp + thirdW * 2, y, left + mw - 18 * dp, y + 36 * dp, Color.rgb(30, 41, 59), Color.rgb(253, 224, 71));

        // Primary Launch & Intro Movie Buttons
        y += 50 * dp;
        drawBtn(canvas, "start_match", "▶ DEPLOY TO 3D BATTLEFIELD", left + 18 * dp, y, left + 18 * dp + halfW, y + 46 * dp,
                Color.rgb(16, 185, 129), Color.BLACK);
        drawBtn(canvas, "watch_intro", "🎬 WATCH 3D INTRO MOVIE", left + 26 * dp + halfW, y, left + mw - 18 * dp, y + 46 * dp,
                Color.rgb(2, 132, 199), Color.WHITE);
    }

    private void drawGameOverModal(Canvas canvas, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(200, 0, 0, 0));
        canvas.drawRect(0, 0, w, h, paint);

        float mw = 360 * dp, mh = 180 * dp;
        float left = (w - mw) * 0.5f, top = (h - mh) * 0.5f;
        paint.setColor(Color.rgb(15, 23, 36));
        canvas.drawRoundRect(new RectF(left, top, left + mw, top + mh), 14 * dp, 14 * dp, paint);

        textPaint.setTextSize(20 * dp);
        textPaint.setColor(state.playerWon ? Color.rgb(74, 222, 128) : Color.rgb(248, 113, 113));
        canvas.drawText(state.playerWon ? "🏆 VICTORY ACHIEVED!" : "💀 COMMAND BASE LOST", left + 28 * dp, top + 55 * dp, textPaint);

        drawBtn(canvas, "start_match", "🚀 LAUNCH NEW 3D SKIRMISH", left + 28 * dp, top + 100 * dp, left + mw - 28 * dp, top + 150 * dp,
                Color.rgb(217, 119, 6), Color.BLACK);
    }

    private void drawBtn(Canvas canvas, String id, String label, float l, float t, float r, float b, int bgColor, int textColor) {
        RectF rect = new RectF(l, t, r, b);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bgColor);
        canvas.drawRoundRect(rect, 6 * dp, 6 * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * dp);
        paint.setColor(Color.argb(140, 148, 163, 184));
        canvas.drawRoundRect(rect, 6 * dp, 6 * dp, paint);

        textPaint.setColor(textColor);
        textPaint.setTextSize(10.5f * dp);
        float tw = textPaint.measureText(label);
        canvas.drawText(label, l + Math.max(4 * dp, (r - l - tw) * 0.5f), t + (b - t) * 0.64f, textPaint);
        buttons.add(new HitButton(id, l, t, r, b));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            downX = x; downY = y;
            lastPanX = x; lastPanY = y;
            isDraggingMap = false;
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (event.getPointerCount() == 1 && !state.inTitleScreen && !state.inIntroCutscene) {
                if (minimapRect.contains(x, y)) {
                    jumpCameraToMinimap(x, y);
                    return true;
                }
                float dx = x - lastPanX;
                float dy = y - lastPanY;
                if (Math.hypot(x - downX, y - downY) > 12 * dp) {
                    isDraggingMap = true;
                }
                if (isDraggingMap) {
                    synchronized (state) {
                        float panScale = 1.65f / Math.max(0.35f, state.cameraZoom);
                        double yawRad = Math.toRadians(state.cameraYawDeg);
                        float cos = (float) Math.cos(yawRad);
                        float sin = (float) Math.sin(yawRad);
                        state.cameraX -= (dx * cos + dy * sin) * panScale;
                        state.cameraZ -= (-dx * sin + dy * cos) * panScale;
                        state.cameraX = Math.max(80f, Math.min(state.worldW - 80f, state.cameraX));
                        state.cameraZ = Math.max(80f, Math.min(state.worldH - 80f, state.cameraZ));
                    }
                }
                lastPanX = x;
                lastPanY = y;
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP) {
            if (!isDraggingMap && event.getPointerCount() <= 1) {
                handleTap(x, y);
            }
            isDraggingMap = false;
            return true;
        }
        return true;
    }

    private void jumpCameraToMinimap(float x, float y) {
        synchronized (state) {
            float nx = (x - minimapRect.left) / Math.max(1f, minimapRect.width());
            float nz = (y - minimapRect.top) / Math.max(1f, minimapRect.height());
            state.cameraX = Math.max(80f, Math.min(state.worldW - 80f, nx * state.worldW));
            state.cameraZ = Math.max(80f, Math.min(state.worldH - 80f, nz * state.worldH));
        }
    }

    private void handleTap(float x, float y) {
        synchronized (state) {
            if (state.inIntroCutscene) {
                state.inIntroCutscene = false;
                state.inTitleScreen = true;
                sound.play("select");
                return;
            }

            for (int i = buttons.size() - 1; i >= 0; i--) {
                HitButton b = buttons.get(i);
                if (b.rect.contains(x, y)) {
                    handleButtonCommand(b.id);
                    return;
                }
            }

            if (state.inTitleScreen || state.gameOver) return;

            if (minimapRect.contains(x, y)) {
                jumpCameraToMinimap(x, y);
                return;
            }

            // Unproject 2D screen tap onto 3D ground coordinate (wx, wz)
            float[] xz = state.screenToWorldXZ(x, y);
            float wx = xz[0], wz = xz[1];

            if ("place".equals(state.orderMode) && state.placingSpec != null) {
                state.spawnBuilding(state.placingSpec.id, wx, wz, "player");
                state.structQueueSpec = null;
                state.structQueueProg = 0f;
                state.structReadyToPlace = false;
                state.placingSpec = null;
                state.orderMode = "normal";
                sound.play("build");
                return;
            }

            if ("support".equals(state.orderMode) && state.activeSupportSpec != null) {
                state.executeSupportPower(state.activeSupportSpec, wx, wz);
                return;
            }

            // Check if player tapped a Building in 3D space
            for (GameState.Building b : state.buildings) {
                if (b.hp <= 0) continue;
                if (Math.abs(wx - b.x) <= b.w * 0.6f && Math.abs(wz - b.z) <= b.d * 0.6f) {
                    if ("player".equals(b.team)) {
                        if ("sell".equals(state.orderMode)) {
                            state.credits += b.spec.cost * 0.5f;
                            b.hp = 0f;
                            state.recalcPower();
                            state.orderMode = "normal";
                            sound.play("harvest");
                            return;
                        } else if ("repair".equals(state.orderMode)) {
                            b.repairing = !b.repairing;
                            state.orderMode = "normal";
                            sound.play("select");
                            return;
                        }
                        for (GameState.Building ob : state.buildings) ob.selected = false;
                        b.selected = true;
                        sound.play("select");
                        return;
                    }
                }
            }

            // Check if player tapped a friendly Unit to select it
            for (GameState.Unit u : state.units) {
                if (u.hp > 0 && "player".equals(u.team)) {
                    if (Math.hypot(wx - u.x, wz - u.z) <= u.radius + 22f) {
                        u.selected = !u.selected;
                        sound.play("select");
                        return;
                    }
                }
            }

            // Otherwise command all selected units to move/attack toward 3D coordinate (wx, wz)
            boolean anySelected = false;
            for (GameState.Unit u : state.units) {
                if (u.hp > 0 && "player".equals(u.team) && u.selected) {
                    u.moveTargetX = wx;
                    u.moveTargetZ = wz;
                    u.attackMove = "attackMove".equals(state.orderMode);
                    anySelected = true;
                }
            }
            if (anySelected) {
                state.orderMode = "normal";
                sound.play("move");
            }
        }
    }

    private void handleButtonCommand(String id) {
        sound.play("select");
        if ("skip_intro".equals(id)) {
            state.inIntroCutscene = false;
            state.inTitleScreen = true;
        } else if ("watch_intro".equals(id)) {
            state.introTimeSec = 0f;
            state.inIntroCutscene = true;
            state.inTitleScreen = false;
            sound.play("intro_boom");
        } else if ("start_match".equals(id)) {
            state.inTitleScreen = false;
            state.inIntroCutscene = false;
            if (state.gameOver) state.initSkirmish();
        } else if ("open_title".equals(id)) {
            state.inTitleScreen = !state.inTitleScreen;
        } else if ("zoom_in".equals(id)) {
            state.setZoom(state.cameraZoom * 1.25f);
        } else if ("zoom_out".equals(id)) {
            state.setZoom(state.cameraZoom / 1.25f);
        } else if ("cam_reset".equals(id)) {
            // Toggle between 3D Ground Perspective (1.75x) and Top-Down RTS (0.55x)
            state.setZoom(state.cameraZoom < 1.2f ? 1.75f : 0.55f);
        } else if ("yaw_left".equals(id)) {
            state.cameraYawDeg = (state.cameraYawDeg - 30f) % 360f;
        } else if ("yaw_right".equals(id)) {
            state.cameraYawDeg = (state.cameraYawDeg + 30f) % 360f;
        } else if ("mode_repair".equals(id)) {
            state.orderMode = "repair".equals(state.orderMode) ? "normal" : "repair";
        } else if ("mode_sell".equals(id)) {
            state.orderMode = "sell".equals(state.orderMode) ? "normal" : "sell";
        } else if ("cmd_atk_move".equals(id)) {
            state.orderMode = "attackMove".equals(state.orderMode) ? "normal" : "attackMove";
        } else if ("cmd_select_all".equals(id)) {
            for (GameState.Unit u : state.units) {
                if (u.hp > 0 && "player".equals(u.team) && !"harvester".equals(u.type)) {
                    u.selected = true;
                }
            }
        } else if ("fac_GDI".equals(id)) {
            state.faction = "GDI";
        } else if ("fac_NOD".equals(id)) {
            state.faction = "NOD";
        } else if (id.startsWith("map_")) {
            state.setMapSize(id.substring(4));
            state.initSkirmish();
        } else if (id.startsWith("biome_")) {
            state.biome = id.substring(6);
            state.terrainDirty = true;
        } else if ("toggle_sfx".equals(id)) {
            sound.setSfxEnabled(!sound.isSfxEnabled());
        } else if ("toggle_music".equals(id)) {
            sound.setMusicEnabled(!sound.isMusicEnabled());
        } else if ("cycle_diff".equals(id)) {
            state.difficulty = "casual".equals(state.difficulty) ? "commander"
                    : "commander".equals(state.difficulty) ? "brutal" : "casual";
        } else if (id.startsWith("tab_")) {
            state.activeTab = id.substring(4);
        } else if (id.startsWith("prod_")) {
            String specId = id.substring(5);
            GameState.Spec s = state.findSpec(specId);
            if ("structures".equals(s.category)) {
                if (state.structReadyToPlace && state.structQueueSpec == s) {
                    state.orderMode = "place";
                    state.placingSpec = s;
                } else if (state.structQueueSpec == null) {
                    state.structQueueSpec = s;
                    state.structQueueProg = 0f;
                    state.structReadyToPlace = false;
                }
            } else if ("infantry".equals(s.category) && state.infQueueSpec == null) {
                state.infQueueSpec = s;
                state.infQueueProg = 0f;
            } else if ("vehicles".equals(s.category) && state.vehQueueSpec == null) {
                state.vehQueueSpec = s;
                state.vehQueueProg = 0f;
            } else if ("support".equals(s.category)) {
                state.orderMode = "support";
                state.activeSupportSpec = s;
            }
        }
    }
}
