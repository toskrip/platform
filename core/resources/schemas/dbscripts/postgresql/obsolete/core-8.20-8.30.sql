/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

-- This empty stored procedure doesn't directly change the database, but calling it from a sql script signals the
-- script runner to invoke the specified method at this point in the script running process.  See usages of the
-- UpgradeCode interface for more details.
CREATE OR REPLACE FUNCTION core.executeJavaUpgradeCode(text) RETURNS void AS $$
    DECLARE note TEXT := 'Empty function that signals script runner to execute Java code.  See usages of UpgradeCode.java.';
    BEGIN
    END
$$ LANGUAGE plpgsql;

/* core-8.20-8.21.sql */

-- Add Active column to Principals table
ALTER TABLE core.Principals
    ADD COLUMN Active boolean NOT NULL DEFAULT true;

SELECT core.executeJavaUpgradeCode('migrateLookAndFeelSettings');

/* core-8.21-8.22.sql */

UPDATE core.Documents
    SET DocumentName = REPLACE(DocumentName, 'cpas-site-logo.', 'labkey-logo.')
    WHERE (DocumentName LIKE 'cpas-site-logo%');

UPDATE core.Documents
    SET DocumentName = REPLACE(DocumentName, 'cpas-site-favicon.ico', 'labkey-favicon.ico')
    WHERE (DocumentName = 'cpas-site-favicon.ico');