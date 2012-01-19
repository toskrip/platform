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

import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;
import org.labkey.api.writer.VirtualFile;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Sep 30, 2009
 */
public class ParticipantCommentWriter implements InternalStudyWriter
{
    public String getSelectionText()
    {
        return "Participant Comment Settings";
    }

    public void write(StudyImpl study, StudyExportContext ctx, VirtualFile vf) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getXml();
        StudyDocument.Study.Comments comment = studyXml.addNewComments();

        if (study.getParticipantCommentDataSetId() != null && study.getParticipantCommentDataSetId() != -1)
        {
            comment.setParticipantCommentDatasetId(study.getParticipantCommentDataSetId());
            comment.setParticipantCommentDatasetProperty(study.getParticipantCommentProperty());
        }

        if (study.getParticipantVisitCommentDataSetId() != null && study.getParticipantVisitCommentDataSetId() != -1)
        {
            comment.setParticipantVisitCommentDatasetId(study.getParticipantVisitCommentDataSetId());
            comment.setParticipantVisitCommentDatasetProperty(study.getParticipantVisitCommentProperty());
        }
    }
}
