/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

ALTER TABLE exp.IndexVarchar ADD COLUMN CreatedBy USERID NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN Created TIMESTAMP NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN ModifiedBy USERID NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN Modified TIMESTAMP NULL;
ALTER TABLE exp.IndexVarchar ADD COLUMN LastIndexed TIMESTAMP NULL;

ALTER TABLE exp.IndexInteger ADD COLUMN CreatedBy USERID NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN Created TIMESTAMP NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN ModifiedBy USERID NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN Modified TIMESTAMP NULL;
ALTER TABLE exp.IndexInteger ADD COLUMN LastIndexed TIMESTAMP NULL;
