package com.jargoyle.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

/**
 * Custom Hibernate {@link UserType} that maps a Java {@code float[]} to PostgreSQL's
 * pgvector {@code vector} type. Converts between the database's string representation
 * (e.g. {@code [0.1,0.2,0.3]}) and a native float array.
 *
 * <p>Register on entity fields with {@code @Type(VectorType.class)}.</p>
 *
 * @see org.hibernate.annotations.Type
 */
public class VectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return SqlTypes.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public float[] deepCopy(float[] value) {
        if (value == null) return null;
        return value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        // Parse incoming vector string (e.g. "[0.1, 0.2, 0.3]" into float[]).
        var strVal = rs.getString(position);
        if (strVal == null) {
            return null;
        }

        var trimmed = strVal.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        var parts = trimmed.split(",");
        var values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Float.parseFloat(parts[i].trim());
        }

        return values;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] floatArray, int position, WrapperOptions options)
            throws SQLException {
        if (floatArray == null) {
            st.setNull(position, SqlTypes.OTHER);
            return;
        }

        var pgObject = new PGobject();
        pgObject.setType("vector");
        pgObject.setValue(Arrays.toString(floatArray));
        st.setObject(position, pgObject);
    }
}
