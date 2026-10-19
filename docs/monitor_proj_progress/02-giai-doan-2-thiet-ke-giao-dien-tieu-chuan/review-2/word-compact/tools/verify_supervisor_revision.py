"""Check approved model invariants, source fields and complete Word extracts."""
from pathlib import Path
import collections
import json
import re

ROOT=Path(__file__).resolve().parents[2]
REPO=ROOT.parents[3]
OUT=ROOT/'word-compact'
model=json.loads((OUT/'supervisor-model.json').read_text(encoding='utf8'))
sequence_ids={4,5,6,7,8,9,37,38}
class_ids={10,11,39,40,41}
checks=[]

def structure(source):
    # Ignore localized labels, compare actors/participants/messages/guard structure.
    found=[]
    for line in source.splitlines()[1:]:
        s=line.strip()
        if re.match(r'(actor|participant) ',s):found.append(s.split(' as ')[0])
        elif re.match(r'(alt|else|opt|loop|ref|Note)\b',s):found.append(s.split(':')[0].split(' ',1)[0])
        elif re.match(r'\w+\s*(?:-+>>?|-\))\s*\w+:',s):found.append(s.split(':',1)[0])
        elif s=='end':found.append(s)
    return found

def messages(source):
    return [l.strip() for l in source.splitlines()[1:] if re.match(r'\s*\w+\s*(?:-+>>?|-\))\s*\w+:',l)]


def message_conditions(source):
    stack=[];found=[]
    for line in source.splitlines()[1:]:
        s=line.strip()
        if re.match(r'(alt|opt|loop) ',s):stack.append(s.split(' ',1)[1])
        elif s.startswith('else '):stack[-1]=s.split(' ',1)[1]
        elif s=='end':stack.pop()
        elif re.match(r'\w+\s*(?:-+>>?|-\))\s*\w+:',s):found.append((s,tuple(stack)))
    return found

modules={'Clinical':'clinical-service','Lab':'lab-service','Pharmacy':'pharmacy-service','Billing':'billing-service',
         'Core_Organization':'core-service','Core_Patient':'core-service','Support_Report':'report-service','Support_Notification':'notification-service'}
for key,item in model.items():
    n=int(key)
    sources={lang:(ROOT/'assets/mermaid'/f'{n:02}-{lang}.mmd').read_text(encoding='utf8') for lang in ['en','vi']}
    assert '[[' not in sources['en'] and '||' not in sources['vi']
    assert structure(sources['en'])==structure(sources['vi']),(n,'bilingual topology mismatch')
    for lang,source in sources.items():
        for orient in ['portrait','landscape']:
            candidate=(OUT/orient/'mermaid'/f'{n:02}-{lang}.mmd').read_text(encoding='utf8')
            assert structure(candidate)==structure(source),(n,orient,'changed sequence messages')
        if n in sequence_ids:
            assert 'participant S as Support' not in source and 'payment.completed V0' not in source
            stack=[]
            for line in source.splitlines()[1:]:
                s=line.strip()
                if re.match(r'(alt|opt|loop) ',s):stack.append(s.split()[0])
                elif s.startswith('else '):assert stack[-1]=='alt'
                elif s=='end':stack.pop()
            assert not stack
            panels=sorted((OUT/'panels/mermaid').glob(f'{n:02}-{lang}-part-*.mmd'))
            assert panels
            assert messages(source)==[m for p in panels for m in messages(p.read_text(encoding='utf8'))],(n,'Word panels omit/reorder messages')
            assert message_conditions(source)==[m for p in panels for m in message_conditions(p.read_text(encoding='utf8'))],(n,'Word panels change enclosing conditions')
        if n in class_ids and lang=='en':
            namespace=None;class_name=None
            for line in source.splitlines()[1:]:
                s=line.strip()
                if s.startswith('namespace '):namespace=s.split()[1]
                elif s.startswith('class '):
                    class_name=s.split()[1]
                    matches=list((REPO/'backend'/modules[namespace]/'src/main/java').rglob(class_name+'.java'))
                    assert len(matches)==1,(n,namespace,class_name)
                    code=matches[0].read_text(encoding='utf8')
                elif s=='}':class_name=None
                elif class_name and re.match(r'\w+ \w+$',s):
                    typ,name=s.split();assert re.search(r'\b'+typ+r'\s+'+name+r'\b',code),(n,class_name,'not a source field',s)
                elif class_name and '(' in s:
                    name=s.split('(',1)[0]
                    assert re.search(r'\b'+re.escape(name)+r'\s*\(',code),(n,class_name,'not a source method',s)
    checks.append({'figure':n,'kind':item['kind'],'bilingual':True})

arch=(ROOT/'assets/mermaid/01-en.mmd').read_text(encoding='utf8')
assert len(re.findall(r'subgraph SERVER\[',arch))==1
assert 'Next.js server' not in arch and 'same-origin /api rewrite' not in arch
assert all(re.search(r'\b'+node+r'\[',arch) for node in ['CORE','CL','LAB','PH','BI','SU'])
assert 'AWAITING_PAYMENT' in (ROOT/'assets/mermaid/07-en.mmd').read_text(encoding='utf8')
assert 'RefreshResponse(accessToken)' in (ROOT/'assets/mermaid/04-en.mmd').read_text(encoding='utf8')
assert 'RefreshResponse(accessToken, refreshToken)' not in (ROOT/'assets/mermaid/04-en.mmd').read_text(encoding='utf8')
for lang in ['en','vi']:
    for kind in ['standard','word']:
        report=next(ROOT.glob(f'*-review-2-{kind}-{lang}.md')).read_text(encoding='utf8')
        stems=re.findall(r'\]\(assets/svg/(\d+-'+lang+r')\.svg\)',report)
        assert len(stems)==len(set(stems))==41,(kind,lang,'missing/duplicate active figure')
        for stem in stems:
            assert (ROOT/'assets/mermaid'/f'{stem}.mmd').is_file()
summary={'figuresChecked':len(checks),'sequenceFigures':8,'serviceClassFigures':5,'failures':0,
         'checks':['bilingual topology','balanced sequence guards','complete ordered panel messages',
                   'source-backed domain fields','one backend boundary','V1 check-in lifecycle',
                   'separate refresh contract','41 unique active figures per language'],'results':checks}
(OUT/'supervisor-validation.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Supervisor model: all semantic/source/panel checks passed')
