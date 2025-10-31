package as.sirhephaistos.simplybetter.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PositionDTO(
        @Nullable Long id,
        @NotNull String dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {}
