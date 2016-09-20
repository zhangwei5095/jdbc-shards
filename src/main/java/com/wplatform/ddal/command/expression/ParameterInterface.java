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

import com.wplatform.ddal.message.DbException;
import com.wplatform.ddal.value.Value;

/**
 * The interface for client side (remote) and server side parameters.
 */
public interface ParameterInterface {

    /**
     * Set the value of the parameter.
     *
     * @param value    the new value
     * @param closeOld if the old value (if one is set) should be closed
     */
    void setValue(Value value, boolean closeOld);

    /**
     * Get the value of the parameter if set.
     *
     * @return the value or null
     */
    Value getParamValue();

    /**
     * Check if the value is set.
     *
     * @throws DbException if not set.
     */
    void checkSet() throws DbException;

    /**
     * Is the value of a parameter set.
     *
     * @return true if set
     */
    boolean isValueSet();

    /**
     * Get the expected data type of the parameter if no value is set, or the
     * data type of the value if one is set.
     *
     * @return the data type
     */
    int getType();

    /**
     * Get the expected precision of this parameter.
     *
     * @return the expected precision
     */
    long getPrecision();

    /**
     * Get the expected scale of this parameter.
     *
     * @return the expected scale
     */
    int getScale();

    /**
     * Check if this column is nullable.
     *
     * @return Column.NULLABLE_*
     */
    int getNullable();

}
