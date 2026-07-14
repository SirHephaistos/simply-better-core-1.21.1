package as.sirhephaistos.util;

import as.sirhephaistos.simplybetter.library.PositionDTO;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class TeleportUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-teleport");

    private TeleportUtils() {}

    public static int teleportPlayerToPosition(
            ServerPlayer player,
            PositionDTO position,
            String errorMessage,
            String successMessage,
            CommandSourceStack src
    ) {
        try {
            ResourceLocation dimId = ResourceLocation.tryParse(position.dimensionId());
            if (dimId == null) {
                LOGGER.error("Invalid dimension ID: {}", position.dimensionId());
                MutableComponent text = Component.literal(errorMessage)
                        .append(Component.literal("'. Invalid dimension."));
                src.sendFailure(text);
                return 0;
            }

            ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, dimId);
            ServerLevel targetWorld = Objects.requireNonNull(player.getServer()).getLevel(targetKey);
            if (targetWorld == null) {
                LOGGER.error("Target world is null for dimension: {}", dimId);
                MutableComponent text = Component.literal(errorMessage)
                        .append(Component.literal("'. World not found."));
                src.sendFailure(text);
                return 0;
            }

            BlockPos targetPos = BlockPos.containing(position.x(), position.y(), position.z());
            ChunkPos chunkPos = new ChunkPos(targetPos);
            targetWorld.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 1, player.getId());
            targetWorld.getChunk(chunkPos.x, chunkPos.z);
            player.teleportTo(targetWorld, position.x(), position.y(), position.z(), position.yRot(), position.xRot());

            MutableComponent text = Component.literal(successMessage);
            src.sendSuccess(() -> text, false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Failed to teleport player to position: {}", position, e);
            MutableComponent text = Component.literal(errorMessage)
                    .append(Component.literal("'. Please try again later."));
            src.sendFailure(text);
            return 0;
        }
    }
}