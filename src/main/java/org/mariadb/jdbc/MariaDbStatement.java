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

package org.mariadb.jdbc;

import org.mariadb.jdbc.internal.queryresults.*;
import org.mariadb.jdbc.internal.queryresults.resultset.MariaSelectResultSet;
import org.mariadb.jdbc.internal.util.ExceptionMapper;
import org.mariadb.jdbc.internal.util.dao.QueryException;
import org.mariadb.jdbc.internal.util.Utils;
import org.mariadb.jdbc.internal.query.MariaDbQuery;
import org.mariadb.jdbc.internal.query.Query;
import org.mariadb.jdbc.internal.protocol.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;


public class MariaDbStatement implements Statement {
    private static volatile Timer timer;
    /**
     * the protocol used to talk to the server.
     */
    protected Protocol protocol;
    /**
     * the  Connection object.
     */
    protected MariaDbConnection connection;
    protected boolean binaryData = false;
    /**
     * The actual query result.
     */
    protected TimerTask timerTask;
    protected boolean isRewriteable = true;
    protected String firstRewrite = null;
    boolean isClosed;
    boolean isTimedout;
    volatile boolean executing;
    Deque<Query> batchQueries;
    Deque<ExecutionResult> cachedExecutionResults;
    //are warnings cleared?
    private boolean warningsCleared;
    private int queryTimeout;
    private boolean escapeProcessing;
    protected int fetchSize;
    private int maxRows;
    public static final Pattern deleteEndSemicolonPattern = Pattern.compile("[;][ ]*$", Pattern.CASE_INSENSITIVE);
    protected ExecutionResult executionResult = null;
    protected int resultSetScrollType;

    /**
     * Creates a new Statement.
     *
     * @param connection the connection to return in getConnection.
     * @param resultSetScrollType one of the following <code>ResultSet</code> constants: <code>ResultSet.TYPE_FORWARD_ONLY</code>,
     * <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     */
    public MariaDbStatement(MariaDbConnection connection, int resultSetScrollType) {
        this.protocol = connection.getProtocol();
        this.connection = connection;
        this.escapeProcessing = true;
        this.resultSetScrollType = resultSetScrollType;
        cachedExecutionResults = new ArrayDeque<>();
    }

    private static Timer getTimer() {
        Timer result = timer;
        if (result == null) {
            synchronized (MariaDbStatement.class) {
                result = timer;
                if (result == null) {
                    timer = result = new Timer("MariaDB-JDBC-Timer", true);
                }
            }
        }
        return result;
    }

    /**
     * Provide a "cleanup" method that can be called after unloading driver, to fix Tomcat's obscure classpath handling.
     */
    public static void unloadDriver() {
        if (timer != null) {
            timer.cancel();
        }
    }

    /**
     * returns the protocol.
     *
     * @return the protocol used.
     */
    public Protocol getProtocol() {
        return protocol;
    }

    // Part of query prolog - setup timeout timer
    private void setTimerTask() {
        assert (timerTask == null);
        timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    isTimedout = true;
                    protocol.cancelCurrentQuery();
                } catch (Throwable e) {
                }
            }
        };
        getTimer().schedule(timerTask, queryTimeout * 1000);
    }

    void executeQueryProlog() throws SQLException {
        if (isClosed) {
            throw new SQLException("execute() is called on closed statement");
        }
        if (protocol.isExplicitClosed()) {
            throw new SQLException("execute() is called on closed connection");
        }
        //old failover handling
        if (protocol.getProxy() == null) {
            checkReconnectWithoutProxy();
        }

        protocol.closeIfActiveResult();
        if (protocol.hasMoreResults()) {
            // Skip remaining result sets. CallableStatement might return many of them  -
            // not only the "select" result sets, but also the "update" results
            try {
                protocol.skip();
            } catch (QueryException e) {
                ExceptionMapper.throwException(e, connection, this);
            }
        }
        warningsCleared = false;
        connection.reenableWarnings();

        cachedExecutionResults.clear();

        try {
            protocol.setMaxRows(maxRows);
        } catch (QueryException qe) {
            ExceptionMapper.throwException(qe, connection, this);
        }
        if (queryTimeout != 0) {
            setTimerTask();
        }
    }

    protected void cacheMoreResults(ExecutionResult execResult, int fetchSize) {
        if (fetchSize != 0) {
            return;
        }
        if (execResult.hasMoreResultAvailable()) {
            try {
                while (protocol.hasMoreResults()) {
                    SingleExecutionResult executionResult = new SingleExecutionResult(this, 0, true);
                    protocol.getMoreResults(executionResult);
                    cachedExecutionResults.add(executionResult);
                }
            } catch (QueryException e) {
                ;
            }
        }
    }

    /**
     * Reset timeout after query, re-throw SQL exception
     *
     * @param queryException exception if exist
     * @throws SQLException exception with new message in case of timer timeout.
     */
    protected void executeQueryEpilog(QueryException queryException) throws SQLException {

        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        if (isTimedout) {
            isTimedout = false;
            queryException = new QueryException("Query timed out", 1317, "JZ0002", queryException);
        }

        if (queryException == null) {
            return;
        }

        //if has a failover, closing the statement
        if (queryException.getSqlState() != null && queryException.getSqlState().startsWith("08")) {
            close();
        }

        ExceptionMapper.throwException(queryException, connection, this);
    }

    /**
     * executes a query.
     *
     * @param query the query
     * @return true if there was a result set, false otherwise.
     * @throws SQLException the error description
     */
    protected boolean execute(Query query) throws SQLException {
        checkClose();
        executing = true;
        QueryException exception = null;
        connection.lock.lock();
        try {
            executeQueryProlog();
            try {
                SingleExecutionResult internalExecutionResult = new SingleExecutionResult(this, fetchSize, true);
                protocol.executeQuery(query, internalExecutionResult, resultSetScrollType, fetchSize);
                cacheMoreResults(internalExecutionResult, fetchSize);
                executionResult = internalExecutionResult;
                return executionResult.getResult() != null;
            } catch (QueryException e) {
                exception = e;
                return false;
            } finally {
                executeQueryEpilog(exception);
                executing = false;
            }
        } finally {
            connection.lock.unlock();
        }
    }

    /**
     * executes a query.
     *
     * @param queryString the query
     * @return true if there was a result set, false otherwise.
     * @throws SQLException if the query could not be sent to server
     */
    public boolean execute(String queryString) throws SQLException {
        return execute(stringToQuery(queryString));
    }

    /**
     * <p>Executes the given SQL statement, which may return multiple results, and signals the driver that any auto-generated keys should be made
     * available for retrieval.  The driver will ignore this signal if the SQL statement is not an <code>INSERT</code> statement, or an SQL statement
     * able to return auto-generated keys (the list of such statements is vendor-specific).</p> <p>In some (uncommon) situations, a single SQL
     * statement may return multiple result sets and/or update counts. Normally you can ignore this unless you are (1) executing a stored procedure
     * that you know may return multiple results or (2) you are dynamically executing an unknown SQL string. </p> The <code>execute</code> method
     * executes an SQL statement and indicates the form of the first result.  You must then use the methods <code>getResultSet</code> or
     * <code>getUpdateCount</code> to retrieve the result, and <code>getInternalMoreResults</code> to move to any subsequent result(s).
     *
     * @param sql any SQL statement
     * @param autoGeneratedKeys a constant indicating whether auto-generated keys should be made available for retrieval using the method
     * <code>getGeneratedKeys</code>; one of the following constants: <code>Statement.RETURN_GENERATED_KEYS</code> or
     * <code>Statement.NO_GENERATED_KEYS</code>
     * @return <code>true</code> if the first result is a <code>ResultSet</code> object; <code>false</code> if it is an update count or there are no
     * results
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the second
     * parameter supplied to this method is not <code>Statement.RETURN_GENERATED_KEYS</code> or <code>Statement.NO_GENERATED_KEYS</code>.
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method with a constant of
     * Statement.RETURN_GENERATED_KEYS
     * @see #getResultSet
     * @see #getUpdateCount
     * @see #getMoreResults
     * @see #getGeneratedKeys
     */
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        return execute(stringToQuery(sql));
    }


    /**
     * Executes the given SQL statement, which may return multiple results, and signals the driver that the auto-generated keys indicated in the given
     * array should be made available for retrieval.  This array contains the indexes of the columns in the target table that contain the
     * auto-generated keys that should be made available. The driver will ignore the array if the SQL statement is not an <code>INSERT</code>
     * statement, or an SQL statement able to return auto-generated keys (the list of such statements is vendor-specific). <p>Under some (uncommon)
     * situations, a single SQL statement may return multiple result sets and/or update counts. Normally you can ignore this unless you are (1)
     * executing a stored procedure that you know may return multiple results or (2) you are dynamically executing an unknown SQL string.</p> The
     * <code>execute</code> method executes an SQL statement and indicates the form of the first result.  You must then use the methods
     * <code>getResultSet</code> or <code>getUpdateCount</code> to retrieve the result, and <code>getInternalMoreResults</code> to move to any
     * subsequent result(s).
     *
     * @param sql any SQL statement
     * @param columnIndexes an array of the indexes of the columns in the inserted row that should be  made available for retrieval by a call to the
     * method <code>getGeneratedKeys</code>
     * @return <code>true</code> if the first result is a <code>ResultSet</code> object; <code>false</code> if it is an update count or there are no
     * results
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the elements in
     * the <code>int</code> array passed to this method are not valid column indexes
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @see #getResultSet
     * @see #getUpdateCount
     * @see #getMoreResults
     * @since 1.4
     */
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        return execute(stringToQuery(sql));
    }


    /**
     * <p>Executes the given SQL statement, which may return multiple results, and signals the driver that the auto-generated keys indicated in the
     * given array should be made available for retrieval. This array contains the names of the columns in the target table that contain the
     * auto-generated keys that should be made available.  The driver will ignore the array if the SQL statement is not an <code>INSERT</code>
     * statement, or an SQL statement able to return auto-generated keys (the list of such statements is vendor-specific). </p>
     * <p> In some (uncommon) situations, a single
     * SQL statement may return multiple result sets and/or update counts. Normally you can ignore this unless you are (1) executing a stored
     * procedure that you know may return multiple results or (2) you are dynamically executing an unknown SQL string. </p>
     * <p>The <code>execute</code> method executes an SQL statement and indicates the form of the first result.  You must then use the methods
     * <code>getResultSet</code> or <code>getUpdateCount</code> to retrieve the result, and <code>getInternalMoreResults</code> to move to any
     * subsequent result(s).</p>
     *
     * @param sql any SQL statement
     * @param columnNames an array of the names of the columns in the inserted row that should be made available for retrieval by a call to the method
     * <code>getGeneratedKeys</code>
     * @return <code>true</code> if the next result is a <code>ResultSet</code> object; <code>false</code> if it is an update count or there are no
     * more results
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the elements of
     * the <code>String</code> array passed to this method are not valid column names
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @see #getResultSet
     * @see #getUpdateCount
     * @see #getMoreResults
     * @see #getGeneratedKeys
     * @since 1.4
     */
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        return execute(stringToQuery(sql));
    }


    /**
     * executes a select query.
     *
     * @param query the query to send to the server
     * @return a result set
     * @throws SQLException if something went wrong
     */
    protected ResultSet executeQuery(Query query) throws SQLException {
        if (execute(query)) {
            return executionResult.getResult();
        }
        //throw new SQLException("executeQuery() with query '" + query +"' did not return a result set");
        return MariaSelectResultSet.EMPTY;
    }

    /**
     * executes a select query.
     *
     * @param queryString the query to send to the server
     * @return a result set
     * @throws SQLException if something went wrong
     */
    public ResultSet executeQuery(String queryString) throws SQLException {
        return executeQuery(stringToQuery(queryString));
    }

    /**
     * Executes an update.
     *
     * @param query the update query.
     * @return update count
     * @throws SQLException if the query could not be sent to server.
     */
    protected int executeUpdate(Query query) throws SQLException {
        if (execute(query)) {
            return 0;
        }
        return getUpdateCount();
    }


    /**
     * Executes an update.
     *
     * @param queryString the update query.
     * @return update count
     * @throws SQLException if the query could not be sent to server.
     */
    public int executeUpdate(String queryString) throws SQLException {
        return executeUpdate(stringToQuery(queryString));
    }


    /**
     * Executes the given SQL statement and signals the driver with the given flag about whether the auto-generated keys produced by this
     * <code>Statement</code> object should be made available for retrieval.  The driver will ignore the flag if the SQL statement is not an
     * <code>INSERT</code> statement, or an SQL statement able to return auto-generated keys (the list of such statements is vendor-specific).
     *
     * @param sql an SQL Data Manipulation Language (DML) statement, such as <code>INSERT</code>, <code>UPDATE</code> or <code>DELETE</code>; or an
     * SQL statement that returns nothing, such as a DDL statement.
     * @param autoGeneratedKeys a flag indicating whether auto-generated keys should be made available for retrieval; one of the following constants:
     * <code>Statement.RETURN_GENERATED_KEYS</code> <code>Statement.NO_GENERATED_KEYS</code>
     * @return either (1) the row count for SQL Data Manipulation Language (DML) statements or (2) 0 for SQL statements that return nothing
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>, the given SQL
     * statement returns a <code>ResultSet</code> object, or the given constant is not one of those allowed
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method with a constant of
     * Statement.RETURN_GENERATED_KEYS
     * @since 1.4
     */
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        return executeUpdate(stringToQuery(sql));
    }


    /**
     * Executes the given SQL statement and signals the driver that the auto-generated keys indicated in the given array should be made available for
     * retrieval.   This array contains the indexes of the columns in the target table that contain the auto-generated keys that should be made
     * available. The driver will ignore the array if the SQL statement is not an <code>INSERT</code> statement, or an SQL statement able to return
     * auto-generated keys (the list of such statements is vendor-specific).
     *
     * @param sql an SQL Data Manipulation Language (DML) statement, such as <code>INSERT</code>, <code>UPDATE</code> or <code>DELETE</code>; or an
     * SQL statement that returns nothing, such as a DDL statement.
     * @param columnIndexes an array of column indexes indicating the columns that should be returned from the inserted row
     * @return either (1) the row count for SQL Data Manipulation Language (DML) statements or (2) 0 for SQL statements that return nothing
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>, the SQL statement
     * returns a <code>ResultSet</code> object, or the second argument supplied to this method is not an <code>int</code> array whose elements are
     * valid column indexes
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        return executeUpdate(stringToQuery(sql));
    }

    /**
     * Executes the given SQL statement and signals the driver that the auto-generated keys indicated in the given array should be made available for
     * retrieval.   This array contains the names of the columns in the target table that contain the auto-generated keys that should be made
     * available. The driver will ignore the array if the SQL statement is not an <code>INSERT</code> statement, or an SQL statement able to return
     * auto-generated keys (the list of such statements is vendor-specific).
     *
     * @param sql an SQL Data Manipulation Language (DML) statement, such as <code>INSERT</code>, <code>UPDATE</code> or <code>DELETE</code>; or an
     * SQL statement that returns nothing, such as a DDL statement.
     * @param columnNames an array of the names of the columns that should be returned from the inserted row
     * @return either the row count for <code>INSERT</code>, <code>UPDATE</code>, or <code>DELETE</code> statements, or 0 for SQL statements that
     * return nothing
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code>, the SQL statement
     * returns a <code>ResultSet</code> object, or the second argument supplied to this method is not a <code>String</code> array whose elements are
     * valid column names
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        return executeUpdate(stringToQuery(sql));
    }


    protected Query stringToQuery(String queryString) throws SQLException {
        queryString = deleteEndSemicolonPattern.matcher(queryString).replaceAll("");
        if (escapeProcessing) {
            queryString = Utils.nativeSql(queryString, connection.noBackslashEscapes);
        }
        return new MariaDbQuery(queryString);
    }

    /**
     * Releases this <code>Statement</code> object's database and JDBC resources immediately instead of waiting for this to happen when it is
     * automatically closed. It is generally good practice to release resources as soon as you are finished with them to avoid tying up database
     * resources. Calling the method <code>close</code> on a <code>Statement</code> object that is already closed has no effect. <B>Note:</B>When a
     * <code>Statement</code> object is closed, its current <code>ResultSet</code> object, if one exists, is also closed.
     *
     * @throws java.sql.SQLException if a database access error occurs
     */
    public void close() throws SQLException {
        boolean hasMoreResult = false;
        if (executionResult != null) {
            hasMoreResult = executionResult.hasMoreResultAvailable();
            if (executionResult.getFetchSize() > 0) {
                executionResult.close();
            }
            executionResult = null;
        }

        // No possible future use for the cached results, so these can be cleared
        // This makes the cache eligible for garbage collection earlier if the statement is not
        // immediately garbage collected
        cachedExecutionResults.clear();

        if (hasMoreResult) {
            connection.lock.lock();
            try {
                skipMoreResults();
            } finally {
                protocol = null;
                isClosed = true;
                connection.lock.unlock();
            }
        } else {
            protocol = null;
            isClosed = true;
        }
        if (connection == null || connection.pooledConnection == null
                || connection.pooledConnection.statementEventListeners.isEmpty()) {
            return;
        }
        connection.pooledConnection.fireStatementClosed(this);
    }

    /**
     * Retrieves the maximum number of bytes that can be returned for character and binary column values in a <code>ResultSet</code> object produced
     * by this <code>Statement</code> object. This limit applies only to <code>BINARY</code>, <code>VARBINARY</code>, <code>LONGVARBINARY</code>,
     * <code>CHAR</code>, <code>VARCHAR</code>, <code>NCHAR</code>, <code>NVARCHAR</code>, <code>LONGNVARCHAR</code> and <code>LONGVARCHAR</code>
     * columns.  If the limit is exceeded, the excess data is silently discarded.
     *
     * @return the current column size limit for columns storing character and binary values; zero means there is no limit
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @see #setMaxFieldSize
     */
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    /**
     * Sets the limit for the maximum number of bytes that can be returned for character and binary column values in a <code>ResultSet</code> object
     * produced by this <code>Statement</code> object. This limit applies only to <code>BINARY</code>, <code>VARBINARY</code>,
     * <code>LONGVARBINARY</code>, <code>CHAR</code>, <code>VARCHAR</code>, <code>NCHAR</code>, <code>NVARCHAR</code>, <code>LONGNVARCHAR</code> and
     * <code>LONGVARCHAR</code> fields.  If the limit is exceeded, the excess data is silently discarded. For maximum portability, use values greater
     * than 256.
     *
     * @param max the new column size limit in bytes; zero means there is no limit
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the condition max
     * &gt;= 0 is not satisfied
     * @see #getMaxFieldSize
     */
    public void setMaxFieldSize(final int max) throws SQLException {
        //we dont support max field sizes
    }

    /**
     * Retrieves the maximum number of rows that a <code>ResultSet</code> object produced by this <code>Statement</code> object can contain.  If this
     * limit is exceeded, the excess rows are silently dropped.
     *
     * @return the current maximum number of rows for a <code>ResultSet</code> object produced by this <code>Statement</code> object; zero means there
     * is no limit
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @see #setMaxRows
     */
    public int getMaxRows() throws SQLException {
        return maxRows;
    }

    /**
     * Sets the limit for the maximum number of rows that any <code>ResultSet</code> object  generated by this <code>Statement</code> object can
     * contain to the given number. If the limit is exceeded, the excess rows are silently dropped.
     *
     * @param max the new max rows limit; zero means there is no limit
     * @throws java.sql.SQLException if the condition max &gt;= 0 is not satisfied
     * @see #getMaxRows
     */
    public void setMaxRows(final int max) throws SQLException {
        if (max < 0) {
            throw new SQLException("max rows cannot be negative : asked for " + max);
        }
        maxRows = max;
    }

    /**
     * Sets escape processing on or off. If escape scanning is on (the default), the driver will do escape substitution before sending the SQL
     * statement to the database. Note: Since prepared statements have usually been parsed prior to making this call, disabling escape processing for
     * <code>PreparedStatements</code> objects will have no effect.
     *
     * @param enable <code>true</code> to enable escape processing; <code>false</code> to disable it
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     */
    public void setEscapeProcessing(final boolean enable) throws SQLException {
        escapeProcessing = enable;
    }

    /**
     * Retrieves the number of seconds the driver will wait for a <code>Statement</code> object to execute. If the limit is exceeded, a
     * <code>SQLException</code> is thrown.
     *
     * @return the current query timeout limit in seconds; zero means there is no limit
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @see #setQueryTimeout
     */
    public int getQueryTimeout() throws SQLException {
        return queryTimeout;
    }

    /**
     * Sets the number of seconds the driver will wait for a <code>Statement</code> object to execute to the given number of seconds. If the limit is
     * exceeded, an <code>SQLException</code> is thrown. A JDBC driver must apply this limit to the <code>execute</code>, <code>executeQuery</code>
     * and <code>executeUpdate</code> methods. JDBC driver implementations may also apply this limit to <code>ResultSet</code> methods (consult your
     * driver vendor documentation for details).
     *
     * @param seconds the new query timeout limit in seconds; zero means there is no limit
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the condition
     * seconds &gt;= 0 is not satisfied
     * @see #getQueryTimeout
     */
    public void setQueryTimeout(final int seconds) throws SQLException {
        this.queryTimeout = seconds;
    }

    /**
     * Sets the inputStream that will be used for the next execute that uses "LOAD DATA LOCAL INFILE". The name specified as local file/URL will be
     * ignored.
     *
     * @param inputStream inputStream instance, that will be used to send data to server
     * @throws java.sql.SQLException if statement is closed
     */
    public void setLocalInfileInputStream(InputStream inputStream) throws SQLException {
        checkClose();
        protocol.setLocalInfileInputStream(inputStream);
    }

    /**
     * Cancels this <code>Statement</code> object if both the DBMS and driver support aborting an SQL statement. This method can be used by one thread
     * to cancel a statement that is being executed by another thread.
     *
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     */
    public void cancel() throws SQLException {
        checkClose();
        try {
            if (!executing) {
                return;
            }
            protocol.cancelCurrentQuery();
        } catch (QueryException e) {
            ExceptionMapper.throwException(e, connection, this);
        } catch (IOException e) {
            // connection gone, query is definitely canceled
        }
    }

    /**
     * Retrieves the first warning reported by calls on this <code>Statement</code> object. Subsequent <code>Statement</code> object warnings will be
     * chained to this <code>SQLWarning</code> object. <p>The warning chain is automatically cleared each time a statement is (re)executed. This
     * method may not be called on a closed <code>Statement</code> object; doing so will cause an <code>SQLException</code> to be thrown.</p>
     * <p><B>Note:</B> If you are processing a <code>ResultSet</code> object, any warnings associated with reads on that <code>ResultSet</code> object
     * will be chained on it rather than on the <code>Statement</code> object that produced it.</p>
     *
     * @return the first <code>SQLWarning</code> object or <code>null</code> if there are no warnings
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     */
    public SQLWarning getWarnings() throws SQLException {
        checkClose();
        if (!warningsCleared) {
            return this.connection.getWarnings();
        }
        return null;
    }

    /**
     * Clears all the warnings reported on this <code>Statement</code> object. After a call to this method, the method <code>getWarnings</code> will
     * return <code>null</code> until a new warning is reported for this <code>Statement</code> object.
     *
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     */
    public void clearWarnings() throws SQLException {
        warningsCleared = true;
    }

    /**
     * Sets the SQL cursor name to the given <code>String</code>, which will be used by subsequent <code>Statement</code> object <code>execute</code>
     * methods. This name can then be used in SQL positioned update or delete statements to identify the current row in the <code>ResultSet</code>
     * object generated by this statement.  If the database does not support positioned update/delete, this method is a noop.  To insure that a cursor
     * has the proper isolation level to support updates, the cursor's <code>SELECT</code> statement should have the form <code>SELECT FOR
     * UPDATE</code>.  If <code>FOR UPDATE</code> is not present, positioned updates may fail. <p><B>Note:</B> By definition, the execution of
     * positioned updates and deletes must be done by a different <code>Statement</code> object than the one that generated the <code>ResultSet</code>
     * object being used for positioning. Also, cursor names must be unique within a connection.</p>
     *
     * @param name the new cursor name, which must be unique within a connection
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     */
    public void setCursorName(final String name) throws SQLException {
        throw ExceptionMapper.getFeatureNotSupportedException("Cursors are not supported");
    }

    /**
     * Gets the connection that created this statement.
     *
     * @return the connection
     * @throws SQLException if connection is invalid
     */
    public Connection getConnection() throws SQLException {
        return this.connection;
    }

    /**
     * Retrieves any auto-generated keys created as a result of executing this <code>Statement</code> object. If this <code>Statement</code> object
     * did not generate any keys, an empty <code>ResultSet</code> object is returned. <p><B>Note:</B>If the columns which represent the auto-generated
     * keys were not specified, the JDBC driver implementation will determine the columns which best represent the auto-generated keys.</p>
     *
     * @return a <code>ResultSet</code> object containing the auto-generated key(s) generated by the execution of this <code>Statement</code> object
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @throws java.sql.SQLFeatureNotSupportedException if the JDBC driver does not support this method
     * @since 1.4
     */
    public ResultSet getGeneratedKeys() throws SQLException {
        if (executionResult != null && executionResult.getResult() == null) {
            int autoIncrementIncrement = connection.getAutoIncrementIncrement();
            //multi insert in one execution. will create result based on autoincrement
            if (executionResult.hasMoreThanOneAffectedRows()) {
                long[] data;
                if (executionResult instanceof  SingleExecutionResult) {
                    int updateCount = executionResult.getFirstAffectedRows();
                    data = new long[updateCount];
                    for (int i = 0; i < updateCount; i++) {
                        data[i] = ((SingleExecutionResult) executionResult).getInsertId() + i * autoIncrementIncrement;
                    }
                } else {
                    MultiIntExecutionResult multiExecution = (MultiIntExecutionResult) executionResult;
                    int size = 0;
                    int affectedRowslength = multiExecution.getAffectedRows().length;
                    for (int i = 0; i < affectedRowslength; i++) {
                        size += multiExecution.getAffectedRows()[i];
                    }
                    data =  new long[size];
                    for (int affectedRows = 0; affectedRows < affectedRowslength; affectedRows++) {
                        for (int i = 0; i < affectedRows; i++) {
                            data[i] = multiExecution.getInsertIds()[affectedRows] + i * autoIncrementIncrement;
                        }
                    }
                }
                return MariaSelectResultSet.createGeneratedData(data, connection.getProtocol(), true);
            }
            return MariaSelectResultSet.createGeneratedData(executionResult.getInsertIds(), connection.getProtocol(), true);
        }
        return MariaSelectResultSet.EMPTY;
    }

    /**
     * Retrieves the result set holdability for <code>ResultSet</code> objects generated by this <code>Statement</code> object.
     *
     * @return either <code>ResultSet.HOLD_CURSORS_OVER_COMMIT</code> or <code>ResultSet.CLOSE_CURSORS_AT_COMMIT</code>
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @since 1.4
     */
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    /**
     * Retrieves whether this <code>Statement</code> object has been closed. A <code>Statement</code> is closed if the method close has been called on
     * it, or if it is automatically closed.
     *
     * @return true if this <code>Statement</code> object is closed; false if it is still open
     * @throws java.sql.SQLException if a database access error occurs
     * @since 1.6
     */
    public boolean isClosed() throws SQLException {
        return isClosed;
    }

    /**
     * Returns a  value indicating whether the <code>Statement</code> is poolable or not.
     *
     * @return <code>true</code> if the <code>Statement</code> is poolable; <code>false</code> otherwise
     * @throws java.sql.SQLException if this method is called on a closed <code>Statement</code>
     * @see java.sql.Statement#setPoolable(boolean) setPoolable(boolean)
     * @since 1.6
     */
    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    /**
     * <p>Requests that a <code>Statement</code> be pooled or not pooled.  The value specified is a hint to the statement pool implementation
     * indicating whether the applicaiton wants the statement to be pooled.  It is up to the statement pool manager as to whether the hint is
     * used.</p> <p> The poolable value of a statement is applicable to both internal statement caches implemented by the driver and external
     * statement caches implemented by application servers and other applications. </p> <p>By default, a <code>Statement</code> is not poolable when
     * created, and a <code>PreparedStatement</code> and <code>CallableStatement</code> are poolable when created.</p>
     *
     * @param poolable requests that the statement be pooled if true and that the statement not be pooled if false
     * @throws java.sql.SQLException if this method is called on a closed <code>Statement</code>
     * @since 1.6
     */
    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
    }

    /**
     * Retrieves the current result as a ResultSet object. This method should be called only once per result.
     *
     * @return the current result as a ResultSet object or null if the result is an update count or there are no more results
     * @throws SQLException if a database access error occurs or this method is called on a closed Statement
     */
    public ResultSet getResultSet() throws SQLException {
        checkClose();
        if (executionResult != null) {
            return executionResult.getResult();
        }
        return null;
    }

    /**
     * Retrieves the current result as an update count; if the result is a ResultSet object or there are no more results, -1 is returned. This method
     * should be called only once per result.
     *
     * @return the current result as an update count; -1 if the current result is a ResultSet object or there are no more results
     * @throws SQLException if a database access error occurs or this method is called on a closed Statement
     */
    public int getUpdateCount() throws SQLException {
        if (executionResult == null || executionResult.getResult() != null) {
            return -1;  /* Result comes from SELECT , or there are no more results */
        }
        if (executionResult instanceof SingleExecutionResult) {
            return (int) ((SingleExecutionResult) executionResult).getAffectedRows();
        } else {
            return ((MultiIntExecutionResult) executionResult).getAffectedRows()[0];
        }
    }

    protected void skipMoreResults() throws SQLException {
        try {
            protocol.skip();
            warningsCleared = false;
            connection.reenableWarnings();
        } catch (QueryException e) {
            ExceptionMapper.throwException(e, connection, this);
        }
    }

    /**
     * <p>Moves to this <code>Statement</code> object's next result, returns <code>true</code> if it is a <code>ResultSet</code> object, and
     * implicitly closes any current <code>ResultSet</code> object(s) obtained with the method <code>getResultSet</code>.</p>
     * There are no more results when the following is true: <pre> // stmt is a Statement object
     * ((stmt.getInternalMoreResults() == false) &amp;&amp; (stmt.getUpdateCount() == -1)) </pre>
     *
     * @return <code>true</code> if the next result is a <code>ResultSet</code> object; <code>false</code> if it is an update count or there are no
     * more results
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @see #execute
     */
    public boolean getMoreResults() throws SQLException {
        return getMoreResults(Statement.CLOSE_CURRENT_RESULT);
    }

    /**
     * <p>Moves to this <code>Statement</code> object's next result, deals with any current <code>ResultSet</code> object(s) according  to the
     * instructions specified by the given flag, and returns <code>true</code> if the next result is a <code>ResultSet</code> object.</p>
     * There are no more results when the following is true: <pre> // stmt is a Statement object
     * ((stmt.getInternalMoreResults(current) == false) &amp;&amp; (stmt.getUpdateCount() == -1))</pre>
     *
     * @param current one of the following <code>Statement</code> constants indicating what should happen to current <code>ResultSet</code> objects
     * obtained using the method <code>getResultSet</code>: <code>Statement.CLOSE_CURRENT_RESULT</code>, <code>Statement.KEEP_CURRENT_RESULT</code>,
     * or <code>Statement.CLOSE_ALL_RESULTS</code>
     * @return <code>true</code> if the next result is a <code>ResultSet</code> object; <code>false</code> if it is an update count or there are no
     * more results
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the argument
     * supplied is not one of the following: <code>Statement.CLOSE_CURRENT_RESULT</code>, <code>Statement.KEEP_CURRENT_RESULT</code> or
     * <code>Statement.CLOSE_ALL_RESULTS</code>
     * @throws java.sql.SQLFeatureNotSupportedException if <code>DatabaseMetaData.supportsMultipleOpenResults</code> returns <code>false</code> and
     * either <code>Statement.KEEP_CURRENT_RESULT</code> or <code>Statement.CLOSE_ALL_RESULTS</code> are supplied as the argument.
     * @see #execute
     * @since 1.4
     */
    public boolean getMoreResults(final int current) throws SQLException {
        //if fetch size is set to read fully, other resultset are put in cache
        if (executionResult != null) {
            if (executionResult.getFetchSize() == 0) {
                executionResult = cachedExecutionResults.poll();
                return executionResult != null && executionResult.getResult() != null;
            }

            //must fetch new data
            checkClose();
            protocol.getLock().lock();
            try {
                executionResult.close();
                SingleExecutionResult internalExecutionResult = new SingleExecutionResult(this, fetchSize, true);
                if (internalExecutionResult.hasMoreResultAvailable()) {
                    protocol.getMoreResults(internalExecutionResult);
                    executionResult = internalExecutionResult;
                    return executionResult.getResult() != null;
                } else {
                    executionResult = null;
                    return false;
                }
            } catch (QueryException queryException) {
                ExceptionMapper.throwException(queryException, connection, this);
                return false;
            } finally {
                protocol.getLock().unlock();
            }
        } else {
            return false;
        }
    }




    /**
     * Retrieves the direction for fetching rows from database tables that is the default for result sets generated from this <code>Statement</code>
     * object. If this <code>Statement</code> object has not set a fetch direction by calling the method <code>setFetchDirection</code>, the return
     * value is implementation-specific.
     *
     * @return the default fetch direction for result sets generated from this <code>Statement</code> object
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @see #setFetchDirection
     * @since 1.2
     */
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }

    /**
     * <p>Gives the driver a hint as to the direction in which rows will be processed in <code>ResultSet</code> objects created using this
     * <code>Statement</code> object.  The default value is <code>ResultSet.FETCH_FORWARD</code>.</p>
     * <p> Note that this method sets the default fetch
     * direction for result sets generated by this <code>Statement</code> object. Each result set has its own methods for getting and setting its own
     * fetch direction. </p>
     *
     * @param direction the initial direction for processing rows
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the given
     * direction is not one of <code>ResultSet.FETCH_FORWARD</code>, <code>ResultSet.FETCH_REVERSE</code>, or <code>ResultSet.FETCH_UNKNOWN</code>
     * @see #getFetchDirection
     * @since 1.2
     */
    public void setFetchDirection(final int direction) throws SQLException {
        return;
    }

    /**
     * Retrieves the number of result set rows that is the default fetch size for <code>ResultSet</code> objects generated from this
     * <code>Statement</code> object. If this <code>Statement</code> object has not set a fetch size by calling the method <code>setFetchSize</code>,
     * the return value is implementation-specific.
     *
     * @return the default fetch size for result sets generated from this <code>Statement</code> object
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @see #setFetchSize
     * @since 1.2
     */
    public int getFetchSize() throws SQLException {
        return this.fetchSize;
    }

    /**
     * Gives the JDBC driver a hint as to the number of rows that should be fetched from the database when more rows are needed for
     * <code>ResultSet</code> objects genrated by this <code>Statement</code>. If the value specified is zero, then the hint is ignored. The default
     * value is zero.
     *
     * @param rows the number of rows to fetch
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the condition
     * <code>rows &gt;= 0</code> is not satisfied.
     * @see #getFetchSize
     * @since 1.2
     */
    public void setFetchSize(final int rows) throws SQLException {
        if (rows < 0 && rows != Integer.MIN_VALUE) {
            throw new SQLException("invalid fetch size");
        } else if (rows == Integer.MIN_VALUE) {
            //for compatibility Integer.MIN_VALUE is transform to 0 => streaming
            this.fetchSize = 1;
            return;
        }
        this.fetchSize = rows;


    }

    /**
     * Retrieves the result set concurrency for <code>ResultSet</code> objects generated by this <code>Statement</code> object.
     *
     * @return either <code>ResultSet.CONCUR_READ_ONLY</code> or <code>ResultSet.CONCUR_UPDATABLE</code>
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @since 1.2
     */
    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }

    /**
     * Retrieves the result set type for <code>ResultSet</code> objects generated by this <code>Statement</code> object.
     *
     * @return one of <code>ResultSet.TYPE_FORWARD_ONLY</code>, <code>ResultSet.TYPE_SCROLL_INSENSITIVE</code>, or
     * <code>ResultSet.TYPE_SCROLL_SENSITIVE</code>
     * @throws java.sql.SQLException if a database access error occurs or this method is called on a closed <code>Statement</code>
     * @since 1.2
     */
    public int getResultSetType() throws SQLException {
        // TODO: this will change when the async protocol is implemented
        return ResultSet.TYPE_SCROLL_INSENSITIVE;
    }

    /**
     * Adds the given SQL command to the current list of commmands for this <code>Statement</code> object. The send in this list can be executed
     * as a batch by calling the method <code>executeBatch</code>.
     *
     * @param sql typically this is a SQL <code>INSERT</code> or <code>UPDATE</code> statement
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the driver does
     * not support batch updates
     * @see #executeBatch
     * @see java.sql.DatabaseMetaData#supportsBatchUpdates
     * @since 1.2
     */
    public void addBatch(final String sql) throws SQLException {
        if (batchQueries == null) {
            batchQueries = new ArrayDeque<>();
        }
        String sqlQuery;
        if (protocol.getOptions().rewriteBatchedStatements || protocol.getOptions().allowMultiQueries) {
            sqlQuery = deleteEndSemicolonPattern.matcher(sql).replaceAll("");
            isInsertRewriteable(sqlQuery);
        } else {
            sqlQuery = sql;
        }

        batchQueries.add(new MariaDbQuery(sqlQuery));
    }

    /**
     * Parses the sql string to understand whether it is compatible with rewritten batches.
     *
     * @param sql the sql string
     */
    protected void isInsertRewriteable(String sql) {
        if (!isRewriteable) {
            return;
        }
        int index = getInsertIncipit(sql);
        if (index == -1) {
            isRewriteable = false;
            return;
        }
        if (firstRewrite == null) {
            firstRewrite = sql.substring(0, index);
        }
    }

    /**
     * Parses the input string to understand if it is an INSERT statement. Returns the position of the round bracket after the VALUE(S) SQL keyword,
     * or -1 if it cannot understand it is an INSERT statement. Multiple statements cannot be parsed.
     *
     * @param sql the input SQL statement
     * @return the position of the round bracket after the VALUE(S) SQL keyword, or -1 if it cannot be parsed as an INSERT statement
     */
    protected int getInsertIncipit(String sql) {
        String sqlUpper = sql.toUpperCase();
        if (!sqlUpper.startsWith("INSERT") && !sqlUpper.startsWith("/*CLIENT*/ INSERT")) {
            return -1;
        }

        // INSERT FROM SELECT Cannot be rewritten.
        if (sqlUpper.indexOf("SELECT") != -1) {
            return -1;
        }

        int idx = sqlUpper.indexOf(" VALUE");
        if (idx == -1) {
            idx = sqlUpper.indexOf(")VALUE");
        }
        int startBracket = sqlUpper.indexOf("(", idx);
        int endBracket = sqlUpper.indexOf(")", startBracket);

        // Check for semicolons. Allow them inside the VALUES() brackets, otherwise return -1
        // there can be multiple, so let's loop through them

        int semicolonPos = sqlUpper.indexOf(';');

        while (semicolonPos > -1) {
            if (semicolonPos < startBracket || semicolonPos > endBracket) {
                return -1;
            }

            semicolonPos = sqlUpper.indexOf(';', semicolonPos + 1);
        }

        return startBracket;
    }

    /**
     * Empties this <code>Statement</code> object's current list of SQL send.
     *
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the driver does
     * not support batch updates
     * @see #addBatch
     * @see java.sql.DatabaseMetaData#supportsBatchUpdates
     * @since 1.2
     */
    public void clearBatch() throws SQLException {
        if (batchQueries != null) {
            batchQueries.clear();
        }
        firstRewrite = null;
        isRewriteable = true;
    }

    /**
     * Execute statements. depending on option, queries mays be rewritten :
     *
     * those queries will be rewritten if possible to
     * INSERT INTO ... VALUES (...) ; INSERT INTO ... VALUES (...);
     *
     * if option rewriteBatchedStatements is set to true, rewritten to
     * INSERT INTO ... VALUES (...), (...);
     *
     * @return an array of update counts containing one element for each command in the batch.  The elements of the array are ordered according to the
     * order in which send were added to the batch.
     * @throws java.sql.SQLException if a database access error occurs, this method is called on a closed <code>Statement</code> or the driver does
     * not support batch statements. Throws {@link java.sql.BatchUpdateException} (a subclass of <code>SQLException</code>) if one of the send
     * sent to the database fails to execute properly or attempts to return a result set.
     * @see #addBatch
     * @see java.sql.DatabaseMetaData#supportsBatchUpdates
     * @since 1.3
     */
    public int[] executeBatch() throws SQLException {
        checkClose();
        if (batchQueries == null || batchQueries.size() == 0) {
            return new int[0];
        }
        cachedExecutionResults.clear();
        MultiIntExecutionResult internalExecutionResult = new MultiIntExecutionResult(this, batchQueries.size(), 0, false);
        connection.lock.lock();
        try {
            QueryException exception = null;

            connection.lock.lock();
            try {
                executing = true;
                executeQueryProlog();
                try {
                    if (getProtocol().getOptions().allowMultiQueries || getProtocol().getOptions().rewriteBatchedStatements) {
                        boolean rewrittenBatch = isRewriteable && getProtocol().getOptions().rewriteBatchedStatements;
                        //fetch size is always set to 0 to permit to read result when rewritten.
                        protocol.executeMultiQueries(batchQueries, internalExecutionResult, 0, rewrittenBatch,
                                (rewrittenBatch && firstRewrite != null) ? firstRewrite.length() : 0);
                        cacheMoreResults(internalExecutionResult, 0);
                        if (rewrittenBatch) {
                            //operation will be done on first execution ( or a few execution if max packet size is not enought for one operation)
                            internalExecutionResult.updateResultsForRewrite();
                        } else {
                            //set update result right (first will have one operation, others are on moreResultPacket).
                            internalExecutionResult.updateResultsMultiple(cachedExecutionResults);
                        }
                    } else {
                        protocol.executeQueries(batchQueries, internalExecutionResult, resultSetScrollType);
                        cacheMoreResults(internalExecutionResult, 0);
                    }
                } catch (QueryException e) {
                    exception = e;
                } finally {
                    executionResult = internalExecutionResult;
                    executing = false;
                    executeQueryEpilog(exception);
                }
            } finally {
                connection.lock.unlock();
            }
            return internalExecutionResult.getAffectedRows();

        } catch (SQLException sqle) {
            throw new BatchUpdateException(sqle.getMessage(), sqle.getSQLState(), sqle.getErrorCode(), internalExecutionResult.getAffectedRows(),
                    sqle);
        } finally {
            connection.lock.unlock();
            clearBatch();
        }
    }

    /**
     * <p>Returns an object that implements the given interface to allow access to non-standard methods, or standard methods not exposed by the
     * proxy.</p>
     * <p>If the receiver implements the interface then the result is the receiver or a proxy for the receiver. If the receiver is a wrapper and
     * the wrapped object implements the interface then the result is the wrapped object or a proxy for the wrapped object. Otherwise return the the
     * result of calling <code>unwrap</code> recursively on the wrapped object or a proxy for that result. If the receiver is not a wrapper and does
     * not implement the interface, then an <code>SQLException</code> is thrown. </p>
     *
     * @param iface A Class defining an interface that the result must implement.
     * @return an object that implements the interface. May be a proxy for the actual implementing object.
     * @throws java.sql.SQLException If no object found that implements the interface
     * @since 1.6
     */
    @SuppressWarnings("unchecked")
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        try {
            if (isWrapperFor(iface)) {
                return (T) this;
            } else {
                throw new SQLException("The receiver is not a wrapper and does not implement the interface");
            }
        } catch (Exception e) {
            throw new SQLException("The receiver is not a wrapper and does not implement the interface");
        }
    }

    /**
     * Returns true if this either implements the interface argument or is directly or indirectly a wrapper for an object that does. Returns false
     * otherwise. If this implements the interface then return true, else if this is a wrapper then return the result of recursively calling
     * <code>isWrapperFor</code> on the wrapped object. If this does not implement the interface and is not a wrapper, return false. This method
     * should be implemented as a low-cost operation compared to <code>unwrap</code> so that callers can use this method to avoid expensive
     * <code>unwrap</code> calls that may fail. If this method returns true then calling <code>unwrap</code> with the same argument should succeed.
     *
     * @param interfaceOrWrapper a Class defining an interface.
     * @return true if this implements the interface or directly or indirectly wraps an object that does.
     * @throws java.sql.SQLException if an error occurs while determining whether this is a wrapper for an object with the given interface.
     * @since 1.6
     */
    public boolean isWrapperFor(final Class<?> interfaceOrWrapper) throws SQLException {
        return interfaceOrWrapper.isInstance(this);
    }

    public void closeOnCompletion() throws SQLException {
        // TODO Auto-generated method stub

    }

    public boolean isCloseOnCompletion() throws SQLException {
        // TODO Auto-generated method stub
        return false;
    }

    // Part of query prolog - check if connection is broken and reconnect
    private void checkReconnectWithoutProxy() throws SQLException {
        if (protocol.shouldReconnectWithoutProxy()) {
            try {
                protocol.connectWithoutProxy();
            } catch (QueryException qe) {
                ExceptionMapper.throwException(qe, connection, this);
            }
        }
    }

    /**
     * Check if statement is closed, and throw exception if so.
     * @throws SQLException if statement close
     */
    protected void checkClose() throws SQLException {
        if (isClosed) {
            throw new SQLException("Cannot do an operation on a closed statement");
        }
    }
}
