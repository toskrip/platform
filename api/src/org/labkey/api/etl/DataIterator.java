/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

package org.labkey.api.etl;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.BatchValidationException;

import java.io.Closeable;
import java.io.IOException;

/**
 * User: matthewb
 * Date: May 16, 2011
 * Time: 1:51:48 PM
 *
 *  Sticking with the jdbc style 1-based indexing
 *
 *  Column 0 is the row number, used for error reporting
 */

public interface DataIterator extends Closeable
{
    String getDebugName();

    /* count of colums, columns are indexed 1-_columnCount */
    int getColumnCount();

    /* description of column i */
    ColumnInfo getColumnInfo(int i);

    /*
     * Iterators should usually just add errors to a shared ValidationException,
     * however, they may throw to force processing to stop.
     */
    boolean next() throws BatchValidationException;

    /*
     * get the value for column i, the returned object may be one of
     *
     * a) null
     * b) real value (e.g. 5.0, or "name")
     * c) MvFieldWrapper
     *
     * MSInspectFeatursDataHandler uses error values as well, but that's what ValidationException is for
     */
    Object get(int i);

    @Override
    void close() throws IOException;
}
