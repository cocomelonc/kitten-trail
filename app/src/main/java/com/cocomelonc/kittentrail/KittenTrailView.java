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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * A complete, dependency-free 2D game rendered with Android's hardware Canvas.
 * The logical world is 1280x720 and scales uniformly to every landscape display.
 */
final class KittenTrailView extends View implements GameWorld.Listener {
    private static final String PREFS = "kitten_trail_progress";
    private static final String PREF_RESUME_LEVEL = "resume_level";
    private static final String PREF_LANGUAGE = "language";
    private static final float PAUSE_RADIUS = 35f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final SharedPreferences preferences;
    private final AudioEngine audio = new AudioEngine();
    private final GameWorld world;
    private final List<Decoration> decorations = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final boolean[] visualCollected = new boolean[3];

    private Context localizedContext;
    private String language;

    private LinearGradient levelGradient;
    private LinearGradient titleGradient;
    private Bitmap levelBackground;
    private Bitmap titleBackground;
    private long lastFrameNanos;
    private boolean hostResumed = true;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private float safeLeftWorld;
    private float safeRightWorld;
    private float pauseX = GameWorld.WORLD_WIDTH - 54f;
    private float pauseY = 52f;
    private float facing = 1f;
    private float hintTime;

    KittenTrailView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setKeepScreenOn(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        language = preferences.getString(PREF_LANGUAGE, "en");
        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        setContentDescription(text(R.string.accessibility_game));
        world = new GameWorld(LevelData.createAll(), this);
        titleGradient = new LinearGradient(
                0f, 0f, 0f, GameWorld.WORLD_HEIGHT,
                new int[]{0xFF6E668F, 0xFF9E91AD, 0xFFF1C7BF},
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP
        );
        rebuildTitleBackground();
        rebuildScene();
        installInsetListener();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateTransform(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.min(dt, 1f / 20f);

        if (hostResumed) {
            world.update(dt);
            updateParticles(dt);
            hintTime += dt;
        }

        drawOutside(canvas);
        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(viewScale, viewScale);
        if (world.state() == GameWorld.State.TITLE) {
            drawTitle(canvas, now / 1_000_000_000f);
        } else {
            drawLevel(canvas, now / 1_000_000_000f);
        }
        canvas.restore();

        if (hostResumed) {
            postInvalidateOnAnimation();
        }
    }

    private void drawOutside(Canvas canvas) {
        paint.setShader(titleGradient);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);
    }

    private void drawTitle(Canvas canvas, float time) {
        canvas.drawBitmap(titleBackground, 0f, 0f, bitmapPaint);

        drawHome(canvas, 1065f, 440f, true, time);

        canvas.save();
        canvas.translate(268f, 455f);
        canvas.scale(1.65f, 1.65f);
        drawKittenAtOrigin(canvas, time, 1f, true);
        canvas.restore();

        drawFittedText(canvas, text(R.string.game_title),
                GameWorld.WORLD_WIDTH / 2f, 185f, 74f, 760f, 0xFFFFF7EC, true);
        drawFittedText(canvas, text(R.string.game_subtitle),
                GameWorld.WORLD_WIDTH / 2f, 236f, 26f, 720f, 0xFFEDE7F1, false);

        float pulse = 0.95f + 0.035f * (float) Math.sin(time * 2.4f);
        canvas.save();
        canvas.scale(pulse, pulse, 650f, 337f);
        drawPill(canvas, 470f, 296f, 830f, 378f, 0xEFFFF7EA, 0x20564F70);
        drawFittedText(canvas, text(R.string.touch_to_begin),
                650f, 347f, 29f, 310f, 0xFF665C7A, true);
        canvas.restore();

        drawFittedText(canvas, text(R.string.hold_to_walk),
                650f, 416f, 21f, 560f, 0xE9FFF7EF, false);

        drawLanguageSwitch(canvas, GameWorld.WORLD_WIDTH - 104f - safeRightWorld, 53f);

        int resume = preferences.getInt(PREF_RESUME_LEVEL, 0);
        for (int i = 0; i < world.levelCount(); i++) {
            paint.setColor(i == resume ? 0xFFFFD778 : 0x70FFF7EE);
            float x = 602f + i * 24f;
            canvas.drawCircle(x, 665f, i == resume ? 6f : 4f, paint);
        }
    }

    private void drawLevel(Canvas canvas, float time) {
        LevelData level = world.currentLevel();
        canvas.drawBitmap(levelBackground, 0f, 0f, bitmapPaint);
        drawDynamicDecorations(canvas, time);

        drawHome(canvas, level.homeX, level.homeY, world.allStarsCollected(), time);

        for (int i = 0; i < level.stars.length; i++) {
            if (!world.isStarCollected(i)) {
                drawStarPickup(canvas, level.stars[i][0], level.stars[i][1], time + i * 1.7f);
            }
        }

        drawTargetHint(canvas, time);
        drawParticles(canvas);
        drawKitten(canvas, time);
        drawHud(canvas, time);

        if (hintTime < 5.2f && world.state() == GameWorld.State.PLAYING) {
            float alpha = hintTime < 3.7f ? 1f : Math.max(0f, (5.2f - hintTime) / 1.5f);
            drawHint(canvas, alpha);
        }

        switch (world.state()) {
            case PAUSED:
                drawOverlay(canvas, R.string.paused, R.string.touch_to_continue, time, false);
                break;
            case LEVEL_COMPLETE:
                drawOverlay(canvas, R.string.level_complete, R.string.touch_to_continue, time, false);
                break;
            case QUEST_COMPLETE:
                drawQuestComplete(canvas, time);
                break;
            default:
                break;
        }
    }

    private void drawCloudShadows(Canvas canvas, float time, int seed) {
        paint.setColor(0x0DFFFFFF);
        float drift = (time * 8f + seed) % 420f;
        for (int i = -1; i < 4; i++) {
            float x = i * 420f + drift - 220f;
            float y = 150f + (i % 2) * 240f;
            rect.set(x, y, x + 260f, y + 82f);
            canvas.drawOval(rect, paint);
            rect.offset(85f, 32f);
            canvas.drawOval(rect, paint);
        }
    }

    private void drawTrail(Canvas canvas, LevelData level) {
        float[] p = level.path;
        path.reset();
        path.moveTo(p[0], p[1]);
        path.cubicTo(p[2], p[3], p[4], p[5], p[6], p[7]);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(104f);
        paint.setColor(0x32FFFFFF);
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(76f);
        paint.setColor(withAlpha(level.groundLight, 120));
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(4f);
        paint.setColor(0x2EFFFFFF);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStaticDecorations(Canvas canvas) {
        for (Decoration decoration : decorations) {
            switch (decoration.kind) {
                case Decoration.GRASS:
                    drawGrass(canvas, decoration);
                    break;
                case Decoration.FLOWER:
                    drawFlower(canvas, decoration, 0f);
                    break;
                default:
                    break;
            }
        }
    }

    private void drawDynamicDecorations(Canvas canvas, float time) {
        for (Decoration decoration : decorations) {
            if (decoration.kind == Decoration.FIREFLY) {
                drawFirefly(canvas, decoration, time);
            }
        }
    }

    private void drawGrass(Canvas canvas, Decoration d) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f);
        paint.setColor(d.color);
        canvas.drawLine(d.x, d.y + d.size, d.x - d.size * 0.38f, d.y - d.size * 0.4f, paint);
        canvas.drawLine(d.x, d.y + d.size, d.x + d.size * 0.32f, d.y - d.size * 0.7f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFlower(Canvas canvas, Decoration d, float time) {
        float sway = (float) Math.sin(time * 1.5f + d.phase) * 1.4f;
        paint.setColor(0x805D8A62);
        paint.setStrokeWidth(2f);
        canvas.drawLine(d.x, d.y + 7f, d.x + sway, d.y, paint);
        paint.setColor(d.color);
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2.0 / 5.0;
            canvas.drawCircle(d.x + sway + (float) Math.cos(angle) * 4.2f,
                    d.y + (float) Math.sin(angle) * 4.2f, 3.5f, paint);
        }
        paint.setColor(0xFFFFE28A);
        canvas.drawCircle(d.x + sway, d.y, 2.7f, paint);
    }

    private void drawFirefly(Canvas canvas, Decoration d, float time) {
        float x = d.x + (float) Math.sin(time * 0.9f + d.phase) * d.size;
        float y = d.y + (float) Math.cos(time * 1.25f + d.phase) * d.size * 0.45f;
        float glow = 0.55f + 0.45f * (float) Math.sin(time * 2.7f + d.phase);
        paint.setColor(Color.argb((int) (30 + 35 * glow), 255, 236, 151));
        canvas.drawCircle(x, y, 12f, paint);
        paint.setColor(Color.argb((int) (145 + 90 * glow), 255, 242, 171));
        canvas.drawCircle(x, y, 2.6f, paint);
    }

    private void drawObstacle(Canvas canvas, LevelData.Obstacle o, LevelData level, float time) {
        switch (o.kind) {
            case LevelData.POND:
                drawPond(canvas, o, time);
                break;
            case LevelData.BUSH:
                drawBush(canvas, o, level.accent);
                break;
            case LevelData.TREE:
                drawTree(canvas, o, level.accent);
                break;
            case LevelData.ROCK:
                drawRock(canvas, o);
                break;
            case LevelData.LAVENDER:
                drawLavender(canvas, o, time);
                break;
            default:
                break;
        }
    }

    private void drawPond(Canvas canvas, LevelData.Obstacle o, float time) {
        rect.set(o.x - o.radiusX - 10f, o.y - o.radiusY + 9f,
                o.x + o.radiusX + 10f, o.y + o.radiusY + 22f);
        paint.setColor(0x20504B69);
        canvas.drawOval(rect, paint);

        rect.set(o.x - o.radiusX, o.y - o.radiusY, o.x + o.radiusX, o.y + o.radiusY);
        paint.setColor(0xFF9FCBD4);
        canvas.drawOval(rect, paint);
        rect.inset(8f, 8f);
        paint.setColor(0xFFB9DCE0);
        canvas.drawOval(rect, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3f);
        paint.setColor(0x78F8FFFF);
        float wave = (float) Math.sin(time * 1.2f) * 9f;
        canvas.drawArc(o.x - 60f + wave, o.y - 28f, o.x + 5f + wave, o.y - 8f,
                195f, 130f, false, paint);
        canvas.drawArc(o.x - 5f - wave, o.y + 15f, o.x + 65f - wave, o.y + 38f,
                15f, 135f, false, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0xFF86B98D);
        canvas.drawOval(o.x + o.radiusX * 0.22f, o.y - 5f,
                o.x + o.radiusX * 0.47f, o.y + 15f, paint);
        paint.setColor(0xFFF5C4C8);
        canvas.drawCircle(o.x + o.radiusX * 0.35f, o.y - 2f, 6f, paint);
    }

    private void drawBush(Canvas canvas, LevelData.Obstacle o, int accent) {
        paint.setColor(0x25504B62);
        canvas.drawOval(o.x - o.radiusX, o.y + o.radiusY * 0.45f,
                o.x + o.radiusX, o.y + o.radiusY * 1.05f, paint);
        paint.setColor(0xFF719E7B);
        canvas.drawCircle(o.x - o.radiusX * 0.45f, o.y + 4f, o.radiusY * 0.65f, paint);
        canvas.drawCircle(o.x + o.radiusX * 0.42f, o.y + 7f, o.radiusY * 0.62f, paint);
        paint.setColor(0xFF82AD83);
        canvas.drawCircle(o.x, o.y - o.radiusY * 0.25f, o.radiusY * 0.78f, paint);
        paint.setColor(withAlpha(accent, 235));
        canvas.drawCircle(o.x - 22f, o.y - 10f, 5f, paint);
        canvas.drawCircle(o.x + 25f, o.y + 1f, 4f, paint);
        canvas.drawCircle(o.x + 2f, o.y - 25f, 4f, paint);
    }

    private void drawTree(Canvas canvas, LevelData.Obstacle o, int accent) {
        paint.setColor(0x25504B62);
        canvas.drawOval(o.x - o.radiusX * 1.1f, o.y + o.radiusY * 0.65f,
                o.x + o.radiusX * 1.1f, o.y + o.radiusY * 1.18f, paint);
        paint.setColor(0xFF9B735F);
        rect.set(o.x - 10f, o.y - 4f, o.x + 10f, o.y + o.radiusY * 0.8f);
        canvas.drawRoundRect(rect, 7f, 7f, paint);
        paint.setColor(0xFF75967A);
        canvas.drawCircle(o.x - 25f, o.y - 21f, o.radiusY * 0.69f, paint);
        canvas.drawCircle(o.x + 27f, o.y - 18f, o.radiusY * 0.66f, paint);
        paint.setColor(0xFF88AA82);
        canvas.drawCircle(o.x, o.y - 43f, o.radiusY * 0.82f, paint);
        paint.setColor(withAlpha(accent, 230));
        canvas.drawCircle(o.x - 18f, o.y - 43f, 6f, paint);
        canvas.drawCircle(o.x + 24f, o.y - 33f, 5f, paint);
    }

    private void drawRock(Canvas canvas, LevelData.Obstacle o) {
        paint.setColor(0x24504B62);
        canvas.drawOval(o.x - o.radiusX, o.y + 4f,
                o.x + o.radiusX, o.y + o.radiusY * 1.12f, paint);
        paint.setColor(0xFFAAA7B4);
        path.reset();
        path.moveTo(o.x - o.radiusX, o.y + o.radiusY * 0.45f);
        path.quadTo(o.x - o.radiusX * 0.72f, o.y - o.radiusY,
                o.x - o.radiusX * 0.05f, o.y - o.radiusY * 0.9f);
        path.quadTo(o.x + o.radiusX * 0.82f, o.y - o.radiusY * 0.65f,
                o.x + o.radiusX, o.y + o.radiusY * 0.5f);
        path.quadTo(o.x, o.y + o.radiusY, o.x - o.radiusX, o.y + o.radiusY * 0.45f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0x70E9E4EC);
        path.reset();
        path.moveTo(o.x - o.radiusX * 0.55f, o.y - o.radiusY * 0.28f);
        path.quadTo(o.x - 5f, o.y - o.radiusY * 0.9f, o.x + o.radiusX * 0.42f, o.y - o.radiusY * 0.38f);
        path.quadTo(o.x, o.y - 5f, o.x - o.radiusX * 0.55f, o.y - o.radiusY * 0.28f);
        canvas.drawPath(path, paint);
    }

    private void drawLavender(Canvas canvas, LevelData.Obstacle o, float time) {
        paint.setColor(0x22504B62);
        canvas.drawOval(o.x - o.radiusX, o.y + 10f, o.x + o.radiusX, o.y + o.radiusY, paint);
        for (int i = -4; i <= 4; i++) {
            float x = o.x + i * 17f;
            float height = 34f + (i & 1) * 12f;
            float sway = (float) Math.sin(time * 1.3f + i) * 2f;
            paint.setColor(0xFF708F72);
            paint.setStrokeWidth(3f);
            canvas.drawLine(x, o.y + 30f, x + sway, o.y - height, paint);
            paint.setColor(i % 2 == 0 ? 0xFFC3ADDE : 0xFFAD9ACF);
            for (int j = 0; j < 4; j++) {
                canvas.drawCircle(x + sway + (j % 2 == 0 ? -3f : 3f),
                        o.y - height + j * 8f, 5f, paint);
            }
        }
    }

    private void drawHome(Canvas canvas, float x, float y, boolean unlocked, float time) {
        if (unlocked) {
            float glow = 1f + 0.08f * (float) Math.sin(time * 2f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setColor(0x40FFE7A3);
            canvas.drawCircle(x, y + 10f, 74f * glow, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x28FFE8A6);
            canvas.drawCircle(x, y + 10f, 62f, paint);
        }
        paint.setColor(0x28504B62);
        canvas.drawOval(x - 66f, y + 37f, x + 66f, y + 66f, paint);
        paint.setColor(unlocked ? 0xFFFFEFE2 : 0xD9E7DED9);
        rect.set(x - 50f, y - 4f, x + 50f, y + 53f);
        canvas.drawRoundRect(rect, 12f, 12f, paint);
        paint.setColor(unlocked ? 0xFFE89A86 : 0xFFAAA3AE);
        path.reset();
        path.moveTo(x - 66f, y + 5f);
        path.lineTo(x, y - 55f);
        path.lineTo(x + 66f, y + 5f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(unlocked ? 0xFF76647B : 0xFF85818A);
        rect.set(x - 17f, y + 17f, x + 17f, y + 53f);
        canvas.drawRoundRect(rect, 14f, 14f, paint);
        paint.setColor(0xAFFFF7D7);
        canvas.drawCircle(x + 28f, y + 18f, 8f, paint);
        if (unlocked) {
            paint.setColor(0xFFFFDB79);
            canvas.drawCircle(x, y + 36f, 4f, paint);
        }
    }

    private void drawStarPickup(Canvas canvas, float x, float y, float time) {
        float pulse = 1f + 0.08f * (float) Math.sin(time * 3f);
        paint.setColor(0x24FFF2B2);
        canvas.drawCircle(x, y, 42f * pulse, paint);
        paint.setColor(0x45FFE9A0);
        canvas.drawCircle(x, y, 28f * pulse, paint);
        canvas.save();
        canvas.rotate((float) Math.sin(time * 0.7f) * 5f, x, y);
        drawStarShape(canvas, x, y, 19f * pulse, 9f * pulse, 0xFFFFD875);
        canvas.restore();
        paint.setColor(0xCFFFF8D9);
        canvas.drawCircle(x - 5f, y - 6f, 2.6f, paint);
    }

    private void drawTargetHint(Canvas canvas, float time) {
        if (!world.isPointerDown() || world.state() != GameWorld.State.PLAYING) {
            return;
        }
        float x = world.targetX();
        float y = world.targetY();
        float pulse = 0.82f + 0.18f * (float) Math.sin(time * 5f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0x70FFF8EA);
        canvas.drawCircle(x, y, 23f * pulse, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xAFFFF8EA);
        canvas.drawCircle(x - 7f, y + 3f, 5f, paint);
        canvas.drawCircle(x + 7f, y + 3f, 5f, paint);
        canvas.drawCircle(x - 9f, y - 7f, 4f, paint);
        canvas.drawCircle(x + 9f, y - 7f, 4f, paint);
        canvas.drawOval(x - 8f, y + 3f, x + 8f, y + 17f, paint);
    }

    private void drawKitten(Canvas canvas, float time) {
        float speed = (float) Math.hypot(world.velocityX(), world.velocityY());
        if (Math.abs(world.velocityX()) > 8f) {
            facing = world.velocityX() >= 0f ? 1f : -1f;
        }
        canvas.save();
        canvas.translate(world.kittenX(), world.kittenY());
        drawKittenAtOrigin(canvas, time, facing, speed > 12f);
        canvas.restore();
    }

    private void drawKittenAtOrigin(Canvas canvas, float time, float direction, boolean moving) {
        float bob = moving ? (float) Math.sin(time * 12f) * 2.2f : (float) Math.sin(time * 2f) * 0.7f;
        float step = moving ? (float) Math.sin(time * 12f) * 5f : 0f;

        paint.setColor(0x28504B62);
        canvas.drawOval(-38f, 25f, 36f, 43f, paint);

        canvas.save();
        canvas.translate(0f, bob);
        canvas.scale(direction, 1f);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(12f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFE99B78);
        path.reset();
        path.moveTo(-25f, 2f);
        path.cubicTo(-52f, -12f, -53f, 18f, -41f, 20f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0xFFF0A582);
        canvas.drawOval(-29f, -17f, 28f, 29f, paint);
        paint.setColor(0xFFF7C6AA);
        canvas.drawOval(-4f, -8f, 31f, 26f, paint);

        paint.setColor(0xFFE69673);
        canvas.drawOval(-23f + step * 0.25f, 19f, -7f + step * 0.25f, 36f, paint);
        canvas.drawOval(10f - step * 0.25f, 19f, 27f - step * 0.25f, 36f, paint);

        paint.setColor(0xFFF0A582);
        canvas.drawCircle(25f, -20f, 29f, paint);
        path.reset();
        path.moveTo(3f, -35f);
        path.lineTo(9f, -64f);
        path.lineTo(27f, -44f);
        path.close();
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(33f, -45f);
        path.lineTo(52f, -61f);
        path.lineTo(48f, -31f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFFF8C4B5);
        path.reset();
        path.moveTo(10f, -43f);
        path.lineTo(12f, -56f);
        path.lineTo(21f, -46f);
        path.close();
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(39f, -46f);
        path.lineTo(49f, -54f);
        path.lineTo(46f, -40f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFFFFE8D7);
        canvas.drawOval(10f, -24f, 48f, 7f, paint);
        paint.setColor(0xFF5E5872);
        canvas.drawOval(16f, -25f, 21f, -17f, paint);
        canvas.drawOval(36f, -25f, 41f, -17f, paint);
        paint.setColor(0xFFD88391);
        path.reset();
        path.moveTo(26f, -13f);
        path.lineTo(33f, -13f);
        path.lineTo(29.5f, -8f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0x66E893A2);
        canvas.drawCircle(12f, -9f, 5f, paint);
        canvas.drawCircle(47f, -9f, 5f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x995E5872);
        canvas.drawLine(8f, -11f, -6f, -15f, paint);
        canvas.drawLine(9f, -5f, -5f, -4f, paint);
        canvas.drawLine(49f, -11f, 61f, -16f, paint);
        canvas.drawLine(49f, -5f, 62f, -4f, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0x55C87862);
        rect.set(-7f, -19f, 1f, 13f);
        canvas.drawRoundRect(rect, 4f, 4f, paint);
        rect.set(-19f, -14f, -12f, 10f);
        canvas.drawRoundRect(rect, 4f, 4f, paint);
        canvas.restore();
    }

    private void drawHud(Canvas canvas, float time) {
        LevelData level = world.currentLevel();
        float left = 34f + safeLeftWorld;
        drawPill(canvas, left, 24f, left + 190f, 80f, 0xC8FFF9EF, 0x12504B62);
        for (int i = 0; i < 3; i++) {
            int color = world.isStarCollected(i) ? 0xFFFFD875 : 0x80B7ADBD;
            drawStarShape(canvas, left + 45f + i * 50f, 52f, 14f, 6.5f, color);
        }

        drawPill(canvas, 470f, 24f, 810f, 78f, 0xBFFFF9EF, 0x10504B62);
        drawFittedText(canvas, text(level.nameRes),
                640f, 59f, 24f, 300f, 0xFF625A71, true);

        pauseX = GameWorld.WORLD_WIDTH - 50f - safeRightWorld;
        pauseY = 52f;
        paint.setColor(0xC8FFF9EF);
        canvas.drawCircle(pauseX, pauseY, PAUSE_RADIUS, paint);
        paint.setColor(0xFF6A627A);
        rect.set(pauseX - 10f, pauseY - 11f, pauseX - 4f, pauseY + 11f);
        canvas.drawRoundRect(rect, 3f, 3f, paint);
        rect.set(pauseX + 4f, pauseY - 11f, pauseX + 10f, pauseY + 11f);
        canvas.drawRoundRect(rect, 3f, 3f, paint);

        if (world.allStarsCollected() && world.state() == GameWorld.State.PLAYING) {
            float alpha = 0.75f + 0.25f * (float) Math.sin(time * 3f);
            drawFittedText(canvas, text(R.string.home_is_waiting),
                    640f, 686f, 20f, 500f, Color.argb((int) (alpha * 255), 255, 249, 235), true);
        }
    }

    private void drawHint(Canvas canvas, float alpha) {
        int a = (int) (215 * alpha);
        drawPill(canvas, 355f, 610f, 925f, 666f, Color.argb(a, 255, 250, 240), 0x10504B62);
        drawFittedText(canvas, text(R.string.hold_to_walk),
                640f, 647f, 21f, 520f, Color.argb((int) (240 * alpha), 98, 90, 113), true);
    }

    private void drawOverlay(Canvas canvas, int titleRes, int subtitleRes, float time, boolean finalCard) {
        paint.setColor(0x68514B68);
        canvas.drawRect(0f, 0f, GameWorld.WORLD_WIDTH, GameWorld.WORLD_HEIGHT, paint);
        float pulse = 1f + 0.012f * (float) Math.sin(time * 2.2f);
        canvas.save();
        canvas.scale(pulse, pulse, 640f, 360f);
        drawPill(canvas, 345f, 245f, 935f, 475f, 0xF8FFF8EE, 0x22504B62);
        if (!finalCard) {
            drawStarShape(canvas, 640f, 300f, 27f, 13f, 0xFFFFD875);
        }
        drawFittedText(canvas, text(titleRes),
                640f, 370f, 43f, 500f, 0xFF665D78, true);
        drawFittedText(canvas, text(subtitleRes),
                640f, 423f, 22f, 460f, 0xFF8B8296, false);
        if (world.state() == GameWorld.State.PAUSED) {
            drawLanguageSwitch(canvas, 640f, 465f);
        }
        canvas.restore();
    }

    private void drawQuestComplete(Canvas canvas, float time) {
        paint.setColor(0x7B514B68);
        canvas.drawRect(0f, 0f, GameWorld.WORLD_WIDTH, GameWorld.WORLD_HEIGHT, paint);
        drawNightStars(canvas, time, 30, 0.45f);
        drawPill(canvas, 255f, 118f, 1025f, 606f, 0xF5FFF8EE, 0x22504B62);

        canvas.save();
        canvas.translate(640f, 295f);
        canvas.scale(1.35f, 1.35f);
        canvas.rotate(-8f);
        drawKittenAtOrigin(canvas, time, 1f, false);
        canvas.restore();

        for (int i = 0; i < 3; i++) {
            drawStarShape(canvas, 580f + i * 60f, 195f + Math.abs(i - 1) * 15f,
                    19f, 9f, 0xFFFFD875);
        }
        drawFittedText(canvas, text(R.string.quest_complete),
                640f, 420f, 48f, 660f, 0xFF665D78, true);
        drawFittedText(canvas, text(R.string.quest_complete_subtitle),
                640f, 470f, 22f, 650f, 0xFF8B8296, false);
        drawPill(canvas, 435f, 510f, 845f, 572f, 0xFFE9DDEA, 0x10504B62);
        drawFittedText(canvas, text(R.string.play_again),
                640f, 550f, 22f, 360f, 0xFF665D78, true);
    }

    private void drawNightStars(Canvas canvas, float time, int count, float alphaScale) {
        for (int i = 0; i < count; i++) {
            float x = ((i * 193f + 71f) % 1240f) + 20f;
            float y = ((i * 109f + 37f) % 470f) + 18f;
            float twinkle = 0.45f + 0.55f * (float) Math.sin(time * (0.8f + i % 5 * 0.11f) + i);
            int alpha = (int) ((65f + twinkle * 150f) * alphaScale);
            paint.setColor(Color.argb(alpha, 255, 246, 213));
            canvas.drawCircle(x, y, 1.5f + i % 3, paint);
        }
    }

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                          int fill, int shadow) {
        paint.setColor(shadow);
        rect.set(left + 2f, top + 5f, right + 2f, bottom + 5f);
        canvas.drawRoundRect(rect, (bottom - top) / 2f, (bottom - top) / 2f, paint);
        paint.setColor(fill);
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, (bottom - top) / 2f, (bottom - top) / 2f, paint);
    }

    private void drawLanguageSwitch(Canvas canvas, float centerX, float centerY) {
        float left = centerX - 78f;
        float top = centerY - 26f;
        drawPill(canvas, left, top, centerX + 78f, centerY + 26f, 0xEFFFF9EF, 0x18504B62);
        paint.setColor("en".equals(language) ? 0xFFFFD98A : 0x00FFFFFF);
        rect.set(left + 5f, top + 5f, centerX - 2f, centerY + 21f);
        canvas.drawRoundRect(rect, 20f, 20f, paint);
        paint.setColor("ru".equals(language) ? 0xFFFFD98A : 0x00FFFFFF);
        rect.set(centerX + 2f, top + 5f, centerX + 73f, centerY + 21f);
        canvas.drawRoundRect(rect, 20f, 20f, paint);
        drawFittedText(canvas, "EN", centerX - 39f, centerY + 8f,
                20f, 55f, 0xFF665D78, true);
        drawFittedText(canvas, "RU", centerX + 39f, centerY + 8f,
                20f, 55f, 0xFF665D78, true);
    }

    private void drawStarShape(Canvas canvas, float x, float y, float outer, float inner, int color) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float radius = (i & 1) == 0 ? outer : inner;
            float px = x + (float) Math.cos(angle) * radius;
            float py = y + (float) Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.close();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    private void drawFittedText(Canvas canvas, String text, float centerX, float baseline,
                                float preferredSize, float maxWidth, int color, boolean useBold) {
        paint.setTypeface(useBold ? bold : regular);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(preferredSize);
        paint.setColor(color);
        float measured = paint.measureText(text);
        if (measured > maxWidth && measured > 0f) {
            paint.setTextSize(preferredSize * maxWidth / measured);
        }
        canvas.drawText(text, centerX, baseline, paint);
    }

    private void spawnStarBurst(float x, float y) {
        Random random = new Random((long) (x * 31f + y * 17f + world.journeyTime() * 1000f));
        int[] colors = {0xFFFFD875, 0xFFFFF1BE, 0xFFF2B7C2, 0xFFD3C6EA};
        for (int i = 0; i < 24; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            float speed = 55f + random.nextFloat() * 105f;
            particles.add(new Particle(
                    x, y,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 24f,
                    0.65f + random.nextFloat() * 0.45f,
                    3f + random.nextFloat() * 5f,
                    colors[i % colors.length]
            ));
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                particles.remove(i);
                continue;
            }
            p.x += p.velocityX * dt;
            p.y += p.velocityY * dt;
            p.velocityY += 55f * dt;
            p.velocityX *= Math.pow(0.3f, dt);
        }
    }

    private void drawParticles(Canvas canvas) {
        for (Particle p : particles) {
            float alpha = Math.min(1f, p.life / p.maxLife * 1.8f);
            paint.setColor(withAlpha(p.color, (int) (alpha * 255f)));
            drawStarShape(canvas, p.x, p.y, p.size, p.size * 0.45f, paint.getColor());
        }
    }

    private void rebuildScene() {
        LevelData level = world.currentLevel();
        levelGradient = new LinearGradient(
                0f, 0f, 0f, GameWorld.WORLD_HEIGHT,
                new int[]{level.skyTop, level.groundLight, level.ground},
                new float[]{0f, 0.22f, 1f}, Shader.TileMode.CLAMP
        );
        decorations.clear();
        particles.clear();
        for (int i = 0; i < visualCollected.length; i++) {
            visualCollected[i] = false;
        }
        Random random = new Random(level.seed);
        int accepted = 0;
        int attempts = 0;
        while (accepted < 72 && attempts++ < 700) {
            float x = 45f + random.nextFloat() * (GameWorld.WORLD_WIDTH - 90f);
            float y = 95f + random.nextFloat() * (GameWorld.WORLD_HEIGHT - 145f);
            if (!level.isCircleFree(x, y, 10f)
                    || distance(x, y, level.startX, level.startY) < 72f
                    || distance(x, y, level.homeX, level.homeY) < 82f) {
                continue;
            }
            int kind;
            if (accepted % 9 == 0) {
                kind = Decoration.FIREFLY;
            } else if (accepted % 3 == 0) {
                kind = Decoration.FLOWER;
            } else {
                kind = Decoration.GRASS;
            }
            int color;
            if (kind == Decoration.FLOWER) {
                int[] flowerColors = {0xFFF2B7C2, 0xFFD7C2EB, 0xFFFFE0A8, 0xFFF7F1E0};
                color = flowerColors[accepted % flowerColors.length];
            } else {
                color = withAlpha(darken(level.ground, 0.78f), kind == Decoration.GRASS ? 85 : 255);
            }
            decorations.add(new Decoration(
                    kind, x, y, 5f + random.nextFloat() * 7f, color,
                    random.nextFloat() * (float) Math.PI * 2f
            ));
            accepted++;
        }
        rebuildLevelBackground();
        hintTime = 0f;
    }

    private void rebuildTitleBackground() {
        if (titleBackground != null) {
            titleBackground.recycle();
        }
        titleBackground = createBackgroundBitmap();
        Canvas canvas = new Canvas(titleBackground);
        paint.setShader(titleGradient);
        canvas.drawRect(0f, 0f, GameWorld.WORLD_WIDTH, GameWorld.WORLD_HEIGHT, paint);
        paint.setShader(null);
        drawNightStars(canvas, 0f, 54, 0.85f);

        paint.setColor(0x405B547B);
        path.reset();
        path.moveTo(0f, 520f);
        path.cubicTo(210f, 390f, 370f, 520f, 570f, 415f);
        path.cubicTo(760f, 315f, 960f, 455f, 1280f, 335f);
        path.lineTo(1280f, 720f);
        path.lineTo(0f, 720f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFF9EBEA7);
        path.reset();
        path.moveTo(0f, 600f);
        path.cubicTo(290f, 455f, 520f, 655f, 790f, 510f);
        path.cubicTo(1020f, 390f, 1140f, 535f, 1280f, 480f);
        path.lineTo(1280f, 720f);
        path.lineTo(0f, 720f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void rebuildLevelBackground() {
        if (levelBackground != null) {
            levelBackground.recycle();
        }
        levelBackground = createBackgroundBitmap();
        Canvas canvas = new Canvas(levelBackground);
        LevelData level = world.currentLevel();
        paint.setShader(levelGradient);
        canvas.drawRect(0f, 0f, GameWorld.WORLD_WIDTH, GameWorld.WORLD_HEIGHT, paint);
        paint.setShader(null);
        drawCloudShadows(canvas, 0f, level.seed);
        drawTrail(canvas, level);
        drawStaticDecorations(canvas);
        for (LevelData.Obstacle obstacle : level.obstacles) {
            drawObstacle(canvas, obstacle, level, 0f);
        }
    }

    private static Bitmap createBackgroundBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(
                (int) GameWorld.WORLD_WIDTH,
                (int) GameWorld.WORLD_HEIGHT,
                Bitmap.Config.ARGB_8888
        );
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        return bitmap;
    }

    private void recycleBackgrounds() {
        if (levelBackground != null) {
            levelBackground.recycle();
            levelBackground = null;
        }
        if (titleBackground != null) {
            titleBackground.recycle();
            titleBackground = null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        float x = (event.getX(index) - viewOffsetX) / viewScale;
        float y = (event.getY(index) - viewOffsetY) / viewScale;

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                return true;
            }
            performClick();
            switch (world.state()) {
                case TITLE:
                    if (isInsideLanguageSwitch(x, y,
                            GameWorld.WORLD_WIDTH - 104f - safeRightWorld, 53f)) {
                        toggleLanguage();
                    } else {
                        int savedLevel = preferences.getInt(PREF_RESUME_LEVEL, 0);
                        world.startJourney(savedLevel);
                        rebuildScene();
                    }
                    break;
                case PAUSED:
                    if (isInsideLanguageSwitch(x, y, 640f, 465f)) {
                        toggleLanguage();
                    } else {
                        world.resume();
                    }
                    break;
                case LEVEL_COMPLETE:
                    int previousLevel = world.levelIndex();
                    world.continueAfterLevel();
                    if (world.state() == GameWorld.State.PLAYING && world.levelIndex() != previousLevel) {
                        rebuildScene();
                    }
                    break;
                case QUEST_COMPLETE:
                    preferences.edit().putInt(PREF_RESUME_LEVEL, 0).apply();
                    world.restartJourney();
                    rebuildScene();
                    break;
                case PLAYING:
                    if (distance(x, y, pauseX, pauseY) <= PAUSE_RADIUS + 18f) {
                        world.pause();
                    } else {
                        world.setPointer(x, y, true);
                    }
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE && world.state() == GameWorld.State.PLAYING) {
            int pointerIndex = event.findPointerIndex(event.getPointerId(0));
            if (pointerIndex >= 0) {
                x = (event.getX(pointerIndex) - viewOffsetX) / viewScale;
                y = (event.getY(pointerIndex) - viewOffsetY) / viewScale;
                world.setPointer(x, y, true);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            world.setPointer(x, y, false);
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    boolean handleBack() {
        switch (world.state()) {
            case PLAYING:
                world.pause();
                invalidate();
                return true;
            case PAUSED:
            case LEVEL_COMPLETE:
            case QUEST_COMPLETE:
                world.showTitle();
                invalidate();
                return true;
            case TITLE:
            default:
                return false;
        }
    }

    void onHostPause() {
        hostResumed = false;
        world.pause();
        lastFrameNanos = 0L;
    }

    void onHostResume() {
        hostResumed = true;
        lastFrameNanos = 0L;
        postInvalidateOnAnimation();
    }

    void close() {
        hostResumed = false;
        audio.close();
        recycleBackgrounds();
    }

    @Override
    public void onStarCollected(int collectedCount) {
        LevelData level = world.currentLevel();
        for (int i = 0; i < visualCollected.length; i++) {
            if (world.isStarCollected(i) && !visualCollected[i]) {
                visualCollected[i] = true;
                spawnStarBurst(level.stars[i][0], level.stars[i][1]);
                break;
            }
        }
        audio.playStar(collectedCount);
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        announceForAccessibility(text(R.string.accessibility_star_found));
    }

    @Override
    public void onLevelComplete(int completedLevel) {
        int next = Math.min(world.levelCount() - 1, completedLevel + 1);
        preferences.edit().putInt(PREF_RESUME_LEVEL, next).apply();
        spawnStarBurst(world.currentLevel().homeX, world.currentLevel().homeY);
        audio.playLevelComplete();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        announceForAccessibility(text(R.string.accessibility_level_complete));
        sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT);
    }

    @Override
    public void onJourneyComplete() {
        preferences.edit().putInt(PREF_RESUME_LEVEL, 0).apply();
        audio.playJourneyComplete();
    }

    private void updateTransform(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        viewScale = Math.min(width / GameWorld.WORLD_WIDTH, height / GameWorld.WORLD_HEIGHT);
        viewOffsetX = (width - GameWorld.WORLD_WIDTH * viewScale) / 2f;
        viewOffsetY = (height - GameWorld.WORLD_HEIGHT * viewScale) / 2f;
    }

    private void installInsetListener() {
        setOnApplyWindowInsetsListener((view, insets) -> {
            int safeLeft = 0;
            int safeRight = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    safeLeft = cutout.getSafeInsetLeft();
                    safeRight = cutout.getSafeInsetRight();
                }
            }
            safeLeftWorld = safeLeft / Math.max(0.01f, viewScale);
            safeRightWorld = safeRight / Math.max(0.01f, viewScale);
            invalidate();
            return insets;
        });
        requestApplyInsets();
    }

    private boolean isInsideLanguageSwitch(float x, float y, float centerX, float centerY) {
        return x >= centerX - 88f && x <= centerX + 88f
                && y >= centerY - 36f && y <= centerY + 36f;
    }

    private void toggleLanguage() {
        language = "en".equals(language) ? "ru" : "en";
        preferences.edit().putString(PREF_LANGUAGE, language).apply();
        applyLanguage(language);
        setContentDescription(text(R.string.accessibility_game));
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        invalidate();
    }

    private void applyLanguage(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        localizedContext = getContext().createConfigurationContext(configuration);
    }

    private String text(int resourceId) {
        return localizedContext.getString(resourceId);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int darken(int color, float amount) {
        int r = Math.round(Color.red(color) * amount);
        int g = Math.round(Color.green(color) * amount);
        int b = Math.round(Color.blue(color) * amount);
        return Color.rgb(r, g, b);
    }

    private static float distance(float ax, float ay, float bx, float by) {
        return (float) Math.hypot(bx - ax, by - ay);
    }

    private static final class Decoration {
        static final int GRASS = 0;
        static final int FLOWER = 1;
        static final int FIREFLY = 2;

        final int kind;
        final float x;
        final float y;
        final float size;
        final int color;
        final float phase;

        Decoration(int kind, float x, float y, float size, int color, float phase) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
            this.phase = phase;
        }
    }

    private static final class Particle {
        float x;
        float y;
        float velocityX;
        float velocityY;
        float life;
        final float maxLife;
        final float size;
        final int color;

        Particle(float x, float y, float velocityX, float velocityY,
                 float life, float size, int color) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.color = color;
        }
    }
}
