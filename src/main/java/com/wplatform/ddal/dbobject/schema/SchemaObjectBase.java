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
package com.wplatform.ddal.dbobject.schema;

import com.wplatform.ddal.dbobject.DbObjectBase;

/**
 * The base class for classes implementing SchemaObject.
 */
public abstract class SchemaObjectBase extends DbObjectBase implements SchemaObject {

    private Schema schema;

    /**
     * Initialize some attributes of this object.
     *
     * @param newSchema   the schema
     * @param id          the object id
     * @param name        the name
     * @param traceModule the trace module name
     */
    protected void initSchemaObjectBase(Schema newSchema, int id, String name, String traceModule) {
        initDbObjectBase(newSchema.getDatabase(), id, name, traceModule);
        this.schema = newSchema;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    @Override
    public String getSQL() {
        return schema.getSQL() + "." + super.getSQL();
    }

    @Override
    public boolean isHidden() {
        return false;
    }

}
