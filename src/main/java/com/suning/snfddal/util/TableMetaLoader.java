/*
 * Copyright 2015 suning.com Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Created on 2015年3月30日
// $Id$

package com.suning.snfddal.util;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.suning.snfddal.command.ddl.CreateTableData;
import com.suning.snfddal.config.TableConfig;
import com.suning.snfddal.dbobject.table.Column;
import com.suning.snfddal.message.DbException;
import com.suning.snfddal.message.ErrorCode;
import com.suning.snfddal.message.Trace;
import com.suning.snfddal.route.rule.RuleColumn;
import com.suning.snfddal.route.rule.TableRouter;
import com.suning.snfddal.value.DataType;
import com.suning.snfddal.value.ValueDate;
import com.suning.snfddal.value.ValueTime;
import com.suning.snfddal.value.ValueTimestamp;

/**
 * @author <a href="mailto:jorgie.mail@gmail.com">jorgie li</a>
 *
 */
public class TableMetaLoader {


    private static final int MAX_RETRY = 2;

    private boolean storesLowerCase;
    private boolean storesMixedCase;
    private boolean storesMixedCaseQuoted;
    private boolean supportsMixedCaseIdentifiers;
    private boolean force;

    private Trace trace;
    private Map<String, DataSource> dataNodes;
    

    /**
     * @param trace the trace to set
     */
    public void setTrace(Trace trace) {
        this.trace = trace;
    }

    /**
     * @param dataNodes the dataNodes to set
     */
    public void setDataNodes(Map<String, DataSource> dataNodes) {
        this.dataNodes = dataNodes;
    }

    public CreateTableData loadMetaData(TableConfig tableConfig) {
        CreateTableData createTableData = new CreateTableData();
        try {
            createTableData = readMetaData(tableConfig);
        } catch (DbException e) {
            if (!force) {
                throw e;
            }
            Column[] cols = {};
            createTableData.columns = New.arrayList();
            //setColumns(cols);
            //linkedIndex = new MappedIndex(this, id, IndexColumn.wrap(cols), IndexType.createNonUnique(false));
            //indexes.add(linkedIndex);
        }
        return createTableData;
    }

    private CreateTableData readMetaData(TableConfig tableConfig) {
        for (int retry = 0;; retry++) {
            try {
                Connection conn = null;
                try {
                    String metadataNode = tableConfig.getMetadata();
                    DataSource ds = this.dataNodes.get(metadataNode);
                    conn = ds.getConnection();
                    return tryReadMetaData(conn, tableConfig);
                } catch (Exception e) {
                    throw DbException.convert(e);
                } finally {
                    JdbcUtils.closeSilently(conn);
                }
            } catch (DbException e) {
                if (retry >= MAX_RETRY) {
                    throw e;
                }
            }
        }
    }

    private CreateTableData tryReadMetaData(Connection conn, TableConfig tableConfig) throws SQLException {
        
        CreateTableData tableData = new CreateTableData();
        
        DatabaseMetaData meta = conn.getMetaData();
        storesLowerCase = meta.storesLowerCaseIdentifiers();
        storesMixedCase = meta.storesMixedCaseIdentifiers();
        storesMixedCaseQuoted = meta.storesMixedCaseQuotedIdentifiers();
        supportsMixedCaseIdentifiers = meta.supportsMixedCaseIdentifiers();
        
        String originalCatalog = tableConfig.getOriginalCatalog();
        String originalSchema = tableConfig.getOriginalSchema();
        String originalTable = tableConfig.getOriginalTable();
        ResultSet rs = meta.getTables(originalCatalog, originalSchema, originalTable, null);
        if (rs.next() && rs.next()) {
            throw DbException.get(ErrorCode.SCHEMA_NAME_MUST_MATCH, originalTable);
        }
        rs.close();
        rs = meta.getColumns(originalCatalog, originalSchema, originalTable, null);
        int i = 0;
        ArrayList<Column> columnList = New.arrayList();
        HashMap<String, Column> columnMap = New.hashMap();
        String catalog = null, schema = null;
        while (rs.next()) {
            String thisCatalog = rs.getString("TABLE_CAT");
            if (catalog == null) {
                catalog = thisCatalog;
            }
            String thisSchema = rs.getString("TABLE_SCHEM");
            if (schema == null) {
                schema = thisSchema;
            }
            if (!StringUtils.equals(catalog, thisCatalog) || !StringUtils.equals(schema, thisSchema)) {
                // if the table exists in multiple schemas or tables,
                // use the alternative solution
                columnMap.clear();
                columnList.clear();
                break;
            }
            String n = rs.getString("COLUMN_NAME");
            n = convertColumnName(n);
            int sqlType = rs.getInt("DATA_TYPE");
            long precision = rs.getInt("COLUMN_SIZE");
            precision = convertPrecision(sqlType, precision);
            int scale = rs.getInt("DECIMAL_DIGITS");
            scale = convertScale(sqlType, scale);
            int displaySize = MathUtils.convertLongToInt(precision);
            int type = DataType.convertSQLTypeToValueType(sqlType);
            Column col = new Column(n, type, precision, scale, displaySize);
            //col.setTable(this, i++);
            columnList.add(col);
            columnMap.put(n, col);
        }
        rs.close();
        String qualifiedTableName = tableConfig.getQualifiedTableName();
        // check if the table is accessible
        Statement stat = null;
        try {
            stat = conn.createStatement();
            rs = stat.executeQuery("SELECT * FROM " + qualifiedTableName + " T WHERE 1=0");
            if (columnList.size() == 0) {
                // alternative solution
                ResultSetMetaData rsMeta = rs.getMetaData();
                for (i = 0; i < rsMeta.getColumnCount();) {
                    String n = rsMeta.getColumnName(i + 1);
                    n = convertColumnName(n);
                    int sqlType = rsMeta.getColumnType(i + 1);
                    long precision = rsMeta.getPrecision(i + 1);
                    precision = convertPrecision(sqlType, precision);
                    int scale = rsMeta.getScale(i + 1);
                    scale = convertScale(sqlType, scale);
                    int displaySize = rsMeta.getColumnDisplaySize(i + 1);
                    int type = DataType.getValueTypeFromResultSet(rsMeta, i + 1);
                    Column col = new Column(n, type, precision, scale, displaySize);
                    //col.setTable(this, i++);
                    columnList.add(col);
                    columnMap.put(n, col);
                }
            }
            rs.close();
        } catch (Exception e) {
            throw DbException.get(ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1, e, originalTable + "(" + e.toString() + ")");
        } finally {
            JdbcUtils.closeSilently(stat);
        }
        tableData.columns = columnList;
        //Column[] cols = new Column[columnList.size()];
        //columnList.toArray(cols);
        //setColumns(cols);
        //int id = getId();
        //linkedIndex = new MappedIndex(this, id, IndexColumn.wrap(cols), IndexType.createNonUnique(false));
        //indexes.add(linkedIndex);
        /*
        try {
            rs = meta.getPrimaryKeys(null, originalSchema, originalTable);
        } catch (Exception e) {
            // Some ODBC bridge drivers don't support it:
            // some combinations of "DataDirect SequeLink(R) for JDBC"
            // http://www.datadirect.com/index.ssp
            rs = null;
        }
        String pkName = "";
        ArrayList<Column> list;
        if (rs != null && rs.next()) {
            // the problem is, the rows are not sorted by KEY_SEQ
            list = New.arrayList();
            do {
                int idx = rs.getInt("KEY_SEQ");
                if (pkName == null) {
                    pkName = rs.getString("PK_NAME");
                }
                while (list.size() < idx) {
                    list.add(null);
                }
                String col = rs.getString("COLUMN_NAME");
                col = convertColumnName(col);
                Column column = columnMap.get(col);
                if (idx == 0) {
                    // workaround for a bug in the SQLite JDBC driver
                    list.add(column);
                } else {
                    list.set(idx - 1, column);
                }
            } while (rs.next());
            addIndex(list, IndexType.createPrimaryKey(false, false));
            rs.close();
        }
        try {
            rs = meta.getIndexInfo(null, originalSchema, originalTable, false, true);
        } catch (Exception e) {
            // Oracle throws an exception if the table is not found or is a
            // SYNONYM
            rs = null;
        }
        String indexName = null;
        list = New.arrayList();
        IndexType indexType = null;
        if (rs != null) {
            while (rs.next()) {
                if (rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    // ignore index statistics
                    continue;
                }
                String newIndex = rs.getString("INDEX_NAME");
                if (pkName.equals(newIndex)) {
                    continue;
                }
                if (indexName != null && !indexName.equals(newIndex)) {
                    addIndex(list, indexType);
                    indexName = null;
                }
                if (indexName == null) {
                    indexName = newIndex;
                    list.clear();
                }
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                indexType = unique ? IndexType.createUnique(false, false) : IndexType.createNonUnique(false);
                String col = rs.getString("COLUMN_NAME");
                col = convertColumnName(col);
                Column column = columnMap.get(col);
                list.add(column);
            }
            rs.close();
        }
        if (indexName != null) {
            addIndex(list, indexType);
        }*/
        //checkRuleColumn();
        
        return tableData;
    }

    private static long convertPrecision(int sqlType, long precision) {
        // workaround for an Oracle problem:
        // for DATE columns, the reported precision is 7
        // for DECIMAL columns, the reported precision is 0
        switch (sqlType) {
        case Types.DECIMAL:
        case Types.NUMERIC:
            if (precision == 0) {
                precision = 65535;
            }
            break;
        case Types.DATE:
            precision = Math.max(ValueDate.PRECISION, precision);
            break;
        case Types.TIMESTAMP:
            precision = Math.max(ValueTimestamp.PRECISION, precision);
            break;
        case Types.TIME:
            precision = Math.max(ValueTime.PRECISION, precision);
            break;
        }
        return precision;
    }

    private static int convertScale(int sqlType, int scale) {
        // workaround for an Oracle problem:
        // for DECIMAL columns, the reported precision is -127
        switch (sqlType) {
        case Types.DECIMAL:
        case Types.NUMERIC:
            if (scale < 0) {
                scale = 32767;
            }
            break;
        }
        return scale;
    }

    private String convertColumnName(String columnName) {
        if ((storesMixedCase || storesLowerCase) && columnName.equals(StringUtils.toLowerEnglish(columnName))) {
            columnName = StringUtils.toUpperEnglish(columnName);
        } else if (storesMixedCase && !supportsMixedCaseIdentifiers) {
            // TeraData
            columnName = StringUtils.toUpperEnglish(columnName);
        } else if (storesMixedCase && storesMixedCaseQuoted) {
            // MS SQL Server (identifiers are case insensitive even if quoted)
            columnName = StringUtils.toUpperEnglish(columnName);
        }
        return columnName;
    }

    /*
    private void addIndex(ArrayList<Column> list, IndexType indexType) {
        Column[] cols = new Column[list.size()];
        list.toArray(cols);
        Index index = new MappedIndex(this, 0, IndexColumn.wrap(cols), indexType);
        indexes.add(index);
    }*/
    
    protected void checkRuleColumn(TableConfig config,CreateTableData data) {
        TableRouter tableRouter = config.getTableRouter();
        if(tableRouter != null) {
            for (RuleColumn ruleCol : tableRouter.getRuleColumns()) {
                List<Column> columns = data.columns;
                Column matched = null;
                for (Column column : columns) {
                    String colName = column.getName();
                    if(colName.equalsIgnoreCase(ruleCol.getName())) {
                        matched = column;
                        break;
                    }                
                }
                if(matched == null){
                    throw DbException.getInvalidValueException("RuleColumn", ruleCol);
                }
            }
        }
    }


    /**
     * Wrap a SQL exception that occurred while accessing a linked table.
     *
     * @param sql the SQL statement
     * @param ex the exception from the remote database
     * @return the wrapped exception
     */
    public static DbException wrapException(String sql, Exception ex) {
        SQLException e = DbException.toSQLException(ex);
        return DbException.get(ErrorCode.ERROR_ACCESSING_DATABASE_TABLE_2, e, sql, e.toString());
    }


}
