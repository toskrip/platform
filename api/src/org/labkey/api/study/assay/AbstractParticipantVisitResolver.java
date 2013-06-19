/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.study.ParticipantVisit;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 2:54:26 PM
 */
public abstract class AbstractParticipantVisitResolver implements ParticipantVisitResolver
{
    private Container _runContainer;
    private Container _targetStudyContainer;
    private Map<ParticipantVisit, ParticipantVisit> _cache = new HashMap<>();

    /**
     * Create the resolver.
     * @param runContainer The Container containing the assay run data.
     * @param targetStudyContainer The TargetStudy Container discovered in the run or batch domain.
     */
    public AbstractParticipantVisitResolver(Container runContainer, @Nullable Container targetStudyContainer)
    {
        _runContainer = runContainer;
        _targetStudyContainer = targetStudyContainer;
    }

    protected Container getRunContainer()
    {
        return _runContainer;
    }

    /**
     * Looks up the specimen information, caching results to be used for future lookups as needed. Defers to subclasses
     * to do the actual resolution.
     * The target study is considered on a row by row basis.  The target study from the result domain has the highest precendence followed by the run or batch domain.
     */
    @NotNull
    public final ParticipantVisit resolve(String specimenID, String participantID, Double visitID, Date date, Container resultDomainTargetStudy)
    {
        specimenID = specimenID == null ? null : specimenID.trim();
        if (specimenID != null && specimenID.length() == 0)
        {
            specimenID = null;
        }
        participantID = participantID == null ? null : participantID.trim();
        if (participantID != null && participantID.length() == 0)
        {
            participantID = null;
        }
        Container studyContainer = resultDomainTargetStudy != null ? resultDomainTargetStudy : _targetStudyContainer;
        ParticipantVisit cacheKey = new ParticipantVisitImpl(specimenID, participantID, visitID, date, getRunContainer(), studyContainer);
        ParticipantVisit result = _cache.get(cacheKey);
        if (result != null)
        {
            return result;
        }
        result = resolveParticipantVisit(specimenID, participantID, visitID, date, studyContainer);

        _cache.put(cacheKey, result);
        return result;
    }

    @NotNull
    protected abstract ParticipantVisit resolveParticipantVisit(String specimenID, String participantID, Double visitID, Date date, Container targetStudyContainer);
}
