/*
 * Kitten Trail
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittentrail;

/** Immutable 14x8 tilemap and gentle palette for one kitten trail. */
final class LevelData {
    static final int COLS = 14;
    static final int ROWS = 8;

    static final char FLOOR = '.';
    static final char WALL = '#';
    static final char CRATE = 'X';
    static final char START = 'S';
    static final char STAR = '*';
    static final char HOME = 'H';

    final int nameRes;
    final int backgroundTop;
    final int backgroundBottom;
    final int floorA;
    final int floorB;
    final int wallColor;
    final int wallTopColor;
    final int accentColor;
    final int starColor;
    final int seed;
    final String[] map;
    final int startRow;
    final int startCol;
    final int homeRow;
    final int homeCol;
    final int starCount;

    private LevelData(
            int nameRes,
            int backgroundTop,
            int backgroundBottom,
            int floorA,
            int floorB,
            int wallColor,
            int wallTopColor,
            int accentColor,
            int starColor,
            int seed,
            String... map
    ) {
        this.nameRes = nameRes;
        this.backgroundTop = backgroundTop;
        this.backgroundBottom = backgroundBottom;
        this.floorA = floorA;
        this.floorB = floorB;
        this.wallColor = wallColor;
        this.wallTopColor = wallTopColor;
        this.accentColor = accentColor;
        this.starColor = starColor;
        this.seed = seed;
        this.map = map.clone();

        if (map.length != ROWS) {
            throw new IllegalArgumentException("Trail must contain exactly " + ROWS + " rows");
        }
        int foundStartRow = -1;
        int foundStartCol = -1;
        int foundHomeRow = -1;
        int foundHomeCol = -1;
        int starts = 0;
        int homes = 0;
        int stars = 0;
        for (int row = 0; row < ROWS; row++) {
            if (map[row].length() != COLS) {
                throw new IllegalArgumentException(
                        "Trail row " + row + " must contain " + COLS + " tiles");
            }
            for (int col = 0; col < COLS; col++) {
                char tile = map[row].charAt(col);
                if (!isKnownTile(tile)) {
                    throw new IllegalArgumentException("Unknown trail tile: " + tile);
                }
                if (tile == START) {
                    starts++;
                    foundStartRow = row;
                    foundStartCol = col;
                } else if (tile == HOME) {
                    homes++;
                    foundHomeRow = row;
                    foundHomeCol = col;
                } else if (tile == STAR) {
                    stars++;
                }
            }
        }
        if (starts != 1 || homes != 1) {
            throw new IllegalArgumentException("Trail needs exactly one start and one home");
        }
        if (stars != 3) {
            throw new IllegalArgumentException("Trail needs exactly three stars");
        }
        startRow = foundStartRow;
        startCol = foundStartCol;
        homeRow = foundHomeRow;
        homeCol = foundHomeCol;
        starCount = stars;
    }

    char tileAt(int row, int col) {
        return map[row].charAt(col);
    }

    boolean isWalkable(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return false;
        }
        char tile = tileAt(row, col);
        return tile != WALL && tile != CRATE;
    }

    static LevelData[] createAll() {
        return new LevelData[]{
                dewdropMeadow(), lavenderGarden(), moonlitPond(), peachOrchard(), starryHill(),
                cottonCloudValley(), rosePetalPath(), mintyBrook(), goldenTwilight()
        };
    }

    private static LevelData dewdropMeadow() {
        return new LevelData(
                R.string.level_1,
                0xFFF9EBE8, 0xFFE8E0F2, 0xFFE7EACF, 0xFFDDE5C4,
                0xFF8BAE82, 0xFFA7C79A, 0xFFF1AE9B, 0xFFFFD56F, 1103,
                "##############",
                "#S...#....*..#",
                "#.#..#..##...#",
                "#.#..#....*..#",
                "#.#..####....#",
                "#*...........#",
                "#...######..H#",
                "##############"
        );
    }

    private static LevelData lavenderGarden() {
        return new LevelData(
                R.string.level_2,
                0xFFF8EAF4, 0xFFDCDCF2, 0xFFE4E3CC, 0xFFD8DDC1,
                0xFF9A8BB5, 0xFFB9ABD0, 0xFFC1A8DE, 0xFFFFD778, 2417,
                "##############",
                "#S..#.....#*.#",
                "#.#.#.###.#..#",
                "#.#...#*#....#",
                "#.#####.#.##.#",
                "#*......#....#",
                "#...##.....#H#",
                "##############"
        );
    }

    private static LevelData moonlitPond() {
        return new LevelData(
                R.string.level_3,
                0xFFDADDED, 0xFFBFD0E2, 0xFFD9E4D8, 0xFFCEE0D4,
                0xFF79A9AF, 0xFF9DC7CA, 0xFF8FC7D0, 0xFFFFD46B, 3701,
                "##############",
                "#S.....#...*.#",
                "#.###..#.#...#",
                "#...#*...#.#.#",
                "###.#####..#.#",
                "#*.......#...#",
                "#..######...H#",
                "##############"
        );
    }

    private static LevelData peachOrchard() {
        return new LevelData(
                R.string.level_4,
                0xFFFFECE3, 0xFFF2D7D1, 0xFFE7E0C4, 0xFFDDD7B7,
                0xFFA88E7C, 0xFFC6AA94, 0xFFF0A58D, 0xFFFFD16B, 4933,
                "##############",
                "#S.#.....#*..#",
                "#..#.###.#.#.#",
                "##.#...#...#.#",
                "#*.###.#####.#",
                "#....*.......#",
                "#.########.#H#",
                "##############"
        );
    }

    private static LevelData starryHill() {
        return new LevelData(
                R.string.level_5,
                0xFFCBC8E4, 0xFFAFC4D4, 0xFFD8E0CE, 0xFFCCD8C2,
                0xFF85839F, 0xFFA7A5BC, 0xFFF0C46F, 0xFFFFD260, 6173,
                "##############",
                "#S..X....#*..#",
                "#.#.X.##.#.#.#",
                "#.#...*#...#.#",
                "#.###.#####..#",
                "#*..X........#",
                "#...X.#####.H#",
                "##############"
        );
    }

    private static LevelData cottonCloudValley() {
        return new LevelData(
                R.string.level_6,
                0xFFF7F1EA, 0xFFDDEAF4, 0xFFE6ECDB, 0xFFDBE7D5,
                0xFF8DA5B7, 0xFFAFC7D7, 0xFFA9C7E8, 0xFFFFD576, 7289,
                "##############",
                "#S....#...*..#",
                "#.###.#.####.#",
                "#...#.#..*...#",
                "#.#.#.#####..#",
                "#*#..........#",
                "#...######..H#",
                "##############"
        );
    }

    private static LevelData rosePetalPath() {
        return new LevelData(
                R.string.level_7,
                0xFFFFF1F2, 0xFFF1DDE5, 0xFFE9E5D2, 0xFFDFDEC7,
                0xFFAD8E9B, 0xFFCBAAB5, 0xFFECAFC1, 0xFFFFD271, 8363,
                "##############",
                "#S.#*......#.#",
                "#..#.#####.#.#",
                "##.#.....#...#",
                "#..#####.###.#",
                "#*.....#...*.#",
                "#.###....###H#",
                "##############"
        );
    }

    private static LevelData mintyBrook() {
        return new LevelData(
                R.string.level_8,
                0xFFEAF5F0, 0xFFD4E8E4, 0xFFE1ECE0, 0xFFD5E4D8,
                0xFF79A69A, 0xFF9BC3B7, 0xFF91CFC1, 0xFFFFD372, 9479,
                "##############",
                "#S...#*......#",
                "#.##.#.####..#",
                "#....#..X.#*.#",
                "####.####.#..#",
                "#*........#..#",
                "#..######...H#",
                "##############"
        );
    }

    private static LevelData goldenTwilight() {
        return new LevelData(
                R.string.level_9,
                0xFFDDD5EC, 0xFFC4C7E3, 0xFFD8DFCF, 0xFFCCD7C4,
                0xFF8B809B, 0xFFACA2BC, 0xFFF1C769, 0xFFFFCE59, 10513,
                "##############",
                "#S....#...*..#",
                "#.##.##.#.##.#",
                "#..#....#....#",
                "##.####...##.#",
                "#*....###.*..#",
                "#...X.......H#",
                "##############"
        );
    }

    private static boolean isKnownTile(char tile) {
        return tile == FLOOR || tile == WALL || tile == CRATE
                || tile == START || tile == STAR || tile == HOME;
    }
}
