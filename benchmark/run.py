#!/usr/bin/env python3
"""Reproducible synthetic workload. All generated inputs/artifacts stay in this repo."""
import argparse, csv, hashlib, json, platform, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'benchmark/work'
parser=argparse.ArgumentParser();parser.add_argument('--output-dir',default='benchmark/work/results');args=parser.parse_args()
RESULT=ROOT/args.output_dir
assert RESULT.resolve().is_relative_to(ROOT), 'Outputs must remain inside repository'
WORK.mkdir(parents=True,exist_ok=True)
RESULT.mkdir(parents=True,exist_ok=True)
jar=ROOT/'target/cobol-dependency-scan.jar'
large=WORK/'large.cbl'
with large.open('w') as f:
    f.write('IDENTIFICATION DIVISION.\nPROGRAM-ID. LARGE.\nPROCEDURE DIVISION.\n')
    for i in range(50000): f.write(f"MOVE 'P{i:07}' TO A{i}.\nMOVE A{i} TO B{i}.\n")
    for i in range(49993): f.write("DISPLAY 'IRRELEVANT CALL NOISE'.\n")
    f.write("CALL B49999.\nEXEC CICS LINK PROGRAM(B49999) END-EXEC.\nEXEC SQL SELECT * FROM S.T END-EXEC.\nGOBACK.\n")
batch=WORK/'batch';batch.mkdir(exist_ok=True)
copy=WORK/'copy';copy.mkdir(exist_ok=True)
(copy/'PGM.cpy').write_text("MOVE 'REPLACED' TO TARGET.\n")
for i in range(1000):
    (batch/f'P{i:04}.cbl').write_text("COPY PGM REPLACING 'REPLACED' BY 'ONE'.\nCOPY PGM REPLACING 'REPLACED' BY 'TWO'.\n"+"DISPLAY 'NOISE'.\n"*195+"CALL TARGET.\nEXEC CICS XCTL PROGRAM(TARGET) END-EXEC.\nEXEC SQL SELECT * FROM S.T END-EXEC.\n")
report={'java':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True).strip(),'platform':platform.platform(),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'runs':[]}
for name,source,threads,expected in [('150k-loc',large,1,1),('1000-programs',batch,4,1000)]:
    output=RESULT/f'{name}.json';metrics=RESULT/f'{name}.tsv';rss=RESULT/f'{name}.time.txt';stderr=RESULT/f'{name}.stderr.txt'
    cmd=['/usr/bin/time','-v','-o',str(rss),'java','-Xmx512m','-jar',str(jar),'--source',str(source),'--copy-dir',str(copy),'--source-format','free','--threads',str(threads),'--output',str(output),'--metrics',str(metrics)]
    start=time.perf_counter()
    with stderr.open('w') as err: result=subprocess.run(cmd,cwd=ROOT,stderr=err,stdout=subprocess.PIPE,text=True)
    elapsed=time.perf_counter()-start
    assert result.returncode==0,(name,result.returncode,stderr.read_text())
    programs=json.loads(output.read_text())['programs'];assert len(programs)==expected
    for p in programs:
        assert p['scanStatus']=='OK',p
        assert p['programs']==(['P0049999'] if name=='150k-loc' else ['ONE','TWO']),p
        assert p['tables']==['S.T'],p
    rows=list(csv.DictReader(metrics.open(),delimiter='\t'))
    loc=sum(int(r['loc']) for r in rows);size=sum(int(r['bytes']) for r in rows)
    rss_kib=int(next(l.split(':')[-1] for l in rss.read_text().splitlines() if 'Maximum resident' in l))
    report['runs'].append({'name':name,'files':len(rows),'loc':loc,'bytes':size,'threads':threads,'heapMiB':512,'wallSeconds':round(elapsed,3),'locPerSecond':round(loc/elapsed),'peakRssKiB':rss_kib,'phaseWorkerMilliseconds':{key:round(sum(float(r[key]) for r in rows),3) for key in ['preparation_ms','scan_ms','resolution_ms','total_ms']},'outputSha256':hashlib.sha256(output.read_bytes()).hexdigest()})
(RESULT/'summary.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
