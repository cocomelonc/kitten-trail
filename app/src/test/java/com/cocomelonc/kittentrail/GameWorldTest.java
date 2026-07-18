package com.cocomelonc.kittentrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GameWorldTest {
    @Test
    public void collectingEveryStarAndEnteringHomeCompletesLevel() {
        LevelData[] levels = LevelData.createAll();
        GameWorld world = new GameWorld(levels, null);
        world.startJourney(0);

        for (float[] star : levels[0].stars) {
            world.teleportForTest(star[0], star[1]);
            world.update(1f / 60f);
        }

        assertEquals(3, world.collectedCount());
        assertTrue(world.allStarsCollected());

        world.teleportForTest(levels[0].homeX, levels[0].homeY);
        world.update(1f / 60f);
        assertEquals(GameWorld.State.LEVEL_COMPLETE, world.state());
    }

    @Test
    public void homeDoesNotOpenBeforeAllStarsAreCollected() {
        LevelData[] levels = LevelData.createAll();
        GameWorld world = new GameWorld(levels, null);
        world.startJourney(0);
        world.teleportForTest(levels[0].homeX, levels[0].homeY);

        world.update(1f / 60f);

        assertEquals(GameWorld.State.PLAYING, world.state());
        assertFalse(world.allStarsCollected());
    }

    @Test
    public void kittenCannotEnterObstacle() {
        LevelData level = LevelData.createAll()[0];
        GameWorld world = new GameWorld(new LevelData[]{level}, null);
        world.startJourney(0);
        LevelData.Obstacle pond = level.obstacles.get(0);
        world.teleportForTest(pond.x - pond.radiusX - GameWorld.KITTEN_RADIUS - 5f, pond.y);
        world.setPointer(pond.x, pond.y, true);

        for (int i = 0; i < 240; i++) {
            world.update(1f / 60f);
            assertFalse(pond.collidesCircle(world.kittenX(), world.kittenY(), GameWorld.KITTEN_RADIUS));
        }
    }

    @Test
    public void savedLevelIsClampedToAvailableLevels() {
        LevelData[] levels = LevelData.createAll();
        GameWorld world = new GameWorld(levels, null);

        world.startJourney(999);

        assertEquals(levels.length - 1, world.levelIndex());
    }
}
