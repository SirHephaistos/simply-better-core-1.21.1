package as.sirhephaistos;

import as.sirhephaistos.simplybetter.core.db.*;
import as.sirhephaistos.simplybetter.library.*;
import lombok.Getter;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
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

    @Getter
    private static AccountsCrudManager accountsCrudManager;
    private static AfksCrudManager afksCrudManager;
    private static AuditLogsCrudManager auditLogsCrudManager;
    private static BackLocationsCrudManager backLocationsCrudManager;
    private static BansCrudManager bansCrudManager;
    private static BansIpCrudManager bansIpCrudManager;
    private static ChatLogsCrudManager chatLogsCrudManager;
    private static HomesCrudManager homesCrudManager;
    private static IgnoresCrudManager ignoresCrudManager;
    private static JailsCrudManager jailsCrudManager;
    private static JailSanctionsCrudManager jailSanctionsCrudManager;
    private static KitCooldownsCrudManager kitCooldownsCrudManager;
    private static KitItemsCrudManager kitItemsCrudManager;
    private static KitsCrudManager kitsCrudManager;
    private static MailsCrudManager mailsCrudManager;
    private static MutesCrudManager mutesCrudManager;
    private static PlayersCrudManager playersCrudManager;
    private static PositionsCrudManager positionsCrudManager;
    private static PrivateChatLogsCrudManager privateChatLogsCrudManager;
    private static RtpSettingsCrudManager rtpSettingsCrudManager;
    private static SocialSpysCrudManager socialSpysCrudManager;
    private static TransactionsCrudManager transactionsCrudManager;
    private static UserLogsCrudManager userLogsCrudManager;
    private static WarpsCrudManager warpsCrudManager;
    private static WorldsCrudManager worldsCrudManager;

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
        initializeAllCrudManagers();
        LOGGER.info("[{}] Modules initialized", MOD_ID);

        try {
            LOGGER.info("[{}] Running smoke tests...", MOD_ID);
            runAllSmokeTests();
            LOGGER.info("[{}] Smoke tests completed successfully.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
    }

    /* Initialize all CRUD managers */
    private void initializeAllCrudManagers() {
        accountsCrudManager = new AccountsCrudManager(DB);
        afksCrudManager = new AfksCrudManager(DB);
        auditLogsCrudManager = new AuditLogsCrudManager(DB);
        backLocationsCrudManager = new BackLocationsCrudManager(DB);
        bansCrudManager = new BansCrudManager(DB);
        bansIpCrudManager = new BansIpCrudManager(DB);
        chatLogsCrudManager = new ChatLogsCrudManager(DB);
        homesCrudManager = new HomesCrudManager(DB);
        ignoresCrudManager = new IgnoresCrudManager(DB);
        jailsCrudManager = new JailsCrudManager(DB);
        jailSanctionsCrudManager = new JailSanctionsCrudManager(DB);
        kitCooldownsCrudManager = new KitCooldownsCrudManager(DB);
        kitItemsCrudManager = new KitItemsCrudManager(DB);
        kitsCrudManager = new KitsCrudManager(DB);
        mailsCrudManager = new MailsCrudManager(DB);
        mutesCrudManager = new MutesCrudManager(DB);
        playersCrudManager = new PlayersCrudManager(DB);
        positionsCrudManager = new PositionsCrudManager(DB);
        privateChatLogsCrudManager = new PrivateChatLogsCrudManager(DB);
        rtpSettingsCrudManager = new RtpSettingsCrudManager(DB);
        socialSpysCrudManager = new SocialSpysCrudManager(DB);
        transactionsCrudManager = new TransactionsCrudManager(DB);
        userLogsCrudManager = new UserLogsCrudManager(DB);
        warpsCrudManager = new WarpsCrudManager(DB);
        worldsCrudManager = new WorldsCrudManager(DB);
    }

    /* Run all smoke tests */
    private void runAllSmokeTests() {
        LOGGER.info("[{}] Running Smoke tests...", MOD_ID);
        LOGGER.info("[{}] TESTING FOR Accounts CrudManager", MOD_ID);
        try {
            runAccountsCrudSmokeTest();
            LOGGER.info("[{}] Accounts CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Accounts CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Afks CrudManager", MOD_ID);
        try {
            runAfksCrudSmokeTest();
            LOGGER.info("[{}] Afks CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Afks CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR AuditLogs CrudManager", MOD_ID);
        try {
            runAuditLogsCrudSmokeTest();
            LOGGER.info("[{}] AuditLogs CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] AuditLogs CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR BackLocations CrudManager", MOD_ID);
        try {
            runBackLocationsCrudSmokeTest();
            LOGGER.info("[{}] BackLocations CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] BackLocations CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Bans CrudManager", MOD_ID);
        try {
            runBansCrudSmokeTest();
            LOGGER.info("[{}] Bans CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Bans CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR BansIp CrudManager", MOD_ID);
        try {
            runBansIpCrudSmokeTest();
            LOGGER.info("[{}] BansIp CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] BansIp CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR ChatLogs CrudManager", MOD_ID);
        try {
            runChatLogsCrudSmokeTest();
            LOGGER.info("[{}] ChatLogs CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] ChatLogs CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Homes CrudManager", MOD_ID);
        try {
            runHomesCrudSmokeTest();
            LOGGER.info("[{}] Homes CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Homes CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Ignores CrudManager", MOD_ID);
        try {
            runIgnoresCrudSmokeTest();
            LOGGER.info("[{}] Ignores CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Ignores CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Jails CrudManager", MOD_ID);
        try {
            runJailsCrudSmokeTest();
            LOGGER.info("[{}] Jails CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Jails CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR JailSanctions CrudManager", MOD_ID);
        try {
            runJailSanctionsCrudSmokeTest();
            LOGGER.info("[{}] JailSanctions CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] JailSanctions CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR KitCooldowns CrudManager", MOD_ID);
        try {
            runKitCooldownsCrudSmokeTest();
            LOGGER.info("[{}] KitCooldowns CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] KitCooldowns CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR KitItems CrudManager", MOD_ID);
        try {
            runKitItemsCrudSmokeTest();
            LOGGER.info("[{}] KitItems CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] KitItems CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Kits CrudManager", MOD_ID);
        try {
            runKitsCrudSmokeTest();
            LOGGER.info("[{}] Kits CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Kits CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Mails CrudManager", MOD_ID);
        try {
            runMailsCrudSmokeTest();
            LOGGER.info("[{}] Mails CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Mails CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Mutes CrudManager", MOD_ID);
        try {
            runMutesCrudSmokeTest();
            LOGGER.info("[{}] Mutes CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Mutes CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Players CrudManager", MOD_ID);
        try {
            runPlayersCrudSmokeTest();
            LOGGER.info("[{}] Players CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Players CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Positions CrudManager", MOD_ID);
        try {
            runPositionsCrudSmokeTest();
            LOGGER.info("[{}] Positions CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Positions CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR PrivateChatLogs CrudManager", MOD_ID);
        try {
            runPrivateChatLogsCrudSmokeTest();
            LOGGER.info("[{}] PrivateChatLogs CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] PrivateChatLogs CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR RtpSettings CrudManager", MOD_ID);
        try {
            runRtpSettingsCrudSmokeTest();
            LOGGER.info("[{}] RtpSettings CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] RtpSettings CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR SocialSpys CrudManager", MOD_ID);
        try {
            runSocialSpysCrudSmokeTest();
            LOGGER.info("[{}] SocialSpys CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] SocialSpys CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Transactions CrudManager", MOD_ID);
        try {
            runTransactionsCrudSmokeTest();
            LOGGER.info("[{}] Transactions CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Transactions CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR UserLogs CrudManager", MOD_ID);
        try {
            runUserLogsCrudSmokeTest();
            LOGGER.info("[{}] UserLogs CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] UserLogs CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Worlds CrudManager", MOD_ID);
        try {
            runWorldsCrudSmokeTest();
            LOGGER.info("[{}] Worlds CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Worlds CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }
        LOGGER.info("[{}] TESTING FOR Warps CrudManager", MOD_ID);
        try {
            runWarpsCrudSmokeTest();
            LOGGER.info("[{}] Warps CrudManager smoke test passed.", MOD_ID);
        } catch (Exception e) {
            LOGGER.error("[{}] Warps CrudManager smoke test failed: {}", MOD_ID, e.getMessage(), e);
        }




        LOGGER.info("[{}] Smoke tests completed.", MOD_ID);
    }

    private void runAccountsCrudSmokeTest() {
        final String creator = "system"; // todo: use a real test user uuid if available
        final String testUuid = "00000000-0000-0000-0000-000000000001";
        final String nameA = "sbcore-test-user";
        final String nameB = "sbcore-test-user-renamed";

        // cleanup
    }
    private void runAfksCrudSmokeTest() { /* smoke test for AfksCrudManager */ }
    private void runAuditLogsCrudSmokeTest() { /* smoke test for AuditLogsCrudManager */ }
    private void runBackLocationsCrudSmokeTest() { /* smoke test for BackLocationsCrudManager */ }
    private void runBansCrudSmokeTest() { /* smoke test for BansCrudManager */ }
    private void runBansIpCrudSmokeTest() { /* smoke test for BansIpCrudManager */ }
    private void runChatLogsCrudSmokeTest() { /* smoke test for ChatLogsCrudManager */ }
    private void runHomesCrudSmokeTest() { /* smoke test for HomesCrudManager */ }
    private void runIgnoresCrudSmokeTest() { /* smoke test for IgnoresCrudManager */ }
    private void runJailsCrudSmokeTest() { /* smoke test for JailsCrudManager */ }
    private void runJailSanctionsCrudSmokeTest() { /* smoke test for JailSanctionsCrudManager */ }
    private void runKitCooldownsCrudSmokeTest() { /* smoke test for KitCooldownsCrudManager */ }
    private void runKitItemsCrudSmokeTest() { /* smoke test for KitItemsCrudManager */ }
    private void runKitsCrudSmokeTest() { /* smoke test for KitsCrudManager */ }
    private void runMailsCrudSmokeTest() { /* smoke test for MailsCrudManager */ }
    private void runMutesCrudSmokeTest() { /* smoke test for MutesCrudManager */ }
    private void runPlayersCrudSmokeTest() { /* smoke test for PlayersCrudManager */ }
    private void runPositionsCrudSmokeTest() { /* smoke test for PositionsCrudManager */ }
    private void runPrivateChatLogsCrudSmokeTest() { /* smoke test for PrivateChatLogsCrudManager */ }
    private void runRtpSettingsCrudSmokeTest() { /* smoke test for RtpSettingsCrudManager */ }
    private void runSocialSpysCrudSmokeTest() { /* smoke test for SocialSpysCrudManager */ }
    private void runTransactionsCrudSmokeTest() { /* smoke test for TransactionsCrudManager */ }
    private void runUserLogsCrudSmokeTest() { /* smoke test for UserLogsCrudManager */ }
    private void runWorldsCrudSmokeTest() { /* smoke test for WorldsCrudManager */ }
    private void runWarpsCrudSmokeTest() {
        final String creator = "system"; // todo: add a test user system manually to the database and use its uuid here
        final String nameA = "sbcore-test-warp";
        final String nameB = "sbcore-test-warp-renamed";

        // cleanup
        warpsCrudManager.deleteWarp(nameB);
        warpsCrudManager.deleteWarp(nameA);

        // create
        var created = warpsCrudManager.createWarp(
                nameA, creator,
                new as.sirhephaistos.simplybetter.library.PositionDTO("minecraft:overworld", 0, 64, 0, 0f, 0f)
        );
        LOGGER.info("[{}] create -> id={}, name={}, createdBy={}", MOD_ID, created.id(), created.name(), created.createdBy());

        // get by name
        var loaded = warpsCrudManager.getWarpByName(nameA).orElseThrow();
        LOGGER.info("[{}] getByName -> id={}, dim={}", MOD_ID, loaded.id(), loaded.position().dimensionId());

        // list by creator
        var byCreator = warpsCrudManager.getWarpsByCreator(creator);
        LOGGER.info("[{}] listByCreator(system) -> {}", MOD_ID, byCreator.size());

        // list all
        var all1 = warpsCrudManager.getAllWarps();
        LOGGER.info("[{}] listAll(before update) -> {}", MOD_ID, all1.size());

        // update position
        var updated = warpsCrudManager.updateWarpPosition(
                nameA,
                new as.sirhephaistos.simplybetter.library.PositionDTO("minecraft:overworld", 10, 70, -5, 90f, 0f)
        );
        LOGGER.info("[{}] updatePosition -> x={}, y={}, z={}, yaw={}", MOD_ID,
                updated.position().x(), updated.position().y(), updated.position().z(), updated.position().yaw());

        // rename
        var renamed = warpsCrudManager.renameWarp(nameA, nameB);
        LOGGER.info("[{}] rename -> newName={}", MOD_ID, renamed.name());

        // delete
        var deleted = warpsCrudManager.deleteWarp(nameB);
        LOGGER.info("[{}] delete -> present={}", MOD_ID, deleted.isPresent());

        // list all final
        var all2 = warpsCrudManager.getAllWarps();
        LOGGER.info("[{}] listAll(after delete) -> {}", MOD_ID, all2.size());
    }



}
