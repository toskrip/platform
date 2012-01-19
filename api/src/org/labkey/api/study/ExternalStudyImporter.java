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

package org.labkey.api.study;

import org.labkey.api.admin.ImportContext;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.jetbrains.annotations.Nullable;
import org.labkey.study.xml.StudyDocument;

import java.io.File;
import java.util.Collection;

/*
* User: adam
* Date: Aug 27, 2009
* Time: 9:28:28 AM
*/
public interface ExternalStudyImporter
{
    // Brief description of the types of objects this class imports
    String getDescription();
    void process(ImportContext<StudyDocument.Study> ctx, File root) throws Exception;
    @Nullable
    Collection<PipelineJobWarning> postProcess(ImportContext<StudyDocument.Study> ctx, File root) throws Exception;
}
