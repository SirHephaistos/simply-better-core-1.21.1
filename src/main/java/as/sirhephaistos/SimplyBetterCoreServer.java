package as.sirhephaistos;

import as.sirhephaistos.simplybetter.core.PlayerSessionTracker;
import as.sirhephaistos.simplybetter.core.db.*;
import as.sirhephaistos.simplybetter.library.*;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

public class SimplyBetterCoreServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "simplybetter-core";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final boolean force = true;
    private final PlayerSessionTracker sessionTracker = new PlayerSessionTracker();

    /** singleton global instance accessible via db()*/
    private static DatabaseManager DB;

    /** Global singleton accessor
     *  @return the singleton DatabaseManager instance
     * */
    public static DatabaseManager db() {
        return DB;
    }

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

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // A server that stopped without a clean shutdown leaves rows claiming to be online
            // forever, and the web app reads that flag.
            corePlayersRepository.markAllOffline();

            if (playersCrudManager.hasAnyPlayers() && !force) LOGGER.info("[{}] players found in database, ignoring usercache file", MOD_ID);
            else loadPlayersFromUserCache(server);
        });
        
        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnect);

        LOGGER.info("[{}] Initializing modules", MOD_ID);
        initializeAllCrudManagers();
        LOGGER.info("[{}] Modules initialized", MOD_ID);
    }

    private void onPlayerDisconnect(ServerGamePacketListenerImpl connection, MinecraftServer minecraftServer) {
        try {
            ServerPlayer player = connection.getPlayer();
            String uuid = player.getUUID().toString();
            String now = java.time.Instant.now().toString();
            long sessionSeconds = sessionTracker.onLeave(uuid);
            if (sessionSeconds <= 0) {
                return;
            }

            Optional<PlayerDTO> existing = playersCrudManager.getPlayerByUuid(uuid);
            if (existing.isEmpty()) {
                // Should not happen if JOIN upserted correctly, mais on sécurise
                PlayerDTO dto = new PlayerDTO(
                        uuid,
                        player.getGameProfile().getName(),
                        now,
                        now,
                        sessionSeconds,
                        false,
                        null,
                        null
                );
                playersCrudManager.upsertPlayer(dto);
                return;
            }

            PlayerDTO p = existing.get();
            long newPlaytime = p.playtimeSeconds() + sessionSeconds;

            PlayerDTO updated = new PlayerDTO(
                    p.uuid(),
                    p.name(),
                    p.firstSeen(),
                    now,              // last_seen on disconnect
                    newPlaytime,
                    p.canBeIgnored(),
                    p.nickname(),
                    p.lastSeenPositionId()
            );

            playersCrudManager.upsertPlayer(updated);
            corePlayersRepository.onLeave(player.getUUID());
            LOGGER.info("[{}] playtime updated: {}", MOD_ID, updated.playtimeSeconds());
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to update playtime: {}", MOD_ID, e.getMessage(), e);
        }
    }

    private void onPlayerJoin(ServerGamePacketListenerImpl connection, PacketSender packetSender, MinecraftServer server) {
        try {
            ServerPlayer player =  connection.getPlayer();
            String uuid = player.getUUID().toString();
            sessionTracker.onJoin(uuid);
            LOGGER.info("[{}] started playtime counter for this game session for player: {}", MOD_ID, player.getGameProfile().getName());
            String name = player.getGameProfile().getName();
            String now = java.time.Instant.now().toString();
            Optional<PlayerDTO> existing = playersCrudManager.getPlayerByUuid(uuid);
            PlayerDTO playerDTO ;
            PlayerDTO dto;
            if (existing.isPresent()) {
                PlayerDTO p = existing.get();
                dto = new PlayerDTO(
                        p.uuid(),
                        name,              // update name just in case
                        p.firstSeen(),
                        now,               // update last_seen
                        p.playtimeSeconds(),
                        p.canBeIgnored(),
                        p.nickname(),
                        p.lastSeenPositionId()
                );
            } else {
                dto = new PlayerDTO(
                        uuid,
                        name,
                        now,       // first_seen
                        now,       // last_seen
                        0L,
                        false,
                        null,
                        null
                );
            }
            playersCrudManager.upsertPlayer(dto);

            // The shared registry, written at the same moment: this is the first point at which
            // the identity is known, which is why simply-better owns it rather than the web mod.
            corePlayersRepository.onJoin(player.getUUID(), name);

            LOGGER.info("[{}] upserted player on join: {}", MOD_ID, name);
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to upsert player on join: {}", MOD_ID, e.getMessage(), e);
        }
    }

    private void loadPlayersFromUserCache(MinecraftServer server) {
        LOGGER.info("[{}] Loading usercache... to retrieve existing player uuid", MOD_ID);
        java.nio.file.Path path = server.getFile("usercache.json");
        if (!java.nio.file.Files.exists(path)) {
            LOGGER.warn("[{}] Usercache.json not found at {}",MOD_ID, path.toAbsolutePath());
            return;
        }
        // usercache.json format: [{"name":"PlayerName","uuid":"player-uuid-string","expiresOn";"yyyy-mm-dd hh:mm:seconds"},...]
        // name -> PlayerDTO.name , uuid -> PlayerDTO.uuid, expiresOn - 1 month -> PlayerDTO.firstSeen , expiresOn - 1 month -> PlayerDTO.lastSeen
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path)) {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            UserCacheEntry[] entries = gson.fromJson(reader, UserCacheEntry[].class); // serialize array of entries
            LOGGER.info("[{}] Usercache.json loaded: {} entries.", MOD_ID, entries.length);
            int loadedCount = 0;
            for (UserCacheEntry entry : entries) {
//                LOGGER.info("[{}] Usercache.json loaded: {} , {} , {}", MOD_ID,entry.getName(), entry.getUuid(), entry.getExpiresOn());
                String uuid = entry.getUuid();
                String name = entry.getName();
                String iso = toIsoInstant(entry.getExpiresOn());
                String adjustedExpiresOn = java.time.Instant.parse(iso)
                        .minus(java.time.Duration.ofDays(30))
                        .toString();
                Optional<PlayerDTO> existingPlayerOpt = playersCrudManager.getPlayerByUuid(uuid);
//                LOGGER.info("[{}] Player lookup by uuid: {} , found: {}", MOD_ID, uuid, existingPlayerOpt.isPresent());
                if (existingPlayerOpt.isEmpty()) {
                    // create new player entry
                    PlayerDTO newPlayer = new PlayerDTO(
                            uuid,
                            name,
                            adjustedExpiresOn,
                            adjustedExpiresOn,
                            0L,
                            true,
                            null,
                            null
                    );
//                    LOGGER.info("[{}] Player created: {},{},{},{},{},{},{},{}", MOD_ID, newPlayer.uuid(), newPlayer.name(), newPlayer.firstSeen(), newPlayer.lastSeen(), newPlayer.playtimeSeconds(), newPlayer.canBeIgnored(), newPlayer.nickname(), newPlayer.lastSeenPositionId());
                    var created = playersCrudManager.createPlayer(newPlayer);
//                    LOGGER.info("[{}] Player created in database:  {},{},{},{},{},{},{},{}", MOD_ID, created.uuid(), created.name(), created.firstSeen(), created.lastSeen(), created.playtimeSeconds(), created.canBeIgnored(), created.nickname(), created.lastSeenPositionId());
                    loadedCount++;
                }
            }
            LOGGER.info("[{}] Loaded {} players from usercache.json", MOD_ID, loadedCount);
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to load usercache: {}", MOD_ID, e.getMessage(), e);
        }
    }
    private String toIsoInstant(String rawExpiresOn) {
        // rawExpiresOn = "2025-12-16 22:08:42 +0100"
        String[] parts = rawExpiresOn.trim().split(" ");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Unexpected expiresOn format: " + rawExpiresOn);
        }

        String date = parts[0];   // "2025-12-16"
        String time = parts[1];   // "22:08:42"
        String offset = parts[2]; // "+0100"

        // "+0100" -> "+01:00"
        if (offset.length() == 5 && (offset.charAt(0) == '+' || offset.charAt(0) == '-')) {
            offset = offset.substring(0, 3) + ":" + offset.substring(3);
        }

        // "2025-12-16T22:08:42+01:00"
        return date + "T" + time + offset;
    }


    @Getter @Setter
    private static final class UserCacheEntry {
        String name;
        String uuid;
        String expiresOn;
    }

    /**
     * The shared player registry, core.players -- not one of simply-better's own tables.
     *
     * <p>Every other mod on this database foreign-keys onto it, and nothing was writing it. See
     * {@link as.sirhephaistos.simplybetter.core.db.CorePlayersRepository}.
     */
    @Getter private static CorePlayersRepository corePlayersRepository;

    @Getter private static AccountsCrudManager accountsCrudManager;
    @Getter private static AfksCrudManager afksCrudManager;
    @Getter private static AuditLogsCrudManager auditLogsCrudManager;
    @Getter private static BackLocationsCrudManager backLocationsCrudManager;
    @Getter private static BansCrudManager bansCrudManager;
    @Getter private static BansIpCrudManager bansIpCrudManager;
    @Getter private static ChatLogsCrudManager chatLogsCrudManager;
    @Getter private static HomesCrudManager homesCrudManager;
    @Getter private static IgnoresCrudManager ignoresCrudManager;
    @Getter private static JailsCrudManager jailsCrudManager;
    @Getter private static JailSanctionsCrudManager jailSanctionsCrudManager;
    @Getter private static KitCooldownsCrudManager kitCooldownsCrudManager;
    @Getter private static KitItemsCrudManager kitItemsCrudManager;
    @Getter private static KitsCrudManager kitsCrudManager;
    @Getter private static MailsCrudManager mailsCrudManager;
    @Getter private static MutesCrudManager mutesCrudManager;
    @Getter private static PlayersCrudManager playersCrudManager;
    @Getter private static PositionsCrudManager positionsCrudManager;
    @Getter private static PrivateChatLogsCrudManager privateChatLogsCrudManager;
    @Getter private static RtpSettingsCrudManager rtpSettingsCrudManager;
    @Getter private static SocialSpysCrudManager socialSpysCrudManager;
    @Getter private static TransactionsCrudManager transactionsCrudManager;
    @Getter private static UserLogsCrudManager userLogsCrudManager;
    @Getter private static WarpsCrudManager warpsCrudManager;
    @Getter private static WorldsCrudManager worldsCrudManager;

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
        corePlayersRepository = new CorePlayersRepository(DB);
        positionsCrudManager = new PositionsCrudManager(DB);
        privateChatLogsCrudManager = new PrivateChatLogsCrudManager(DB);
        rtpSettingsCrudManager = new RtpSettingsCrudManager(DB);
        socialSpysCrudManager = new SocialSpysCrudManager(DB);
        transactionsCrudManager = new TransactionsCrudManager(DB);
        userLogsCrudManager = new UserLogsCrudManager(DB);
        warpsCrudManager = new WarpsCrudManager(DB, positionsCrudManager);
        worldsCrudManager = new WorldsCrudManager(DB);
    }
}
