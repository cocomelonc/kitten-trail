/*
 * Kitten Trail
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittentrail;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;

public final class LevelReachabilityTest {
    private static final int CELL = 24;
    private static final int COLS = (int) GameWorld.WORLD_WIDTH / CELL;
    private static final int ROWS = (int) GameWorld.WORLD_HEIGHT / CELL;

    @Test
    public void everyStarAndHomeAreReachableFromStart() {
        for (LevelData level : LevelData.createAll()) {
            boolean[][] reached = flood(level, level.startX, level.startY);
            for (float[] star : level.stars) {
                assertTrue("Unreachable star in level " + level.nameRes,
                        isReachedNear(reached, star[0], star[1]));
            }
            assertTrue("Unreachable home in level " + level.nameRes,
                    isReachedNear(reached, level.homeX, level.homeY));
        }
    }

    @Test
    public void startsStarsAndHomesDoNotOverlapObstacles() {
        for (LevelData level : LevelData.createAll()) {
            assertTrue(level.isCircleFree(level.startX, level.startY, GameWorld.KITTEN_RADIUS));
            assertTrue(level.isCircleFree(level.homeX, level.homeY, GameWorld.KITTEN_RADIUS));
            for (float[] star : level.stars) {
                assertTrue(level.isCircleFree(star[0], star[1], GameWorld.KITTEN_RADIUS));
            }
        }
    }

    private static boolean[][] flood(LevelData level, float startX, float startY) {
        boolean[][] reached = new boolean[ROWS][COLS];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int startCol = clamp((int) startX / CELL, 0, COLS - 1);
        int startRow = clamp((int) startY / CELL, 0, ROWS - 1);
        reached[startRow][startCol] = true;
        queue.add(startRow * COLS + startCol);
        int[] dc = {1, -1, 0, 0};
        int[] dr = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int value = queue.removeFirst();
            int row = value / COLS;
            int col = value % COLS;
            for (int i = 0; i < 4; i++) {
                int nextCol = col + dc[i];
                int nextRow = row + dr[i];
                if (nextCol < 0 || nextRow < 0 || nextCol >= COLS || nextRow >= ROWS
                        || reached[nextRow][nextCol]) {
                    continue;
                }
                float x = nextCol * CELL + CELL / 2f;
                float y = nextRow * CELL + CELL / 2f;
                if (!level.isCircleFree(x, y, GameWorld.KITTEN_RADIUS)) {
                    continue;
                }
                reached[nextRow][nextCol] = true;
                queue.add(nextRow * COLS + nextCol);
            }
        }
        return reached;
    }

    private static boolean isReachedNear(boolean[][] reached, float x, float y) {
        int col = clamp((int) x / CELL, 0, COLS - 1);
        int row = clamp((int) y / CELL, 0, ROWS - 1);
        for (int r = Math.max(0, row - 2); r <= Math.min(ROWS - 1, row + 2); r++) {
            for (int c = Math.max(0, col - 2); c <= Math.min(COLS - 1, col + 2); c++) {
                if (reached[r][c]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
