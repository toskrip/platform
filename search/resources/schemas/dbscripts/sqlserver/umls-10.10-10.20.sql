/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

CREATE SCHEMA umls;
GO

CREATE TABLE umls.MRCOC
(
    CUI1 CHAR(8) NOT NULL,
    AUI1 NVARCHAR(9) NOT NULL,
    CUI2 CHAR(8),
    AUI2 NVARCHAR(9),
    SAB NVARCHAR(20) NOT NULL,
    COT NVARCHAR(3) NOT NULL,
    COF INTEGER,
    COA NVARCHAR(300),
    CVF INTEGER
);

CREATE TABLE umls.MRCOLS
(
    COL NVARCHAR(20),
    DES NVARCHAR(200),
    REF NVARCHAR(20),
    MIN INTEGER,
    AV numeric(5,2),
    MAX INTEGER,
    FIL NVARCHAR(50),
    DTY NVARCHAR(20)
);

CREATE TABLE umls.MRCONSO
(
    CUI CHAR(8) NOT NULL,
    LAT CHAR(3) NOT NULL,
    TS CHAR(1) NOT NULL,
    LUI NVARCHAR(10) NOT NULL,
    STT NVARCHAR(3) NOT NULL,
    SUI NVARCHAR(10) NOT NULL,
    ISPREF CHAR(1) NOT NULL,
    AUI NVARCHAR(9) NOT NULL,
    SAUI NVARCHAR(50),
    SCUI NVARCHAR(50),
    SDUI NVARCHAR(50),
    SAB NVARCHAR(20) NOT NULL,
    TTY NVARCHAR(20) NOT NULL,
    CODE NVARCHAR(50) NOT NULL,
    STR NVARCHAR(3000) NOT NULL,
    SRL INTEGER NOT NULL,
    SUPPRESS CHAR(1) NOT NULL,
    CVF INTEGER
);

CREATE TABLE umls.MRCUI
(
    CUI1 CHAR(8) NOT NULL,
    VER NVARCHAR(10) NOT NULL,
    REL NVARCHAR(4) NOT NULL,
    RELA NVARCHAR(100),
    MAPREASON NVARCHAR(4000),
    CUI2 CHAR(8),
    MAPIN CHAR(1)
);

CREATE TABLE umls.MRCXT
(
    CUI CHAR(8),
    SUI NVARCHAR(10),
    AUI NVARCHAR(9),
    SAB NVARCHAR(20),
    CODE NVARCHAR(50),
    CXN INTEGER,
    CXL CHAR(3),
    RANK INTEGER,
    CXS NVARCHAR(3000),
    CUI2 CHAR(8),
    AUI2 NVARCHAR(9),
    HCD NVARCHAR(50),
    RELA NVARCHAR(100),
    XC NVARCHAR(1),
    CVF INTEGER
);

CREATE TABLE umls.MRDEF
(
    CUI CHAR(8) NOT NULL,
    AUI NVARCHAR(9) NOT NULL,
    ATUI NVARCHAR(11) NOT NULL,
    SATUI NVARCHAR(50),
    SAB NVARCHAR(20) NOT NULL,
    DEF NTEXT NOT NULL,
    SUPPRESS CHAR(1) NOT NULL,
    CVF INTEGER
);

CREATE TABLE umls.MRDOC
(
    DOCKEY NVARCHAR(50) NOT NULL,
    VALUE NVARCHAR(200),
    TYPE NVARCHAR(50) NOT NULL,
    EXPL NVARCHAR(1000)
);


CREATE TABLE umls.MRFILES (
    FIL NVARCHAR(50),
    DES NVARCHAR(200),
    FMT NVARCHAR(300),
    CLS INTEGER,
    RWS INTEGER,
    BTS INTEGER
);

CREATE TABLE umls.MRHIER
(
    CUI CHAR(8) NOT NULL,
    AUI NVARCHAR(9) NOT NULL,
    CXN INTEGER NOT NULL,
    PAUI NVARCHAR(10),
    SAB NVARCHAR(20) NOT NULL,
    RELA NVARCHAR(100),
    PTR NVARCHAR(1000),
    HCD NVARCHAR(50),
    CVF INTEGER
);

CREATE TABLE umls.MRHIST
(
    CUI CHAR(8),
    SOURCEUI NVARCHAR(50),
    SAB NVARCHAR(20),
    SVER NVARCHAR(20),
    CHANGETYPE NVARCHAR(1000),
    CHANGEKEY NVARCHAR(1000),
    CHANGEVAL NVARCHAR(1000),
    REASON NVARCHAR(1000),
    CVF INTEGER
);

CREATE TABLE umls.MRMAP
(
    MAPSETCUI CHAR(8) NOT NULL,
    MAPSETSAB NVARCHAR(20) NOT NULL,
    MAPSUBSETID NVARCHAR(10),
    MAPRANK INTEGER,
    MAPID NVARCHAR(50) NOT NULL,
    MAPSID NVARCHAR(50),
    FROMID NVARCHAR(50) NOT NULL,
    FROMSID NVARCHAR(50),
    FROMEXPR NVARCHAR(4000) NOT NULL,
    FROMTYPE NVARCHAR(50) NOT NULL,
    FROMRULE NVARCHAR(4000),
    FROMRES NVARCHAR(4000),
    REL NVARCHAR(4) NOT NULL,
    RELA NVARCHAR(100),
    TOID NVARCHAR(50),
    TOSID NVARCHAR(50),
    TOEXPR NVARCHAR(4000),
    TOTYPE NVARCHAR(50),
    TORULE NVARCHAR(4000),
    TORES NVARCHAR(4000),
    MAPRULE NVARCHAR(4000),
    MAPRES NVARCHAR(4000),
    MAPTYPE NVARCHAR(50),
    MAPATN NVARCHAR(20),
    MAPATV NVARCHAR(4000),
    CVF INTEGER
);

CREATE TABLE umls.MRRANK
(
    RANK INTEGER NOT NULL,
    SAB NVARCHAR(20) NOT NULL,
    TTY NVARCHAR(20) NOT NULL,
    SUPPRESS CHAR(1) NOT NULL
);

CREATE TABLE umls.MRREL
(
    CUI1 CHAR(8) NOT NULL,
    AUI1 NVARCHAR(9),
    STYPE1 NVARCHAR(50) NOT NULL,
    REL NVARCHAR(4) NOT NULL,
    CUI2 CHAR(8) NOT NULL,
    AUI2 NVARCHAR(9),
    STYPE2 NVARCHAR(50) NOT NULL,
    RELA NVARCHAR(100),
    RUI NVARCHAR(10) NOT NULL,
    SRUI NVARCHAR(50),
    SAB NVARCHAR(20) NOT NULL,
    SL NVARCHAR(20) NOT NULL,
    RG NVARCHAR(10),
    DIR NVARCHAR(1),
    SUPPRESS CHAR(1) NOT NULL,
    CVF INTEGER
);

CREATE TABLE umls.MRSAB
(
    VCUI CHAR(8),
    RCUI CHAR(8) NOT NULL,
    VSAB NVARCHAR(20) NOT NULL,
    RSAB NVARCHAR(20) NOT NULL,
    SON NVARCHAR(3000) NOT NULL,
    SF NVARCHAR(20) NOT NULL,
    SVER NVARCHAR(20),
    VSTART CHAR(8),
    VEND CHAR(8),
    IMETA NVARCHAR(10) NOT NULL,
    RMETA NVARCHAR(10),
    SLC NVARCHAR(1000),
    SCC NVARCHAR(1000),
    SRL INTEGER NOT NULL,
    TFR INTEGER,
    CFR INTEGER,
    CXTY NVARCHAR(50),
    TTYL NVARCHAR(300),
    ATNL NVARCHAR(1000),
    LAT CHAR(3),
    CENC NVARCHAR(20) NOT NULL,
    CURVER CHAR(1) NOT NULL,
    SABIN CHAR(1) NOT NULL,
    SSN NVARCHAR(3000) NOT NULL,
    SCIT NVARCHAR(4000) NOT NULL
);

CREATE TABLE umls.MRSAT
(
    CUI CHAR(8) NOT NULL,
    LUI NVARCHAR(10),
    SUI NVARCHAR(10),
    METAUI NVARCHAR(50),
    STYPE NVARCHAR(50) NOT NULL,
    CODE NVARCHAR(50),
    ATUI NVARCHAR(11) NOT NULL,
    SATUI NVARCHAR(50),
    ATN NVARCHAR(50) NOT NULL,
    SAB NVARCHAR(20) NOT NULL,
    ATV NVARCHAR(4000),
    SUPPRESS CHAR(1) NOT NULL,
    CVF INTEGER
);

CREATE TABLE umls.MRSMAP
(
    MAPSETCUI CHAR(8) NOT NULL,
    MAPSETSAB NVARCHAR(20) NOT NULL,
    MAPID NVARCHAR(50) NOT NULL,
    MAPSID NVARCHAR(50),
    FROMEXPR NVARCHAR(4000) NOT NULL,
    FROMTYPE NVARCHAR(50) NOT NULL,
    REL NVARCHAR(4) NOT NULL,
    RELA NVARCHAR(100),
    TOEXPR NVARCHAR(4000),
    TOTYPE NVARCHAR(50),
    CVF INTEGER
);

CREATE TABLE umls.MRSTY
(
    CUI CHAR(8) NOT NULL,
    TUI CHAR(4) NOT NULL,
    STN NVARCHAR(100) NOT NULL,
    STY NVARCHAR(50) NOT NULL,
    ATUI NVARCHAR(11) NOT NULL,
    CVF INTEGER
);

CREATE TABLE umls.MRXNS_ENG
(
    LAT CHAR(3) NOT NULL,
    NSTR NVARCHAR(3000) NOT NULL,
    CUI CHAR(8) NOT NULL,
    LUI NVARCHAR(10) NOT NULL,
    SUI NVARCHAR(10) NOT NULL
);

CREATE TABLE umls.MRXNW_ENG
(
    LAT CHAR(3) NOT NULL,
    NWD NVARCHAR(100) NOT NULL,
    CUI CHAR(8) NOT NULL,
    LUI NVARCHAR(10) NOT NULL,
    SUI NVARCHAR(10) NOT NULL
);

CREATE TABLE umls.MRAUI
(
    AUI1 NVARCHAR(9) NOT NULL,
    CUI1 CHAR(8) NOT NULL,
    VER NVARCHAR(10) NOT NULL,
    REL NVARCHAR(4),
    RELA NVARCHAR(100),
    MAPREASON NVARCHAR(4000) NOT NULL,
    AUI2 NVARCHAR(9) NOT NULL,
    CUI2 CHAR(8) NOT NULL,
    MAPIN CHAR(1) NOT NULL
);

CREATE TABLE umls.MRXW
(
    LAT CHAR(3) NOT NULL,
    WD NVARCHAR(200) NOT NULL,
    CUI CHAR(8) NOT NULL,
    LUI NVARCHAR(10) NOT NULL,
    SUI NVARCHAR(10) NOT NULL
);

CREATE TABLE umls.AMBIGSUI
(
    SUI NVARCHAR(10) NOT NULL,
    CUI CHAR(8) NOT NULL
);

CREATE TABLE umls.AMBIGLUI
(
    LUI NVARCHAR(10) NOT NULL,
    CUI CHAR(8) NOT NULL
);

CREATE TABLE umls.DELETEDCUI
(
    PCUI CHAR(8) NOT NULL,
    PSTR NVARCHAR(3000) NOT NULL
);

CREATE TABLE umls.DELETEDLUI
(
    PLUI NVARCHAR(10) NOT NULL,
    PSTR NVARCHAR(3000) NOT NULL
);

CREATE TABLE umls.DELETEDSUI
(
    PSUI NVARCHAR(10) NOT NULL,
    LAT CHAR(3) NOT NULL,
    PSTR NVARCHAR(3000) NOT NULL
);

CREATE TABLE umls.MERGEDCUI
(
    PCUI CHAR(8) NOT NULL,
    CUI CHAR(8) NOT NULL
);

CREATE TABLE umls.MERGEDLUI
(
    PLUI NVARCHAR(10),
    LUI NVARCHAR(10)
);

GO

CREATE PROCEDURE umls.createIndexes AS
BEGIN
    CREATE INDEX X_MRCOC_CUI1 ON umls.MRCOC(CUI1);
    CREATE INDEX X_MRCOC_AUI1 ON umls.MRCOC(AUI1);
    CREATE INDEX X_MRCOC_CUI2 ON umls.MRCOC(CUI2);
    CREATE INDEX X_MRCOC_AUI2 ON umls.MRCOC(AUI2);
    CREATE INDEX X_MRCOC_SAB ON umls.MRCOC(SAB);
    CREATE INDEX X_MRCONSO_CUI ON umls.MRCONSO(CUI);
    ALTER TABLE umls.MRCONSO ADD CONSTRAINT X_MRCONSO_PK PRIMARY KEY (AUI);
    CREATE INDEX X_MRCONSO_SUI ON umls.MRCONSO(SUI);
    CREATE INDEX X_MRCONSO_LUI ON umls.MRCONSO(LUI);
    CREATE INDEX X_MRCONSO_CODE ON umls.MRCONSO(CODE);
    CREATE INDEX X_MRCONSO_SAB_TTY ON umls.MRCONSO(SAB,TTY);
    CREATE INDEX X_MRCONSO_SCUI ON umls.MRCONSO(SCUI);
    CREATE INDEX X_MRCONSO_SDUI ON umls.MRCONSO(SDUI);
  --  CREATE INDEX X_MRCONSO_STR ON umls.MRCONSO(STR);
    CREATE INDEX X_MRCXT_CUI ON umls.MRCXT(CUI);
    CREATE INDEX X_MRCXT_AUI ON umls.MRCXT(AUI);
    CREATE INDEX X_MRCXT_SAB ON umls.MRCXT(SAB);
    CREATE INDEX X_MRDEF_CUI ON umls.MRDEF(CUI);
    CREATE INDEX X_MRDEF_AUI ON umls.MRDEF(AUI);
    ALTER TABLE umls.MRDEF ADD CONSTRAINT X_MRDEF_PK PRIMARY KEY (ATUI);
    CREATE INDEX X_MRDEF_SAB ON umls.MRDEF(SAB);
    CREATE INDEX X_MRHIER_CUI ON umls.MRHIER(CUI);
    CREATE INDEX X_MRHIER_AUI ON umls.MRHIER(AUI);
    CREATE INDEX X_MRHIER_SAB ON umls.MRHIER(SAB);
  --  CREATE INDEX X_MRHIER_PTR ON umls.MRHIER(PTR);
    CREATE INDEX X_MRHIER_PAUI ON umls.MRHIER(PAUI);
    CREATE INDEX X_MRHIST_CUI ON umls.MRHIST(CUI);
    CREATE INDEX X_MRHIST_SOURCEUI ON umls.MRHIST(SOURCEUI);
    CREATE INDEX X_MRHIST_SAB ON umls.MRHIST(SAB);
    ALTER TABLE umls.MRRANK ADD CONSTRAINT X_MRRANK_PK PRIMARY KEY (SAB,TTY);
    CREATE INDEX X_MRREL_CUI1 ON umls.MRREL(CUI1);
    CREATE INDEX X_MRREL_AUI1 ON umls.MRREL(AUI1);
    CREATE INDEX X_MRREL_CUI2 ON umls.MRREL(CUI2);
    CREATE INDEX X_MRREL_AUI2 ON umls.MRREL(AUI2);
    ALTER TABLE umls.MRREL ADD CONSTRAINT X_MRREL_PK PRIMARY KEY (RUI);
    CREATE INDEX X_MRREL_SAB ON umls.MRREL(SAB);
    ALTER TABLE umls.MRSAB ADD CONSTRAINT X_MRSAB_PK PRIMARY KEY (VSAB);
    CREATE INDEX X_MRSAB_RSAB ON umls.MRSAB(RSAB);
    CREATE INDEX X_MRSAT_CUI ON umls.MRSAT(CUI);
    CREATE INDEX X_MRSAT_METAUI ON umls.MRSAT(METAUI);
    ALTER TABLE umls.MRSAT ADD CONSTRAINT X_MRSAT_PK PRIMARY KEY (ATUI);
    CREATE INDEX X_MRSAT_SAB ON umls.MRSAT(SAB);
    CREATE INDEX X_MRSAT_ATN ON umls.MRSAT(ATN);
    CREATE INDEX X_MRSTY_CUI ON umls.MRSTY(CUI);
    ALTER TABLE umls.MRSTY ADD CONSTRAINT X_MRSTY_PK PRIMARY KEY (ATUI);
    CREATE INDEX X_MRSTY_STY ON umls.MRSTY(STY);
  --  CREATE INDEX X_MRXNS_ENG_NSTR ON umls.MRXNS_ENG(NSTR);
    CREATE INDEX X_MRXNW_ENG_NWD ON umls.MRXNW_ENG(NWD);
    CREATE INDEX X_MRXW_WD ON umls.MRXW(WD);
    CREATE INDEX X_AMBIGSUI_SUI ON umls.AMBIGSUI(SUI);
    CREATE INDEX X_AMBIGLUI_LUI ON umls.AMBIGLUI(LUI);
    CREATE INDEX X_MRAUI_CUI2 ON umls.MRAUI(CUI2);
    CREATE INDEX X_MRCUI_CUI2 ON umls.MRCUI(CUI2);
    CREATE INDEX X_MRMAP_MAPSETCUI ON umls.MRMAP(MAPSETCUI);
END;

GO

-- DROP PROCEDURE umls.dropIndexes
CREATE PROCEDURE umls.dropIndexes AS
BEGIN
    DROP INDEX umls.MRCOC.X_MRCOC_CUI1
    DROP INDEX umls.MRCOC.X_MRCOC_AUI1
    DROP INDEX umls.MRCOC.X_MRCOC_CUI2
    DROP INDEX umls.MRCOC.X_MRCOC_AUI2
    DROP INDEX umls.MRCOC.X_MRCOC_SAB
    DROP INDEX  umls.MRCONSO.X_MRCONSO_CUI
    ALTER TABLE umls.MRCONSO DROP CONSTRAINT X_MRCONSO_PK
    DROP INDEX umls.MRCONSO.X_MRCONSO_SUI
    DROP INDEX umls.MRCONSO.X_MRCONSO_LUI
    DROP INDEX umls.MRCONSO.X_MRCONSO_CODE
    DROP INDEX umls.MRCONSO.X_MRCONSO_SAB_TTY
    DROP INDEX umls.MRCONSO.X_MRCONSO_SCUI
    DROP INDEX umls.MRCONSO.X_MRCONSO_SDUI
  --  DROP INDEX X_MRCONSO_STR ON umls.MRCONSO
    DROP INDEX umls.MRCXT.X_MRCXT_CUI
    DROP INDEX umls.MRCXT.X_MRCXT_AUI
    DROP INDEX umls.MRCXT.X_MRCXT_SAB
    DROP INDEX umls.MRDEF.X_MRDEF_CUI
    DROP INDEX umls.MRDEF.X_MRDEF_AUI
    ALTER TABLE umls.MRDEF DROP CONSTRAINT X_MRDEF_PK
    DROP INDEX umls.MRDEF.X_MRDEF_SAB
    DROP INDEX umls.MRHIER.X_MRHIER_CUI
    DROP INDEX umls.MRHIER.X_MRHIER_AUI
    DROP INDEX umls.MRHIER.X_MRHIER_SAB
  --  DROP INDEX X_MRHIER_PTR ON umls.MRHIER
    DROP INDEX umls.MRHIER.X_MRHIER_PAUI
    DROP INDEX umls.MRHIST.X_MRHIST_CUI
    DROP INDEX umls.MRHIST.X_MRHIST_SOURCEUI
    DROP INDEX umls.MRHIST.X_MRHIST_SAB
    ALTER TABLE umls.MRRANK DROP CONSTRAINT X_MRRANK_PK
    DROP INDEX umls.MRREL.X_MRREL_CUI1
    DROP INDEX umls.MRREL.X_MRREL_AUI1
    DROP INDEX umls.MRREL.X_MRREL_CUI2
    DROP INDEX umls.MRREL.X_MRREL_AUI2
    ALTER TABLE umls.MRREL DROP CONSTRAINT X_MRREL_PK
    DROP INDEX umls.MRREL.X_MRREL_SAB
    ALTER TABLE umls.MRSAB DROP CONSTRAINT X_MRSAB_PK
    DROP INDEX umls.MRSAB.X_MRSAB_RSAB
    DROP INDEX umls.MRSAT.X_MRSAT_CUI
    DROP INDEX umls.MRSAT.X_MRSAT_METAUI
    ALTER TABLE umls.MRSAT DROP CONSTRAINT X_MRSAT_PK
    DROP INDEX umls.MRSAT.X_MRSAT_SAB
    DROP INDEX umls.MRSAT.X_MRSAT_ATN
    DROP INDEX umls.MRSTY.X_MRSTY_CUI
    ALTER TABLE umls.MRSTY DROP CONSTRAINT X_MRSTY_PK
    DROP INDEX umls.MRSTY.X_MRSTY_STY
  --  DROP INDEX X_MRXNS_ENG_NSTR ON umls.MRXNS_ENG
    DROP INDEX umls.MRXNW_ENG.X_MRXNW_ENG_NWD
    DROP INDEX umls.MRXW.X_MRXW_WD
    DROP INDEX umls.AMBIGSUI.X_AMBIGSUI_SUI
    DROP INDEX umls.AMBIGLUI.X_AMBIGLUI_LUI
    DROP INDEX umls.MRAUI.X_MRAUI_CUI2
    DROP INDEX umls.MRCUI.X_MRCUI_CUI2
    DROP INDEX umls.MRMAP.X_MRMAP_MAPSETCUI
END;