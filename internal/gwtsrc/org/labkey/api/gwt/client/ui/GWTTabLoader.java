/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.Window;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.*;

/**
 * User: jgarms
 * Date: Oct 27, 2008
 */
public class GWTTabLoader
{
    private final String tsv;

    public GWTTabLoader(String tsv)
    {
        this.tsv = tsv;
    }

    public boolean processTsv(PropertiesEditor propertiesEditor)
    {
        List<Map<String, String>> data = getData();

        if (data.size() == 0)
        {
            Window.alert("Unable to parse the tab-delimited text");
            return false;
        }

        // Insert the new properties
        List<GWTPropertyDescriptor> properties = new ArrayList<GWTPropertyDescriptor>();
        for (Map<String, String> row : data)
        {
            GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
            prop.setName(row.get("property"));
            prop.setLabel(row.get("label"));
            prop.setDescription(row.get("description"));
            prop.setRequired(isRequired(row));
            prop.setRangeURI(getRangeURI(row));
            prop.setFormat(row.get("format"));
            properties.add(prop);
        }
        propertiesEditor.setPropertyDescriptors(properties);

        return true;
    }

    private boolean isRequired(Map<String, String> map)
    {
        String reqString = map.get("notnull");
        return reqString != null && reqString.equalsIgnoreCase("TRUE");
    }

    private String getRangeURI(Map map)
    {
        String rangeString = (String) map.get("rangeuri");
        if (rangeString != null)
        {
            if (rangeString.equalsIgnoreCase("xsd:int"))
                return TypePicker.xsdInt;
            if (rangeString.equalsIgnoreCase("xsd:double"))
                return TypePicker.xsdDouble;
            if (rangeString.equalsIgnoreCase("xsd:boolean"))
                return TypePicker.xsdBoolean;
            if (rangeString.equalsIgnoreCase("xsd:dateTime"))
                return TypePicker.xsdDateTime;
        }

        // Default to string
        return TypePicker.xsdString;
    }

    private List<Map<String,String>> getData()
    {
        List rows = getRows();

        if (rows.size() < 2)
        {
            return Collections.emptyList();
        }

        // First row is keys -- lowercase for easier access
        String[] keys = ((String)rows.get(0)).split("\t");
        for (int i=0;i<keys.length;i++)
        {
            keys[i] = keys[i].trim().toLowerCase();
        }

        List<Map<String,String>> data = new ArrayList<Map<String,String>>(rows.size() - 1);

        // Iterate starting at 1, since our keys were the first row
        for (int i=1;i<rows.size(); i++)
        {
            String row = (String)rows.get(i);
            String[] rowData = row.split("\t");
            Map<String,String> map = new HashMap<String,String>();
            for (int j=0;j<keys.length;j++)
            {
                String fieldData = "";
                if (j < rowData.length)
                    fieldData = rowData[j];
                map.put(keys[j], fieldData);
            }

            data.add(map);
        }
        return data;

    }

    private List<String> getRows()
    {
        List<String> rows = new ArrayList<String>();
        StringBuffer sb = new StringBuffer();

        for (int index = 0; index < tsv.length(); index++)
        {
            char c = tsv.charAt(index);
            if (c == '\n' || c == '\r')
            {
                if (sb.length() == 0)
                {
                    // Handle windows \r\n
                    continue;
                }
                rows.add(sb.toString());
                sb.setLength(0);
            }
            else
            {
                sb.append(c);
            }
        }
        // Handle last row which may not be properly terminated
        if (sb.length() > 0)
            rows.add(sb.toString());
        return rows;
    }
}
