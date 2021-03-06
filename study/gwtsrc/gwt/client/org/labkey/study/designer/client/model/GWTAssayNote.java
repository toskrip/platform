/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 1:22:09 PM
 */
public class GWTAssayNote implements IsSerializable
{
    private GWTSampleMeasure sampleMeasure;

    public GWTAssayNote()
    {

    }

    public GWTAssayNote(GWTAssayDefinition assay)
    {
        this.sampleMeasure = null;
    }

    public GWTAssayNote(GWTSampleMeasure sampleMeasure)
    {
        this.sampleMeasure = sampleMeasure;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;

        GWTAssayNote that = (GWTAssayNote) o;

        if (!sampleMeasure.equals(that.sampleMeasure)) return false;

        return true;
    }

    public int hashCode()
    {
        return sampleMeasure.hashCode();
    }

    public String toString()
    {
        return "[x] " + (!sampleMeasure.isEmpty() ? sampleMeasure.toString() : "");
    }

    public GWTSampleMeasure getSampleMeasure()
    {
        return sampleMeasure;
    }

    public void setSampleMeasure(GWTSampleMeasure sampleMeasure)
    {
        this.sampleMeasure = sampleMeasure;
    }
}
