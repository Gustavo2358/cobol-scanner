       WORKING-STORAGE SECTION.
       01 WS-PGM-A PIC X(8).
       01 WS-PGM-B PIC X(8)
          VALUE 'SUB00002'.
       PROCEDURE DIVISION.
           DISPLAY 'FIM'.
           MOVE 'SUB00001' TO WS-PGM-A.
           IF X = Y
               MOVE WS-PGM-B TO WS-PGM-A
           END-IF.
           CALL WS-PGM-A.
           EXEC CICS LINK
               PROGRAM(WS-PGM-A)
           END-EXEC.
           GOBACK.
