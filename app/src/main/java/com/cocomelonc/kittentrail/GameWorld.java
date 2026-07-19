/*
 * Kitten Trail
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittentrail;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Pure-Java star collection and shortest-path movement rules. */
final class GameWorld {
    enum State {
        TITLE,
        PLAYING,
        PAUSED,
        LEVEL_COMPLETE,
        QUEST_COMPLETE
    }

    interface Listener {
        void onStarCollected(float col, float row, int collectedCount);

        void onLevelComplete(int completedLevel);

        void onJourneyComplete();
    }

    private static final float MOVE_SPEED = 4.8f;
    private static final int[] ROW_STEP = {-1, 0, 1, 0};
    private static final int[] COL_STEP = {0, 1, 0, -1};

    private final LevelData[] levels;
    private final Listener listener;
    private final ArrayDeque<Integer> path = new ArrayDeque<>();

    private State state = State.TITLE;
    private LevelData level;
    private int levelIndex;
    private int row;
    private int col;
    private float visualRow;
    private float visualCol;
    private float facing = 1f;
    private boolean[][] collectedStars;
    private int collectedCount;

    GameWorld(LevelData[] levels, Listener listener) {
        if (levels == null || levels.length == 0) {
            throw new IllegalArgumentException("At least one trail is required");
        }
        this.levels = levels.clone();
        this.listener = listener;
        loadLevel(0);
        state = State.TITLE;
    }

    void startJourney(int requestedLevel) {
        loadLevel(clampLevel(requestedLevel));
        state = State.PLAYING;
    }

    void restartJourney() {
        startJourney(0);
    }

    void showTitle() {
        path.clear();
        state = State.TITLE;
    }

    void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    boolean tapCell(int targetRow, int targetCol) {
        if (state != State.PLAYING || !level.isWalkable(targetRow, targetCol)) {
            return false;
        }
        if (level.tileAt(targetRow, targetCol) == LevelData.HOME && !allStarsCollected()) {
            return false;
        }
        int routeStartRow = row;
        int routeStartCol = col;
        int pendingCell = -1;
        if (!path.isEmpty()
                && Math.hypot(visualRow - row, visualCol - col) > 0.0001f) {
            pendingCell = path.peekFirst();
            routeStartRow = pendingCell / LevelData.COLS;
            routeStartCol = pendingCell % LevelData.COLS;
        }
        if (targetRow == row && targetCol == col) {
            path.clear();
            if (pendingCell >= 0) {
                path.add(encode(row, col));
            }
            return true;
        }

        int total = LevelData.ROWS * LevelData.COLS;
        int[] parent = new int[total];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[total];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int start = encode(routeStartRow, routeStartCol);
        int goal = encode(targetRow, targetCol);
        visited[start] = true;
        open.add(start);

        while (!open.isEmpty() && !visited[goal]) {
            int current = open.removeFirst();
            int currentRow = current / LevelData.COLS;
            int currentCol = current % LevelData.COLS;
            for (int direction = 0; direction < ROW_STEP.length; direction++) {
                int nextRow = currentRow + ROW_STEP[direction];
                int nextCol = currentCol + COL_STEP[direction];
                if (!level.isWalkable(nextRow, nextCol)) {
                    continue;
                }
                if (!allStarsCollected()
                        && level.tileAt(nextRow, nextCol) == LevelData.HOME) {
                    continue;
                }
                int next = encode(nextRow, nextCol);
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                parent[next] = current;
                open.addLast(next);
            }
        }

        if (!visited[goal]) {
            return false;
        }
        ArrayDeque<Integer> reversed = new ArrayDeque<>();
        for (int cursor = goal; cursor != start; cursor = parent[cursor]) {
            reversed.addFirst(cursor);
        }
        path.clear();
        if (pendingCell >= 0) {
            path.add(pendingCell);
        }
        path.addAll(reversed);
        return true;
    }

    void update(float elapsedSeconds) {
        if (state != State.PLAYING || path.isEmpty()) {
            return;
        }
        float remaining = MOVE_SPEED * Math.min(Math.max(elapsedSeconds, 0f), 0.1f);
        while (remaining > 0f && !path.isEmpty() && state == State.PLAYING) {
            int next = path.peekFirst();
            int nextRow = next / LevelData.COLS;
            int nextCol = next % LevelData.COLS;
            float deltaRow = nextRow - visualRow;
            float deltaCol = nextCol - visualCol;
            float distance = (float) Math.hypot(deltaRow, deltaCol);
            if (deltaCol != 0f) {
                facing = Math.signum(deltaCol);
            }
            if (distance <= remaining + 0.0001f) {
                visualRow = nextRow;
                visualCol = nextCol;
                row = nextRow;
                col = nextCol;
                remaining -= distance;
                path.removeFirst();
                enterCell();
            } else {
                visualRow += deltaRow / distance * remaining;
                visualCol += deltaCol / distance * remaining;
                remaining = 0f;
            }
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
            return;
        }
        loadLevel(levelIndex + 1);
        state = State.PLAYING;
    }

    private void enterCell() {
        char tile = level.tileAt(row, col);
        if (tile == LevelData.STAR && !collectedStars[row][col]) {
            collectedStars[row][col] = true;
            collectedCount++;
            if (listener != null) {
                listener.onStarCollected(visualCol, visualRow, collectedCount);
            }
        }

        if (tile == LevelData.HOME && allStarsCollected()) {
            path.clear();
            state = State.LEVEL_COMPLETE;
            if (listener != null) {
                listener.onLevelComplete(levelIndex);
            }
        }
    }

    private void loadLevel(int index) {
        levelIndex = index;
        level = levels[levelIndex];
        row = level.startRow;
        col = level.startCol;
        visualRow = row;
        visualCol = col;
        facing = 1f;
        collectedCount = 0;
        collectedStars = new boolean[LevelData.ROWS][LevelData.COLS];
        path.clear();
    }

    private int clampLevel(int requestedLevel) {
        return Math.max(0, Math.min(requestedLevel, levels.length - 1));
    }

    private static int encode(int encodedRow, int encodedCol) {
        return encodedRow * LevelData.COLS + encodedCol;
    }

    State getState() {
        return state;
    }

    LevelData getLevel() {
        return level;
    }

    int getLevelIndex() {
        return levelIndex;
    }

    int getLevelCount() {
        return levels.length;
    }

    int getRow() {
        return row;
    }

    int getCol() {
        return col;
    }

    float getVisualRow() {
        return visualRow;
    }

    float getVisualCol() {
        return visualCol;
    }

    float getFacing() {
        return facing;
    }

    int getCollectedCount() {
        return collectedCount;
    }

    int getPathLength() {
        return path.size();
    }

    boolean allStarsCollected() {
        return collectedCount == level.starCount;
    }

    boolean isStarCollected(int checkRow, int checkCol) {
        return collectedStars[checkRow][checkCol];
    }
}
