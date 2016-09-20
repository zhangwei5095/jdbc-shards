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
package com.wplatform.ddal.result;

import java.sql.SQLException;

/**
 * This interface is for classes that create rows on demand.
 * It is used together with SimpleResultSet to create a dynamic result set.
 */
public interface SimpleRowSource {

    /**
     * Get the next row. Must return null if no more rows are available.
     *
     * @return the row or null
     */
    Object[] readRow() throws SQLException;

    /**
     * Close the row source.
     */
    void close();

    /**
     * Reset the position (before the first row).
     *
     * @throws SQLException if this operation is not supported
     */
    void reset() throws SQLException;
}
