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
exec core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container'
GO

-- dropping this index because it seems very nonselective and has been the cause(?) of some deadlocks
exec core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Status'
GO

CREATE INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container)
GO
