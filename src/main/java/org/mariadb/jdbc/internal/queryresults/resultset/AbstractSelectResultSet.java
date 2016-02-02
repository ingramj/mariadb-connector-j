/*
MariaDB Client for Java

Copyright (c) 2012 Monty Program Ab.

This library is free software; you can redistribute it and/or modify it under
the terms of the GNU Lesser General Public License as published by the Free
Software Foundation; either version 2.1 of the License, or (at your option)
any later version.

This library is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License along
with this library; if not, write to Monty Program Ab info@montyprogram.com.

This particular MariaDB Client for Java file is work
derived from a Drizzle-JDBC. Drizzle-JDBC file which is covered by subject to
the following copyright and notice provisions:

Copyright (c) 2009-2011, Marcus Eriksson

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

Neither the name of the driver nor the names of its contributors may not be
used to endorse or promote products derived from this software without specific
prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS  AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
*/

package org.mariadb.jdbc.internal.queryresults.resultset;

import org.mariadb.jdbc.MariaDbBlob;
import org.mariadb.jdbc.MariaDbClob;
import org.mariadb.jdbc.MariaDbResultSetMetaData;
import org.mariadb.jdbc.internal.packet.dao.ColumnInformation;
import org.mariadb.jdbc.internal.protocol.Protocol;
import org.mariadb.jdbc.internal.queryresults.ColumnNameMap;
import org.mariadb.jdbc.internal.queryresults.resultset.value.ValueObject;
import org.mariadb.jdbc.internal.util.ExceptionMapper;
import org.mariadb.jdbc.internal.util.Options;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Map;

public abstract class AbstractSelectResultSet implements ResultSet {
    protected int rowPointer;
    protected ColumnNameMap columnNameMap;
    protected Calendar cal;
    protected Protocol protocol;
    protected boolean lastGetWasNull;
    protected ColumnInformation[] columnsInformation;
    private int dataTypeMappingFlags;
    protected Options options;
    private boolean returnTableAlias;
    protected boolean isClosed;
    protected Statement statement;

    /**
     * Create result set.
     * @param columnsInformation columninformation
     * @param protocol current protocol
     * @param statement statement
     */
    public AbstractSelectResultSet(ColumnInformation[] columnsInformation, Protocol protocol, Statement statement) {
        this.statement = statement;
        this.isClosed = false;
        this.protocol = protocol;
        if (protocol != null) {
            this.options = protocol.getOptions();
            this.cal = protocol.getCalendar();
            this.dataTypeMappingFlags = protocol.getDataTypeMappingFlags();
            this.returnTableAlias = this.options.useOldAliasMetadataBehavior;
        } else {
            this.options = null;
            this.cal = null;
            this.dataTypeMappingFlags = 3;
            this.returnTableAlias = false;
        }
        this.columnsInformation = columnsInformation;
        this.columnNameMap = new ColumnNameMap(columnsInformation);
        this.statement = statement;
    }

    protected abstract ValueObject getValueObject(int columnIndex) throws SQLException;

    public abstract SQLWarning getWarnings() throws SQLException;

    public abstract void clearWarnings() throws SQLException;

    public abstract boolean isBeforeFirst() throws SQLException;

    public abstract boolean isAfterLast() throws SQLException;

    public abstract boolean isFirst() throws SQLException;

    public abstract boolean isLast() throws SQLException;

    public abstract void beforeFirst() throws SQLException;

    public abstract void afterLast() throws SQLException;

    public abstract boolean first() throws SQLException;

    public abstract boolean last() throws SQLException;

    public abstract int getRow() throws SQLException;

    public abstract boolean absolute(int row) throws SQLException;

    public abstract boolean relative(int rows) throws SQLException;

    public abstract boolean previous() throws SQLException;

    public abstract int getFetchDirection() throws SQLException;

    public abstract void setFetchDirection(int direction) throws SQLException;

    public abstract int getFetchSize() throws SQLException;

    public abstract void setFetchSize(int rows) throws SQLException;

    public abstract int getConcurrency() throws SQLException;

    public abstract boolean isBinaryProtocol();

    public abstract int getType() throws SQLException;

    public abstract boolean next() throws SQLException;

    public boolean isClosed() {
        return isClosed;
    }

    public Statement getStatement() {
        return statement;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    public void close() throws SQLException {
        isClosed = true;
    }

    /**
     * Reports whether the last column read had a value of SQL <code>NULL</code>. Note that you must first call one of the getter methods on a column
     * to try to read its value and then call the method <code>wasNull</code> to see if the value read was SQL <code>NULL</code>.
     *
     * @return <code>true</code> if the last column value read was SQL <code>NULL</code> and <code>false</code> otherwise
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed result set
     */
    public boolean wasNull() throws SQLException {
        return lastGetWasNull;
    }


    /**
     * <p>Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a stream of ASCII characters. The
     * value can then be read in chunks from the stream. This method is particularly suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC driver will do any necessary conversion from the database format into ASCII.</p><p><b>Note:</b> All the data in the returned
     * stream must be read prior to getting the value of any other column. The next call to a getter method implicitly closes the stream. Also, a
     * stream may return <code>0</code> when the method <code>available</code> is called whether there is data available or not.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a Java input stream that delivers the database column value as a stream of one-byte ASCII characters. If the value is SQL
     * <code>NULL</code>, the value returned is <code>null</code>.
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        return getAsciiStream(findColumn(columnLabel));

    }

    /**
     * <p>Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a stream of ASCII characters. The
     * value can then be read in chunks from the stream. This method is particularly suitable for retrieving large <code>LONGVARCHAR</code> values.
     * The JDBC driver will do any necessary conversion from the database format into ASCII. </p>
     *
     * <B>Note:</B> All the data in the returned stream must be read prior to getting the value of any other column. The next call to a getter method
     * implicitly closes the stream.  Also, a stream may return <code>0</code> when the method <code>InputStream.available</code> is called whether
     * there is data available or not.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a Java input stream that delivers the database column value as a stream of one-byte ASCII characters; if the value is SQL
     * <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getInputStream();
    }


    public String getString(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getString(cal);
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>String</code> in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    /**
     * <p>Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a stream of uninterpreted bytes.
     * The value can then be read in chunks from the stream. This method is particularly suitable for retrieving large <code>LONGVARBINARY</code>
     * values.</p>
     *
     * <p><B>Note:</B> All the data in the returned stream must be read prior to getting the value of any other column. The next call to a getter
     * method implicitly closes the stream.  Also, a stream may return <code>0</code> when the method <code>InputStream.available</code> is called
     * whether there is data available or not.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a Java input stream that delivers the database column value as a stream of uninterpreted bytes; if the value is SQL <code>NULL</code>,
     * the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getBinaryInputStream();
    }

    /**
     * <p>Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a stream of uninterpreted
     * <code>byte</code>s. The value can then be read in chunks from the stream. This method is particularly suitable for retrieving large
     * <code>LONGVARBINARY</code> values. </p>
     *
     * <b>Note:</b> All the data in the returned stream must be read prior to getting the value of any other column. The next call to a getter method
     * implicitly closes the stream. Also, a stream may return <code>0</code> when the method <code>available</code> is called whether there is data
     * available or not.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a Java input stream that delivers the database column value as a stream of uninterpreted bytes; if the value is SQL <code>NULL</code>,
     * the result is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        return getBinaryStream(findColumn(columnLabel));
    }

    public int getInt(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getInt();
    }

    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>long</code> in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    public long getLong(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getLong();
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>float</code> in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    public float getFloat(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getFloat();
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>double</code> in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }


    public double getDouble(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getDouble();
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.math.BigDecimal</code> in
     * the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param scale the number of digits to the right of the decimal point
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @deprecated use of scale is deprecated
     */
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.BigDecimal</code> in
     * the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param scale the number of digits to the right of the decimal point
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @deprecated use of scale is deprecated
     */
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        return getValueObject(columnIndex).getBigDecimal();
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.math.BigDecimal</code>
     * with full precision.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value (full precision); if the value is SQL <code>NULL</code>, the value returned is <code>null</code> in the Java
     * programming language.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getBigDecimal();
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.math.BigDecimal</code>
     * with full precision.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value (full precision); if the value is SQL <code>NULL</code>, the value returned is <code>null</code> in the Java
     * programming language.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return getValueObject(findColumn(columnLabel)).getBigDecimal();
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>byte</code> array in the Java
     * programming language. The bytes represent the raw values returned by the driver.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>byte</code> array in the Java
     * programming language. The bytes represent the raw values returned by the driver.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public byte[] getBytes(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getBytes();
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Date</code> object in
     * the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Date getDate(int columnIndex) throws SQLException {
        try {
            return getValueObject(columnIndex).getDate(cal);
        } catch (ParseException e) {
            throw ExceptionMapper.getSqlException("Could not parse column as date, was: \""
                    + getValueObject(columnIndex).getString()
                    + "\"", e);
        }
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Date</code> object in
     * the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Date getDate(String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Date</code> object in
     * the Java programming language. This method uses the given calendar to construct an appropriate millisecond value for the date if the underlying
     * database does not store timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param cal the <code>java.util.Calendar</code> object to use in constructing the date
     * @return the column value as a <code>java.sql.Date</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * in the Java programming language
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        try {
            return getValueObject(columnIndex).getDate(cal);
        } catch (ParseException e) {
            throw ExceptionMapper.getSqlException("Could not parse as date");
        }
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Date</code> object in
     * the Java programming language. This method uses the given calendar to construct an appropriate millisecond value for the date if the underlying
     * database does not store timezone information.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param cal the <code>java.util.Calendar</code> object to use in constructing the date
     * @return the column value as a <code>java.sql.Date</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        return getDate(findColumn(columnLabel), cal);
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Time</code> object in
     * the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Time getTime(int columnIndex) throws SQLException {
        try {
            return getValueObject(columnIndex).getTime(cal);
        } catch (ParseException e) {
            throw ExceptionMapper.getSqlException("Could not parse column as time, was: \""
                    + getValueObject(columnIndex).getString()
                    + "\"", e);
        }
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Time</code> object in
     * the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Time getTime(String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Time</code> object in
     * the Java programming language. This method uses the given calendar to construct an appropriate millisecond value for the time if the underlying
     * database does not store timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param cal the <code>java.util.Calendar</code> object to use in constructing the time
     * @return the column value as a <code>java.sql.Time</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * in the Java programming language
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        try {
            return getValueObject(columnIndex).getTime(cal);
        } catch (ParseException e) {
            throw ExceptionMapper.getSqlException("Could not parse time", e);
        }
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Time</code> object in
     * the Java programming language. This method uses the given calendar to construct an appropriate millisecond value for the time if the underlying
     * database does not store timezone information.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param cal the <code>java.util.Calendar</code> object to use in constructing the time
     * @return the column value as a <code>java.sql.Time</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        return getTime(findColumn(columnLabel), cal);
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Timestamp</code>
     * object in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Timestamp</code>
     * object in the Java programming language. This method uses the given calendar to construct an appropriate millisecond value for the timestamp if
     * the underlying database does not store timezone information.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param cal the <code>java.util.Calendar</code> object to use in constructing the timestamp
     * @return the column value as a <code>java.sql.Timestamp</code> object; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        try {
            Timestamp result = getValueObject(columnIndex).getTimestamp(cal);
            if (result == null) {
                return null;
            }
            return new Timestamp(result.getTime());
        } catch (ParseException e) {
            throw ExceptionMapper.getSqlException("Could not parse timestamp", e);
        }
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Timestamp</code>
     * object in the Java programming language. This method uses the given calendar to construct an appropriate millisecond value for the timestamp if
     * the underlying database does not store timezone information.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param cal the <code>java.util.Calendar</code> object to use in constructing the date
     * @return the column value as a <code>java.sql.Timestamp</code> object; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid or if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        return getTimestamp(findColumn(columnLabel), cal);
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.Timestamp</code>
     * object in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        try {
            return getValueObject(columnIndex).getTimestamp(cal);
        } catch (ParseException e) {
            throw ExceptionMapper.getSqlException("Could not parse column as timestamp, was: \""
                    + getValueObject(columnIndex).getString()
                    + "\"", e);
        }
    }

    /**
     * <p>Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a stream of two-byte Unicode
     * characters. The first byte is the high byte; the second byte is the low byte. </p>
     * <p>The value can then be read in chunks from the stream. This method is particularly suitable for retrieving large <code>LONGVARCHAR</code>
     * values. The JDBC technology-enabled driver will do any necessary conversion from the database format into Unicode. </p>
     * <p> <b>Note:</b> All the data in the returned stream must be read prior to getting the value of any other column. The next call to a getter
     * method implicitly closes the stream. Also, a stream may return <code>0</code> when the method <code>InputStream.available</code> is called,
     * whether there is data available or not. </p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a Java input stream that delivers the database column value as a stream of two-byte Unicode characters. If the value is SQL
     * <code>NULL</code>, the value returned is <code>null</code>.
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @deprecated use <code>getCharacterStream</code> instead
     */
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        return getUnicodeStream(findColumn(columnLabel));
    }

    /**
     * <p>Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as as a stream of two-byte 3
     * characters. The first byte is the high byte; the second byte is the low byte. </p>
     * <p>The value can then be read in chunks from the stream. This method is particularly suitable for retrieving large
     * <code>LONGVARCHAR</code>values. The JDBC driver will do any necessary conversion from the database format into Unicode.</p>
     * <p><B>Note:</B> All the data in the returned stream must be read prior to getting the value of any other column. The next call to a getter
     * method implicitly closes the stream. Also, a stream may return <code>0</code> when the method <code>InputStream.available</code> is called,
     * whether there is data available or not.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a Java input stream that delivers the database column value as a stream of two-byte Unicode characters; if the value is SQL
     * <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @deprecated use <code>getCharacterStream</code> in place of <code>getUnicodeStream</code>
     */
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        return getValueObject(columnIndex).getInputStream();
    }

    /**
     * Retrieves the name of the SQL cursor used by this <code>ResultSet</code> object.<p>
     * In SQL, a result table is retrieved through a cursor that is named. The current row of a result set can be updated or deleted using a
     * positioned update/delete statement that references the cursor name. To insure that the cursor has the proper isolation level to support update,
     * the cursor's <code>SELECT</code> statement should be of the form <code>SELECT FOR UPDATE</code>. If <code>FOR UPDATE</code> is omitted, the
     * positioned updates may fail.<p>
     * The JDBC API supports this SQL feature by providing the name of the SQL cursor used by a <code>ResultSet</code> object. The current row of a
     * <code>ResultSet</code> object is also the current row of this SQL cursor.
     *
     * @return the SQL name for this <code>ResultSet</code> object's cursor
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     */
    public String getCursorName() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Cursors not supported");
    }

    /**
     * Retrieves the  number, types and properties of this <code>ResultSet</code> object's columns.
     *
     * @return the description of this <code>ResultSet</code> object's columns
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed result set
     */
    public ResultSetMetaData getMetaData() throws SQLException {
        return new MariaDbResultSetMetaData(columnsInformation, dataTypeMappingFlags, returnTableAlias);
    }

    /**
     * <p>Gets the value of the designated column in the current row of this <code>ResultSet</code> object as an <code>Object</code> in the Java
     * programming language. This method will return the value of the given column as a Java object.  The type of the Java object will be the default
     * Java object type corresponding to the column's SQL type, following the mapping for built-in types specified in the JDBC specification. If the
     * value is an SQL <code>NULL</code>, the driver returns a Java <code>null</code>. </p>
     * <p>This method may also be used to read database-specific abstract data types. </p>
     * In the JDBC 2.0 API, the behavior of method <code>getObject</code> is extended to materialize data of SQL user-defined types. If
     * <code>Connection.getTypeMap</code> does not throw a <code>SQLFeatureNotSupportedException</code>, then when a column contains a structured or
     * distinct value, the behavior of this method is as if it were a call to: <code>getObject(columnIndex,
     * this.getStatement().getConnection().getTypeMap())</code>. If <code>Connection.getTypeMap</code> does throw a
     * <code>SQLFeatureNotSupportedException</code>, then structured values are not supported, and distinct values are mapped to the default Java
     * class as determined by the underlying SQL type of the DISTINCT type.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>java.lang.Object</code> holding the column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Object getObject(int columnIndex) throws SQLException {
        try {
            return getValueObject(columnIndex).getObject(dataTypeMappingFlags, cal);
        } catch (ParseException e) {
            throw ExceptionMapper.getSqlException("Could not get object: " + e.getMessage(), "S1009", e);
        }
    }

    /**
     * <p>Gets the value of the designated column in the current row of this <code>ResultSet</code> object as an <code>Object</code> in the Java
     * programming language. </p>
     * <p>This method will return the value of the given column as a Java object.  The type of the Java object will be the default Java object type
     * corresponding to the column's SQL type, following the mapping for built-in types specified in the JDBC specification. If the value is an SQL
     * <code>NULL</code>, the driver returns a Java <code>null</code>. </p>
     * <p>This method may also be used to read database-specific abstract data types. In the JDBC 2.0 API, the behavior of the method
     * <code>getObject</code> is extended to materialize data of SQL user-defined types.  When a column contains a structured or distinct value, the
     * behavior of this method is as if it were a call to: <code>getObject(columnIndex, this.getStatement().getConnection().getTypeMap())</code>.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>java.lang.Object</code> holding the column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    /**
     * According to the JDBC4 spec, this is only required for UDT's, and since drizzle does not support UDTs, this method ignores the map parameter
     * <p> Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as an <code>Object</code> in the Java
     * programming language. If the value is an SQL <code>NULL</code>, the driver returns a Java <code>null</code>. This method uses the given
     * <code>Map</code> object for the custom mapping of the SQL structured or distinct type that is being retrieved. </p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param map a <code>java.util.Map</code> object that contains the mapping from SQL type names to classes in the Java programming language
     * @return an <code>Object</code> in the Java programming language representing the SQL value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return getObject(columnIndex);
    }

    /**
     * <p>According to the JDBC4 spec, this is only required for UDT's, and since drizzle does not support UDTs, this method ignores the map parameter
     * </p>
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as an <code>Object</code> in the Java
     * programming language. If the value is an SQL <code>NULL</code>, the driver returns a Java <code>null</code>. This method uses the specified
     * <code>Map</code> object for custom mapping if appropriate.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param map a <code>java.util.Map</code> object that contains the mapping from SQL type names to classes in the Java programming language
     * @return an <code>Object</code> representing the SQL value in the specified column
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return getObject(findColumn(columnLabel));
    }


    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        return getValueObject(columnIndex).getObject(type);
    }

    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
    }

    /**
     * Maps the given <code>ResultSet</code> column label to its <code>ResultSet</code> column index.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column index of the given column name
     * @throws java.sql.SQLException if the <code>ResultSet</code> object does not contain a column labeled <code>columnLabel</code>, a database
     * access error occurs or this method is called on a closed result set
     */
    public int findColumn(String columnLabel) throws SQLException {
        return columnNameMap.getIndex(columnLabel) + 1;
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.io.Reader</code> object.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>java.io.Reader</code> object that contains the column value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        return getCharacterStream(findColumn(columnLabel));
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.io.Reader</code> object.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>java.io.Reader</code> object that contains the column value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @since 1.2
     */
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        String value = getValueObject(columnIndex).getString();
        if (value == null) {
            return null;
        }
        return new StringReader(value);
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.io.Reader</code> object.
     * It is intended for use when accessing  <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>java.io.Reader</code> object that contains the column value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        return getCharacterStream(columnIndex);
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.io.Reader</code> object.
     * It is intended for use when accessing  <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>java.io.Reader</code> object that contains the column value; if the value is SQL <code>NULL</code>, the value returned is
     * <code>null</code> in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        return getCharacterStream(columnLabel);
    }

    /**
     * <p>Retrieves whether the current row has been updated.  The value returned depends on whether or not the result set can detect updates.</p>
     * <strong>Note:</strong> Support for the <code>rowUpdated</code> method is optional with a result set concurrency of
     * <code>CONCUR_READ_ONLY</code>
     *
     * @return <code>true</code> if the current row is detected to have been visibly updated by the owner or another; <code>false</code> otherwise
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @see java.sql.DatabaseMetaData#updatesAreDetected
     * @since 1.2
     */
    public boolean rowUpdated() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Detecting row updates are not supported");
    }

    /**
     * Retrieves whether the current row has had an insertion. The value returned depends on whether or not this <code>ResultSet</code> object can
     * detect visible inserts.<p>
     * <strong>Note:</strong> Support for the <code>rowInserted</code> method is optional with a result set concurrency of
     * <code>CONCUR_READ_ONLY</code>
     *
     * @return <code>true</code> if the current row is detected to have been inserted; <code>false</code> otherwise
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @see java.sql.DatabaseMetaData#insertsAreDetected
     * @since 1.2
     */
    public boolean rowInserted() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Detecting inserts are not supported");
    }

    /**
     * <p>Retrieves whether a row has been deleted.  A deleted row may leave a visible "hole" in a result set.  This method can be used to detect
     * holes in a result set.  The value returned depends on whether or not this <code>ResultSet</code> object can detect deletions. </p>
     * <strong>Note:</strong> Support for the <code>rowDeleted</code> method is optional with a result set concurrency of
     * <code>CONCUR_READ_ONLY</code>
     *
     * @return <code>true</code> if the current row is detected to have been deleted by the owner or another; <code>false</code> otherwise
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @see java.sql.DatabaseMetaData#deletesAreDetected
     * @since 1.2
     */
    public boolean rowDeleted() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Row deletes are not supported");
    }

    /**
     * Updates the designated column with a <code>null</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateNull(int columnIndex) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>null</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateNull(String columnLabel) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>boolean</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param bool the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBoolean(int columnIndex, boolean bool) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>boolean</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBoolean(String columnLabel, boolean value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>byte</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateByte(int columnIndex, byte value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>byte</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateByte(String columnLabel, byte value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>short</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateShort(int columnIndex, short value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>short</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateShort(String columnLabel, short value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with an <code>int</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateInt(int columnIndex, int value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with an <code>int</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateInt(String columnLabel, int value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>float</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateFloat(int columnIndex, float value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>float </code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateFloat(String columnLabel, float value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>double</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateDouble(int columnIndex, double value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>double</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateDouble(String columnLabel, double value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.math.BigDecimal</code> value. The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBigDecimal(int columnIndex, BigDecimal value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.BigDecimal</code> value. The updater methods are used to update column values in the
     * current row or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or
     * <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBigDecimal(String columnLabel, BigDecimal value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>String</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateString(int columnIndex, String value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>String</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateString(String columnLabel, String value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>byte</code> array value. The updater methods are used to update column values in the current row or
     * the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBytes(int columnIndex, byte[] value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Updates the designated column with a byte array value.</p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBytes(String columnLabel, byte[] value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Date</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param date the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateDate(int columnIndex, Date date) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Date</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateDate(String columnLabel, Date value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Time</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param time the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateTime(int columnIndex, Time time) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * Updates the designated column with a <code>java.sql.Time</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateTime(String columnLabel, Time value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Timestamp</code> value. The updater methods are used to update column values in the current
     * row or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param timeStamp the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateTimestamp(int columnIndex, Timestamp timeStamp) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * Updates the designated column with a <code>java.sql.Timestamp</code> value. The updater methods are used to update column values in the current
     * row or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateTimestamp(String columnLabel, Timestamp value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with an ascii stream value, which will have the specified number of bytes. The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateAsciiStream(int columnIndex, InputStream inputStream, int length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * <p>Updates the designated column with an ascii stream value. The data will be read from the stream as needed until end-of-stream is
     * reached.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of
     * <code>updateAsciiStream</code> which takes a length parameter.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param inputStream the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateAsciiStream(String columnLabel, InputStream inputStream) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * Updates the designated column with an ascii stream value, which will have the specified number of bytes. The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateAsciiStream(String columnLabel, InputStream value, int length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * <p>Updates the designated column with an ascii stream value, which will have the specified number of bytes.</p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateAsciiStream(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column with an ascii stream value, which will have the specified number of bytes. </p> The updater methods are used
     * to update column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param inputStream the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateAsciiStream(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column with an ascii stream value. The data will be read from the stream as needed until end-of-stream is
     * reached.</p><p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of
     * <code>updateAsciiStream</code> which takes a length parameter.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateAsciiStream(int columnIndex, InputStream inputStream) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }


    /**
     * Updates the designated column with a binary stream value, which will have the specified number of bytes. The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBinaryStream(int columnIndex, InputStream inputStream, int length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Updates the designated column with a binary stream value, which will have the specified number of bytes.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the
     * underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBinaryStream(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * Updates the designated column with a binary stream value, which will have the specified number of bytes. The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateBinaryStream(String columnLabel, InputStream value, int length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Updates the designated column with a binary stream value, which will have the specified number of bytes. </p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param inputStream the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBinaryStream(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column with a binary stream value. The data will be read from the stream as needed until end-of-stream is
     * reached.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of
     * <code>updateBinaryStream</code> which takes a length parameter.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBinaryStream(int columnIndex, InputStream inputStream) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column with a binary stream value. The data will be read from the stream as needed until end-of-stream is
     * reached.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of
     * <code>updateBinaryStream</code> which takes a length parameter.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param inputStream the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBinaryStream(String columnLabel, InputStream inputStream) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * Updates the designated column with a character stream value, which will have the specified number of bytes. The updater methods are used to
     * update column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateCharacterStream(int columnIndex, Reader value, int length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Updates the designated column with a character stream value. The data will be read from the stream as needed until end-of-stream is
     * reached.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of
     * <code>updateCharacterStream</code> which takes a length parameter.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateCharacterStream(int columnIndex, Reader value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * Updates the designated column with a character stream value, which will have the specified number of bytes. The updater methods are used to
     * update column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader the <code>java.io.Reader</code> object containing the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * <p>Updates the designated column with a character stream value, which will have the specified number of bytes.</p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateCharacterStream(int columnIndex, Reader value, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column with a character stream value, which will have the specified number of bytes. </p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader the <code>java.io.Reader</code> object containing the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }


    /**
     * <p>Updates the designated column with a character stream value. The data will be read from the stream as needed until end-of-stream is
     * reached.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of
     * <code>updateCharacterStream</code> which takes a length parameter.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader the <code>java.io.Reader</code> object containing the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }


    /**
     * <p>Updates the designated column with an <code>Object</code> value. The updater methods are used to update column values in the current row or
     * the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.</p>
     * <p>If the second argument is an <code>InputStream</code> then the stream must contain the number of bytes specified by scaleOrLength.  If the
     * second argument is a <code>Reader</code> then the reader must contain the number of characters specified by scaleOrLength. If these conditions
     * are not true the driver will generate a <code>SQLException</code> when the statement is executed.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @param scaleOrLength for an object of <code>java.math.BigDecimal</code> , this is the number of digits after the decimal point. For Java Object
     * types <code>InputStream</code> and <code>Reader</code>, this is the length of the data in the stream or reader.  For all other types, this
     * value will be ignored.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateObject(int columnIndex, Object value, int scaleOrLength) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with an <code>Object</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateObject(int columnIndex, Object value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Updates the designated column with an <code>Object</code> value. The updater methods are used to update column values in the current row or
     * the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database. </p>
     * <p>If the second argument is an <code>InputStream</code> then the stream must contain the number of bytes specified by scaleOrLength.  If the
     * second argument is a <code>Reader</code> then the reader must contain the number of characters specified by scaleOrLength. If these conditions
     * are not true the driver will generate a <code>SQLException</code> when the statement is executed.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @param scaleOrLength for an object of <code>java.math.BigDecimal</code> , this is the number of digits after the decimal point. For Java Object
     * types <code>InputStream</code> and <code>Reader</code>, this is the length of the data in the stream or reader.  For all other types, this
     * value will be ignored.
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateObject(String columnLabel, Object value, int scaleOrLength) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with an <code>Object</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateObject(String columnLabel, Object value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * Updates the designated column with a <code>long</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param value the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateLong(String columnLabel, long value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>long</code> value. The updater methods are used to update column values in the current row or the
     * insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods
     * are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateLong(int columnIndex, long value) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * Inserts the contents of the insert row into this <code>ResultSet</code> object and into the database. The cursor must be on the insert row when
     * this method is called.
     *
     * @throws java.sql.SQLException if a database access error occurs; the result set concurrency is <code>CONCUR_READ_ONLY</code>, this method is
     * called on a closed result set, if this method is called when the cursor is not on the insert row, or if not all of non-nullable columns in the
     * insert row have been given a non-null value
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void insertRow() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the underlying database with the new contents of the current row of this <code>ResultSet</code> object. This method cannot be called
     * when the cursor is on the insert row.
     *
     * @throws java.sql.SQLException if a database access error occurs; the result set concurrency is <code>CONCUR_READ_ONLY</code>; this method is
     * called on a closed result set or if this method is called when the cursor is on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void updateRow() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Deletes the current row from this <code>ResultSet</code> object and from the underlying database.  This method cannot be called when the cursor
     * is on the insert row.
     *
     * @throws java.sql.SQLException if a database access error occurs; the result set concurrency is <code>CONCUR_READ_ONLY</code>; this method is
     * called on a closed result set or if this method is called when the cursor is on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void deleteRow() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Refreshes the current row with its most recent value in the database.  This method cannot be called when the cursor is on the insert
     * row.</p> <p>The <code>refreshRow</code> method provides a way for an application to explicitly tell the JDBC driver to refetch a row(s) from
     * the database.  An application may want to call <code>refreshRow</code> when caching or prefetching is being done by the JDBC driver to fetch
     * the latest value of a row from the database.  The JDBC driver may actually refresh multiple rows at once if the fetch size is greater than one.
     * </p> All values are refetched subject to the transaction isolation level and cursor sensitivity.  If <code>refreshRow</code> is called after
     * calling an updater method, but before calling the method <code>updateRow</code>, then the updates made to the row are lost. Calling the method
     * <code>refreshRow</code> frequently will likely slow performance.
     *
     * @throws java.sql.SQLException if a database access error occurs; this method is called on a closed result set; the result set type is
     * <code>TYPE_FORWARD_ONLY</code> or if this method is called when the cursor is on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method or this method is not supported for the
     * specified result set type and result set concurrency.
     * @since 1.2
     */
    public void refreshRow() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Row refresh is not supported");
    }

    /**
     * Cancels the updates made to the current row in this <code>ResultSet</code> object. This method may be called after calling an updater method(s)
     * and before calling the method <code>updateRow</code> to roll back the updates made to a row.  If no updates have been made or
     * <code>updateRow</code> has already been called, this method has no effect.
     *
     * @throws java.sql.SQLException if a database access error occurs; this method is called on a closed result set; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or if this method is called when the cursor is on the insert row
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void cancelRowUpdates() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Moves the cursor to the insert row.  The current cursor position is remembered while the cursor is positioned on the insert row. <p> The insert
     * row is a special row associated with an updatable result set.  It is essentially a buffer where a new row may be constructed by calling the
     * updater methods prior to inserting the row into the result set. </p> Only the updater, getter, and <code>insertRow</code> methods may be called
     * when the cursor is on the insert row. All of the columns in a result set must be given a value each time this method is called before calling
     * <code>insertRow</code>. An updater method must be called before a getter method can be called on a column value.
     *
     * @throws java.sql.SQLException if a database access error occurs; this method is called on a closed result set or the result set concurrency is
     * <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void moveToInsertRow() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Moves the cursor to the remembered cursor position, usually the current row.  This method has no effect if the cursor is not on the insert
     * row.
     *
     * @throws java.sql.SQLException if a database access error occurs; this method is called on a closed result set or the result set concurrency is
     * <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public void moveToCurrentRow() throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>Ref</code> object in the Java
     * programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>Ref</code> object representing an SQL <code>REF</code> value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Ref getRef(int columnIndex) throws SQLException {
        // TODO: figure out what REF's are and implement this method
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>Ref</code> object in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>Ref</code> object representing the SQL <code>REF</code> value in the specified column
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Ref getRef(String columnLabel) throws SQLException {
        // TODO see getRef(int)
        throw ExceptionMapper.getFeatureNotSupportedException("Getting REFs not supported");
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>Blob</code> object in the Java
     * programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>Blob</code> object representing the SQL <code>BLOB</code> value in the specified column
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Blob getBlob(int columnIndex) throws SQLException {
        byte[] bytes = getValueObject(columnIndex).getBytes();
        if (bytes == null) {
            return null;
        }
        return new MariaDbBlob(bytes);
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>Blob</code> object in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>Blob</code> object representing the SQL <code>BLOB</code> value in the specified column
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Blob getBlob(String columnLabel) throws SQLException {
        return getBlob(findColumn(columnLabel));
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>Clob</code> object in the Java
     * programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>Clob</code> object representing the SQL <code>CLOB</code> value in the specified column
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Clob getClob(int columnIndex) throws SQLException {
        byte[] bytes = getValueObject(columnIndex).getBytes();
        if (bytes == null) {
            return null;
        }
        return new MariaDbClob(bytes);
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>Clob</code> object in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>Clob</code> object representing the SQL <code>CLOB</code> value in the specified column
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Clob getClob(String columnLabel) throws SQLException {
        return getClob(findColumn(columnLabel));
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as an <code>Array</code> object in the
     * Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return an <code>Array</code> object representing the SQL <code>ARRAY</code> value in the specified column
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Array getArray(int columnIndex) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Arrays are not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as an <code>Array</code> object in the
     * Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return an <code>Array</code> object representing the SQL <code>ARRAY</code> value in the specified column
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.2
     */
    public Array getArray(String columnLabel) throws SQLException {
        return getArray(findColumn(columnLabel));
    }


    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.net.URL</code> object in
     * the Java programming language.
     *
     * @param columnIndex the index of the column 1 is the first, 2 is the second,...
     * @return the column value as a <code>java.net.URL</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * in the Java programming language
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; this method is called on a closed result set
     * or if a URL is malformed
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    @Override
    public URL getURL(int columnIndex) throws SQLException {
        try {
            return new URL(getValueObject(columnIndex).getString());
        } catch (MalformedURLException e) {
            throw ExceptionMapper.getSqlException("Could not parse as URL");
        }
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.net.URL</code> object in
     * the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value as a <code>java.net.URL</code> object; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * in the Java programming language
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; this method is called on a closed result set
     * or if a URL is malformed
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    @Override
    public URL getURL(String columnLabel) throws SQLException {
        return getURL(findColumn(columnLabel));
    }

    /**
     * Updates the designated column with a <code>java.sql.Ref</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param ref the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateRef(int columnIndex, Ref ref) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Ref</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param ref the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateRef(String columnLabel, Ref ref) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Blob</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param blob the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateBlob(int columnIndex, Blob blob) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Blob</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param blob the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateBlob(String columnLabel, Blob blob) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Updates the designated column using the given input stream. The data will be read from the stream as needed until end-of-stream is
     * reached.</p><p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of <code>updateBlob</code>
     * which takes a length parameter.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given input stream. The data will be read from the stream as needed until end-of-stream is
     * reached.</p><p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not
     * update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of <code>updateBlob</code>
     * which takes a length parameter.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param inputStream An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given input stream, which will have the specified number of bytes. </p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param inputStream An object that contains the data to set the parameter value to.
     * @param length the number of bytes in the parameter data.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given input stream, which will have the specified number of bytes. </p> <p> The updater methods are
     * used to update column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param inputStream An object that contains the data to set the parameter value to.
     * @param length the number of bytes in the parameter data.
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }


    /**
     * Updates the designated column with a <code>java.sql.Clob</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param clob the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateClob(int columnIndex, Clob clob) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Clob</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param clob the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateClob(String columnLabel, Clob clob) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * <p>Updates the designated column using the given <code>Reader</code> object, which is the given number of characters long. When a very large
     * UNICODE value is input to a <code>LONGVARCHAR</code> parameter, it may be more practical to send it via a <code>java.io.Reader</code> object.
     * The JDBC driver will do any necessary conversion from UNICODE to the database char format.</p><p>The updater methods are used to
     * update column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database. </p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given <code>Reader</code> object, which is the given number of characters long. When a very large
     * UNICODE value is input to a <code>LONGVARCHAR</code> parameter, it may be more practical to send it via a <code>java.io.Reader</code> object.
     * The JDBC driver will do any necessary conversion from UNICODE to the database char format.</p><p>The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given <code>Reader</code> object. The data will be read from the stream as needed until
     * end-of-stream is reached.  The JDBC driver will do any necessary conversion from UNICODE to the database char format.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the
     * underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of <code>updateClob</code>
     * which takes a length parameter.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given <code>Reader</code> object. The data will be read from the stream as needed until
     * end-of-stream is reached.  The JDBC driver will do any necessary conversion from UNICODE to the database char format.</p>
     * <p>The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the
     * underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     * <p><B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of <code>updateClob</code>
     * which takes a length parameter.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Array</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param array the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateArray(int columnIndex, Array array) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.Array</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param array the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public void updateArray(String columnLabel, Array array) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.RowId</code> object
     * in the Java programming language.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @return the column value; if the value is a SQL <code>NULL</code> the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public java.sql.RowId getRowId(int columnIndex) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("RowIDs not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>java.sql.RowId</code> object
     * in the Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value ; if the value is a SQL <code>NULL</code> the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public java.sql.RowId getRowId(String columnLabel) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("RowIDs not supported");
    }

    /**
     * Updates the designated column with a <code>RowId</code> value. The updater methods are used to update column values in the current row or the
     * insert row. The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are
     * called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param rowId the column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateRowId(int columnIndex, java.sql.RowId rowId) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");

    }

    /**
     * Updates the designated column with a <code>RowId</code> value. The updater methods are used to update column values in the current row or the
     * insert row. The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are
     * called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param rowId the column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateRowId(String columnLabel, java.sql.RowId rowId) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");

    }

    /**
     * Retrieves the holdability of this <code>ResultSet</code> object
     *
     * @return either <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @throws SQLException if a database access error occurs or this method is called on a closed result set
     * @since 1.6
     */
    public int getHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }



    /**
     * Updates the designated column with a <code>String</code> value. It is intended for use when updating <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns. The updater methods are used to update column values in the current row or the insert row.  The updater
     * methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the
     * database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param nstring the value for the column to be updated
     * @throws java.sql.SQLException if the columnIndex is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNString(int columnIndex, String nstring) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>String</code> value. It is intended for use when updating <code>NCHAR</code>,<code>NVARCHAR</code>
     * and <code>LONGNVARCHAR</code> columns. The updater methods are used to update column values in the current row or the insert row.  The updater
     * methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the
     * database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param nstring the value for the column to be updated
     * @throws java.sql.SQLException if the columnLabel is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set; the result set concurrency is
     * <CODE>CONCUR_READ_ONLY</code> or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNString(String columnLabel, String nstring) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.NClob</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param nclob the value for the column to be updated
     * @throws java.sql.SQLException if the columnIndex is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set; if a database access error occurs or the result
     * set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNClob(int columnIndex, NClob nclob) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.NClob</code> value. The updater methods are used to update column values in the current row
     * or the insert row.  The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param nclob the value for the column to be updated
     * @throws java.sql.SQLException if the columnLabel is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set; if a database access error occurs or the result
     * set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNClob(String columnLabel, NClob nclob) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates are not supported");
    }

    /**
     * Updates the designated column using the given <code>Reader</code><p>
     * The data will be read from the stream as needed until end-of-stream is reached.  The JDBC driver will do any necessary conversion from UNICODE
     * to the database char format.<p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.<p>
     * <B>Note:</B> Consult your JDBC driver documentation to determine if it might be more efficient to use a version of <code>updateNClob</code>
     * which takes a length parameter.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException if the columnIndex is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set, if a database access error occurs or the result
     * set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given <code>Reader</code> object. The data will be read from the stream as needed until
     * end-of-stream is reached.  The JDBC driver will do any necessary conversion from UNICODE to the database char format.</p> <p>The updater
     * methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying database;
     * instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p> <B>Note:</B> Consult your JDBC
     * driver documentation to determine if it might be more efficient to use a version of <code>updateNClob</code> which takes a length parameter.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @throws java.sql.SQLException if the columnLabel is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set; if a database access error occurs or the result
     * set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }


    /**
     * <p>Updates the designated column using the given <code>Reader</code> object, which is the given number of characters long. When a very large
     * UNICODE value is input to a <code>LONGVARCHAR</code> parameter, it may be more practical to send it via a <code>java.io.Reader</code> object.
     * The JDBC driver will do any necessary conversion from UNICODE to the database char format.</p><p>The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @throws java.sql.SQLException if the columnIndex is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set, if a database access error occurs or the result
     * set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column using the given <code>Reader</code> object, which is the given number of characters long. When a very large
     * UNICODE value is input to a <code>LONGVARCHAR</code> parameter, it may be more practical to send it via a <code>java.io.Reader</code> object.
     * The JDBC driver will do any necessary conversion from UNICODE to the database char format.</p><p>The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader An object that contains the data to set the parameter value to.
     * @param length the number of characters in the parameter data.
     * @throws java.sql.SQLException if the columnLabel is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set; if a database access error occurs or the result
     * set concurrency is <code>CONCUR_READ_ONLY</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>NClob</code> object in the
     * Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>NClob</code> object representing the SQL <code>NCLOB</code> value in the specified column
     * @throws java.sql.SQLException if the columnIndex is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public NClob getNClob(int columnIndex) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("NClobs are not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>NClob</code> object in the
     * Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>NClob</code> object representing the SQL <code>NCLOB</code> value in the specified column
     * @throws java.sql.SQLException if the columnLabel is not valid; if the driver does not support national character sets;  if the driver can
     * detect that a data conversion error could occur; this method is called on a closed result set or if a database access error occurs
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public NClob getNClob(String columnLabel) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("NClobs are not supported");
    }

    /**
     * Retrieves the value of the designated column in  the current row of this <code>ResultSet</code> as a <code>java.sql.SQLXML</code> object in the
     * Java programming language.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return a <code>SQLXML</code> object that maps an <code>SQL XML</code> value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("SQLXML not supported");
    }

    /**
     * Retrieves the value of the designated column in  the current row of this <code>ResultSet</code> as a <code>java.sql.SQLXML</code> object in the
     * Java programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return a <code>SQLXML</code> object that maps an <code>SQL XML</code> value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("SQLXML not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.SQLXML</code> value. The updater methods are used to update column values in the current
     * row or the insert row. The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second 2, ...
     * @param xmlObject the value for the column to be updated
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; this method is called on a closed result set;
     * the <code>java.xml.transform.Result</code>, <code>Writer</code> or <code>OutputStream</code> has not been closed for the <code>SQLXML</code>
     * object; if there is an error processing the XML value or the result set concurrency is <code>CONCUR_READ_ONLY</code>.  The
     * <code>getCause</code> method of the exception may provide a more detailed exception, for example, if the stream does not contain valid XML.
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("SQLXML not supported");
    }

    /**
     * Updates the designated column with a <code>java.sql.SQLXML</code> value. The updater methods are used to update column values in the current
     * row or the insert row. The updater methods do not update the underlying database; instead the <code>updateRow</code> or <code>insertRow</code>
     * methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param xmlObject the column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; this method is called on a closed result set;
     * the <code>java.xml.transform.Result</code>, <code>Writer</code> or <code>OutputStream</code> has not been closed for the <code>SQLXML</code>
     * object; if there is an error processing the XML value or the result set concurrency is <code>CONCUR_READ_ONLY</code>.  The
     * <code>getCause</code> method of the exception may provide a more detailed exception, for example, if the stream does not contain valid XML.
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("SQLXML not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>String</code> in the Java
     * programming language. It is intended for use when accessing <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public String getNString(int columnIndex) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("NString not supported");
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>String</code> in the Java
     * programming language. It is intended for use when accessing <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>null</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public String getNString(String columnLabel) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("NString not supported");
    }


    /**
     * <p>Updates the designated column with a character stream value, which will have the specified number of bytes.   The driver does the necessary
     * conversion from Java character format to the national character set in the database. It is intended for use when updating
     * <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.</p>
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param value the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNCharacterStream(int columnIndex, Reader value, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * Updates the designated column with a character stream value, which will have the specified number of bytes.  The driver does the necessary
     * conversion from Java character format to the national character set in the database. It is intended for use when updating
     * <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.
     * The updater methods are used to update column values in the current row or the insert row.  The updater methods do not update the underlying
     * database; instead the <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader the <code>java.io.Reader</code> object containing the new column value
     * @param length the length of the stream
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }


    /**
     * <p>Updates the designated column with a character stream value. The data will be read from the stream as needed until end-of-stream is reached.
     * The driver does the necessary conversion from Java character format to the national character set in the database. It is intended for use when
     * updating <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.</p><p>The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p><p><B>Note:</B> Consult your JDBC driver
     * documentation to determine if it might be more efficient to use a version of <code>updateNCharacterStream</code> which takes a length
     * parameter.</p>
     *
     * @param columnIndex the first column is 1, the second is 2, ...
     * @param reader the new column value
     * @throws java.sql.SQLException if the columnIndex is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNCharacterStream(int columnIndex, Reader reader) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }

    /**
     * <p>Updates the designated column with a character stream value. The data will be read from the stream as needed until end-of-stream is reached.
     * The driver does the necessary conversion from Java character format to the national character set in the database. It is intended for use when
     * updating <code>NCHAR</code>,<code>NVARCHAR</code> and <code>LONGNVARCHAR</code> columns.</p><p>The updater methods are used to update
     * column values in the current row or the insert row.  The updater methods do not update the underlying database; instead the
     * <code>updateRow</code> or <code>insertRow</code> methods are called to update the database.</p><p><B>Note:</B> Consult your JDBC driver
     * documentation to determine if it might be more efficient to use a version of <code>updateNCharacterStream</code> which takes a length
     * parameter.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @param reader the <code>java.io.Reader</code> object containing the new column value
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs; the result set concurrency is
     * <code>CONCUR_READ_ONLY</code> or this method is called on a closed result set
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.6
     */
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Updates not supported");
    }


    public boolean getBoolean(int index) throws SQLException {
        return getValueObject(index).getBoolean();
    }


    /**
     * <p>Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>boolean</code> in the Java
     * programming language.</p> <p>If the designated column has a datatype of CHAR or VARCHAR and contains a "0" or has a datatype of BIT,
     * TINYINT, SMALLINT, INTEGER or BIGINT and contains  a 0, a value of <code>false</code> is returned.  If the designated column has a datatype of
     * CHAR or VARCHAR and contains a "1" or has a datatype of BIT, TINYINT, SMALLINT, INTEGER or BIGINT and contains  a 1, a value of
     * <code>true</code> is returned.</p>
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>false</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    public byte getByte(int index) throws SQLException {
        return getValueObject(index).getByte();
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>byte</code> in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    public short getShort(int index) throws SQLException {
        return getValueObject(index).getShort();
    }

    /**
     * Retrieves the value of the designated column in the current row of this <code>ResultSet</code> object as a <code>short</code> in the Java
     * programming language.
     *
     * @param columnLabel the label for the column specified with the SQL AS clause.  If the SQL AS clause was not specified, then the label is the
     * name of the column
     * @return the column value; if the value is SQL <code>NULL</code>, the value returned is <code>0</code>
     * @throws java.sql.SQLException if the columnLabel is not valid; if a database access error occurs or this method is called on a closed result
     * set
     */
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }


    /**
     * <p>Returns an object that implements the given interface to allow access to non-standard methods, or standard methods not exposed by the
     * proxy.</p><p>If the receiver implements the interface then the result is the receiver or a proxy for the receiver. If the receiver is a
     * wrapper and the wrapped object implements the interface then the result is the wrapped object or a proxy for the wrapped object. Otherwise
     * return the the result of calling <code>unwrap</code> recursively on the wrapped object or a proxy for that result. If the receiver is not a
     * wrapper and does not implement the interface, then an <code>SQLException</code> is thrown.</p>
     *
     * @param iface A Class defining an interface that the result must implement.
     * @return an object that implements the interface. May be a proxy for the actual implementing object.
     * @throws java.sql.SQLException If no object found that implements the interface
     * @since 1.6
     */
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    /**
     * Returns true if this either implements the interface argument or is directly or indirectly a wrapper for an object that does. Returns false
     * otherwise. If this implements the interface then return true, else if this is a wrapper then return the result of recursively calling
     * <code>isWrapperFor</code> on the wrapped object. If this does not implement the interface and is not a wrapper, return false. This method
     * should be implemented as a low-cost operation compared to <code>unwrap</code> so that callers can use this method to avoid expensive
     * <code>unwrap</code> calls that may fail. If this method returns true then calling <code>unwrap</code> with the same argument should succeed.
     *
     * @param iface a Class defining an interface.
     * @return true if this implements the interface or directly or indirectly wraps an object that does.
     * @throws java.sql.SQLException if an error occurs while determining whether this is a wrapper for an object with the given interface.
     * @since 1.6
     */
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public void setReturnTableAlias(boolean returnTableAlias) {
        this.returnTableAlias = returnTableAlias;
    }
}
