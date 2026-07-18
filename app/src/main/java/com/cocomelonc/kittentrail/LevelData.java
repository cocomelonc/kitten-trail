/*
 * Kitten Trail
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittentrail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, resolution-independent level data. All coordinates use the 1280x720 game world. */
final class LevelData {
    static final int POND = 0;
    static final int BUSH = 1;
    static final int TREE = 2;
    static final int ROCK = 3;
    static final int LAVENDER = 4;

    final int nameRes;
    final int skyTop;
    final int skyBottom;
    final int ground;
    final int groundLight;
    final int accent;
    final int seed;
    final float startX;
    final float startY;
    final float homeX;
    final float homeY;
    final float[] path;
    final float[][] stars;
    final List<Obstacle> obstacles;

    private LevelData(
            int nameRes,
            int skyTop,
            int skyBottom,
            int ground,
            int groundLight,
            int accent,
            int seed,
            float startX,
            float startY,
            float homeX,
            float homeY,
            float[] path,
            float[][] stars,
            Obstacle... obstacles
    ) {
        this.nameRes = nameRes;
        this.skyTop = skyTop;
        this.skyBottom = skyBottom;
        this.ground = ground;
        this.groundLight = groundLight;
        this.accent = accent;
        this.seed = seed;
        this.startX = startX;
        this.startY = startY;
        this.homeX = homeX;
        this.homeY = homeY;
        this.path = path.clone();
        this.stars = copyPoints(stars);
        ArrayList<Obstacle> copy = new ArrayList<>();
        Collections.addAll(copy, obstacles);
        this.obstacles = Collections.unmodifiableList(copy);
    }

    static LevelData[] createAll() {
        return new LevelData[]{
                meadow(), lavender(), pond(), orchard(), hill(),
                cloudValley(), rosePath(), mintBrook(), goldenTwilight()
        };
    }

    boolean isCircleFree(float x, float y, float radius) {
        if (x - radius < 42f || x + radius > GameWorld.WORLD_WIDTH - 42f
                || y - radius < 76f || y + radius > GameWorld.WORLD_HEIGHT - 42f) {
            return false;
        }
        for (Obstacle obstacle : obstacles) {
            if (obstacle.collidesCircle(x, y, radius)) {
                return false;
            }
        }
        return true;
    }

    private static LevelData meadow() {
        return new LevelData(
                R.string.level_1,
                0xFFF8E9E7, 0xFFE7DFF2, 0xFFBFD9B4, 0xFFDCE8C8, 0xFFF4C7A5, 1103,
                130f, 575f, 1115f, 145f,
                new float[]{130f, 575f, 410f, 620f, 805f, 125f, 1115f, 145f},
                new float[][]{{330f, 520f}, {650f, 470f}, {930f, 230f}},
                new Obstacle(POND, 440f, 280f, 120f, 78f),
                new Obstacle(BUSH, 785f, 560f, 76f, 48f),
                new Obstacle(TREE, 1000f, 510f, 54f, 44f),
                new Obstacle(ROCK, 190f, 240f, 46f, 31f)
        );
    }

    private static LevelData lavender() {
        return new LevelData(
                R.string.level_2,
                0xFFF7EAF4, 0xFFDCDCF2, 0xFFC5D4B7, 0xFFE3E1C8, 0xFFB8A3D8, 2417,
                125f, 360f, 1120f, 355f,
                new float[]{125f, 360f, 410f, 120f, 805f, 610f, 1120f, 355f},
                new float[][]{{320f, 175f}, {640f, 540f}, {940f, 165f}},
                new Obstacle(LAVENDER, 440f, 390f, 85f, 50f),
                new Obstacle(POND, 720f, 230f, 105f, 68f),
                new Obstacle(TREE, 945f, 520f, 55f, 45f),
                new Obstacle(BUSH, 240f, 565f, 65f, 42f)
        );
    }

    private static LevelData pond() {
        return new LevelData(
                R.string.level_3,
                0xFFDBDDF1, 0xFFBECFE2, 0xFFAFCBB8, 0xFFD6DFCB, 0xFF8FC7D0, 3701,
                145f, 585f, 1115f, 125f,
                new float[]{145f, 585f, 470f, 700f, 920f, 520f, 1115f, 125f},
                new float[][]{{300f, 500f}, {680f, 590f}, {970f, 260f}},
                new Obstacle(POND, 625f, 310f, 225f, 150f),
                new Obstacle(ROCK, 345f, 205f, 52f, 34f),
                new Obstacle(BUSH, 1015f, 520f, 72f, 46f),
                new Obstacle(TREE, 1080f, 350f, 52f, 43f)
        );
    }

    private static LevelData orchard() {
        return new LevelData(
                R.string.level_4,
                0xFFFFECE3, 0xFFF2D7D1, 0xFFC9D2A8, 0xFFE7DEB7, 0xFFF0A58D, 4933,
                130f, 145f, 1115f, 570f,
                new float[]{130f, 145f, 430f, 120f, 750f, 660f, 1115f, 570f},
                new float[][]{{355f, 225f}, {645f, 505f}, {935f, 345f}},
                new Obstacle(TREE, 500f, 350f, 62f, 50f),
                new Obstacle(TREE, 805f, 190f, 59f, 48f),
                new Obstacle(TREE, 910f, 570f, 58f, 47f),
                new Obstacle(POND, 270f, 500f, 92f, 58f)
        );
    }

    private static LevelData hill() {
        return new LevelData(
                R.string.level_5,
                0xFFCBC8E4, 0xFFAFC4D4, 0xFF9EBEA7, 0xFFC8D8BD, 0xFFF6D47B, 6173,
                140f, 585f, 1095f, 125f,
                new float[]{140f, 585f, 420f, 510f, 780f, 210f, 1095f, 125f},
                new float[][]{{345f, 455f}, {650f, 290f}, {895f, 185f}},
                new Obstacle(ROCK, 480f, 190f, 76f, 49f),
                new Obstacle(POND, 785f, 505f, 120f, 72f),
                new Obstacle(BUSH, 1035f, 390f, 70f, 46f),
                new Obstacle(TREE, 250f, 285f, 54f, 45f)
        );
    }

    private static LevelData cloudValley() {
        return new LevelData(
                R.string.level_6,
                0xFFF7F1EA, 0xFFDDEAF4, 0xFFC5DCC7, 0xFFE5EEDB, 0xFFA9C7E8, 7289,
                140f, 130f, 1110f, 580f,
                new float[]{140f, 130f, 390f, 80f, 760f, 650f, 1110f, 580f},
                new float[][]{{330f, 215f}, {635f, 480f}, {925f, 525f}},
                new Obstacle(POND, 500f, 350f, 135f, 80f),
                new Obstacle(ROCK, 800f, 190f, 60f, 38f),
                new Obstacle(BUSH, 870f, 410f, 70f, 45f),
                new Obstacle(TREE, 300f, 520f, 55f, 45f)
        );
    }

    private static LevelData rosePath() {
        return new LevelData(
                R.string.level_7,
                0xFFFFF1F2, 0xFFF1DDE5, 0xFFC9D7B8, 0xFFE8E4CE, 0xFFECAFC1, 8363,
                140f, 570f, 1120f, 560f,
                new float[]{140f, 570f, 380f, 120f, 810f, 120f, 1120f, 560f},
                new float[][]{{325f, 395f}, {645f, 170f}, {945f, 390f}},
                new Obstacle(LAVENDER, 480f, 520f, 90f, 52f),
                new Obstacle(POND, 750f, 430f, 110f, 65f),
                new Obstacle(BUSH, 960f, 190f, 70f, 45f),
                new Obstacle(ROCK, 250f, 210f, 55f, 35f)
        );
    }

    private static LevelData mintBrook() {
        return new LevelData(
                R.string.level_8,
                0xFFEAF5F0, 0xFFD4E8E4, 0xFFB4D4C6, 0xFFDCEBDD, 0xFF91CFC1, 9479,
                125f, 340f, 1125f, 180f,
                new float[]{125f, 340f, 430f, 650f, 800f, 80f, 1125f, 180f},
                new float[][]{{330f, 520f}, {665f, 315f}, {950f, 145f}},
                new Obstacle(POND, 520f, 230f, 135f, 80f),
                new Obstacle(POND, 840f, 520f, 115f, 70f),
                new Obstacle(TREE, 335f, 170f, 55f, 45f),
                new Obstacle(BUSH, 1040f, 430f, 70f, 45f)
        );
    }

    private static LevelData goldenTwilight() {
        return new LevelData(
                R.string.level_9,
                0xFFDDD5EC, 0xFFC4C7E3, 0xFF99B8A6, 0xFFC9D5BF, 0xFFF6CF74, 10513,
                150f, 590f, 1100f, 125f,
                new float[]{150f, 590f, 420f, 620f, 720f, 150f, 1100f, 125f},
                new float[][]{{360f, 520f}, {650f, 335f}, {915f, 190f}},
                new Obstacle(ROCK, 430f, 230f, 70f, 45f),
                new Obstacle(POND, 735f, 530f, 120f, 75f),
                new Obstacle(LAVENDER, 940f, 430f, 90f, 52f),
                new Obstacle(TREE, 240f, 300f, 55f, 45f)
        );
    }

    private static float[][] copyPoints(float[][] points) {
        float[][] copy = new float[points.length][];
        for (int i = 0; i < points.length; i++) {
            copy[i] = points[i].clone();
        }
        return copy;
    }

    static final class Obstacle {
        final int kind;
        final float x;
        final float y;
        final float radiusX;
        final float radiusY;

        Obstacle(int kind, float x, float y, float radiusX, float radiusY) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.radiusX = radiusX;
            this.radiusY = radiusY;
        }

        boolean collidesCircle(float px, float py, float radius) {
            float nx = (px - x) / (radiusX + radius);
            float ny = (py - y) / (radiusY + radius);
            return nx * nx + ny * ny < 1f;
        }
    }
}
