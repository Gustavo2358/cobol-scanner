#!/usr/bin/env python3
"""Verify frozen inputs, execute production JAR, measure candidates against reviewed gold."""
import argparse, csv, hashlib, json, subprocess, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];Q=ROOT/'qualification'
parser=argparse.ArgumentParser();parser.add_argument('--output-dir',default='.tmp/qualification-results');args=parser.parse_args()
out=ROOT/args.output_dir;assert out.resolve().is_relative_to(ROOT), 'Outputs must remain inside repository'
out.mkdir(parents=True,exist_ok=True)
for name,digest in json.loads((Q/'source-sha256.json').read_text()).items():
    assert hashlib.sha256((Q/name).read_bytes()).hexdigest()==digest,('changed source',name)
jar=ROOT/'target/cobol-dependency-scan.jar';corpus=Q/'corpus/carddemo'
assert jar.is_file(), 'Build the JAR with mvn clean verify first'
command=['/usr/bin/time','-v','-o',str(out/'final.time.txt'),'java','-Xmx512m','-jar',str(jar),'--source',str(corpus/'cbl'),'--copy-dir',str(corpus/'cpy'),'--copy-dir',str(corpus/'cpy-bms'),'--sql-include-dir',str(corpus/'cpy'),'--output',str(out/'final.json'),'--metrics',str(out/'final.tsv')]
start=time.perf_counter()
with (out/'final.stderr.txt').open('w') as err: run=subprocess.run(command,cwd=ROOT,stderr=err,stdout=subprocess.PIPE,text=True)
elapsed=time.perf_counter()-start;assert run.returncode in (0,1),run.returncode
actual={r['source']:r for r in json.loads((out/'final.json').read_text())['programs']}
gold=json.loads((Q/'gold.json').read_text());expected={r['source']:r for r in gold['sources']};assert actual.keys()==expected.keys()
fields=['programs','externalFileNames','tables','copybooks','sqlIncludes','dclgens'];categories={};misses=[];extras=[]
for field in fields:
    found=want=returned=0
    for name,e in expected.items():
        a=set(actual[name][field]);b=set(e[field]);found+=len(a&b);want+=len(b);returned+=len(a)
        misses += [{'source':name,'category':field,'value':v} for v in sorted(b-a)]
        extras += [{'source':name,'category':field,'value':v} for v in sorted(a-b)]
    categories[field]={'expected':want,'returned':returned,'matched':found,'recall':found/want if want else None,'precision':found/returned if returned else None,'candidateInflation':returned/want if want else None}
rows=list(csv.DictReader((out/'final.tsv').open(),delimiter='\t'))
report={'goldReview':gold['reviewStatus'],'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'sources':len(actual),'statuses':{s:sum(r['scanStatus']==s for r in actual.values()) for s in ['OK','PARTIAL','ERROR']},'programResolutionIncompleteRate':sum(r['programResolutionIncomplete'] for r in actual.values())/len(actual),'wallSeconds':round(elapsed,3),'loc':sum(int(r['loc']) for r in rows),'bytes':sum(int(r['bytes']) for r in rows),'peakRssKiB':int(next(l.split(':')[-1] for l in (out/'final.time.txt').read_text().splitlines() if 'Maximum resident' in l)),'categories':categories,'misses':misses,'extras':extras}
(out/'summary.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
assert not misses,misses
assert not extras,extras
assert report['statuses']['ERROR']==0,report
