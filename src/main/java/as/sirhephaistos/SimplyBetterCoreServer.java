package as.sirhephaistos;

import as.sirhephaistos.simplybetter.core.db.DatabaseManager;
import as.sirhephaistos.simplybetter.core.db.WarpCrudManager;
import as.sirhephaistos.simplybetter.library.WarpDTO;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

public class SimplyBetterCoreServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "simplybetter-core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** singleton global instance accessible via db()*/
    private static DatabaseManager DB;

    /** Global singleton accessor
     *  @return the singleton DatabaseManager instance
     * */
    public static DatabaseManager db() {
        return DB;
    }

    private static WarpCrudManager warpCrudManager;
    public static WarpCrudManager warpCrudManager() { return warpCrudManager; }

    @Override
    public void onInitializeServer() {
        LOGGER.info("[{}] Initializing database...", MOD_ID);
        try {
            DB = DatabaseManager.createDefault();
            DB.init();
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to initialize database: {}", MOD_ID, e.getMessage(), e);
            return;
        }
        LOGGER.info("[{}] database initialized.", MOD_ID);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                if (DB != null) {
                    DB.shutdown(Duration.ofSeconds(5));
                    LOGGER.info("[{}] database shut down.", MOD_ID);
                }
            } catch (Exception e) {
                LOGGER.error("[{}] Failed to shut down database: {}", MOD_ID, e.getMessage(), e);
            }
        });

        LOGGER.info("[{}] Initializing modules", MOD_ID);
        warpCrudManager = new WarpCrudManager(DB);
        LOGGER.info("[{}] Modules initialized", MOD_ID);

        try {
            runWarpCrudSmokeTest();
        } catch (Exception e) {
            LOGGER.error("[{}] Smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
    }

    private void runWarpCrudSmokeTest() {
        final String creator = "system"; // tu as déjà inséré ce joueur
        final String nameA = "sbcore-test-warp";
        final String nameB = "sbcore-test-warp-renamed";

        // cleanup préalable
        warpCrudManager.deleteWarp(nameB);
        warpCrudManager.deleteWarp(nameA);

        // create
        var created = warpCrudManager.createWarp(
                nameA, creator,
                new as.sirhephaistos.simplybetter.library.PositionDTO("minecraft:overworld", 0, 64, 0, 0f, 0f)
        );
        LOGGER.info("[{}] create -> id={}, name={}, createdBy={}", MOD_ID, created.id(), created.name(), created.createdBy());

        // get by name
        var loaded = warpCrudManager.getWarpByName(nameA).orElseThrow();
        LOGGER.info("[{}] getByName -> id={}, dim={}", MOD_ID, loaded.id(), loaded.position().dimensionId());

        // list by creator
        var byCreator = warpCrudManager.getWarpsByCreator(creator);
        LOGGER.info("[{}] listByCreator(system) -> {}", MOD_ID, byCreator.size());

        // list all
        var all1 = warpCrudManager.getAllWarps();
        LOGGER.info("[{}] listAll(before update) -> {}", MOD_ID, all1.size());

        // update position
        var updated = warpCrudManager.updateWarpPosition(
                nameA,
                new as.sirhephaistos.simplybetter.library.PositionDTO("minecraft:overworld", 10, 70, -5, 90f, 0f)
        );
        LOGGER.info("[{}] updatePosition -> x={}, y={}, z={}, yaw={}", MOD_ID,
                updated.position().x(), updated.position().y(), updated.position().z(), updated.position().yaw());

        // rename
        var renamed = warpCrudManager.renameWarp(nameA, nameB);
        LOGGER.info("[{}] rename -> newName={}", MOD_ID, renamed.name());

        // delete
        var deleted = warpCrudManager.deleteWarp(nameB);
        LOGGER.info("[{}] delete -> present={}", MOD_ID, deleted.isPresent());

        // list all final
        var all2 = warpCrudManager.getAllWarps();
        LOGGER.info("[{}] listAll(after delete) -> {}", MOD_ID, all2.size());
    }

}
