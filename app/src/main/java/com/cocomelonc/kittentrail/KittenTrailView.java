/*
 * Kitten Trail
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittentrail;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dependency-free renderer and input layer. The 1280x720 logical canvas scales
 * uniformly to phones, tablets, foldables, Chromebooks, and resizable windows.
 */
final class KittenTrailView extends View implements GameWorld.Listener {
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float BOARD_LEFT = 164f;
    private static final float BOARD_TOP = 102f;
    private static final float TILE = 68f;
    private static final float PAUSE_X = 1218f;
    private static final float PAUSE_Y = 53f;
    private static final float OVERLAY_LEFT = 310f;
    private static final float OVERLAY_TOP = 168f;
    private static final float OVERLAY_RIGHT = 970f;
    private static final float OVERLAY_BOTTOM = 552f;
    private static final float OVERLAY_PADDING = 72f;
    private static final String PREFS = "kitten_trail_progress";
    private static final String PREF_RESUME_LEVEL = "resume_level";
    private static final String PREF_LANGUAGE = "language";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final SharedPreferences preferences;
    private final AudioEngine audio = new AudioEngine();
    private final MusicEngine music;
    private final GameWorld world;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random(0xC0C04D0L);

    private Context localizedContext;
    private String language;
    private LinearGradient outsideGradient;
    private LinearGradient levelGradient;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private long lastFrameNanos;
    private boolean hostResumed = true;
    private float hintTime;
    private int targetRow = -1;
    private int targetCol = -1;
    private GameWorld.State lastVisualState = GameWorld.State.TITLE;
    private float overlayProgress = 1f;

    KittenTrailView(Context context) {
        super(context);
        music = new MusicEngine(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setKeepScreenOn(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        language = preferences.getString(PREF_LANGUAGE, "en");
        if (!"ru".equals(language)) {
            language = "en";
        }
        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        setContentDescription(text(R.string.accessibility_game));

        world = new GameWorld(LevelData.createAll(), this);
        outsideGradient = new LinearGradient(
                0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{0xFFF8EAE5, 0xFFE8E0E2, 0xFFDDD8E8},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP
        );
        rebuildLevelGradient();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewScale = Math.min(width / WORLD_WIDTH, height / WORLD_HEIGHT);
        viewOffsetX = (width - WORLD_WIDTH * viewScale) * 0.5f;
        viewOffsetY = (height - WORLD_HEIGHT * viewScale) * 0.5f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.min(dt, 0.05f);
        if (hostResumed) {
            world.update(dt);
            updateParticles(dt);
            hintTime += dt;
        }

        GameWorld.State visualState = world.getState();
        music.setPlaying(hostResumed && visualState == GameWorld.State.PLAYING);
        if (visualState != lastVisualState) {
            lastVisualState = visualState;
            overlayProgress = isOverlayState(visualState) ? 0f : 1f;
        }
        if (isOverlayState(visualState) && hostResumed) {
            overlayProgress = Math.min(1f, overlayProgress + dt * 5.5f);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(outsideGradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(viewScale, viewScale);
        float time = now / 1_000_000_000f;
        if (visualState == GameWorld.State.TITLE) {
            drawTitle(canvas, time);
        } else {
            drawLevel(canvas, time);
        }
        canvas.restore();

        if (hostResumed) {
            postInvalidateOnAnimation();
        }
    }

    private static boolean isOverlayState(GameWorld.State state) {
        return state == GameWorld.State.PAUSED || state == GameWorld.State.LEVEL_COMPLETE;
    }

    private void drawTitle(Canvas canvas, float time) {
        paint.setShader(outsideGradient);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        paint.setShader(null);

        drawSun(canvas, 156f, 128f);
        drawCloud(canvas, 1010f, 121f, 1.05f);
        drawCloud(canvas, 242f, 255f, 0.72f);
        drawHill(canvas, 0xFFD9CFC6, 520f, 64f, 0.013f);
        drawHill(canvas, 0xFFC8BDB5, 584f, 46f, 0.017f);
        drawTitleTrail(canvas, time);

        drawFittedText(canvas, text(R.string.game_title), 640f, 151f,
                70f, 780f, 0xFF62596D, true);
        drawFittedText(canvas, text(R.string.game_subtitle), 640f, 207f,
                25f, 720f, 0xFF817789, false);

        canvas.save();
        canvas.translate(307f, 434f + (float) Math.sin(time * 2f) * 3f);
        canvas.scale(1.75f, 1.75f);
        drawKittenAtOrigin(canvas, time, 1f);
        canvas.restore();

        float pulse = 0.98f + 0.025f * (float) Math.sin(time * 2.6f);
        canvas.save();
        canvas.scale(pulse, pulse, 692f, 337f);
        drawPill(canvas, 504f, 294f, 880f, 380f, 0xF7FFF9F1, 0x265F576A);
        drawFittedText(canvas, text(R.string.touch_to_begin), 692f, 347f,
                29f, 325f, 0xFF675E72, true);
        canvas.restore();

        drawFittedText(canvas, text(R.string.tap_to_walk), 700f, 423f,
                21f, 580f, 0xE8635D6D, false);
        drawFittedText(canvas, text(R.string.collect_stars), 700f, 458f,
                21f, 580f, 0xE8635D6D, false);
        drawLanguageSwitch(canvas, 1167f, 56f);

        int resume = preferences.getInt(PREF_RESUME_LEVEL, 0);
        float first = 640f - (world.getLevelCount() - 1) * 11f;
        for (int i = 0; i < world.getLevelCount(); i++) {
            paint.setColor(i == resume ? 0xFFFFCE72 : 0x78FFFFFF);
            canvas.drawCircle(first + i * 22f, 674f, i == resume ? 6f : 4f, paint);
        }
    }

    private void drawLevel(Canvas canvas, float time) {
        paint.setShader(levelGradient);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        paint.setShader(null);
        drawCloud(canvas, 74f, 56f, 0.56f);
        drawCloud(canvas, 1085f, 650f, 0.48f);

        drawBoard(canvas, time);
        drawTarget(canvas, time);
        drawParticles(canvas);
        float kittenX = BOARD_LEFT + (world.getVisualCol() + 0.5f) * TILE;
        float kittenY = BOARD_TOP + (world.getVisualRow() + 0.5f) * TILE + 6f;
        canvas.save();
        canvas.translate(kittenX, kittenY);
        canvas.scale(0.72f, 0.72f);
        drawKittenAtOrigin(canvas, time, world.getFacing());
        canvas.restore();
        drawHud(canvas, time);

        if (hintTime < 5.5f && world.getState() == GameWorld.State.PLAYING) {
            float alpha = hintTime < 4f ? 1f : Math.max(0f, (5.5f - hintTime) / 1.5f);
            drawHint(canvas, alpha);
        }

        if (world.getState() == GameWorld.State.PAUSED) {
            drawOverlay(canvas, R.string.paused, R.string.touch_to_continue, time);
        } else if (world.getState() == GameWorld.State.LEVEL_COMPLETE) {
            drawOverlay(canvas, R.string.level_complete, R.string.touch_to_continue, time);
        } else if (world.getState() == GameWorld.State.QUEST_COMPLETE) {
            drawJourneyComplete(canvas, time);
        }
    }

    private void drawBoard(Canvas canvas, float time) {
        LevelData level = world.getLevel();
        rect.set(BOARD_LEFT - 12f, BOARD_TOP - 12f,
                BOARD_LEFT + LevelData.COLS * TILE + 12f,
                BOARD_TOP + LevelData.ROWS * TILE + 12f);
        paint.setColor(0x2A5B5260);
        canvas.drawRoundRect(rect.left + 5f, rect.top + 8f, rect.right + 5f,
                rect.bottom + 8f, 31f, 31f, paint);
        paint.setColor(0xBFF9F3E6);
        canvas.drawRoundRect(rect, 31f, 31f, paint);

        for (int row = 0; row < LevelData.ROWS; row++) {
            for (int col = 0; col < LevelData.COLS; col++) {
                float left = BOARD_LEFT + col * TILE;
                float top = BOARD_TOP + row * TILE;
                int floor = ((row + col) & 1) == 0 ? level.floorA : level.floorB;
                paint.setColor(floor);
                canvas.drawRoundRect(left + 1.5f, top + 1.5f,
                        left + TILE - 1.5f, top + TILE - 1.5f, 13f, 13f, paint);
                drawFloorDetails(canvas, left, top, row, col, level, time);
            }
        }

        for (int row = 0; row < LevelData.ROWS; row++) {
            for (int col = 0; col < LevelData.COLS; col++) {
                float cx = BOARD_LEFT + (col + 0.5f) * TILE;
                float cy = BOARD_TOP + (row + 0.5f) * TILE;
                char tile = level.tileAt(row, col);
                if (tile == LevelData.WALL) {
                    drawWall(canvas, cx, cy, level.wallColor, level.wallTopColor);
                } else if (tile == LevelData.CRATE) {
                    drawCrate(canvas, cx, cy, level.accentColor);
                } else if (tile == LevelData.STAR && !world.isStarCollected(row, col)) {
                    drawStar(canvas, cx, cy, level.starColor,
                            time + row * 0.7f + col);
                } else if (tile == LevelData.HOME) {
                    drawHome(canvas, cx, cy, world.allStarsCollected(), time);
                }
            }
        }
    }

    private void drawFloorDetails(Canvas canvas, float left, float top,
                                  int row, int col, LevelData level, float time) {
        char tile = level.tileAt(row, col);
        if (tile == LevelData.WALL || tile == LevelData.CRATE) {
            return;
        }
        int value = level.seed + row * 97 + col * 53;
        if (Math.floorMod(value, 4) == 0) {
            paint.setColor(0x39749A72);
            paint.setStrokeWidth(1.5f);
            paint.setStyle(Paint.Style.STROKE);
            float x = left + 13f + Math.floorMod(value, 31);
            float y = top + 43f;
            canvas.drawLine(x, y + 3f, x - 4f, y - 5f, paint);
            canvas.drawLine(x, y + 3f, x + 5f, y - 7f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (tile != LevelData.FLOOR) {
            return;
        }
        if (Math.floorMod(value, 11) == 0) {
            float x = left + 15f + Math.floorMod(value * 7, 38);
            float y = top + 17f + Math.floorMod(value * 13, 34);
            drawFlower(canvas, x, y, petalColor(level, value), 5.6f, time, value);
        }
        if (Math.floorMod(value, 13) == 5) {
            float x = left + 14f + Math.floorMod(value * 5, 40);
            float y = top + 15f + Math.floorMod(value * 17, 38);
            drawBlossom(canvas, x, y, petalColor(level, value + 1), time, value);
        }
    }

    private int petalColor(LevelData level, int value) {
        int pick = Math.floorMod(value, 3);
        if (pick == 0) {
            return 0xFFF4B9C6;
        }
        if (pick == 1) {
            return lighten(level.accentColor, 0.24f);
        }
        return 0xFFFDF3DC;
    }

    private void drawFlower(Canvas canvas, float cx, float cy, int petalColor,
                            float size, float time, int phase) {
        float sway = (float) Math.sin(time * 1.6f + Math.floorMod(phase, 628) * 0.01f) * 1.3f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setColor(0x7A72976D);
        canvas.drawLine(cx, cy + size * 1.9f, cx + sway, cy + size * 0.4f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(petalColor);
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI * 2.0 * i / 5.0 - Math.PI / 2.0;
            canvas.drawCircle(cx + sway + (float) Math.cos(angle) * size,
                    cy + (float) Math.sin(angle) * size, size * 0.66f, paint);
        }
        paint.setColor(0xFFFFE9A2);
        canvas.drawCircle(cx + sway, cy, size * 0.5f, paint);
    }

    private void drawBlossom(Canvas canvas, float cx, float cy, int color,
                             float time, int phase) {
        float sway = (float) Math.sin(time * 1.9f + Math.floorMod(phase, 628) * 0.01f) * 0.9f;
        paint.setColor(color);
        canvas.drawCircle(cx + sway - 2.4f, cy, 2.4f, paint);
        canvas.drawCircle(cx + sway + 2.4f, cy, 2.4f, paint);
        canvas.drawCircle(cx + sway, cy - 2.4f, 2.4f, paint);
        canvas.drawCircle(cx + sway, cy + 2.4f, 2.4f, paint);
        paint.setColor(0xFFFFF2C0);
        canvas.drawCircle(cx + sway, cy, 1.6f, paint);
    }

    private void drawWall(Canvas canvas, float cx, float cy, int wallColor, int topColor) {
        paint.setColor(0x31594A4F);
        canvas.drawRoundRect(cx - 31f, cy - 27f, cx + 32f, cy + 34f,
                11f, 11f, paint);
        paint.setColor(wallColor);
        canvas.drawRoundRect(cx - 31f, cy - 31f, cx + 31f, cy + 29f,
                11f, 11f, paint);
        paint.setColor(topColor);
        canvas.drawRoundRect(cx - 27f, cy - 27f, cx + 27f, cy - 10f,
                8f, 8f, paint);
        paint.setColor(0x28FFFFFF);
        canvas.drawRoundRect(cx - 24f, cy - 24f, cx + 6f, cy - 19f,
                3f, 3f, paint);
        paint.setColor(lighten(wallColor, 0.08f));
        canvas.drawCircle(cx - 16f, cy + 7f, 5f, paint);
        canvas.drawCircle(cx, cy + 11f, 6f, paint);
        canvas.drawCircle(cx + 17f, cy + 6f, 5f, paint);
        paint.setColor(0x4DFFFFFF);
        canvas.drawCircle(cx - 15f, cy - 14f, 4f, paint);
        canvas.drawCircle(cx + 13f, cy - 18f, 3f, paint);
        paint.setColor(0x38FFFFFF);
        canvas.drawCircle(cx - 3f, cy + 8f, 2.2f, paint);
    }

    private void drawCrate(Canvas canvas, float cx, float cy, int accent) {
        paint.setColor(0x3456494A);
        canvas.drawRoundRect(cx - 26f, cy - 22f, cx + 30f, cy + 31f,
                9f, 9f, paint);
        paint.setColor(darken(accent, 0.84f));
        canvas.drawRoundRect(cx - 28f, cy - 28f, cx + 28f, cy + 27f,
                9f, 9f, paint);
        paint.setColor(lighten(accent, 0.12f));
        canvas.drawRoundRect(cx - 22f, cy - 22f, cx + 22f, cy + 21f,
                6f, 6f, paint);
        paint.setColor(0x4FFFFFFF);
        canvas.drawOval(cx - 16f, cy - 17f, cx + 7f, cy - 9f, paint);
        paint.setColor(darken(accent, 0.72f));
        canvas.drawCircle(cx + 12f, cy + 10f, 4f, paint);
        canvas.drawCircle(cx - 13f, cy + 14f, 3f, paint);
    }

    private void drawStar(Canvas canvas, float cx, float cy, int color, float time) {
        float bob = (float) Math.sin(time * 2.5f) * 2.4f;
        float pulse = 1f + (float) Math.sin(time * 2f) * 0.05f;
        canvas.save();
        canvas.translate(cx, cy + bob);
        canvas.scale(pulse, pulse);
        paint.setColor(0x32FFF0A0);
        canvas.drawCircle(0f, 0f, 29f, paint);
        path.reset();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float radius = (i & 1) == 0 ? 22f : 10f;
            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setColor(0xCFFFFFF0);
        canvas.drawCircle(-6f, -7f, 3f, paint);
        canvas.restore();
    }

    private void drawHome(Canvas canvas, float cx, float cy, boolean ready, float time) {
        if (ready) {
            paint.setColor(0x40FFE59A);
            canvas.drawCircle(cx, cy + 2f, 35f + (float) Math.sin(time * 2.2f) * 3f, paint);
        }
        paint.setColor(0x2D5A4D55);
        canvas.drawOval(cx - 30f, cy + 17f, cx + 31f, cy + 30f, paint);
        paint.setColor(ready ? 0xFFFFEEE1 : 0xFFE1DAD8);
        canvas.drawRoundRect(cx - 24f, cy - 7f, cx + 24f, cy + 24f, 7f, 7f, paint);
        path.reset();
        path.moveTo(cx - 31f, cy - 5f);
        path.lineTo(cx, cy - 32f);
        path.lineTo(cx + 31f, cy - 5f);
        path.close();
        paint.setColor(ready ? 0xFFE99B88 : 0xFFACA5AA);
        canvas.drawPath(path, paint);
        paint.setColor(ready ? 0xFF706175 : 0xFF89838C);
        canvas.drawRoundRect(cx - 8f, cy + 5f, cx + 8f, cy + 24f, 7f, 7f, paint);
        paint.setColor(ready ? 0xFFFFD671 : 0xFFBBB3B1);
        canvas.drawCircle(cx + 14f, cy + 5f, 3.5f, paint);
    }

    private void drawKittenAtOrigin(Canvas canvas, float time, float facing) {
        canvas.save();
        canvas.scale(facing < 0f ? -1f : 1f, 1f);
        float bounce = (float) Math.sin(time * 7f) * (world.getPathLength() > 0 ? 2.2f : 0.8f);
        canvas.translate(0f, bounce);

        paint.setColor(0x28564E60);
        canvas.drawOval(-42f, 27f, 44f, 40f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(11f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFE89571);
        path.reset();
        path.moveTo(-26f, 5f);
        path.cubicTo(-49f, -11f, -54f, 14f, -43f, 21f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0xFFF0A17D);
        canvas.drawOval(-31f, -17f, 31f, 31f, paint);
        paint.setColor(0xFFFFD1B7);
        canvas.drawOval(-5f, -7f, 31f, 27f, paint);
        paint.setColor(0xFFE89472);
        canvas.drawOval(-22f, 21f, -5f, 36f, paint);
        canvas.drawOval(11f, 21f, 29f, 36f, paint);

        paint.setColor(0xFFF0A17D);
        canvas.drawCircle(25f, -22f, 30f, paint);
        path.reset();
        path.moveTo(3f, -38f);
        path.lineTo(10f, -65f);
        path.lineTo(27f, -45f);
        path.close();
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(33f, -46f);
        path.lineTo(52f, -62f);
        path.lineTo(48f, -32f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xFFF8B9AE);
        path.reset();
        path.moveTo(11f, -44f);
        path.lineTo(13f, -56f);
        path.lineTo(21f, -47f);
        path.close();
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(39f, -47f);
        path.lineTo(49f, -55f);
        path.lineTo(46f, -41f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xFFFFE9D8);
        canvas.drawOval(8f, -26f, 49f, 8f, paint);
        paint.setColor(0xFF5D5670);
        canvas.drawOval(15f, -27f, 21f, -17f, paint);
        canvas.drawOval(36f, -27f, 42f, -17f, paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(18f, -24f, 1.5f, paint);
        canvas.drawCircle(39f, -24f, 1.5f, paint);
        paint.setColor(0xFFD7808E);
        path.reset();
        path.moveTo(26f, -13f);
        path.lineTo(33f, -13f);
        path.lineTo(29.5f, -8f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0x66E992A0);
        canvas.drawCircle(10f, -9f, 5f, paint);
        canvas.drawCircle(48f, -9f, 5f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setColor(0x8A5D5670);
        canvas.drawLine(8f, -12f, -5f, -16f, paint);
        canvas.drawLine(9f, -6f, -5f, -5f, paint);
        canvas.drawLine(49f, -12f, 62f, -16f, paint);
        canvas.drawLine(49f, -6f, 63f, -5f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawTarget(Canvas canvas, float time) {
        if (targetRow < 0 || world.getState() != GameWorld.State.PLAYING) {
            return;
        }
        float cx = BOARD_LEFT + (targetCol + 0.5f) * TILE;
        float cy = BOARD_TOP + (targetRow + 0.5f) * TILE;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xA8FFF9E6);
        float radius = 17f + (float) Math.sin(time * 4f) * 3f;
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHud(Canvas canvas, float time) {
        LevelData level = world.getLevel();
        drawPill(canvas, 178f, 18f, 1104f, 86f, 0xDFFFFFF5, 0x20594F5E);
        drawFittedText(canvas, (world.getLevelIndex() + 1) + " / " + world.getLevelCount()
                        + "  ·  " + text(level.nameRes),
                640f, 60f, 27f, 430f, 0xFF625A67, true);

        canvas.save();
        canvas.translate(224f, 51f);
        canvas.scale(0.62f, 0.62f);
        drawStar(canvas, 0f, 0f, level.starColor, time);
        canvas.restore();
        drawFittedText(canvas, text(R.string.star_label) + "  "
                        + world.getCollectedCount() + "/" + level.starCount,
                320f, 61f, 20f, 160f, 0xFF625A67, true);
        canvas.save();
        canvas.translate(936f, 51f);
        canvas.scale(0.62f, 0.62f);
        drawHome(canvas, 0f, 0f, world.allStarsCollected(), time);
        canvas.restore();
        drawFittedText(canvas, text(R.string.home_label) + "  "
                        + (world.allStarsCollected() ? "✓" : "…"),
                1023f, 61f, 20f, 145f, 0xFF625A67, true);

        paint.setColor(0xEFFFFFF5);
        canvas.drawCircle(PAUSE_X, PAUSE_Y, 34f, paint);
        paint.setColor(0xFF716877);
        canvas.drawRoundRect(PAUSE_X - 9f, PAUSE_Y - 12f,
                PAUSE_X - 3f, PAUSE_Y + 12f, 3f, 3f, paint);
        canvas.drawRoundRect(PAUSE_X + 3f, PAUSE_Y - 12f,
                PAUSE_X + 9f, PAUSE_Y + 12f, 3f, 3f, paint);
    }

    private void drawHint(Canvas canvas, float alpha) {
        int a = Math.round(alpha * 220f);
        drawPill(canvas, 338f, 660f, 942f, 708f,
                Color.argb(a, 255, 251, 241), Color.argb(Math.round(alpha * 24f), 80, 70, 90));
        drawFittedText(canvas, text(R.string.tap_to_walk), 640f, 691f,
                19f, 555f, Color.argb(Math.round(alpha * 255f), 101, 91, 107), false);
    }

    private void drawOverlay(Canvas canvas, int titleRes, int subtitleRes, float time) {
        float eased = overlayProgress * overlayProgress * (3f - 2f * overlayProgress);
        paint.setColor(Color.argb(Math.round(120f * eased), 99, 91, 107));
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);

        float cardScale = 0.95f + 0.05f * eased;
        int layer = canvas.saveLayerAlpha(
                OVERLAY_LEFT - 12f, OVERLAY_TOP - 12f,
                OVERLAY_RIGHT + 16f, OVERLAY_BOTTOM + 20f,
                Math.round(255f * eased)
        );
        canvas.scale(cardScale, cardScale, 640f, 360f);
        paint.setColor(0x255B5260);
        canvas.drawRoundRect(OVERLAY_LEFT + 4f, OVERLAY_TOP + 7f,
                OVERLAY_RIGHT + 4f, OVERLAY_BOTTOM + 7f, 46f, 46f, paint);
        paint.setColor(0xF8FFF9EE);
        canvas.drawRoundRect(OVERLAY_LEFT, OVERLAY_TOP,
                OVERLAY_RIGHT, OVERLAY_BOTTOM, 46f, 46f, paint);

        float contentWidth = OVERLAY_RIGHT - OVERLAY_LEFT - OVERLAY_PADDING * 2f;
        float iconPulse = 1f + 0.03f * (float) Math.sin(time * 2.2f);
        canvas.save();
        canvas.translate(640f, 251f);
        canvas.scale(iconPulse, iconPulse);
        drawStar(canvas, 0f, 0f, world.getLevel().starColor, time);
        canvas.restore();
        drawFittedText(canvas, text(titleRes), 640f, 355f,
                39f, contentWidth, 0xFF625A69, true);
        drawFittedText(canvas, text(subtitleRes), 640f, 420f,
                22f, contentWidth, 0xFF817685, false);
        if (world.getState() == GameWorld.State.PAUSED) {
            drawLanguageSwitch(canvas, 640f, 492f);
        }
        canvas.restoreToCount(layer);
    }

    private void drawJourneyComplete(Canvas canvas, float time) {
        paint.setColor(0x84635B6B);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        rect.set(278f, 123f, 1002f, 596f);
        paint.setColor(0xFAFFF9ED);
        canvas.drawRoundRect(rect, 48f, 48f, paint);
        canvas.save();
        canvas.translate(640f, 259f);
        canvas.scale(1.3f, 1.3f);
        drawKittenAtOrigin(canvas, time, 1f);
        canvas.restore();
        drawFittedText(canvas, text(R.string.quest_complete), 640f, 392f,
                42f, 620f, 0xFF605868, true);
        drawFittedText(canvas, text(R.string.quest_complete_subtitle), 640f, 444f,
                22f, 590f, 0xFF817686, false);
        drawPill(canvas, 454f, 480f, 826f, 548f, 0xFFFFE7A8, 0x245B5360);
        drawFittedText(canvas, text(R.string.play_again), 640f, 523f,
                23f, 325f, 0xFF665D6B, true);
    }

    private void drawLanguageSwitch(Canvas canvas, float cx, float cy) {
        drawPill(canvas, cx - 58f, cy - 27f, cx + 58f, cy + 27f,
                0xEFFFFFF4, 0x20584F60);
        paint.setColor(0xFF716877);
        paint.setStrokeWidth(2f);
        float selectedX = "en".equals(language) ? cx - 28f : cx + 28f;
        paint.setColor(0xFFFFD980);
        canvas.drawCircle(selectedX, cy, 21f, paint);
        drawFittedText(canvas, "EN", cx - 28f, cy + 7f, 17f, 36f, 0xFF615969, true);
        drawFittedText(canvas, "RU", cx + 28f, cy + 7f, 17f, 36f, 0xFF615969, true);
    }

    private void drawTitleTrail(Canvas canvas, float time) {
        paint.setColor(0xFFB9C9A8);
        canvas.drawRect(0f, 611f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        int[] gaps = {2, 5, 8, 12, 15};
        for (int i = 0; i < 18; i++) {
            boolean gap = false;
            for (int candidate : gaps) {
                gap |= i == candidate;
            }
            if (!gap) {
                drawWall(canvas, 25f + i * 75f, 664f,
                        0xFF89A67E, 0xFFA9C59C);
            }
        }
        drawStar(canvas, 212f, 665f, 0xFFFFCF67, time);
        drawStar(canvas, 662f, 665f, 0xFFFFCF67, time + 1f);
        drawHome(canvas, 1112f, 665f, true, time);
    }

    private void drawSun(Canvas canvas, float cx, float cy) {
        paint.setColor(0x65FFF0A8);
        canvas.drawCircle(cx, cy, 78f, paint);
        paint.setColor(0xFFFFD985);
        canvas.drawCircle(cx, cy, 48f, paint);
    }

    private void drawCloud(Canvas canvas, float cx, float cy, float scale) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        paint.setColor(0x8AFFFDF4);
        canvas.drawCircle(-34f, 8f, 25f, paint);
        canvas.drawCircle(0f, -4f, 34f, paint);
        canvas.drawCircle(35f, 9f, 24f, paint);
        canvas.drawRoundRect(-58f, 6f, 58f, 34f, 17f, 17f, paint);
        canvas.restore();
    }

    private void drawHill(Canvas canvas, int color, float top, float amplitude, float frequency) {
        path.reset();
        path.moveTo(0f, WORLD_HEIGHT);
        path.lineTo(0f, top);
        for (int x = 0; x <= 1280; x += 32) {
            path.lineTo(x, top + (float) Math.sin(x * frequency) * amplitude);
        }
        path.lineTo(WORLD_WIDTH, WORLD_HEIGHT);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                          int fillColor, int shadowColor) {
        float radius = (bottom - top) * 0.5f;
        paint.setColor(shadowColor);
        canvas.drawRoundRect(left + 3f, top + 5f, right + 3f, bottom + 5f,
                radius, radius, paint);
        paint.setColor(fillColor);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void drawFittedText(Canvas canvas, String value, float centerX, float baseline,
                                float preferredSize, float maxWidth, int color, boolean useBold) {
        paint.setTypeface(useBold ? bold : regular);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(preferredSize);
        float width = paint.measureText(value);
        if (width > maxWidth && width > 0f) {
            paint.setTextSize(preferredSize * maxWidth / width);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(value, centerX, baseline, paint);
    }

    private void spawnParticles(float col, float row, int color, int count) {
        float x = BOARD_LEFT + (col + 0.5f) * TILE;
        float y = BOARD_TOP + (row + 0.5f) * TILE;
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = 45f + random.nextFloat() * 80f;
            particles.add(new Particle(
                    x, y,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 25f,
                    0.7f + random.nextFloat() * 0.55f,
                    3f + random.nextFloat() * 4f,
                    color
            ));
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.life -= dt;
            if (particle.life <= 0f) {
                particles.remove(i);
                continue;
            }
            particle.x += particle.velocityX * dt;
            particle.y += particle.velocityY * dt;
            particle.velocityY += 34f * dt;
        }
    }

    private void drawParticles(Canvas canvas) {
        for (Particle particle : particles) {
            float alpha = Math.min(1f, particle.life * 2f);
            paint.setColor(Color.argb(Math.round(alpha * 255f),
                    Color.red(particle.color), Color.green(particle.color), Color.blue(particle.color)));
            canvas.drawCircle(particle.x, particle.y, particle.radius * alpha, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP || viewScale <= 0f) {
            return true;
        }
        float x = (event.getX() - viewOffsetX) / viewScale;
        float y = (event.getY() - viewOffsetY) / viewScale;
        performClick();
        GameWorld.State state = world.getState();

        if (state == GameWorld.State.TITLE) {
            if (isLanguageHit(x, y, 1167f, 56f)) {
                toggleLanguage();
            } else {
                world.startJourney(preferences.getInt(PREF_RESUME_LEVEL, 0));
                hintTime = 0f;
                targetRow = -1;
                rebuildLevelGradient();
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            }
        } else if (state == GameWorld.State.PLAYING) {
            if (distance(x, y, PAUSE_X, PAUSE_Y) <= 43f) {
                world.pause();
            } else {
                int col = (int) ((x - BOARD_LEFT) / TILE);
                int row = (int) ((y - BOARD_TOP) / TILE);
                if (x >= BOARD_LEFT && y >= BOARD_TOP
                        && col >= 0 && col < LevelData.COLS
                        && row >= 0 && row < LevelData.ROWS
                        && world.tapCell(row, col)) {
                    targetRow = row;
                    targetCol = col;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }
        } else if (state == GameWorld.State.PAUSED) {
            if (isLanguageHit(x, y, 640f, 492f)) {
                toggleLanguage();
            } else {
                world.resume();
            }
        } else if (state == GameWorld.State.LEVEL_COMPLETE) {
            world.continueAfterLevel();
            targetRow = -1;
            hintTime = 0f;
            rebuildLevelGradient();
        } else if (state == GameWorld.State.QUEST_COMPLETE) {
            preferences.edit().putInt(PREF_RESUME_LEVEL, 0).apply();
            world.restartJourney();
            targetRow = -1;
            hintTime = 0f;
            rebuildLevelGradient();
        }
        invalidate();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private boolean isLanguageHit(float x, float y, float centerX, float centerY) {
        return Math.abs(x - centerX) <= 70f && Math.abs(y - centerY) <= 42f;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private void toggleLanguage() {
        language = "en".equals(language) ? "ru" : "en";
        preferences.edit().putString(PREF_LANGUAGE, language).apply();
        applyLanguage(language);
        setContentDescription(text(R.string.accessibility_game));
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        invalidate();
    }

    private void applyLanguage(String languageCode) {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(languageCode));
        localizedContext = getContext().createConfigurationContext(configuration);
    }

    private String text(int resource) {
        return localizedContext.getString(resource);
    }

    private void rebuildLevelGradient() {
        LevelData level = world.getLevel();
        levelGradient = new LinearGradient(0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{level.backgroundTop, level.backgroundBottom}, null, Shader.TileMode.CLAMP);
    }

    @Override
    public void onStarCollected(float col, float row, int collectedCount) {
        audio.playStar(collectedCount);
        spawnParticles(col, row, world.getLevel().starColor, 16);
        announceForAccessibility(text(R.string.accessibility_star_found));
    }

    @Override
    public void onLevelComplete(int completedLevel) {
        int resume = Math.min(completedLevel + 1, world.getLevelCount() - 1);
        preferences.edit().putInt(PREF_RESUME_LEVEL, resume).apply();
        audio.playLevelComplete();
        targetRow = -1;
        announceForAccessibility(text(R.string.accessibility_level_complete));
    }

    @Override
    public void onJourneyComplete() {
        audio.playJourneyComplete();
    }

    boolean handleBack() {
        GameWorld.State state = world.getState();
        if (state == GameWorld.State.PLAYING) {
            world.pause();
            invalidate();
            return true;
        }
        if (state == GameWorld.State.PAUSED
                || state == GameWorld.State.LEVEL_COMPLETE
                || state == GameWorld.State.QUEST_COMPLETE) {
            world.showTitle();
            targetRow = -1;
            invalidate();
            return true;
        }
        return false;
    }

    void onHostPause() {
        hostResumed = false;
        music.setPlaying(false);
        lastFrameNanos = 0L;
        world.pause();
    }

    void onHostResume() {
        hostResumed = true;
        lastFrameNanos = 0L;
        invalidate();
    }

    void close() {
        hostResumed = false;
        music.close();
        audio.close();
    }

    private static int lighten(int color, float amount) {
        int red = Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount));
        int green = Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount));
        int blue = Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount));
        return Color.rgb(red, green, blue);
    }

    private static int darken(int color, float factor) {
        return Color.rgb(Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor));
    }

    private static final class Particle {
        float x;
        float y;
        final float velocityX;
        float velocityY;
        float life;
        final float radius;
        final int color;

        Particle(float x, float y, float velocityX, float velocityY,
                 float life, float radius, int color) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.life = life;
            this.radius = radius;
            this.color = color;
        }
    }
}
