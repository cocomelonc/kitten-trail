/*
 * Kitten Trail
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittentrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GameWorldTest {
    @Test
    public void cubesCannotBeSelected() {
        GameWorld world = new GameWorld(LevelData.createAll(), null);
        world.startJourney(0);
        assertFalse(world.tapCell(0, 0));
        assertEquals(0, world.getPathLength());

        world.startJourney(4);
        assertFalse(world.tapCell(1, 4));
        assertEquals(0, world.getPathLength());
    }

    @Test
    public void homeWaitsUntilAllStarsAreCollected() {
        GameWorld world = new GameWorld(LevelData.createAll(), null);
        world.startJourney(0);
        assertFalse(world.tapCell(world.getLevel().homeRow, world.getLevel().homeCol));
        assertEquals(GameWorld.State.PLAYING, world.getState());
        assertFalse(world.allStarsCollected());

        collectEveryStar(world);
        assertTrue(world.allStarsCollected());
        move(world, world.getLevel().homeRow, world.getLevel().homeCol);
        assertEquals(GameWorld.State.LEVEL_COMPLETE, world.getState());
    }

    @Test
    public void allNineTrailsCanBeCompleted() {
        LevelData[] levels = LevelData.createAll();
        GameWorld world = new GameWorld(levels, null);
        world.startJourney(0);

        for (int levelIndex = 0; levelIndex < levels.length; levelIndex++) {
            assertEquals(levelIndex, world.getLevelIndex());
            collectEveryStar(world);
            assertEquals(3, world.getCollectedCount());
            move(world, world.getLevel().homeRow, world.getLevel().homeCol);
            assertEquals(GameWorld.State.LEVEL_COMPLETE, world.getState());
            world.continueAfterLevel();
        }
        assertEquals(GameWorld.State.QUEST_COMPLETE, world.getState());
    }

    @Test
    public void aNewTapReplacesThePreviousRoute() {
        GameWorld world = new GameWorld(LevelData.createAll(), null);
        world.startJourney(0);
        assertTrue(world.tapCell(5, 1));
        int firstLength = world.getPathLength();
        assertTrue(firstLength > 0);

        assertTrue(world.tapCell(1, 2));
        assertTrue(world.getPathLength() < firstLength);
        move(world, 1, 2);
    }

    @Test
    public void retappingTheCurrentCellMidStepReturnsToItsCenter() {
        GameWorld world = new GameWorld(LevelData.createAll(), null);
        world.startJourney(0);
        int startRow = world.getRow();
        int startCol = world.getCol();
        assertTrue(world.tapCell(5, 1));
        world.update(0.05f);
        assertTrue(world.getVisualRow() > startRow);

        assertTrue(world.tapCell(startRow, startCol));
        move(world, startRow, startCol);
        assertEquals(startRow, world.getVisualRow(), 0.0001f);
        assertEquals(startCol, world.getVisualCol(), 0.0001f);
    }

    @Test
    public void savedLevelIsClampedToAvailableLevels() {
        GameWorld world = new GameWorld(LevelData.createAll(), null);
        world.startJourney(999);
        assertEquals(8, world.getLevelIndex());
        world.startJourney(-5);
        assertEquals(0, world.getLevelIndex());
    }

    private static void collectEveryStar(GameWorld world) {
        LevelData level = world.getLevel();
        for (int row = 0; row < LevelData.ROWS; row++) {
            for (int col = 0; col < LevelData.COLS; col++) {
                if (level.tileAt(row, col) == LevelData.STAR
                        && !world.isStarCollected(row, col)) {
                    move(world, row, col);
                }
            }
        }
    }

    private static void move(GameWorld world, int row, int col) {
        assertTrue("No route to " + row + "," + col, world.tapCell(row, col));
        int guard = 0;
        while (world.getPathLength() > 0 && world.getState() == GameWorld.State.PLAYING) {
            world.update(0.1f);
            if (++guard > 1000) {
                throw new AssertionError("Movement did not settle");
            }
        }
        assertEquals(row, world.getRow());
        assertEquals(col, world.getCol());
    }
}
