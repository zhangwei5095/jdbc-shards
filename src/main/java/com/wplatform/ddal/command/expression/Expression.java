/*
 * Copyright 2014-2015 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wplatform.ddal.command.expression;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import com.wplatform.ddal.dbobject.table.Column;
import com.wplatform.ddal.dbobject.table.ColumnResolver;
import com.wplatform.ddal.dbobject.table.TableFilter;
import com.wplatform.ddal.engine.Database;
import com.wplatform.ddal.engine.Session;
import com.wplatform.ddal.message.DbException;
import com.wplatform.ddal.util.StringUtils;
import com.wplatform.ddal.value.DataType;
import com.wplatform.ddal.value.Value;
import com.wplatform.ddal.value.ValueArray;

/**
 * An expression is a operation, a value, or a function in a query.
 */
public abstract class Expression {

    private boolean addedToFilter;

    /**
     * Extracts expression columns from ValueArray
     *
     * @param session the current session
     * @param value   the value to extract columns from
     * @return array of expression columns
     */
    static Expression[] getExpressionColumns(Session session, ValueArray value) {
        Value[] list = value.getList();
        ExpressionColumn[] expr = new ExpressionColumn[list.length];
        for (int i = 0, len = list.length; i < len; i++) {
            Value v = list[i];
            Column col = new Column("C" + (i + 1), v.getType(),
                    v.getPrecision(), v.getScale(),
                    v.getDisplaySize());
            expr[i] = new ExpressionColumn(session.getDatabase(), col);
        }
        return expr;
    }

    /**
     * Extracts expression columns from the given result set.
     *
     * @param session the session
     * @param rs      the result set
     * @return an array of expression columns
     */
    public static Expression[] getExpressionColumns(Session session, ResultSet rs) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            Expression[] expressions = new Expression[columnCount];
            Database db = session == null ? null : session.getDatabase();
            for (int i = 0; i < columnCount; i++) {
                String name = meta.getColumnLabel(i + 1);
                int type = DataType.getValueTypeFromResultSet(meta, i + 1);
                int precision = meta.getPrecision(i + 1);
                int scale = meta.getScale(i + 1);
                int displaySize = meta.getColumnDisplaySize(i + 1);
                Column col = new Column(name, type, precision, scale, displaySize);
                Expression expr = new ExpressionColumn(db, col);
                expressions[i] = expr;
            }
            return expressions;
        } catch (SQLException e) {
            throw DbException.convert(e);
        }
    }

    /**
     * Return the resulting value for the current row.
     *
     * @param session the session
     * @return the result
     */
    public abstract Value getValue(Session session);

    /**
     * Return the data type. The data type may not be known before the
     * optimization phase.
     *
     * @return the type
     */
    public abstract int getType();

    /**
     * Map the columns of the resolver to expression columns.
     *
     * @param resolver the column resolver
     * @param level    the subquery nesting level
     */
    public abstract void mapColumns(ColumnResolver resolver, int level);

    /**
     * Try to optimize the expression.
     *
     * @param session the session
     * @return the optimized expression
     */
    public abstract Expression optimize(Session session);

    /**
     * Tell the expression columns whether the table filter can return values
     * now. This is used when optimizing the query.
     *
     * @param tableFilter the table filter
     * @param value       true if the table filter can return value
     */
    public abstract void setEvaluatable(TableFilter tableFilter, boolean value);

    /**
     * Get the scale of this expression.
     *
     * @return the scale
     */
    public abstract int getScale();

    /**
     * Get the precision of this expression.
     *
     * @return the precision
     */
    public abstract long getPrecision();

    /**
     * Get the display size of this expression.
     *
     * @return the display size
     */
    public abstract int getDisplaySize();

    /**
     * Get the SQL statement of this expression.
     * This may not always be the original SQL statement,
     * specially after optimization.
     *
     * @return the SQL statement
     */
    public abstract String getSQL();

    /**
     * Update an aggregate value. This method is called at statement execution
     * time. It is usually called once for each row, but if the expression is
     * used multiple times (for example in the column list, and as part of the
     * HAVING expression) it is called multiple times - the row counter needs to
     * be used to make sure the internal state is only updated once.
     *
     * @param session the session
     */
    public abstract void updateAggregate(Session session);

    /**
     * Check if this expression and all sub-expressions can fulfill a criteria.
     * If any part returns false, the result is false.
     *
     * @param visitor the visitor
     * @return if the criteria can be fulfilled
     */
    public abstract boolean isEverything(ExpressionVisitor visitor);

    /**
     * Estimate the cost to process the expression.
     * Used when optimizing the query, to calculate the query plan
     * with the lowest estimated cost.
     *
     * @return the estimated cost
     */
    public abstract int getCost();

    /**
     * If it is possible, return the negated expression. This is used
     * to optimize NOT expressions: NOT ID>10 can be converted to
     * ID&lt;=10. Returns null if negating is not possible.
     *
     * @param session the session
     * @return the negated expression, or null
     */
    public Expression getNotIfPossible(Session session) {
        // by default it is not possible
        return null;
    }

    /**
     * Check if this expression will always return the same value.
     *
     * @return if the expression is constant
     */
    public boolean isConstant() {
        return false;
    }

    /**
     * Is the value of a parameter set.
     *
     * @return true if set
     */
    public boolean isValueSet() {
        return false;
    }

    /**
     * Check if this is an auto-increment column.
     *
     * @return true if it is an auto-increment column
     */
    public boolean isAutoIncrement() {
        return false;
    }

    /**
     * Get the value in form of a boolean expression.
     * Returns true, false, or null.
     * In this database, everything can be a condition.
     *
     * @param session the session
     * @return the result
     */
    public Boolean getBooleanValue(Session session) {
        return getValue(session).getBoolean();
    }

    /**
     * Create index conditions if possible and attach them to the table filter.
     *
     * @param session the session
     * @param filter  the table filter
     */
    public void createIndexConditions(Session session, TableFilter filter) {
        // default is do nothing
    }

    /**
     * Get the column name or alias name of this expression.
     *
     * @return the column name
     */
    public String getColumnName() {
        return getAlias();
    }

    /**
     * Get the schema name, or null
     *
     * @return the schema name
     */
    public String getSchemaName() {
        return null;
    }

    /**
     * Get the table name, or null
     *
     * @return the table name
     */
    public String getTableName() {
        return null;
    }

    /**
     * Check whether this expression is a column and can store NULL.
     *
     * @return whether NULL is allowed
     */
    public int getNullable() {
        return Column.NULLABLE_UNKNOWN;
    }

    /**
     * Get the table alias name or null
     * if this expression does not represent a column.
     *
     * @return the table alias name
     */
    public String getTableAlias() {
        return null;
    }

    /**
     * Get the alias name of a column or SQL expression
     * if it is not an aliased expression.
     *
     * @return the alias name
     */
    public String getAlias() {
        return StringUtils.unEnclose(getSQL());
    }

    /**
     * Only returns true if the expression is a wildcard.
     *
     * @return if this expression is a wildcard
     */
    public boolean isWildcard() {
        return false;
    }

    /**
     * Returns the main expression, skipping aliases.
     *
     * @return the expression
     */
    public Expression getNonAliasExpression() {
        return this;
    }

    /**
     * Add conditions to a table filter if they can be evaluated.
     *
     * @param filter    the table filter
     * @param outerJoin if the expression is part of an outer join
     */
    public void addFilterConditions(TableFilter filter, boolean outerJoin) {
        if (!addedToFilter && !outerJoin &&
                isEverything(ExpressionVisitor.EVALUATABLE_VISITOR)) {
            filter.addFilterCondition(this, false);
            addedToFilter = true;
        }
    }

    /**
     * Convert this expression to a String.
     *
     * @return the string representation
     */
    @Override
    public String toString() {
        return getSQL();
    }

    /**
     * If this expression consists of column expressions it should return them.
     *
     * @param session the session
     * @return array of expression columns if applicable, null otherwise
     */
    public Expression[] getExpressionColumns(Session session) {
        return null;
    }

    /**
     * Exporting parameters If this expression contains parameters
     *
     * @param container parameters container
     * @return the SQL replaced parameter with '?'
     */
    public String exportParameters(TableFilter filter, List<Value> container) {
        return getSQL();
    }
}
