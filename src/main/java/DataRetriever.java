import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DataRetriever {
    public Dish findDishById(Integer id) throws SQLException {
        Dish dish = null;
        String sql = "SELECT d.id, d.name, d.dish_type, i.id as ing_id, i.name as ing_name, " +
                "i.price, i.category_ingredient " +
                "FROM Dish d " +
                "LEFT JOIN Ingredient i ON d.id = i.id_dish " +
                "WHERE d.id = ?";

        try (Connection conn = DBConnection.getDBConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                if (dish == null) {
                    dish = new Dish(
                            rs.getInt("id"),
                            rs.getString("name"),
                            DishTypeEnum.valueOf(rs.getString("dish_type"))
                    );
                }

                int ingId = rs.getInt("ing_id");
                if (ingId != 0) {
                    Ingredient ing = new Ingredient(
                            ingId,
                            rs.getString("ing_name"),
                            rs.getDouble("price"),
                            CategoryEnum.valueOf(rs.getString("category_ingredient")),
                            dish
                    );
                    dish.getIngredient().add(ing);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        if (dish == null) {
            throw new RuntimeException("Plat non trouvé avec l'id : " + id);
        }
        return dish;
    }

    public List<Ingredient> findIngredients(int page, int size) throws SQLException {
        List<Ingredient> ingredients = new ArrayList<>();
        int offset = (page - 1) * size;

        String sql = "SELECT i.*, d.name AS dish_name, d.dish_type " +
                "FROM Ingredient i " +
                "LEFT JOIN Dish d ON i.id_dish = d.id " +
                "ORDER BY i.id LIMIT ? OFFSET ?";

        try (Connection conn = DBConnection.getDBConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, size);
            pstmt.setInt(2, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Dish associatedDish = null;
                    int dishId = rs.getInt("id_dish");

                    if (dishId > 0) {
                        String typeStr = rs.getString("dish_type");
                        DishTypeEnum type = (typeStr != null) ? DishTypeEnum.valueOf(typeStr) : null;

                        associatedDish = new Dish(
                                dishId,
                                rs.getString("dish_name"),
                                type
                        );
                    }
                    ingredients.add(new Ingredient(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            CategoryEnum.valueOf(rs.getString("category_ingredient")),
                            associatedDish
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lors de la récupération des ingrédients", e);
        }
        return ingredients;
    }

    public List<Ingredient> createIngredients(List<Ingredient> newIngredients) throws SQLException {
        Connection conn = null;
        String sql = "INSERT INTO Ingredient (id, name, price, category_ingredient, id_dish) " +
                "VALUES (nextval('ingredient_id_seq'), ?, ?, ?::category, ?)";

        try {
            conn = DBConnection.getDBConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Ingredient ingredient : newIngredients) {
                    pstmt.setString(1, ingredient.getName());
                    pstmt.setDouble(2, ingredient.getPrice());
                    pstmt.setString(3, ingredient.getCategory().name());

                    if (ingredient.getDish() != null) {
                        pstmt.setInt(4, ingredient.getDish().getId());
                    } else {
                        pstmt.setNull(4, java.sql.Types.INTEGER);
                    }
                    pstmt.executeUpdate();

                    try (PreparedStatement pstmtSeq = conn.prepareStatement("SELECT lastval()")) {
                        try (ResultSet rs = pstmtSeq.executeQuery()) {
                            if (rs.next()) {
                                ingredient.setId(rs.getInt(1));
                            }
                        }
                    }
                }
                conn.commit();
            }
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw new RuntimeException(e.getMessage());
        } finally {
            if (conn != null) conn.close();
        }
        return newIngredients;
    }

    public Dish saveDish(Dish dishToSave) throws SQLException {
        Connection conn = null;
        try {
            conn = DBConnection.getDBConnection();
            conn.setAutoCommit(false);

            if (dishToSave.getId() == 0) {
                String sqlInsert = "INSERT INTO Dish (name, dish_type) VALUES (?, ?::dish_type) " +
                        "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name " +
                        "RETURNING id";

                try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                    pstmt.setString(1, dishToSave.getName());
                    pstmt.setString(2, dishToSave.getDishType().name());

                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            dishToSave.setId(rs.getInt("id"));
                        }
                    }
                }
            } else {
                String sqlUpdate = "UPDATE Dish SET name = ?, dish_type = ?::dish_type WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
                    pstmt.setString(1, dishToSave.getName());
                    pstmt.setString(2, dishToSave.getDishType().name());
                    pstmt.setInt(3, dishToSave.getId());
                    pstmt.executeUpdate();
                }
            }

            if (dishToSave.getIngredient() != null) {
                saveAssociatedIngredients(conn, dishToSave);
            }

            conn.commit();
            System.out.println("Plat '" + dishToSave.getName() + "' sauvegardé avec succès.");

        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw new RuntimeException("Erreur lors de la sauvegarde du plat : " + e.getMessage(), e);
        } finally {
            if (conn != null) conn.close();
        }
        return dishToSave;
    }

    private void saveAssociatedIngredients(Connection conn, Dish dish) throws SQLException {
        String sqlUpdateNull = "UPDATE Ingredient SET id_dish = NULL WHERE id_dish = ?";
        try (PreparedStatement pstmtNull = conn.prepareStatement(sqlUpdateNull)) {
            pstmtNull.setInt(1, dish.getId());
            pstmtNull.executeUpdate();
        }

        String sql = "INSERT INTO Ingredient (name, price, category_ingredient, id_dish) " +
                "VALUES (?, ?, ?::category, ?) " +
                "ON CONFLICT (name) DO UPDATE SET price = EXCLUDED.price, id_dish = EXCLUDED.id_dish";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Ingredient ing : dish.getIngredient()) {
                pstmt.setString(1, ing.getName());
                pstmt.setDouble(2, ing.getPrice());
                pstmt.setString(3, ing.getCategory().name());
                pstmt.setInt(4, dish.getId());
                pstmt.executeUpdate();
            }
        }
    }

    public List<Dish> findDishsByIngredientName(String ingredientName) throws SQLException {
        List<Dish> dishes = new ArrayList<>();
        String sql = "SELECT DISTINCT d.id, d.name, d.dish_type " +
                "FROM Dish d " +
                "JOIN Ingredient i ON d.id = i.id_dish " +
                "WHERE i.name ILIKE ?";

        try (Connection conn = DBConnection.getDBConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + ingredientName + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    dishes.add(new Dish(
                            rs.getInt("id"),
                            rs.getString("name"),
                            DishTypeEnum.valueOf(rs.getString("dish_type"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erreur lors de la recherche par ingrédient", e);
        }
        return dishes;
    }

    public List<Ingredient> findIngredientsByCriteria(
            String ingredientName,
            CategoryEnum category,
            String dishName,
            int page,
            int size
    ) throws SQLException {
        List<Ingredient> ingredients = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT i.id, i.name, i.price, i.category_ingredient, i.id_dish, d.name as dish_name " +
                        "FROM Ingredient i " +
                        "LEFT JOIN Dish d ON i.id_dish = d.id " +
                        "WHERE 1=1"
        );

        if (ingredientName != null && !ingredientName.isEmpty()) {
            sql.append(" AND i.name ILIKE ?");
        }
        if (category != null) {
            sql.append(" AND i.category_ingredient = ?::category");
        }
        if (dishName != null && !dishName.isEmpty()) {
            sql.append(" AND d.name ILIKE ?");
        }

        sql.append(" ORDER BY i.id LIMIT ? OFFSET ?");

        try (Connection conn = DBConnection.getDBConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;

            if (ingredientName != null && !ingredientName.isEmpty()) {
                pstmt.setString(paramIndex++, "%" + ingredientName + "%");
            }
            if (category != null) {
                pstmt.setString(paramIndex++, category.name());
            }
            if (dishName != null && !dishName.isEmpty()) {
                pstmt.setString(paramIndex++, "%" + dishName + "%");
            }

            int offset = (page - 1) * size;
            pstmt.setInt(paramIndex++, size);
            pstmt.setInt(paramIndex++, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Dish dish = null;
                    if (rs.getString("dish_name") != null) {
                        dish = new Dish(rs.getInt("id_dish"), rs.getString("dish_name"), null);
                    }

                    ingredients.add(new Ingredient(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("price"),
                            CategoryEnum.valueOf(rs.getString("category_ingredient")),
                            dish
                    ));
                }
            }
        }
        return ingredients;
    }

}
