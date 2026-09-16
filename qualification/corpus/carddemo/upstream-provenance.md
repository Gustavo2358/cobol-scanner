# CardDemo corpus provenance

## External corpus provenance

- **repository:** `https://github.com/aws-samples/aws-mainframe-modernization-carddemo`
- **commit SHA:** `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`
- **commit date:** `2025-10-16T14:14:30-05:00`
- **evaluation date:** `2026-09-04`
- **license:** Apache License 2.0, from the upstream root `LICENSE`.
- **notice:** upstream root `NOTICE`, preserving `Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.`
- **upstream checkout:** `/tmp/work-cond-007/external-corpora/aws-mainframe-modernization-carddemo`
- **selected programs:** `COACTUPC`, `COCRDSLC`, `COPAUS1C`, `COTRTUPC`, `COTRTLIC`, `COACCT01`, `CBSTM03A`, `CBPAUP0C`, `CBACT01C`, `COUSR01C`.
- **selected copybooks:** 27 available closure copybooks, listed below by upstream basename.
- **missing dependencies:** `CMQGMOV`, `CMQMDV`, `CMQODV`, `CMQPMOV`, `CMQTML`, `CMQV`, `DFHAID`, `DFHBMSCA`; each is recorded as `COPY_NOT_FOUND` and was not stubbed.
- **nested COPYs:** no nested COPY statements were found in the selected closure after quote-aware scanning.

The two legal files in `corpus/carddemo/licenses/` are exact copies of the upstream `LICENSE` and `NOTICE`. Selected program files and available copybooks were copied without source edits and their SHA-256 values were checked against the pinned checkout. The repository follows the existing corpus convention (`corpus/cbl`, `corpus/cpy`, `corpus/cpy-bms`) with an explicit `carddemo` namespace; the complete upstream project was not vendored.

## Selected programs

| Program | Upstream path | Local path | SHA-256 |
| --- | --- | --- | --- |
| COACTUPC | `app/cbl/COACTUPC.cbl` | `corpus/carddemo/cbl/COACTUPC.cbl` | `b5bb7d6ccad022e0fc91b4dd1e971f49d184adf89b56abdce14eccff35b39396` |
| COCRDSLC | `app/cbl/COCRDSLC.cbl` | `corpus/carddemo/cbl/COCRDSLC.cbl` | `d5af307fb4b1a155f03df9eea14b402d866a332360e14a1e37dbefe59b73363b` |
| COPAUS1C | `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl` | `corpus/carddemo/cbl/COPAUS1C.cbl` | `27a969cbee69426fa1056053e676041430e99399912f0e27ee1f1a454093c21e` |
| COTRTUPC | `app/app-transaction-type-db2/cbl/COTRTUPC.cbl` | `corpus/carddemo/cbl/COTRTUPC.cbl` | `c16e40c391c0ad2d3e797a0e01144ef5b78d3b0db0a1771411a95a9d08b01f67` |
| COTRTLIC | `app/app-transaction-type-db2/cbl/COTRTLIC.cbl` | `corpus/carddemo/cbl/COTRTLIC.cbl` | `916a5fe2279ad626147990d8f86fac1e4b296f827889485b020cc310b5323601` |
| COACCT01 | `app/app-vsam-mq/cbl/COACCT01.cbl` | `corpus/carddemo/cbl/COACCT01.cbl` | `92776ed2801da1148839a9ed98b6537de4b41e0c7f050c8dcaae0d4e4ea543b5` |
| CBSTM03A | `app/cbl/CBSTM03A.CBL` | `corpus/carddemo/cbl/CBSTM03A.CBL` | `23c8753b6b4e0c24d4560c83861fe8162626bab195faec0fe88cf80b8bf432b` |
| CBPAUP0C | `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl` | `corpus/carddemo/cbl/CBPAUP0C.cbl` | `309468a5c4745f92c6dcf6ed28b396334b2f131ced78eb43eda962a63d2b174e` |
| CBACT01C | `app/cbl/CBACT01C.cbl` | `corpus/carddemo/cbl/CBACT01C.cbl` | `f8eb6e3a561ff96a1889a8f777a8998850a11dddae221da2c5018067d7b95551b` |
| COUSR01C | `app/cbl/COUSR01C.cbl` | `corpus/carddemo/cbl/COUSR01C.cbl` | `aa131b1e3382dc6d101b42f1c97d4fb0c2fdd706819ed9f9a0187c82b30f3019` |

## Selected copybooks and dependency closure

Available copybooks are kept in their upstream roots and preserve upstream names:

`CIPAUDTY`, `CIPAUSMY`, `COACTUP`, `COCOM01Y`, `COCRDSL`, `CODATECN`, `COPAU01`, `COSTM01`, `COTRTLI`, `COTRTUP`, `COTTL01Y`, `COUSR01`, `CSDAT01Y`, `CSLKPCDY`, `CSMSG01Y`, `CSMSG02Y`, `CSSETATY`, `CSSTRPFY`, `CSUSR01Y`, `CSUTLDPY`, `CSUTLDWY`, `CUSTREC`, `CVACT01Y`, `CVACT02Y`, `CVACT03Y`, `CVCRD01Y`, `CVCUS01Y`.

| Program | Direct COPY names | Transitive COPY names | COPY_NOT_FOUND |
| --- | --- | --- | --- |
| COACTUPC | `CVCRD01Y`, `CSLKPCDY`, `DFHBMSCA`, `DFHAID`, `COTTL01Y`, `COACTUP`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CSUSR01Y`, `CVACT01Y`, `CVACT03Y`, `CVCUS01Y`, `COCOM01Y`, `CSSETATY`, `CSUTLDPY`, `CSUTLDWY` | `CSSTRPFY` | `DFHBMSCA`, `DFHAID` |
| COCRDSLC | `CVCRD01Y`, `COCOM01Y`, `DFHBMSCA`, `DFHAID`, `COTTL01Y`, `COCRDSL`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CSUSR01Y`, `CVACT02Y`, `CVCUS01Y` | `CSSTRPFY` | `DFHBMSCA`, `DFHAID` |
| COPAUS1C | `COCOM01Y`, `COPAU01`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSMSG02Y`, `CIPAUSMY`, `CIPAUDTY`, `DFHAID`, `DFHBMSCA` | — | `DFHAID`, `DFHBMSCA` |
| COTRTUPC | `CVCRD01Y`, `DFHBMSCA`, `DFHAID`, `COTTL01Y`, `COTRTUP`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `COCOM01Y`, `CSSETATY`, `CSUTLDWY` | `CSSTRPFY` | `DFHBMSCA`, `DFHAID` |
| COTRTLIC | `CVCRD01Y`, `COCOM01Y`, `DFHBMSCA`, `DFHAID`, `COTTL01Y`, `COTRTLI`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `CVACT02Y` | `CSSTRPFY` | `DFHBMSCA`, `DFHAID` |
| COACCT01 | `CMQGMOV`, `CMQPMOV`, `CMQMDV`, `CMQODV`, `CMQV`, `CMQTML`, `CVACT01Y` | — | `CMQGMOV`, `CMQPMOV`, `CMQMDV`, `CMQODV`, `CMQV`, `CMQTML` |
| CBSTM03A | `COSTM01`, `CVACT03Y`, `CUSTREC`, `CVACT01Y` | — | — |
| CBPAUP0C | `CIPAUSMY`, `CIPAUDTY` | — | — |
| CBACT01C | `CVACT01Y`, `CODATECN` | — | — |
| COUSR01C | `COCOM01Y`, `COUSR01`, `COTTL01Y`, `CSDAT01Y`, `CSMSG01Y`, `CSUSR01Y`, `DFHAID`, `DFHBMSCA` | — | `DFHAID`, `DFHBMSCA` |

`SQLCA`, DCLGEN structures and other `EXEC SQL INCLUDE` dependencies in `COTRTLIC` are not COPY statements and are therefore not part of this COPY closure. The two `EXEC DLI` programs are intentionally retained as partial-input evidence.

## Transformations

No selected COBOL source was simplified, renamed, normalized, or edited. The only transformation is placement under `corpus/carddemo/{cbl,cpy,cpy-bms}` and the addition of exact legal files. Harnesses pass both copybook directories explicitly. `COTRTLIC` retains its upstream TAB and consequently exercises the configured fixed-format normalization failure.
