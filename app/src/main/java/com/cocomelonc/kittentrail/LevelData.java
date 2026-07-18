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
        return new LevelData[]{meadow(), lavender(), pond(), orchard(), hill()};
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
