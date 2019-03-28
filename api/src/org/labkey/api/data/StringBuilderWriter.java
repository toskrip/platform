/*
 * Copyright (c) 2010-2018 LabKey Corporation
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

package org.labkey.api.data;

import java.io.IOException;
import java.io.Writer;

public class StringBuilderWriter extends Writer
{
    private final StringBuilder _builder;
    private boolean _closed = false;

    public StringBuilderWriter(StringBuilder builder)
    {
        _builder = builder;
    }

    public void write(char cbuf[], int off, int len) throws IOException
    {
        if (_closed)
            throw new IOException("Cannot write to closed writer.");
        _builder.append(cbuf, off, len);
    }

    public void close()
    {
        _closed = true;
    }

    public void flush()
    {
        // no-op
    }
}