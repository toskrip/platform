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
CREATE TABLE study.Cohort
    (
    RowId SERIAL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
    );

ALTER TABLE study.Dataset
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId);

ALTER TABLE study.Participant
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Participant_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Participant_CohortId ON study.Participant(CohortId);
CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);

ALTER TABLE study.Visit
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);

ALTER TABLE study.Study
    ADD COLUMN ParticipantCohortDataSetId INT NULL,
    ADD COLUMN ParticipantCohortProperty VARCHAR(200) NULL;
