package as.sirhephaistos.simplybetter.core.db;

import as.sirhephaistos.simplybetter.library.HomeDTO;
import as.sirhephaistos.simplybetter.library.PositionDTO;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <h1><img src="https://docs.godsmg.com/~gitbook/image?url=https%3A%2F%2F602320278-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_hKBWF%252Ficon%252FF3ga5TrIrIMXtWecHo3z%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252017_44_38.png%3Falt%3Dmedia%26token%3D8c3f45e4-ed6f-47ab-a4ab-474d24fa3bb3&width=32&dpr=1&quality=100&sign=2c456f01&sv=2"></img>
 * &nbsp;CRUD manager for {@link HomeDTO}
 * <img src="https://docs-sbs.godsmg.com/~gitbook/image?url=https%3A%2F%2F655127117-files.gitbook.io%2F%7E%2Ffiles%2Fv0%2Fb%2Fgitbook-x-prod.appspot.com%2Fo%2Forganizations%252FpIa3Cyk1OAYwYiLI3sxf%252Fsites%252Fsite_ofAiW%252Ficon%252F9SRBPTo3OKBsw5DvBwL3%252FChatGPT%2520Image%252025%2520oct.%25202025%252C%252000_07_28.png%3Falt%3Dmedia%26token%3D396dda36-5693-4638-b53e-59bf0770f309&width=32&dpr=1&quality=100&sign=55c114e6&sv=2"></img> </h1>
 * <h2>Create Methods</h2>
 * <ul>
 *     <li>{@link #createHome}:</br>
 *         Creates a new home entry for given {@code playerUUID} and given {@code name}. And returns the created {@link HomeDTO}.</li>
 *     <li>{@link #upsertHome}:</br>
 *         Creates or updates a home entry for given {@code playerUUID} and given {@code name}.
 *         If a home with the same name and owner already exists, it is deleted along with its position.
 *         And returns the created or updated {@link HomeDTO}.</li>
 * </ul>
 * <h2>Read Methods</h2>
 * <ul>
 *     <li>{@link #getHomeById}:</br>
 *         Retrieves a home by its ID. And returns an {@link Optional} containing the {@link HomeDTO} if found, or empty if not found. </li>
 *     <li>{@link #getHomeByNameAndOwner}:</br>
 *         Retrieves a home by its name and owner UUID. And returns an {@link Optional} containing the {@link HomeDTO} if found, or empty if not found. </li>
 * </ul>
 * <h2>Update Methods</h2>
 * <h3>There's no usage for update methods since homes are either created or deleted.</h3>
 * <h2>Delete Methods</h2>
 * <ul>
 *     <li>{@link #deleteHomeById}:</br>
 *         Deletes a home by its ID.</li>
 *     <li>{@link #deleteHomeByNameAndOwner}:</br>
 *         Deletes a home by its name and owner UUID.</li>
 * </ul>
 *
 *<h3>General Information</h3>
 * @codeBaseStatus Complete / On going / Needs review / Deprecated / Abandoned / To do
 * @testingStatus AwaitingJUnitTests / JUnitTestsWritten / JUnitTestsPassed / JUnitTestsFailed / NoJUnitTestsPlanned
 * @author Sirhephaistos
 * @version 1.0
 */
@SuppressWarnings("ClassCanBeRecord")
public final class HomesCrudManager {
    private final DatabaseManager db;
    private static PositionsCrudManager positionsCrudManager;

    public HomesCrudManager(@NotNull DatabaseManager db) {
        this.db = db;
        positionsCrudManager = new PositionsCrudManager(db);
    }

    /**
     * Private helper to map a {@link HomeDTO} from a {@link ResultSet}.
     * @param rs the {@link ResultSet}, positioned at the desired row to map.
     * @return the mapped {@link HomeDTO}
     * @throws SQLException on SQL errors coming from JDBC.
     * @throws IllegalArgumentException if rs is null.
     * @throws IllegalStateException if any non-nullable column is null or unexpected SQL NULLs are encountered.
     */
    private static HomeDTO mapHomes(ResultSet rs) throws SQLException {
        if (rs == null) throw new IllegalArgumentException("rs is null");
        final long id = rs.getLong("id");
        if (rs.wasNull()) throw new IllegalStateException("id is null");
        @NotNull final String name = rs.getString("name");
        if (name == null) throw new IllegalStateException("name is null");
        @NotNull final String ownerUuid = rs.getString("owner_uuid");
        if (ownerUuid == null) throw new IllegalStateException("owner_uuid is null");
        final long positionId = rs.getLong("position_id");
        if (rs.wasNull()) throw new IllegalStateException("position_id is null");
        @NotNull final String createdAt = rs.getString("created_at");
        if (createdAt == null) throw new IllegalStateException("created_at is null");
        return new HomeDTO(id, name, createdAt, ownerUuid, positionId);
    }

    /**
     * Reformats a string to be used as a home name.
     * Removes all non-alphanumeric characters and converts to lowercase.
     * @param s the string to reformat.
     * @return the reformatted string.
     * @throws IllegalArgumentException if s is null.
     */
    private static String strReformat(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String is null");
        }
        return s.replaceAll("[^a-zA-Z0-9_]", "").replace(" ", "_").toLowerCase();
    }
    // -- Create

    /**
     * Creates a new home entry for given {@code playerUUID} and given {@code name}.
     * @param name the name of the home.
     * @param createdAt timestap string of creation time.
     * @param ownerUuid {@code playerUUID} of the owner of the home.
     * @param positionDTO {@link PositionDTO} of the home location.
     * @return the created {@link HomeDTO}.
     * @throws IllegalArgumentException if any argument is null.
     * @throws IllegalStateException if position creation fails or created home cannot be retrieved.
     * @throws RuntimeException on SQL errors.
     */
    public HomeDTO createHome(@NotNull String name, @NotNull String createdAt, @NotNull String ownerUuid, @NotNull PositionDTO positionDTO) {
        name = strReformat(name);
        final String sql = "INSERT INTO sb_homes (name, created_at, owner_uuid, position_id) VALUES (?, ?, ?, ?) RETURNING id, name, created_at, owner_uuid, position_id;";
        if (positionDTO.id() == null) {
            positionDTO = positionsCrudManager.createPosition(positionDTO);
            if (positionDTO.id() == null) {
                throw new IllegalStateException("Failed to create position for home");
            }
        }
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, name);
            ps.setString(2, createdAt);
            ps.setString(3, ownerUuid);
            ps.setLong(4, positionDTO.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("INSERT RETURNING did not return a row");
                return mapHomes(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error creating home", e);
        }
    }

    /**
     * Creates or updates a home entry for given {@code playerUUID} and given {@code name}.
     * If a home with the same name and owner already exists, it is deleted along with its position.
     * @param name the name of the home.
     * @param createdAt timestap string of creation time.
     * @param ownerUuid {@code playerUUID} of the owner of the home.
     * @param positionDTO {@link PositionDTO} of the home location.
     * @return the created or updated {@link HomeDTO}.
     * @throws IllegalArgumentException if any argument is null.
     * @throws IllegalStateException if position creation fails or created home cannot be retrieved.
     * @throws RuntimeException on SQL errors.
     */
    public HomeDTO upsertHome(@NotNull String name, @NotNull String createdAt, @NotNull String ownerUuid, @NotNull PositionDTO positionDTO) {
        final Optional<HomeDTO> existingHomeOpt = getHomeByNameAndOwner(name, ownerUuid);
        if (existingHomeOpt.isPresent()) {
            try {
                positionsCrudManager.deletePositionById(positionDTO.id());
                deleteHomeByNameAndOwner(name, ownerUuid);
            } catch (Exception e) {
                throw new RuntimeException("Error deleting existing home for upsert", e);
            }
        }
        positionDTO = positionsCrudManager.createPosition(positionDTO);
        return createHome(name, createdAt, ownerUuid, positionDTO);
    }

    // -- Read

    /**
     * Retrieves a home by its ID.
     * @param id the ID of the home.
     * @return an {@link Optional} containing the {@link HomeDTO} if found, or empty if not found.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<HomeDTO> getHomeById(long id) {
        final String sql = "SELECT id, name, created_at, owner_uuid, position_id FROM sb_homes WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapHomes(rs));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving home by id="+id, e);
        }
    }

    /**
     * Retrieves a home by its name and owner UUID.
     * @param name the name of the home.
     * @param ownerUuid the UUID of the owner of the home.
     * @return an {@link Optional} containing the {@link HomeDTO} if found, or empty if not found.
     * @throws IllegalArgumentException if any argument is null.
     * @throws RuntimeException on SQL errors.
     */
    public Optional<HomeDTO> getHomeByNameAndOwner(@NotNull String name, @NotNull String ownerUuid) {
        name = strReformat(name);
        final String sql = "SELECT id, name, created_at, owner_uuid, position_id FROM sb_homes WHERE name = ? AND owner_uuid = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, name);
            ps.setString(2, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapHomes(rs));
                } else {
                    return Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving home by name="+name+" and ownerUuid="+ownerUuid, e);
        }
    }

    public List<HomeDTO> getHomesForPlayer(@NotNull String ownerUuid) {
        final String sql = "SELECT id, name, created_at, owner_uuid, position_id FROM sb_homes WHERE owner_uuid = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                List<HomeDTO> homes = new ArrayList<>();
                while (rs.next()) {
                    homes.add(mapHomes(rs));
                }
                return homes;
            }
        }catch (SQLException e){
            throw new RuntimeException("Error retrieving homes for ownerUuid="+ownerUuid, e);
        }
    }

    /**
     * Scans for homes centered at a given position within a specified radius. Uses Euclidean distance.
     * @param centerPosition the center position to scan around.
     * @param radius the radius to scan within.
     * @return a list of {@link HomeDTO} within the specified radius.
     * @throws IllegalArgumentException if centerPosition is null.
     * @throws RuntimeException on SQL errors.
     */
    public List<HomeDTO> scanHomesCenteredAtPosition(@NotNull PositionDTO centerPosition, double radius) {
        if (centerPosition == null) throw new IllegalArgumentException("centerPosition is null");
        var dimId = centerPosition.dimensionId();
        var centerX = centerPosition.x();
        var centerZ = centerPosition.z();
        var minx = centerX - radius;
        var maxx = centerX + radius;
        var minz = centerZ - radius;
        var maxz = centerZ + radius;
        final String sql = """
            SELECT h.id, h.name, h.created_at, h.owner_uuid, h.position_id
            FROM sb_homes h
            JOIN sb_positions p ON h.position_id = p.id
            WHERE p.dimension_id = ?
              AND p.x BETWEEN ? AND ?
              AND p.z BETWEEN ? AND ?
              AND ((p.x - ?) * (p.x - ?) + (p.z - ?) * (p.z - ?)) <= ?;
            """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, dimId);
            ps.setDouble(i++, minx);
            ps.setDouble(i++, maxx);
            ps.setDouble(i++, minz);
            ps.setDouble(i++, maxz);
            ps.setDouble(i++, centerX);
            ps.setDouble(i++, centerX);
            ps.setDouble(i++, centerZ);
            ps.setDouble(i++, centerZ);
            ps.setDouble(i, radius * radius);
            try (ResultSet rs = ps.executeQuery()) {
                List<HomeDTO> homes = new ArrayList<>();
                while (rs.next()) {
                    homes.add(mapHomes(rs));
                }
                return homes;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error retrieving homes for position="+centerPosition, e);
        }
    }
    // -- Update
    // not needed for now
    // -- Delete

    /**
     * Deletes a home by its ID.
     * @param id the ID of the home to delete.
     * @throws RuntimeException on SQL errors.
     */
    public void deleteHomeById(long id) {
        final String sql = "DELETE FROM sb_homes WHERE id = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting home by id="+id, e);
        }
    }

    /**
     * Deletes a home by its name and owner UUID.
     * @param name the name of the home to delete.
     * @param ownerUuid the UUID of the owner of the home to delete.
     * @throws IllegalArgumentException if any argument is null.
     * @throws RuntimeException on SQL errors.
     */
    public void deleteHomeByNameAndOwner(@NotNull String name, @NotNull String ownerUuid) {
        name = strReformat(name);
        final String sql = "DELETE FROM sb_homes WHERE name = ? AND owner_uuid = ?;";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, name);
            ps.setString(2, ownerUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting home by name="+name+" and ownerUuid="+ownerUuid, e);
        }
    }
}
