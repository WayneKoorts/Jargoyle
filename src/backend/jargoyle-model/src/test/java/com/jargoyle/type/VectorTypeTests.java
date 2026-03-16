package com.jargoyle.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.WrapperOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;

public class VectorTypeTests {

    private ResultSet mockResultSet;
    private PreparedStatement mockPreparedStatement;
    private WrapperOptions mockWrapperOptions = mock(WrapperOptions.class);

    private final VectorType sut = new VectorType();

    @BeforeEach
    void setUp() {
        mockResultSet = mock(ResultSet.class);
        mockPreparedStatement = mock(PreparedStatement.class);
    }

    @Test
    void deepCopy_withNull_returnsNull() {
        var copy = sut.deepCopy(null);

        assertThat(copy).isNull();
    }

    @Test
    void deepCopy_returnsIndependentCopy() {
        var inputVal = new float[] { 0.1f, 0.2f, 0.3f };

        var copiedVal = sut.deepCopy(inputVal);

        assertThat(copiedVal).isEqualTo(inputVal);
        assertThat(copiedVal).isNotSameAs(inputVal);
    }

    @Test
    void nullSafeGet_nullFromDb_returnsNull() throws SQLException {
        when(mockResultSet.getString(1)).thenReturn(null);

        var result = sut.nullSafeGet(mockResultSet, 1, mockWrapperOptions);

        assertThat(result).isNull();
    }

    @Test
    void nullSafeGet_vectorFromDb_returnsFloatArray() throws SQLException {
        when(mockResultSet.getString(1)).thenReturn("[0.1,0.2,0.3]");

        var result = sut.nullSafeGet(mockResultSet, 1, mockWrapperOptions);

        assertThat(result).isEqualTo(new float[] { 0.1f, 0.2f, 0.3f });
    }

    @Test
    void nullSafeSet_nullInput_callsSetNull() throws SQLException {
        sut.nullSafeSet(mockPreparedStatement, null, 1, mockWrapperOptions);

        verify(mockPreparedStatement).setNull(1, SqlTypes.OTHER);
    }

    @Test
    void nullSafeSet_floatArrayInput_writesVectorString() throws SQLException {
        sut.nullSafeSet(mockPreparedStatement, new float[] { 0.1f, 0.2f, 0.3f }, 1, mockWrapperOptions);

        var captor = ArgumentCaptor.forClass(PGobject.class);
        verify(mockPreparedStatement).setObject(anyInt(), captor.capture());
        var savedPgObject = captor.getValue();
        assertThat(savedPgObject.getType()).isEqualTo("vector");
        assertThat(savedPgObject.getValue()).isEqualTo("[0.1, 0.2, 0.3]");
    }

}
