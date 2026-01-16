import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DataRetriever {

    private final DBConnection dbConnection = new DBConnection();

    // 1. Récupérer un plat par son ID
    public Dish findDishById(Integer id) {
        Connection connection = dbConnection.getConnection();
        try {
            String sql = "SELECT id, name, dish_type, price FROM dish WHERE id = ?";
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Dish dish = new Dish();
                dish.setId(rs.getInt("id"));
                dish.setName(rs.getString("name"));
                dish.setDishType(DishTypeEnum.valueOf(rs.getString("dish_type")));
                dish.setPrice(rs.getObject("price") != null ? rs.getDouble("price") : null);
                dish.setIngredients(findIngredientByDishId(id));
                return dish;
            }
            throw new RuntimeException("Dish not found " + id);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lors de la récupération du plat : " + e.getMessage());
        } finally {
            dbConnection.closeConnection(connection);
        }
    }

    // 2. Pagination des ingrédients
    public List<Ingredient> findIngredients(int page, int size) {
        List<Ingredient> ingredients = new ArrayList<>();
        String sql = "SELECT * FROM ingredient ORDER BY name LIMIT ? OFFSET ?";
        Connection conn = dbConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, (page - 1) * size);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ingredients.add(mapResultSetToIngredient(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur pagination : " + e.getMessage());
        } finally {
            dbConnection.closeConnection(conn);
        }
        return ingredients;
    }

    // 3. Créer des ingrédients
    public List<Ingredient> createIngredients(List<Ingredient> newIngredients) {
        if (newIngredients == null || newIngredients.isEmpty()) return List.of();
        Connection conn = dbConnection.getConnection();
        List<Ingredient> savedIngredients = new ArrayList<>();
        try {
            conn.setAutoCommit(false);
            String sql = "INSERT INTO ingredient (id, name, category, price, required_quantity) VALUES (?, ?, ?::category, ?, ?) RETURNING id";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Ingredient ing : newIngredients) {
                    ps.setInt(1, ing.getId() != null ? ing.getId() : getNextSerialValue(conn, "ingredient", "id"));
                    ps.setString(2, ing.getName());
                    ps.setString(3, ing.getCategory().name());
                    ps.setDouble(4, ing.getPrice());
                    ps.setObject(5, ing.getQuantity(), Types.DOUBLE);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        ing.setId(rs.getInt(1));
                        savedIngredients.add(ing);
                    }
                }
                conn.commit();
                return savedIngredients;
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.closeConnection(conn);
        }
    }

    // 4. Sauvegarder/Mettre à jour un plat
    public Dish saveDish(Dish toSave) {
        String upsertDishSql = """
                INSERT INTO dish (id, price, name, dish_type)
                VALUES (?, ?, ?, ?::dish_type)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name,
                    dish_type = EXCLUDED.dish_type,
                    price = EXCLUDED.price
                RETURNING id
                """;

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            Integer dishId;

            try (PreparedStatement ps = conn.prepareStatement(upsertDishSql)) {
                ps.setInt(1, toSave.getId() != null ? toSave.getId() : getNextSerialValue(conn, "dish", "id"));
                ps.setObject(2, toSave.getPrice(), Types.DOUBLE);
                ps.setString(3, toSave.getName());
                ps.setString(4, toSave.getDishType().name());

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    dishId = rs.getInt(1);
                }
            }

            detachIngredients(conn, dishId, toSave.getIngredients());
            attachIngredients(conn, dishId, toSave.getIngredients());

            conn.commit();
            return findDishById(dishId);
        } catch (SQLException e) {
            throw new RuntimeException("Erreur sauvegarde plat : " + e.getMessage());
        }
    }

    // 5. Trouver les plats contenant un ingrédient spécifique
    public List<Dish> findDishsByIngredientName(String search) {
        List<Dish> dishes = new ArrayList<>();
        // Jointure pour trouver les plats via leurs ingrédients
        String sql = "SELECT DISTINCT d.* FROM dish d JOIN ingredient i ON d.id = i.id_dish WHERE i.name ILIKE ?";
        Connection conn = dbConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + search + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Dish d = new Dish();
                d.setId(rs.getInt("id"));
                d.setName(rs.getString("name"));
                d.setDishType(DishTypeEnum.valueOf(rs.getString("dish_type")));
                d.setPrice(rs.getObject("price") != null ? rs.getDouble("price") : null);
                dishes.add(d);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur recherche par ingrédient : " + e.getMessage());
        } finally {
            dbConnection.closeConnection(conn);
        }
        return dishes;
    }

    // 6. Recherche multicritères
    public List<Ingredient> findIngredientsByCriteria(String name, CategoryEnum cat, String dishName, int page, int size) {
        List<Ingredient> ingredients = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT i.* FROM ingredient i LEFT JOIN dish d ON i.id_dish = d.id WHERE 1=1");

        if (name != null) sql.append(" AND i.name ILIKE ?");
        if (cat != null) sql.append(" AND i.category = ?::category");
        if (dishName != null) sql.append(" AND d.name ILIKE ?");
        sql.append(" LIMIT ? OFFSET ?");

        Connection conn = dbConnection.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (name != null) ps.setString(idx++, "%" + name + "%");
            if (cat != null) ps.setString(idx++, cat.name());
            if (dishName != null) ps.setString(idx++, "%" + dishName + "%");
            ps.setInt(idx++, size);
            ps.setInt(idx, (page - 1) * size);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ingredients.add(mapResultSetToIngredient(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur recherche critères : " + e.getMessage());
        } finally {
            dbConnection.closeConnection(conn);
        }
        return ingredients;
    }



    // --- MÉTHODES PRIVÉES / UTILITAIRES ---

    private Ingredient mapResultSetToIngredient(ResultSet rs) throws SQLException {
        Ingredient ing = new Ingredient();
        ing.setId(rs.getInt("id"));
        ing.setName(rs.getString("name"));
        ing.setPrice(rs.getDouble("price"));
        ing.setCategory(CategoryEnum.valueOf(rs.getString("category")));
        ing.setQuantity(rs.getObject("required_quantity") != null ? rs.getDouble("required_quantity") : null);
        return ing;
    }

    private void detachIngredients(Connection conn, Integer dishId, List<Ingredient> ingredients) throws SQLException {
        if (ingredients == null || ingredients.isEmpty()) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE ingredient SET id_dish = NULL WHERE id_dish = ?")) {
                ps.setInt(1, dishId);
                ps.executeUpdate();
            }
            return;
        }
        String inClause = ingredients.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = String.format("UPDATE ingredient SET id_dish = NULL WHERE id_dish = ? AND id NOT IN (%s)", inClause);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dishId);
            int index = 2;
            for (Ingredient ing : ingredients) {
                ps.setInt(index++, ing.getId());
            }
            ps.executeUpdate();
        }
    }

    private void attachIngredients(Connection conn, Integer dishId, List<Ingredient> ingredients) throws SQLException {
        if (ingredients == null || ingredients.isEmpty()) return;
        String sql = "UPDATE ingredient SET id_dish = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Ingredient ing : ingredients) {
                ps.setInt(1, dishId);
                ps.setInt(2, ing.getId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<Ingredient> findIngredientByDishId(Integer idDish) {
        Connection connection = dbConnection.getConnection();
        List<Ingredient> ingredients = new ArrayList<>();
        try {
            String sql = "SELECT id, name, price, category, required_quantity FROM ingredient WHERE id_dish = ?";
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, idDish);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ingredients.add(mapResultSetToIngredient(rs));
            }
            return ingredients;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            dbConnection.closeConnection(connection);
        }
    }

    private int getNextSerialValue(Connection conn, String tableName, String columnName) throws SQLException {
        String sequenceName;
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_get_serial_sequence(?, ?)")) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) sequenceName = rs.getString(1);
                else throw new RuntimeException("No sequence found");
            }
        }
        String syncSql = String.format("SELECT setval('%s', (SELECT COALESCE(MAX(%s), 0) FROM %s))", sequenceName, columnName, tableName);
        conn.createStatement().executeQuery(syncSql);
        try (ResultSet rs = conn.createStatement().executeQuery(String.format("SELECT nextval('%s')", sequenceName))) {
            rs.next();
            return rs.getInt(1);
        }
    }
}