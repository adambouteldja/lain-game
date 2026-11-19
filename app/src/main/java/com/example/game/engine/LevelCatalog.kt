package com.example.game.engine

import com.example.data.JudoTrialId
import com.example.data.WorldId
import com.example.game.model.*

/**
 * Complete catalog of all 6 Worlds and 12 handcrafted, physics-verified levels.
 */
object LevelCatalog {

    val allLevels: List<LevelDefinition> = listOf(
        level1_1(),
        level1_2(),
        level2_1(),
        level2_2(),
        level3_1(),
        level3_2(),
        level4_1(),
        level4_2(),
        level5_1(),
        level5_2(),
        level6_1(),
        level6_2()
    )

    fun levelById(id: String): LevelDefinition =
        allLevels.find { it.id == id } ?: allLevels.first()

    fun levelsForWorld(worldId: WorldId): List<LevelDefinition> =
        allLevels.filter { it.worldId == worldId }

    fun nextLevelId(currentId: String): String? {
        val idx = allLevels.indexOfFirst { it.id == currentId }
        return if (idx in 0 until allLevels.lastIndex) allLevels[idx + 1].id else null
    }

    fun worldForLevel(levelId: String): WorldId = levelById(levelId).worldId

    // =========================================================================
    // WORLD 1: LAIN'S ROOM (Tutorial & Physical Introduction)
    // =========================================================================

    private fun level1_1() = LevelDefinition(
        id = "1-1",
        worldId = WorldId.LAINS_ROOM,
        orderIndex = 0,
        worldWidth = 1480f,
        worldHeight = 580f,
        spawn = Vec2(90f, 380f),
        platforms = listOf(
            // Main bedroom floor
            Platform("f1", 0f, 470f, 540f, 110f),
            // Gentle desk step-up
            Platform("desk", 320f, 395f, 160f, 24f),
            // Gap of 110px (easy tutorial jump)
            Platform("f2", 650f, 470f, 830f, 110f),
            // Raised shelf for Navi terminal & collectible
            Platform("shelf1", 720f, 385f, 180f, 24f),
            Platform("shelf2", 950f, 315f, 200f, 24f),
            // Ceiling boundary wall above door so player must open the door
            Platform("wall_top", 1210f, 0f, 28f, 366f)
        ),
        switches = listOf(
            SwitchNode(
                id = "term_1_1",
                x = 1020f,
                y = 263f,
                width = 46f,
                height = 52f,
                kind = SwitchKind.TERMINAL,
                signalId = "sig_door_1_1"
            )
        ),
        doors = listOf(
            Door("door_1_1", x = 1210f, y = 366f, width = 28f, height = 104f, linkedSignalId = "sig_door_1_1")
        ),
        shards = listOf(
            CollectibleShard("s_1_1_a", 400f, 345f),
            CollectibleShard("s_1_1_b", 810f, 335f),
            CollectibleShard("s_1_1_c", 1120f, 265f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_1_1", 700f, 470f)
        ),
        exit = PortalExit(x = 1350f, y = 384f)
    )

    private fun level1_2() = LevelDefinition(
        id = "1-2",
        worldId = WorldId.LAINS_ROOM,
        orderIndex = 1,
        worldWidth = 1720f,
        worldHeight = 600f,
        spawn = Vec2(75f, 390f),
        platforms = listOf(
            Platform("f1", 0f, 480f, 520f, 120f),
            // Moving platform across cable trench
            Platform(
                id = "mov_1_2",
                x = 560f,
                y = 455f,
                width = 140f,
                height = 22f,
                type = PlatformType.MOVING,
                startX = 545f,
                startY = 455f,
                endX = 760f,
                endY = 395f,
                moveSpeed = 1.45f
            ),
            Platform("f2", 920f, 480f, 800f, 120f),
            Platform("ledge_crate", 980f, 390f, 240f, 24f),
            Platform("wall_door", 1450f, 0f, 28f, 376f)
        ),
        boxes = listOf(
            PhysicsBox("box_1_2", x = 1040f, y = 340f)
        ),
        switches = listOf(
            SwitchNode(
                id = "plate_1_2",
                x = 1310f,
                y = 464f,
                width = 64f,
                height = 16f,
                kind = SwitchKind.PRESSURE_PLATE,
                signalId = "sig_1_2",
                requiresWeight = true
            )
        ),
        doors = listOf(
            Door("door_1_2", x = 1450f, y = 376f, width = 28f, height = 104f, linkedSignalId = "sig_1_2")
        ),
        shards = listOf(
            CollectibleShard("s_1_2_a", 690f, 340f),
            CollectibleShard("s_1_2_b", 1140f, 330f),
            CollectibleShard("s_1_2_c", 1545f, 410f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_1_2", 955f, 480f)
        ),
        hazards = listOf(
            HazardZone("hz_1_2", 520f, 560f, 400f, 40f)
        ),
        exit = PortalExit(x = 1610f, y = 394f)
    )

    // =========================================================================
    // WORLD 2: GRAVITY (Momentum, Ice Friction & Gravity Inversion)
    // =========================================================================

    private fun level2_1() = LevelDefinition(
        id = "2-1",
        worldId = WorldId.GRAVITY,
        orderIndex = 2,
        worldWidth = 1860f,
        worldHeight = 620f,
        spawn = Vec2(80f, 410f),
        platforms = listOf(
            Platform("start", 0f, 490f, 280f, 130f),
            // Long low-friction ice runway
            Platform("ice1", 280f, 490f, 420f, 36f, type = PlatformType.ICE),
            // Sticky brake pad before gap
            Platform("sticky1", 700f, 490f, 120f, 36f, type = PlatformType.STICKY),
            // Mid island with ice slide for crate puzzle
            Platform("mid_solid", 970f, 490f, 160f, 130f),
            Platform("ice2", 1130f, 490f, 420f, 36f, type = PlatformType.ICE),
            Platform("end_floor", 1550f, 490f, 310f, 130f),
            // Upper shard platform reachable via momentum jump
            Platform("high1", 760f, 375f, 150f, 22f),
            Platform("wall_2_1", 1600f, 0f, 28f, 386f)
        ),
        boxes = listOf(
            PhysicsBox("box_2_1", x = 1040f, y = 440f)
        ),
        switches = listOf(
            SwitchNode(
                id = "plate_2_1",
                x = 1480f,
                y = 474f,
                width = 64f,
                height = 16f,
                kind = SwitchKind.PRESSURE_PLATE,
                signalId = "sig_2_1",
                requiresWeight = true
            )
        ),
        doors = listOf(
            Door("door_2_1", x = 1600f, y = 386f, width = 28f, height = 104f, linkedSignalId = "sig_2_1")
        ),
        shards = listOf(
            CollectibleShard("s_2_1_a", 500f, 430f),
            CollectibleShard("s_2_1_b", 835f, 325f),
            CollectibleShard("s_2_1_c", 1320f, 415f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_2_1", 1000f, 490f)
        ),
        hazards = listOf(
            HazardZone("hz_2_1", 820f, 575f, 150f, 45f)
        ),
        exit = PortalExit(x = 1740f, y = 404f)
    )

    private fun level2_2() = LevelDefinition(
        id = "2-2",
        worldId = WorldId.GRAVITY,
        orderIndex = 3,
        worldWidth = 1860f,
        worldHeight = 640f,
        spawn = Vec2(80f, 440f),
        platforms = listOf(
            // Floor and Ceiling corridor
            Platform("floor1", 0f, 520f, 520f, 120f),
            Platform("ceil1", 0f, 0f, 1860f, 70f),
            // Low-gravity shaft platforms
            Platform("mid_ledge", 540f, 430f, 180f, 24f),
            Platform("ceil_walk", 760f, 70f, 560f, 28f), // Ceiling path when inverted
            Platform("floor_mid", 780f, 520f, 420f, 120f),
            Platform("floor_end", 1340f, 520f, 520f, 120f),
            Platform("wall_2_2", 1600f, 70f, 28f, 346f)
        ),
        gravityZones = listOf(
            GravityZone("gz_low", 480f, 100f, 280f, 420f, GravityZoneMode.LOW_GRAVITY),
            GravityZone("gz_inv", 820f, 98f, 360f, 422f, GravityZoneMode.INVERTED)
        ),
        switches = listOf(
            SwitchNode(
                id = "grav_sw_2_2",
                x = 1230f,
                y = 468f,
                width = 46f,
                height = 52f,
                kind = SwitchKind.GRAVITY_SWITCH,
                signalId = "sig_grav"
            ),
            SwitchNode(
                id = "term_ceil_2_2",
                x = 1000f,
                y = 98f,
                width = 46f,
                height = 52f,
                kind = SwitchKind.TERMINAL,
                signalId = "sig_2_2"
            )
        ),
        doors = listOf(
            Door("door_2_2", x = 1600f, y = 416f, width = 28f, height = 104f, linkedSignalId = "sig_2_2")
        ),
        shards = listOf(
            CollectibleShard("s_2_2_a", 620f, 210f),
            CollectibleShard("s_2_2_b", 920f, 150f),
            CollectibleShard("s_2_2_c", 1450f, 450f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_2_2", 830f, 520f)
        ),
        hazards = listOf(
            HazardZone("hz_2_2_a", 520f, 600f, 260f, 40f),
            HazardZone("hz_2_2_b", 1200f, 600f, 140f, 40f)
        ),
        exit = PortalExit(x = 1730f, y = 434f)
    )

    // =========================================================================
    // WORLD 3: CREATION (Interactive Ink Bridges + Sketchbook Synergy)
    // =========================================================================

    private fun level3_1() = LevelDefinition(
        id = "3-1",
        worldId = WorldId.CREATION,
        orderIndex = 4,
        worldWidth = 1760f,
        worldHeight = 620f,
        spawn = Vec2(80f, 420f),
        allowInkDrawing = true,
        platforms = listOf(
            Platform("p1", 0f, 500f, 380f, 120f),
            // Wide chasm (~290px) — draw an ink bridge to cross!
            Platform("p2", 670f, 460f, 260f, 160f),
            // High island (~190px up) — draw a stepping ink platform!
            Platform("p3", 1040f, 290f, 230f, 26f),
            Platform("p4", 1360f, 480f, 400f, 140f),
            Platform("wall_3_1", 1500f, 0f, 28f, 376f)
        ),
        switches = listOf(
            SwitchNode(
                id = "term_3_1",
                x = 1140f,
                y = 238f,
                width = 46f,
                height = 52f,
                kind = SwitchKind.TERMINAL,
                signalId = "sig_3_1"
            )
        ),
        doors = listOf(
            Door("door_3_1", x = 1500f, y = 376f, width = 28f, height = 104f, linkedSignalId = "sig_3_1")
        ),
        shards = listOf(
            CollectibleShard("s_3_1_a", 525f, 390f),
            CollectibleShard("s_3_1_b", 980f, 230f),
            CollectibleShard("s_3_1_c", 1410f, 415f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_3_1", 730f, 460f)
        ),
        hazards = listOf(
            HazardZone("hz_3_1_a", 380f, 585f, 290f, 35f),
            HazardZone("hz_3_1_b", 930f, 585f, 430f, 35f)
        ),
        exit = PortalExit(x = 1640f, y = 394f)
    )

    private fun level3_2() = LevelDefinition(
        id = "3-2",
        worldId = WorldId.CREATION,
        orderIndex = 5,
        worldWidth = 1880f,
        worldHeight = 620f,
        spawn = Vec2(75f, 400f),
        allowInkDrawing = true,
        platforms = listOf(
            Platform("p1", 0f, 480f, 460f, 140f),
            // Gap of 210px where player draws a flat ink bridge so crate can roll across
            Platform("p2", 670f, 480f, 420f, 140f),
            Platform(
                id = "mov_3_2",
                x = 1130f,
                y = 430f,
                width = 130f,
                height = 22f,
                type = PlatformType.MOVING,
                startX = 1120f,
                startY = 440f,
                endX = 1300f,
                endY = 340f,
                moveSpeed = 1.3f
            ),
            Platform("p3", 1420f, 480f, 460f, 140f),
            Platform("wall_3_2", 1620f, 0f, 28f, 376f)
        ),
        boxes = listOf(
            PhysicsBox("box_3_2", x = 310f, y = 430f)
        ),
        switches = listOf(
            SwitchNode(
                id = "plate_3_2",
                x = 960f,
                y = 464f,
                width = 64f,
                height = 16f,
                kind = SwitchKind.PRESSURE_PLATE,
                signalId = "sig_3_2",
                requiresWeight = true
            )
        ),
        doors = listOf(
            Door("door_3_2", x = 1620f, y = 376f, width = 28f, height = 104f, linkedSignalId = "sig_3_2")
        ),
        shards = listOf(
            CollectibleShard("s_3_2_a", 560f, 390f),
            CollectibleShard("s_3_2_b", 1240f, 270f),
            CollectibleShard("s_3_2_c", 1520f, 415f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_3_2", 720f, 480f)
        ),
        hazards = listOf(
            HazardZone("hz_3_2_a", 460f, 585f, 210f, 35f),
            HazardZone("hz_3_2_b", 1090f, 585f, 330f, 35f)
        ),
        exit = PortalExit(x = 1750f, y = 394f)
    )

    // =========================================================================
    // WORLD 4: ENERGY (Light-Beam Mirrors & Magnetic Polarity Coils)
    // =========================================================================

    private fun level4_1() = LevelDefinition(
        id = "4-1",
        worldId = WorldId.ENERGY,
        orderIndex = 6,
        worldWidth = 1780f,
        worldHeight = 620f,
        spawn = Vec2(80f, 410f),
        platforms = listOf(
            Platform("p1", 0f, 490f, 920f, 130f),
            // Raised platform for mirror 1
            Platform("m_plat1", 430f, 390f, 150f, 22f),
            // Hard-light bridge energized when photo-sensor is hit!
            Platform(
                id = "hl_4_1",
                x = 920f,
                y = 490f,
                width = 320f,
                height = 24f,
                type = PlatformType.HARD_LIGHT,
                linkedSignalId = "sig_beam_4_1",
                isSolidActive = false
            ),
            Platform("p2", 1240f, 490f, 540f, 130f),
            Platform("wall_4_1", 1500f, 0f, 28f, 386f)
        ),
        // Emitter shoots straight right at y=350 -> hits mirror1 at (505,350) -> rotate to aim Up-Right (step 7) or Right (step 0) into mirror2 at (820,350) -> aim Down-Right/Up into sensor!
        // Let's make it crisp: Emitter at (180, 340) shoots right (1,0) -> hits Mirror1 at (505, 340).
        // Mirror1 initially points Down (step 2). Player rotates Mirror1 to point Right (step 0) -> hits Mirror2 at (810, 340).
        // Mirror2 initially points Up (step 6). Player rotates Mirror2 to point Down-Right (step 1) or Right (step 0) -> hits PhotoSensor at (810, 180) when Up (step 6), wait: let's have Mirror2 start at step 2 (Down), and PhotoSensor at (810, 180) so rotating Mirror2 to step 6 (Up) hits it!
        emitters = listOf(
            LightEmitter("em_4_1", x = 180f, y = 340f, dirX = 1f, dirY = 0f)
        ),
        mirrors = listOf(
            MirrorNode("mir_4_1_a", x = 505f, y = 340f, angleStep = 2), // Rotate to 0 (Right)
            MirrorNode("mir_4_1_b", x = 810f, y = 340f, angleStep = 4)  // Rotate to 6 (Up) -> hits sensor at (810, 175)
        ),
        switches = listOf(
            SwitchNode(
                id = "photo_4_1",
                x = 785f,
                y = 155f,
                width = 50f,
                height = 42f,
                kind = SwitchKind.PHOTO_SENSOR,
                signalId = "sig_beam_4_1"
            )
        ),
        doors = listOf(
            Door("door_4_1", x = 1500f, y = 386f, width = 28f, height = 104f, linkedSignalId = "sig_beam_4_1")
        ),
        shards = listOf(
            CollectibleShard("s_4_1_a", 505f, 260f),
            CollectibleShard("s_4_1_b", 1080f, 420f),
            CollectibleShard("s_4_1_c", 1360f, 420f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_4_1", 720f, 490f)
        ),
        hazards = listOf(
            HazardZone("hz_4_1", 920f, 585f, 320f, 35f)
        ),
        exit = PortalExit(x = 1640f, y = 404f)
    )

    private fun level4_2() = LevelDefinition(
        id = "4-2",
        worldId = WorldId.ENERGY,
        orderIndex = 7,
        worldWidth = 1880f,
        worldHeight = 620f,
        spawn = Vec2(80f, 410f),
        platforms = listOf(
            Platform("p1", 0f, 490f, 520f, 130f),
            Platform("p2", 640f, 490f, 1240f, 130f),
            // Raised glass track for the metallic crate so magnets pull/push it cleanly onto the switch
            Platform("track", 720f, 360f, 660f, 24f, type = PlatformType.ICE),
            Platform("track_stop", 1308f, 290f, 24f, 70f),
            Platform("step_up", 560f, 430f, 110f, 22f),
            Platform("wall_4_2", 1600f, 0f, 28f, 386f)
        ),
        boxes = listOf(
            PhysicsBox("metal_4_2", x = 800f, y = 305f, isMetal = true, mass = 1.0f)
        ),
        magnets = listOf(
            MagnetCoil(
                id = "mag_4_2_left",
                x = 710f,
                y = 290f,
                mode = MagnetMode.ATTRACT,
                range = 420f,
                strength = 1150f,
                linkedSignalId = "sig_mag_4_2"
            ),
            MagnetCoil(
                id = "mag_4_2_right",
                x = 1334f,
                y = 290f,
                mode = MagnetMode.REPEL,
                range = 620f,
                strength = 1350f,
                linkedSignalId = "sig_mag_4_2"
            )
        ),
        switches = listOf(
            // Toggling this flips left coil to REPEL and right coil to ATTRACT -> zooms metal crate right onto plate!
            SwitchNode(
                id = "mag_sw_4_2",
                x = 920f,
                y = 438f,
                width = 46f,
                height = 52f,
                kind = SwitchKind.MAGNET_SWITCH,
                signalId = "sig_mag_4_2"
            ),
            SwitchNode(
                id = "plate_4_2",
                x = 1244f,
                y = 344f,
                width = 64f,
                height = 16f,
                kind = SwitchKind.PRESSURE_PLATE,
                signalId = "sig_door_4_2",
                requiresWeight = true
            )
        ),
        doors = listOf(
            Door("door_4_2", x = 1600f, y = 386f, width = 28f, height = 104f, linkedSignalId = "sig_door_4_2")
        ),
        shards = listOf(
            CollectibleShard("s_4_2_a", 615f, 365f),
            CollectibleShard("s_4_2_b", 1040f, 290f),
            CollectibleShard("s_4_2_c", 1470f, 420f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_4_2", 720f, 490f)
        ),
        hazards = listOf(
            HazardZone("hz_4_2", 520f, 585f, 120f, 35f)
        ),
        exit = PortalExit(x = 1740f, y = 404f)
    )

    // =========================================================================
    // WORLD 5: BALANCE (Tilting Center-of-Gravity Beams & Judo Trials)
    // =========================================================================

    private fun level5_1() = LevelDefinition(
        id = "5-1",
        worldId = WorldId.BALANCE,
        orderIndex = 8,
        worldWidth = 1760f,
        worldHeight = 620f,
        spawn = Vec2(75f, 410f),
        platforms = listOf(
            Platform("p1", 0f, 490f, 360f, 130f),
            // Tilting Judo balance beam across courtyard gap
            Platform("beam_5_1", 410f, 480f, 300f, 20f, type = PlatformType.TILTING_BEAM),
            Platform("p2", 760f, 490f, 1000f, 130f),
            Platform("dojo_step", 950f, 415f, 240f, 22f),
            Platform("wall_5_1", 1490f, 0f, 28f, 386f)
        ),
        judoShrines = listOf(
            JudoShrine(
                id = "shrine_5_1",
                x = 1070f,
                y = 415f,
                trialId = JudoTrialId.STILL_CENTER,
                signalId = "sig_judo_5_1"
            )
        ),
        doors = listOf(
            Door("door_5_1", x = 1490f, y = 386f, width = 28f, height = 104f, linkedSignalId = "sig_judo_5_1")
        ),
        shards = listOf(
            CollectibleShard("s_5_1_a", 560f, 405f),
            CollectibleShard("s_5_1_b", 870f, 420f),
            CollectibleShard("s_5_1_c", 1360f, 420f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_5_1", 820f, 490f)
        ),
        hazards = listOf(
            HazardZone("hz_5_1", 360f, 585f, 400f, 35f)
        ),
        exit = PortalExit(x = 1620f, y = 404f)
    )

    private fun level5_2() = LevelDefinition(
        id = "5-2",
        worldId = WorldId.BALANCE,
        orderIndex = 9,
        worldWidth = 1940f,
        worldHeight = 620f,
        spawn = Vec2(75f, 410f),
        platforms = listOf(
            Platform("p1", 0f, 490f, 320f, 130f),
            Platform("beam_5_2_a", 370f, 475f, 280f, 20f, type = PlatformType.TILTING_BEAM),
            Platform("mid_5_2", 700f, 465f, 220f, 155f),
            Platform("beam_5_2_b", 970f, 445f, 290f, 20f, type = PlatformType.TILTING_BEAM),
            Platform("p_end", 1310f, 490f, 630f, 130f),
            Platform("wall_5_2", 1680f, 0f, 28f, 386f)
        ),
        judoShrines = listOf(
            JudoShrine(
                id = "shrine_5_2",
                x = 1480f,
                y = 490f,
                trialId = JudoTrialId.KUZUSHI_REDIRECT,
                signalId = "sig_judo_5_2"
            )
        ),
        doors = listOf(
            Door("door_5_2", x = 1680f, y = 386f, width = 28f, height = 104f, linkedSignalId = "sig_judo_5_2")
        ),
        shards = listOf(
            CollectibleShard("s_5_2_a", 510f, 395f),
            CollectibleShard("s_5_2_b", 1115f, 365f),
            CollectibleShard("s_5_2_c", 1590f, 415f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_5_2", 780f, 465f)
        ),
        hazards = listOf(
            HazardZone("hz_5_2_a", 320f, 585f, 380f, 35f),
            HazardZone("hz_5_2_b", 920f, 585f, 390f, 35f)
        ),
        exit = PortalExit(x = 1805f, y = 404f)
    )

    // =========================================================================
    // WORLD 6: THE WIRED (Final Synthesis of All Mechanics)
    // =========================================================================

    private fun level6_1() = LevelDefinition(
        id = "6-1",
        worldId = WorldId.THE_WIRED,
        orderIndex = 10,
        worldWidth = 2040f,
        worldHeight = 640f,
        spawn = Vec2(75f, 430f),
        platforms = listOf(
            Platform("p1", 0f, 510f, 560f, 130f),
            Platform("lowg_ledge", 620f, 340f, 220f, 24f),
            Platform("p2", 900f, 510f, 1140f, 130f),
            Platform("track_6_1", 1080f, 370f, 540f, 24f, type = PlatformType.ICE),
            Platform("track_6_1_stop", 1564f, 300f, 24f, 70f),
            Platform(
                id = "hl_6_1",
                x = 560f,
                y = 510f,
                width = 340f,
                height = 22f,
                type = PlatformType.HARD_LIGHT,
                linkedSignalId = "sig_beam_6_1",
                isSolidActive = false
            ),
            Platform("wall_6_1", 1770f, 0f, 28f, 406f)
        ),
        gravityZones = listOf(
            GravityZone("gz_6_1", 520f, 160f, 360f, 350f, GravityZoneMode.LOW_GRAVITY)
        ),
        emitters = listOf(
            LightEmitter("em_6_1", x = 150f, y = 420f, dirX = 1f, dirY = 0f)
        ),
        mirrors = listOf(
            MirrorNode("mir_6_1", x = 440f, y = 420f, angleStep = 2) // Rotate to 7 (Up-Right) or 6 (Up) -> sensor at (440, 220)
        ),
        boxes = listOf(
            PhysicsBox("metal_6_1", x = 1160f, y = 315f, isMetal = true)
        ),
        magnets = listOf(
            MagnetCoil(
                id = "mag_6_1",
                x = 1580f,
                y = 305f,
                mode = MagnetMode.REPEL,
                range = 520f,
                strength = 1280f,
                linkedSignalId = "sig_mag_6_1"
            )
        ),
        switches = listOf(
            SwitchNode(
                id = "photo_6_1",
                x = 415f,
                y = 200f,
                width = 50f,
                height = 42f,
                kind = SwitchKind.PHOTO_SENSOR,
                signalId = "sig_beam_6_1"
            ),
            SwitchNode(
                id = "mag_sw_6_1",
                x = 1230f,
                y = 458f,
                width = 46f,
                height = 52f,
                kind = SwitchKind.MAGNET_SWITCH,
                signalId = "sig_mag_6_1"
            ),
            SwitchNode(
                id = "plate_6_1",
                x = 1500f,
                y = 354f,
                width = 64f,
                height = 16f,
                kind = SwitchKind.PRESSURE_PLATE,
                signalId = "sig_door_6_1",
                requiresWeight = true
            )
        ),
        doors = listOf(
            Door("door_6_1", x = 1770f, y = 406f, width = 28f, height = 104f, linkedSignalId = "sig_door_6_1")
        ),
        shards = listOf(
            CollectibleShard("s_6_1_a", 730f, 270f),
            CollectibleShard("s_6_1_b", 1340f, 300f),
            CollectibleShard("s_6_1_c", 1680f, 440f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_6_1", 960f, 510f)
        ),
        hazards = listOf(
            HazardZone("hz_6_1", 560f, 605f, 340f, 35f)
        ),
        exit = PortalExit(x = 1900f, y = 424f)
    )

    private fun level6_2() = LevelDefinition(
        id = "6-2",
        worldId = WorldId.THE_WIRED,
        orderIndex = 11,
        worldWidth = 2160f,
        worldHeight = 640f,
        spawn = Vec2(75f, 430f),
        allowInkDrawing = true,
        platforms = listOf(
            Platform("p1", 0f, 510f, 380f, 130f),
            // Gap for Ink Bridge creation!
            Platform("p2", 650f, 480f, 300f, 160f),
            // Tilting balance beam in Wired void
            Platform("beam_6_2", 1000f, 470f, 280f, 20f, type = PlatformType.TILTING_BEAM),
            Platform("p3", 1330f, 510f, 830f, 130f),
            Platform("core_step", 1500f, 425f, 220f, 24f),
            Platform("wall_6_2", 1870f, 0f, 28f, 406f)
        ),
        gravityZones = listOf(
            GravityZone("gz_6_2", 650f, 160f, 300f, 320f, GravityZoneMode.LOW_GRAVITY)
        ),
        judoShrines = listOf(
            JudoShrine(
                id = "shrine_6_2",
                x = 1610f,
                y = 425f,
                trialId = JudoTrialId.EQUILIBRIUM_FLOW,
                signalId = "sig_final_core"
            )
        ),
        doors = listOf(
            Door("door_6_2", x = 1870f, y = 406f, width = 28f, height = 104f, linkedSignalId = "sig_final_core")
        ),
        shards = listOf(
            CollectibleShard("s_6_2_a", 515f, 390f),
            CollectibleShard("s_6_2_b", 800f, 250f),
            CollectibleShard("s_6_2_c", 1140f, 385f)
        ),
        checkpoints = listOf(
            Checkpoint("cp_6_2", 720f, 480f),
            Checkpoint("cp_6_2_b", 1380f, 510f)
        ),
        hazards = listOf(
            HazardZone("hz_6_2_a", 380f, 605f, 270f, 35f),
            HazardZone("hz_6_2_b", 950f, 605f, 380f, 35f)
        ),
        exit = PortalExit(x = 2005f, y = 424f)
    )
}
