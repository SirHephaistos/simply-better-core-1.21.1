package as.sirhephaistos.simplybetter.library;

public record PositionDTO(String dimensionId, double x, double y, double z, float yaw, float pitch) {
    /**
     * Creates a PositionDTO from explicit parameters
     *
     * @param dimensionId Dimension identifier as a namespaced string, e.g. "minecraft:overworld".
     * @param x           Block X coordinate
     * @param y           Block Y coordinate
     * @param z           Block Z coordinate
     * @param yaw         Player yaw rotation
     * @param pitch       Player pitch rotation
     *
     */
    public PositionDTO {
    }
}
