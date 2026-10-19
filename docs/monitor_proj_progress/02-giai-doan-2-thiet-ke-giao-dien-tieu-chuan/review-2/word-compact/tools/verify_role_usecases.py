"""Check role coverage, bilingual parity, and exported UML relationships."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'word-compact'
REPO=ROOT.parents[3]
data=json.loads((OUT/'role-usecases.json').read_text(encoding='utf8'))
assert {d['role'] for d in data}=={'ADMIN','MANAGER','DOCTOR','NURSE','LAB_TECH','PHARMACIST','CASHIER','PATIENT'}
checked=[]
for item in data:
    assert {'AUTH_LOGIN','AUTH_REFRESH'} <= set(item['main'])
    assert not any({a,b}=={'AUTH_LOGIN','AUTH_REFRESH'} for a,b,_,_ in item['edges'])
    for src in item['sources']:
        assert (REPO/src).is_file(),src
    for lang in ['en','vi']:
        stem=f'{item["number"]:02}-{lang}'
        for base in [ROOT/'assets',OUT/'portrait',OUT/'landscape']:
            source=(base/'mermaid'/f'{stem}.mmd').read_text(encoding='utf8')
            assert source.count(' ACTOR[')==1
            assert set(re.findall(r'^ (\w+)\(\[',source,re.M))==set(item['nodes'])
            assert re.findall(r'^ ACTOR --> (\w+)$',source,re.M)==item['main']
            edges=re.findall(r'^ (\w+) -\.->\|"«(include|extend)»[^"\n]*"\| (\w+)$',source,re.M)
            assert {(s,d,k) for s,k,d in edges}=={(s,d,k) for s,d,k,_ in item['edges']}
            svg=ET.parse(base/'svg'/f'{stem}.svg').getroot()
            nodes=[n for n in svg.iter() if 'node' in n.attrib.get('class','').split()]
            assert len([n for n in nodes if 'actor' in n.attrib.get('class','').split()])==1
            assert len([n for n in svg.iter() if n.tag.endswith('ellipse')])==len(item['nodes'])
            paths={n.attrib['data-id']:n for n in svg.iter() if n.tag.endswith('path') and 'flowchart-link' in n.attrib.get('class','')}
            def edge(a,b):
                matches=[v for k,v in paths.items() if k.startswith(f'L_{a}_{b}_')]
                assert len(matches)==1,(stem,a,b)
                return matches[0]
            for main in item['main']:
                e=edge('ACTOR',main)
                assert e.attrib.get('marker-end') and not e.attrib.get('marker-start')
                assert 'edge-pattern-dotted' not in e.attrib['class']
            for src,dst,kind,_ in item['edges']:
                e=edge(dst,src) if kind=='extend' else edge(src,dst)
                assert 'edge-pattern-dotted' in e.attrib['class']
                if kind=='extend':
                    assert e.attrib.get('marker-start') and not e.attrib.get('marker-end')
                else:
                    assert e.attrib.get('marker-end') and not e.attrib.get('marker-start')
            checked.append({'id':stem,'variant':base.name,'nodes':len(nodes),'edges':len(paths)})
        # Panel sources collectively preserve the full task/relationship set.
        panel_nodes=set();panel_edges=set();panel_main=set()
        for p in sorted((OUT/'panels/mermaid').glob(stem+'-part-*.mmd')):
            s=p.read_text(encoding='utf8')
            assert s.count(' ACTOR[')==1
            panel_nodes.update(re.findall(r'^ (\w+)\(\[',s,re.M))
            panel_main.update(re.findall(r'^ ACTOR --> (\w+)$',s,re.M))
            panel_edges.update((a,b,k) for a,k,b in re.findall(r'^ (\w+) -\.->\|"«(include|extend)»[^"\n]*"\| (\w+)$',s,re.M))
        assert panel_nodes==set(item['nodes']) and panel_main==set(item['main'])
        assert panel_edges=={(s,d,k) for s,d,k,_ in item['edges']}

for lang in ['en','vi']:
    for kind in ['standard','word']:
        report=next(ROOT.glob(f'*-review-2-{kind}-{lang}.md')).read_text(encoding='utf8')
        assert report.count('<!-- role-usecases:start -->')==1
        for item in data:
            assert f'{item["number"]:02}-{lang}' in report

summary={'roles':len(data),'fullDiagramVariants':len(checked),'failures':0,
         'checks':['eight human roles','one actor per diagram','source evidence paths',
                   'bilingual node/edge parity','true exported ellipses','solid primary arrows',
                   'dashed include arrows','extension-to-base arrowheads','panel union equals full graph',
                   'reports include all roles','independent sign-in and renewal per role'],
         'results':checked}
(OUT/'role-validation.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(f'Role use cases: {len(data)} roles; {len(checked)} full diagram variants; all checks passed')
