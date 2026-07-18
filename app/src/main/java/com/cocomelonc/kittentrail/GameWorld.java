/*
 * Kitten Trail
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittentrail;

/** Pure game rules, intentionally independent of Android so they can be unit tested. */
final class GameWorld {
    static final float WORLD_WIDTH = 1280f;
    static final float WORLD_HEIGHT = 720f;
    static final float KITTEN_RADIUS = 23f;
    private static final float MAX_SPEED = 255f;
    private static final float STAR_RADIUS = 43f;
    private static final float HOME_RADIUS = 66f;

    enum State {
        TITLE,
        PLAYING,
        PAUSED,
        LEVEL_COMPLETE,
        QUEST_COMPLETE
    }

    interface Listener {
        void onStarCollected(int collectedCount);

        void onLevelComplete(int completedLevel);

        void onJourneyComplete();
    }

    private final LevelData[] levels;
    private final Listener listener;
    private final boolean[] collected = new boolean[3];

    private State state = State.TITLE;
    private int levelIndex;
    private float kittenX;
    private float kittenY;
    private float targetX;
    private float targetY;
    private float velocityX;
    private float velocityY;
    private float journeyTime;
    private boolean pointerDown;

    GameWorld(LevelData[] levels, Listener listener) {
        if (levels == null || levels.length == 0) {
            throw new IllegalArgumentException("At least one level is required");
        }
        this.levels = levels.clone();
        this.listener = listener;
        loadLevel(0);
    }

    void startJourney(int savedLevel) {
        loadLevel(clamp(savedLevel, 0, levels.length - 1));
        state = State.PLAYING;
    }

    void restartJourney() {
        loadLevel(0);
        state = State.PLAYING;
    }

    void setPointer(float x, float y, boolean down) {
        pointerDown = down;
        targetX = clamp(x, 45f, WORLD_WIDTH - 45f);
        targetY = clamp(y, 78f, WORLD_HEIGHT - 45f);
    }

    void pause() {
        if (state == State.PLAYING) {
            pointerDown = false;
            state = State.PAUSED;
        }
    }

    void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    void continueAfterLevel() {
        if (state != State.LEVEL_COMPLETE) {
            return;
        }
        if (levelIndex + 1 >= levels.length) {
            state = State.QUEST_COMPLETE;
            if (listener != null) {
                listener.onJourneyComplete();
            }
        } else {
            loadLevel(levelIndex + 1);
            state = State.PLAYING;
        }
    }

    void showTitle() {
        pointerDown = false;
        velocityX = 0f;
        velocityY = 0f;
        state = State.TITLE;
    }

    void update(float deltaSeconds) {
        if (state != State.PLAYING) {
            return;
        }
        float dt = clamp(deltaSeconds, 0f, 1f / 20f);
        journeyTime += dt;

        if (pointerDown) {
            float dx = targetX - kittenX;
            float dy = targetY - kittenY;
            float distance = length(dx, dy);
            if (distance > 3f) {
                float desiredSpeed = MAX_SPEED * Math.min(1f, distance / 72f);
                float desiredX = dx / distance * desiredSpeed;
                float desiredY = dy / distance * desiredSpeed;
                float smoothing = Math.min(1f, dt * 11f);
                velocityX += (desiredX - velocityX) * smoothing;
                velocityY += (desiredY - velocityY) * smoothing;
            } else {
                velocityX *= 0.72f;
                velocityY *= 0.72f;
            }
        } else {
            float damping = (float) Math.pow(0.001f, dt);
            velocityX *= damping;
            velocityY *= damping;
        }

        moveWithCollisions(velocityX * dt, velocityY * dt);
        collectNearbyStars();
        checkHome();
    }

    private void moveWithCollisions(float dx, float dy) {
        LevelData level = currentLevel();
        float nextX = clamp(kittenX + dx, 45f + KITTEN_RADIUS, WORLD_WIDTH - 45f - KITTEN_RADIUS);
        if (level.isCircleFree(nextX, kittenY, KITTEN_RADIUS)) {
            kittenX = nextX;
        } else {
            velocityX *= -0.08f;
        }

        float nextY = clamp(kittenY + dy, 78f + KITTEN_RADIUS, WORLD_HEIGHT - 45f - KITTEN_RADIUS);
        if (level.isCircleFree(kittenX, nextY, KITTEN_RADIUS)) {
            kittenY = nextY;
        } else {
            velocityY *= -0.08f;
        }
    }

    private void collectNearbyStars() {
        float[][] stars = currentLevel().stars;
        for (int i = 0; i < collected.length; i++) {
            if (!collected[i] && distanceSquared(kittenX, kittenY, stars[i][0], stars[i][1])
                    <= STAR_RADIUS * STAR_RADIUS) {
                collected[i] = true;
                if (listener != null) {
                    listener.onStarCollected(collectedCount());
                }
            }
        }
    }

    private void checkHome() {
        if (!allStarsCollected()) {
            return;
        }
        LevelData level = currentLevel();
        if (distanceSquared(kittenX, kittenY, level.homeX, level.homeY) <= HOME_RADIUS * HOME_RADIUS) {
            pointerDown = false;
            velocityX = 0f;
            velocityY = 0f;
            state = State.LEVEL_COMPLETE;
            if (listener != null) {
                listener.onLevelComplete(levelIndex);
            }
        }
    }

    private void loadLevel(int index) {
        levelIndex = index;
        for (int i = 0; i < collected.length; i++) {
            collected[i] = false;
        }
        LevelData level = levels[index];
        kittenX = level.startX;
        kittenY = level.startY;
        targetX = kittenX;
        targetY = kittenY;
        velocityX = 0f;
        velocityY = 0f;
        journeyTime = 0f;
        pointerDown = false;
    }

    State state() {
        return state;
    }

    int levelIndex() {
        return levelIndex;
    }

    int levelCount() {
        return levels.length;
    }

    LevelData currentLevel() {
        return levels[levelIndex];
    }

    float kittenX() {
        return kittenX;
    }

    float kittenY() {
        return kittenY;
    }

    float targetX() {
        return targetX;
    }

    float targetY() {
        return targetY;
    }

    float velocityX() {
        return velocityX;
    }

    float velocityY() {
        return velocityY;
    }

    float journeyTime() {
        return journeyTime;
    }

    boolean isPointerDown() {
        return pointerDown;
    }

    boolean isStarCollected(int index) {
        return collected[index];
    }

    int collectedCount() {
        int count = 0;
        for (boolean value : collected) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    boolean allStarsCollected() {
        return collectedCount() == collected.length;
    }

    void teleportForTest(float x, float y) {
        kittenX = x;
        kittenY = y;
    }

    private static float distanceSquared(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        return dx * dx + dy * dy;
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
