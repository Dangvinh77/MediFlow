"""Readable Word extracts preserving message order, guards and service namespaces."""
import re
import json


def sequence_panels(source, lang):
    lines=source.splitlines();config=lines[0]
    declarations=[l for l in lines[1:] if re.match(r'\s*(actor|participant) ',l)]
    actor_ids=[re.match(r'\s*(?:actor|participant) (\w+)',l)[1] for l in declarations]
    content=[l for l in lines[1:] if l.strip() not in ['sequenceDiagram','autonumber'] and l not in declarations]
    root=[];current=root;stack=[];leaves=[]
    for line in content:
        stripped=line.strip()
        if re.match(r'(alt|opt|loop) ',stripped):
            block={'branches':[(stripped,[])]};current.append(block)
            stack.append((current,block));current=block['branches'][0][1]
        elif stripped.startswith('else '):
            assert stack and stack[-1][1]['branches'][0][0].startswith('alt ')
            block=stack[-1][1];branch=[];block['branches'].append((stripped,branch));current=branch
        elif stripped=='end':
            current,_=stack.pop()
        else:
            current.append((len(leaves),stripped));leaves.append(stripped)
    assert not stack
    results=[]
    # Chunk by messages/notes while retaining enclosing alternative conditions.
    chunks=[list(range(start,min(start+8,len(leaves)))) for start in range(0,len(leaves),8)]
    if len(chunks)>1 and len(chunks[-1])<=2:
        tail=chunks.pop();chunks[-1].extend(tail)
    for chunk in chunks:
        start=chunk[0];chosen=set(chunk)
        def active(node):
            return node[0] in chosen if isinstance(node,tuple) else any(active(n) for _,children in node['branches'] for n in children)
        selected_text='\n'.join(leaves[i] for i in chosen)
        used=[a for a in actor_ids if re.search(r'\b'+re.escape(a)+r'\b',selected_text)]
        if not used:used=actor_ids[:2]
        anchor=used[0]+(','+used[-1] if len(used)>1 else '')
        def emit(nodes):
            out=[]
            for node in nodes:
                if not active(node):continue
                if isinstance(node,tuple):out.append(node[1]);continue
                branches=[(h,c) for h,c in node['branches'] if any(active(n) for n in c)]
                # A single branch is a guarded excerpt, not a complete alternative.
                # Keep its exact condition without repeating empty opposite cases.
                for index,(heading,children) in enumerate(branches):
                    kind,condition=heading.split(' ',1)
                    if len(branches)==1:kind='loop' if kind=='loop' else 'opt'
                    else:kind='alt' if index==0 else 'else'
                    out.append(kind+' '+condition)
                    out.extend(emit(children))
                out.append('end')
            return out
        first=1+sum(bool(re.match(r'\w+\s*(?:-+>>?|-\))\s*\w+:',m)) for m in leaves[:start])
        settings=json.loads(config[len('%%{init: '):-len('}%%')])
        settings['sequence'].update(actorMargin=180 if len(used)<=2 else 60,width=190 if len(used)<=2 else 150)
        panel_config='%%{init: '+json.dumps(settings,ensure_ascii=False)+'}%%'
        result=[panel_config,'sequenceDiagram',f'autonumber {first}']+[d for d,a in zip(declarations,actor_ids) if a in used]
        result += [f'Note over {anchor}: '+('Continuation under the shown conditions. See full figure.' if lang=='en' else 'Tiếp nối theo điều kiện đã ghi. Xem hình đầy đủ.')]
        result += emit(root)
        results.append('\n'.join(result)+'\n')
    return results


def flow_panel(source,selected):
    lines=source.splitlines();out=[lines[0],lines[1]];groups=[];nodes={};group_items=[]
    edges=[]
    edge_re=re.compile(r'^\s*([\w &]+?)\s+(<-->|-->|---|-\.->)(?:\|(.+?)\|)?\s+([\w &]+)$')
    # Preserve the original nesting for every selected node.
    for line in lines[2:]:
        s=line.strip()
        if s.startswith('subgraph '):
            groups.append((s,re.match(r'subgraph (\w+)',s)[1]));continue
        if s=='end':groups.pop();continue
        match=re.match(r'(\w+)(?:\[|\()',s)
        if match:
            nodes[match[1]]=(list(groups),s);continue
        m=edge_re.match(s)
        if m:
            for a in m[1].split('&'):
                for b in m[4].split('&'):
                    edges.append((a.strip(),b.strip(),m[2],m[3]))
        elif s:raise ValueError('Unparsed flowchart line: '+s)
    valid=set(selected)
    for key in selected:
        assert key in nodes,key
        valid.update(gid for _,gid in nodes[key][0])
    opened=[]
    for key,(parents,line) in nodes.items():
        if key not in selected:continue
        common=0
        while common<min(len(opened),len(parents)) and opened[common]==parents[common]:common+=1
        out.extend(' end' for _ in opened[common:]);opened=opened[:common]
        for group in parents[common:]:out.append(' '+group[0]);opened.append(group)
        out.append(' '+line)
    out.extend(' end' for _ in opened)
    for a,b,arrow,label in edges:
        if 'WEB' in selected and 'GW' in selected and not ({a,b} & {'GW','WEB','MOBILE'}):continue
        if a in valid and b in valid:out.append(f' {a} {arrow}'+(f'|{label}|' if label else '')+f' {b}')
    return '\n'.join(out)+'\n'


def class_panel(source,selected):
    lines=source.splitlines();out=[lines[0],'classDiagram'];namespace=None;classes={};relations=[]
    i=2
    while i<len(lines):
        s=lines[i].strip();i+=1
        if s.startswith('direction '):out.append(' '+s)
        elif s.startswith('namespace '):namespace=s
        elif s=='}':namespace=None
        elif s.startswith('class '):
            key=re.match(r'class (\w+)',s)[1];body=[s]
            while lines[i].strip()!='}':body.append(lines[i].strip());i+=1
            body.append('}');i+=1;classes[key]=(namespace,body)
        elif s:relations.append(s)
    active=None
    for key,(ns,body) in classes.items():
        if key not in selected:continue
        if active!=ns:
            if active:out.append(' }')
            if ns:out.append(' '+ns)
            active=ns
        out.extend('  '+line for line in body)
    if active:out.append(' }')
    for line in relations:
        endpoints=re.findall(r'\b(?:'+'|'.join(classes)+r')\b',line.split(':')[0])
        if len(endpoints)==2 and set(endpoints)<=set(selected):out.append(' '+line)
    return '\n'.join(out)+'\n'


def write_panels(root,figures,source_factory):
    out=root/'word-compact/panels/mermaid'
    flow_groups={
        1:[['WEB','MOBILE','GW','CORE','CL','LAB','PH','BI','SU'],
           ['CORE','CL','LAB','PH','BI','SU','MQ','EU'],
           ['CORE','CL','LAB','DCORE','DCL','DLAB'],['PH','BI','SU','DPH','DBI','DSU']],
        3:[['WP','API','MP','GW','WEB','APP','DOMAIN'],
           ['APP','PORT','PERSIST','REST','OUTBOX','DB','PEER','MQ','DEP']]}
    class_groups={10:[['Appointment','MedicalRecord','Diagnosis'],['Appointment','MedicalRecord','ClinicalLabOrder','ExamClearance']],
        11:[['Drug','Prescription','PrescriptionLine','StockReservation','DispenseSlip'],
            ['BillingAccount','Charge','PaymentRequest','PaymentTransaction','PaymentAllocation','FinancialClearance'],
            ['Prescription','Charge','FinancialClearance']]}
    for number,item in figures.items():
        for lang in ['en','vi']:
            source=source_factory(number,lang,'portrait')
            if item['kind']=='sequence':panels=sequence_panels(source,lang)
            elif number in flow_groups:panels=[flow_panel(source,g) for g in flow_groups[number]]
            elif number in class_groups:panels=[class_panel(source,g) for g in class_groups[number]]
            else:continue
            for i,panel in enumerate(panels,1):
                (out/f'{number:02}-{lang}-part-{i:02}.mmd').write_text(panel,encoding='utf8')
