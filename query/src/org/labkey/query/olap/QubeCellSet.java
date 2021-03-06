/*
 * Copyright (c) 2014-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.query.olap;

import org.labkey.api.util.MemTracker;
import org.olap4j.AllocationPolicy;
import org.olap4j.Axis;
import org.olap4j.Cell;
import org.olap4j.CellSet;
import org.olap4j.CellSetAxis;
import org.olap4j.CellSetAxisMetaData;
import org.olap4j.CellSetMetaData;
import org.olap4j.OlapException;
import org.olap4j.OlapStatement;
import org.olap4j.Position;
import org.olap4j.impl.Named;
import org.olap4j.impl.NamedListImpl;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Level;
import org.olap4j.metadata.Member;
import org.olap4j.metadata.NamedList;
import org.olap4j.metadata.Property;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.RowId;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
* Created by matthew on 3/24/14.
*/
public class QubeCellSet implements CellSet
{
    // COLUMN,ROWS,PAGES,CHAPTERS,SECTIONS
    private final Cube _cube;
    private final List<CellSetAxis> _axes = new ArrayList<>(3);
    private final int _columnCount;
//    final CellSetAxis _filterAxis;
    private List<Number> _results;
    private boolean _closed = false;


    QubeCellSet(Cube cube, BitSetQueryImpl.MeasureDef measure, List<Number> results, Collection<Member> columns, Collection<Member> rows)
    {
        _cube = cube;
        if (null == columns && null == rows)
            throw new IllegalArgumentException("Query must specify at least one axis (onRows or onColumns)");
        if (null == columns)
            _axes.add(new _MeasureAxis(Axis.COLUMNS, measure));
        else
            _axes.add(new _MemberAxis(Axis.COLUMNS, columns));
        if (null == rows)
            _axes.add(new _MeasureAxis(Axis.ROWS, measure));
        else
            _axes.add(new _MemberAxis(Axis.ROWS, rows));
        _columnCount = _axes.get(0).getPositionCount();
        _results = results;
        assert MemTracker.getInstance().put(this);
    }

    @Override
    public OlapStatement getStatement()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public CellSetMetaData getMetaData()
    {
        assert !_closed;
        return new _CellSetMetaData();
    }

    @Override
    public List<CellSetAxis> getAxes()
    {
        assert !_closed;
        return _axes;
    }

    @Override
    public CellSetAxis getFilterAxis()
    {
        return null;
    }

    @Override
    public Cell getCell(List<Integer> integers)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cell getCell(int i)
    {
        return new _Cell(i);
    }

    @Override
    public Cell getCell(Position... positions)
    {
        assert !_closed;
        int ordinal = 0;
        int multiplier = 1;
        for (int i=0 ; i<positions.length ; i++)
        {
            ordinal += positions[i].getOrdinal() * multiplier;
            multiplier *= _axes.get(i).getPositionCount();
        }
        return new _Cell(ordinal);
    }

    @Override
    public List<Integer> ordinalToCoordinates(int ordinal)
    {
        assert !_closed;
        if (1 == _axes.size())
            return Collections.singletonList(ordinal);
        if (2 == _axes.size())
            return Arrays.asList(ordinal / _columnCount, ordinal % _columnCount);
        else
            throw new UnsupportedOperationException("only support rows/columns");
    }

    @Override
    public int coordinatesToOrdinal(List<Integer> integers)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean next()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close()
    {
        _closed = true;
    }

    @Override
    public boolean wasNull()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getString(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBoolean(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte getByte(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShort(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInt(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getFloat(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getBytes(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getDate(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Time getTime(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Timestamp getTimestamp(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getAsciiStream(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getUnicodeStream(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getBinaryStream(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getString(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBoolean(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte getByte(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public short getShort(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInt(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLong(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public float getFloat(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getDouble(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getBytes(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getDate(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Time getTime(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Timestamp getTimestamp(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getAsciiStream(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getUnicodeStream(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getBinaryStream(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLWarning getWarnings()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearWarnings()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCursorName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int findColumn(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getCharacterStream(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getCharacterStream(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBeforeFirst()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAfterLast()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFirst()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLast()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void beforeFirst()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void afterLast()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean first()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean last()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getRow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean absolute(int row)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean relative(int rows)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean previous()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchDirection(int direction)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchDirection()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setFetchSize(int rows)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getFetchSize()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getType()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getConcurrency()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean rowUpdated()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean rowInserted()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean rowDeleted()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNull(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateByte(int columnIndex, byte x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateShort(int columnIndex, short x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateInt(int columnIndex, int x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateLong(int columnIndex, long x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateFloat(int columnIndex, float x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDouble(int columnIndex, double x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateString(int columnIndex, String x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDate(int columnIndex, Date x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTime(int columnIndex, Time x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(int columnIndex, Object x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNull(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateByte(String columnLabel, byte x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateShort(String columnLabel, short x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateInt(String columnLabel, int x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateLong(String columnLabel, long x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateFloat(String columnLabel, float x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDouble(String columnLabel, double x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateString(String columnLabel, String x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateDate(String columnLabel, Date x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTime(String columnLabel, Time x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateTimestamp(String columnLabel, Timestamp x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateObject(String columnLabel, Object x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void insertRow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void refreshRow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancelRowUpdates()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveToInsertRow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void moveToCurrentRow()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Ref getRef(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Blob getBlob(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Clob getClob(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Array getArray(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Ref getRef(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Blob getBlob(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Clob getClob(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Array getArray(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getDate(String columnLabel, Calendar cal)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Time getTime(String columnLabel, Calendar cal)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL getURL(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL getURL(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRef(int columnIndex, Ref x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRef(String columnLabel, Ref x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int columnIndex, Blob x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String columnLabel, Blob x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int columnIndex, Clob x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String columnLabel, Clob x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArray(int columnIndex, Array x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateArray(String columnLabel, Array x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public RowId getRowId(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRowId(int columnIndex, RowId x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateRowId(String columnLabel, RowId x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHoldability()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClosed()
    {
        return _closed;
    }

    @Override
    public void updateNString(int columnIndex, String nString)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNString(String columnLabel, String nString)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public NClob getNClob(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SQLXML getSQLXML(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getNString(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(int columnIndex)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Reader getNCharacterStream(String columnLabel)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(int columnIndex, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateClob(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T unwrap(Class<T> iface)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface)
    {
        throw new UnsupportedOperationException();
    }


    public class _Cell implements Cell
    {
        int _ordinal;

        _Cell(int i)
        {
            _ordinal = i;
        }

        @Override
        public CellSet getCellSet()
        {
            return QubeCellSet.this;
        }

        @Override
        public int getOrdinal()
        {
            return _ordinal;
        }

        @Override
        public List<Integer> getCoordinateList()
        {
            return ordinalToCoordinates(_ordinal);
        }

        @Override
        public Object getPropertyValue(Property property)
        {
            return null;
        }

        @Override
        public boolean isEmpty()
        {
            return null==getValue();
        }

        @Override
        public boolean isError()
        {
            return false;
        }

        @Override
        public boolean isNull()
        {
            return null==getValue();
        }

        @Override
        public double getDoubleValue()
        {
            Number n = (Number)getValue();
            return null==n ? 0.0 : n.doubleValue();
        }

        @Override
        public String getErrorText()
        {
            return null;
        }

        @Override
        public Object getValue()
        {
            return _ordinal < _results.size() ? _results.get(_ordinal) : null;
        }

        @Override
        public String getFormattedValue()
        {
            Number d = (Number)getValue();
            return null==d ? "" : String.valueOf(d);
        }

        @Override
        public ResultSet drillThrough()
        {
            throw new UnsupportedOperationException("drillThrough not supported");
        }

        @Override
        public void setValue(Object o, AllocationPolicy allocationPolicy, Object... objects)
        {
            throw new UnsupportedOperationException("drillThrough not supported");
        }
    }


    abstract class _CellSetAxis implements CellSetAxis
    {
        final Axis _axis;
        ArrayList<Position> _positions;

        _CellSetAxis(Axis axis)
        {
            _axis = axis;
        }

        @Override
        public Axis getAxisOrdinal()
        {
            return _axis;
        }

        @Override
        public CellSet getCellSet()
        {
            return QubeCellSet.this;
        }

        @Override
        public CellSetAxisMetaData getAxisMetaData()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Position> getPositions()
        {
            return Collections.unmodifiableList(_positions);
        }

        @Override
        public int getPositionCount()
        {
            return _positions.size();
        }

        @Override
        public ListIterator<Position> iterator()
        {
            return (Collections.unmodifiableList(_positions)).listIterator();
        }
    }


    class _LevelAxis extends _CellSetAxis
    {
        _LevelAxis(Axis axis, Level level) throws OlapException
        {
            super(axis);
            List<Member> members = level.getMembers();
            ArrayList<Position> positions = new ArrayList<>(members.size());
            for (Member m : members)
            {
                Position p = new _Position(positions.size(), m);
                positions.add(p);
            }
            _positions = positions;
        }
    }


    class _HierarchyAxis extends _CellSetAxis
    {
        _HierarchyAxis(CellSet cellset, Axis axis, Hierarchy hierarchy) throws OlapException
        {
            super(axis);
            int size = 0;
            for (Level l : hierarchy.getLevels())
                size += l.getCardinality();
            ArrayList<Position> positions = new ArrayList<>(size);
            for (Level l : hierarchy.getLevels())
            {
                for (Member m : l.getMembers())
                {
                    Position p = new _Position(positions.size(), m);
                    positions.add(p);
                }
            }
            _positions = positions;
        }
    }


    class _MemberAxis extends _CellSetAxis
    {
        _MemberAxis(Axis axis, Collection<Member> members)
        {
            super(axis);
            ArrayList<Position> positions = new ArrayList<>(members.size());
            for (Member m : members)
            {
                Position p = new _Position(positions.size(), m);
                positions.add(p);
            }
            _positions = positions;
        }
    }


    class _MeasureAxis extends _CellSetAxis
    {
        _MeasureAxis(Axis axis, BitSetQueryImpl.MeasureDef measure)
        {
            super(axis);
            _positions = new ArrayList<>(1);
            Position p = new _Position(0, measure.measureMember);
            _positions.add(p);
        }
    }



    class _Position implements Position
    {
        int _ordinal;
        Member _member;

        _Position(int ordinal, Member member)
        {
            _ordinal = ordinal;
            _member = member;
        }

        @Override
        public List<Member> getMembers()
        {
            return Collections.singletonList(_member);
        }

        @Override
        public int getOrdinal()
        {
            return _ordinal;
        }
    }


    class _CellSetMetaData implements CellSetMetaData
    {
        @Override
        public NamedList<Property> getCellProperties()
        {
            return null;
        }

        @Override
        public Cube getCube()
        {
            return _cube;
        }

        @Override
        public NamedList<CellSetAxisMetaData> getAxesMetaData()
        {
            NamedList<CellSetAxisMetaData> nl = new NamedListImpl();
            for (CellSetAxis a : getAxes())
                nl.add(new _CellSetAxisMetaData((_CellSetAxis)a));
            return nl;
        }

        @Override
        public CellSetAxisMetaData getFilterAxisMetaData()
        {
            return null;
        }

        @Override
        public int getColumnCount()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAutoIncrement(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCaseSensitive(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSearchable(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCurrency(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int isNullable(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSigned(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getColumnDisplaySize(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getColumnLabel(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getColumnName(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getSchemaName(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getPrecision(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getScale(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getTableName(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCatalogName(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getColumnType(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getColumnTypeName(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isReadOnly(int column)
        {
            return true;
        }

        @Override
        public boolean isWritable(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDefinitelyWritable(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getColumnClassName(int column)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface)
        {
            return false;
        }
    }


    class _CellSetAxisMetaData implements CellSetAxisMetaData, Named
    {
        _CellSetAxis axis;

        _CellSetAxisMetaData(_CellSetAxis a)
        {
            axis = a;
        }

        @Override
        public Axis getAxisOrdinal()
        {
            return axis.getAxisOrdinal();
        }

        @Override
        public List<Hierarchy> getHierarchies()
        {
            List<Hierarchy> ret = new ArrayList<>();
            HashSet<String> names = new HashSet<>();
            for (Position p : axis.getPositions())
            {
                for (Member m : p.getMembers())
                {
                    Hierarchy h = m.getLevel().getHierarchy();
                    if (names.add(h.getUniqueName()))
                        ret.add(h);
                }
            }
            return ret;
        }

        @Override
        public List<Property> getProperties()
        {
            return Collections.emptyList();
        }

        @Override
        public String getName()
        {
            return axis._axis.name();
        }
    }


    /*
    class _NamedList<T> implements NamedList<T>
    {
        LinkedHashMap<String,T> map;

        @Override
        public T get(String s)
        {
            return map.get(s);
        }

        @Override
        public int indexOfName(String s)
        {
            return asList().i
            T t = get(s);
            if (null == t)
                return -1;
            return map.
        }

        @Override
        public String getName(Object o)
        {
            return null;
        }

        @Override
        public Map<String, T> asMap()
        {
            return null;
        }

        @Override
        public int size()
        {
            return 0;
        }

        @Override
        public boolean isEmpty()
        {
            return false;
        }

        @Override
        public boolean contains(Object o)
        {
            return false;
        }

        @NotNull
        @Override
        public Iterator<T> iterator()
        {
            return null;
        }

        @NotNull
        @Override
        public Object[] toArray()
        {
            return new Object[0];
        }

        @NotNull
        @Override
        public <T1> T1[] toArray(T1[] a)
        {
            return null;
        }

        @Override
        public boolean add(T t)
        {
            return false;
        }

        @Override
        public boolean remove(Object o)
        {
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> c)
        {
            return false;
        }

        @Override
        public boolean addAll(Collection<? extends T> c)
        {
            return false;
        }

        @Override
        public boolean addAll(int index, Collection<? extends T> c)
        {
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c)
        {
            return false;
        }

        @Override
        public boolean retainAll(Collection<?> c)
        {
            return false;
        }

        @Override
        public void clear()
        {

        }

        @Override
        public T get(int index)
        {
            return null;
        }

        @Override
        public T set(int index, T element)
        {
            return null;
        }

        @Override
        public void add(int index, T element)
        {

        }

        @Override
        public T remove(int index)
        {
            return null;
        }

        @Override
        public int indexOf(Object o)
        {
            return 0;
        }

        @Override
        public int lastIndexOf(Object o)
        {
            return 0;
        }

        @NotNull
        @Override
        public ListIterator<T> listIterator()
        {
            return null;
        }

        @NotNull
        @Override
        public ListIterator<T> listIterator(int index)
        {
            return null;
        }

        @NotNull
        @Override
        public List<T> subList(int fromIndex, int toIndex)
        {
            return null;
        }
    } */
}
