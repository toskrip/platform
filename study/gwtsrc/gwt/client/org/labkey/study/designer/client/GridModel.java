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

package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.Widget;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 2:39:57 PM
 */
public interface GridModel
{
    int getDataColumnCount();
    int getDataRowCount();
    Widget getCellWidget(int row, int col);
    int getHeaderRows();
    Widget getColumnHeader(int row, int column);
    void setOwner(EditableGrid grid);
    void makeGhostRowReal();
}
