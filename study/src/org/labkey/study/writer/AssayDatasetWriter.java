/*
 * Copyright (c) 2009 LabKey Corporation
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

package org.labkey.study.writer;

import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.study.model.StudyImpl;

/*
* User: adam
* Date: Jun 30, 2009
* Time: 10:56:48 PM
*/
// DatasetWriter actually writes all datasets (crf & assay).  This is a do-nothing writer to gets the assay dataset checkbox
// to show up in the UI.  TODO: More flexible data-driven UI mechanism.
public class AssayDatasetWriter implements Writer<StudyImpl, StudyExportContextImpl>
{
    static final String SELECTION_TEXT = "Assay Datasets";

    public String getSelectionText()
    {
        return SELECTION_TEXT;
    }

    public void write(StudyImpl object, StudyExportContextImpl ctx, VirtualFile vf) throws Exception
    {
    }
}
