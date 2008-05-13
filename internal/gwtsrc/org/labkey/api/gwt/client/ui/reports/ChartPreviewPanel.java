/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.gwt.client.ui.reports;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.Window;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.WebPartPanel;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 6, 2007
 */
public class ChartPreviewPanel extends AbstractChartPanel
{
    private VerticalPanel _panel;
    private Image _image;

    public ChartPreviewPanel(GWTChart chart, ChartServiceAsync service)
    {
        super(chart, service);
    }

    protected boolean validate()
    {
        List errors = new ArrayList();

        getChart().validate(errors);

        if (!errors.isEmpty())
        {
            String s = "";
            for (int i=0 ; i<errors.size() ; i++)
                s += errors.get(i) + "\n";
            Window.alert(s);
            return false;
        }
        return true;
    }

    public Widget createWidget()
    {
        _panel = new VerticalPanel();

        ImageButton plotButton = new ImageButton("Refresh Chart");
        plotButton.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                if (validate())
                {
                    if (_image != null)
                    {
                        _panel.remove(_image);
                        _image = null;
                    }
                    asyncPlotChart();
                }
            }
        });
        _panel.add(plotButton);
        WebPartPanel wpp = new WebPartPanel("Chart Preview", _panel);
        wpp.setWidth("100%");

        return wpp;
    }

    private void asyncPlotChart()
    {
        getService().getDisplayURL(getChart(), new AsyncCallback()
        {
            public void onFailure(Throwable caught)
            {
            }

            public void onSuccess(Object result)
            {
                _image = new Image((String)result);
                _panel.add(_image);
            }
        });
    }
}
