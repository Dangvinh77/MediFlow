"""Author Review 2 role use cases; render/publish with the existing Word toolchain.

Run to regenerate role sources and report sections after editing role_tasks.json.
Labels are paired (English, Vietnamese); graph IDs and edge direction are shared.
"""
from pathlib import Path
import copy
import json
import re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'word-compact'

# node id: (English label, Vietnamese label). Edges: source, target, kind, condition.
# Main tasks are directly available to the actor; includes model required behavior,
# while extension arrows point FROM the optional task TO its base task.
DATA = json.loads(Path(__file__).with_name('role_tasks.json').read_text(encoding='utf8'))


def make_source(item, lang, orientation):
    li = 0 if lang == 'en' else 1
    seed = (OUT / orientation / 'mermaid' / f'36-{lang}.mmd').read_text(encoding='utf8')
    config = json.loads(seed.splitlines()[0][len('%%{init: '):-len('}%%')])
    config = copy.deepcopy(config)
    config['themeVariables']['fontSize'] = '18px'
    config['flowchart'].update(curve='linear', nodeSpacing=22 if orientation == 'portrait' else 30,
                               rankSpacing=44, padding=24, wrappingWidth=165)
    config['themeCSS'] = ".nodeLabel p {margin:0;} .edgeLabel {font-size:15px;} .nodeLabel img {display:inline-block!important;width:36px!important;height:48px!important;}"
    actor = re.search(r"data:image/svg\+xml;base64,[^'\"]+", seed).group()
    direction = 'LR' if orientation == 'portrait' else 'TB'
    lines = ['%%{init: ' + json.dumps(config, ensure_ascii=False) + '}%%',
             '%% Role use case: one actor; solid primary task; dashed include/extend.',
             'flowchart ' + direction,
             f' ACTOR["<img src=\'{actor}\' width=\'36\' height=\'48\'/><br/>{item["role"]}"]',
             f' subgraph SYS["MediFlow — {item["role"]}"]']
    for key, label in item['nodes'].items():
        # Mermaid stadium is the portable rounded use-case shape; render.mjs
        # replaces its boundary with an ellipse for these specific figures.
        lines.append(f' {key}(["{label[li]}"])')
    lines.append(' end')
    for key in item['main']:
        lines.append(f' ACTOR --> {key}')
    extension_index = 0
    for src, dst, kind, condition in item['edges']:
        if condition:
            extension_index += 1
        label = f'«{kind}»' + (f' [E{extension_index}]' if condition else '')
        lines.append(f' {src} -.->|"{label}"| {dst}')
    lines += [' classDef actor fill:transparent,stroke:transparent,color:#0f172a;',
              ' classDef main fill:#e0f2fe,stroke:#0284c7,color:#0f172a;',
              ' classDef sub fill:#f0fdfa,stroke:#0f766e,color:#0f172a;',
              ' class ACTOR actor;', ' class ' + ','.join(item['main']) + ' main;']
    supporting=[k for k in item['nodes'] if k not in item['main']]
    if supporting:
        lines.append(' class ' + ','.join(supporting) + ' sub;')
    return '\n'.join(lines) + '\n'


def report_section(lang):
    li = 0 if lang == 'en' else 1
    en = lang == 'en'
    lines = ['<!-- role-usecases:start -->',
             '### Detailed use cases — one diagram per role' if en else '### Use case chi tiết — một sơ đồ cho mỗi vai trò', '',
             ('Figures 29–36 follow the supervisor’s presentation: one human actor per diagram, solid arrows to primary tasks, dashed `«include»` arrows for required supporting behavior, and dashed `«extend»` arrows from a conditional extension to its base task. An extension may have its own included subflow. Arrow direction is relational, not chronological. Sign-in and token renewal are independent goals. A valid session is a precondition of protected business tasks. Blue ovals are primary tasks; teal ovals are supporting/extension tasks. These are source capabilities, not proof of UI completeness or production enablement.' if en else
              'Hình 29–36 theo mẫu của giảng viên: mỗi hình có một actor người dùng, mũi tên liền đến tác vụ chính, mũi tên đứt `«include»` cho hành vi con bắt buộc và mũi tên đứt `«extend»` từ nhánh mở rộng có điều kiện về tác vụ gốc. Nhánh mở rộng có thể có luồng con `include` riêng. Mũi tên thể hiện quan hệ, không phải thứ tự thực hiện. Đăng nhập và gia hạn là hai use case độc lập của mỗi role; phiên hợp lệ là tiền điều kiện của các tác vụ nghiệp vụ. Oval xanh dương là tác vụ chính; xanh teal là tác vụ con/mở rộng. Hình mô tả khả năng trong source, không khẳng định mọi UI đã hoàn thiện hoặc mọi feature đã bật.'), '',
             ('**Scope correction:** PATIENT currently has authentication/refresh and own-notification access. No appointment or laboratory self-service is claimed. SYSTEM is a machine identity and has no human-role diagram. All role permissions below are intersected with the Gateway route matrix and downstream guards.' if en else
              '**Hiệu chỉnh phạm vi:** PATIENT hiện có đăng nhập/refresh và truy cập thông báo của mình. Không gán quyền tự thao tác lịch hẹn/xét nghiệm cho PATIENT. SYSTEM là định danh máy, không tạo sơ đồ role người dùng. Quyền từng role lấy giao của ma trận Gateway và guards downstream.'), '']
    for item in DATA:
        stem = f'{item["number"]:02}-{lang}'
        title = item['role'] + ' — ' + item['title'][li]
        lines += ['#### ' + title, '', f'![{title}](assets/svg/{stem}.svg)', '',
                  '<details>', f'<summary>Mermaid · {stem}</summary>', '', '```mermaid',
                  make_source(item, lang, 'landscape').rstrip(), '```', '', '</details>', '',
                  item['note'][li], '',
                  ('Extension conditions: ' if en else 'Điều kiện mở rộng: ') + ('None.' if en else 'Không có.') if not any(e[3] for e in item['edges']) else ('Extension conditions: ' if en else 'Điều kiện mở rộng: ') + '; '.join(
                      f'E{i}: {item["nodes"][src][li]} — {condition[li]}'
                      for i,(src,dst,kind,condition) in enumerate([e for e in item['edges'] if e[3]],1)), '',
                  ('Source evidence: ' if en else 'Đối chiếu source: ') + ' · '.join(
                      f'[{Path(p).stem}](../../../../{p})' for p in item['sources']), '']
    lines += ['<!-- role-usecases:end -->', '']
    return '\n'.join(lines)


def main():
    for item in DATA:
        for lang in ['en', 'vi']:
            stem = f'{item["number"]:02}-{lang}'
            for orientation in ['portrait', 'landscape']:
                (OUT/orientation/'mermaid'/f'{stem}.mmd').write_text(make_source(item,lang,orientation),encoding='utf8')
            (ROOT/'assets/mermaid'/f'{stem}.mmd').write_text(make_source(item,lang,'landscape'),encoding='utf8')
            for old in (OUT/'panels/mermaid').glob(stem+'-part-*.mmd'):
                for folder,ext in [('mermaid','mmd'),('svg','svg'),('png','png')]:
                    (OUT/'panels'/folder/(old.stem+'.'+ext)).unlink(missing_ok=True)
            # Optional Word detail panels retain the same actor and all edges.
            # Each panel is a readable extract; the full diagram remains above.
            for part, start in enumerate(range(0,len(item['main']),2),1):
                panel=copy.deepcopy(item)
                panel['main']=item['main'][start:start+2]
                visible=set(panel['main'])
                while True:
                    expanded=visible | {dst for src,dst,kind,_ in item['edges'] if kind=='include' and src in visible} | {src for src,dst,kind,_ in item['edges'] if kind=='extend' and dst in visible}
                    if expanded==visible:break
                    visible=expanded
                panel['nodes']={k:v for k,v in item['nodes'].items() if k in visible}
                panel['edges']=[e for e in item['edges'] if e[0] in visible and e[1] in visible]
                source=make_source(panel,lang,'portrait')
                # Preserve full-diagram extension IDs when an earlier extension
                # falls in another panel, so captions use the same condition key.
                full_conditions=[e for e in item['edges'] if e[3]]
                panel_conditions=[e for e in panel['edges'] if e[3]]
                source=re.sub(r'\[E(\d+)\]',lambda m:f'[E{full_conditions.index(panel_conditions[int(m[1])-1])+1}]',source)
                (OUT/'panels/mermaid'/f'{stem}-part-{part:02}.mmd').write_text(source,encoding='utf8')
    for lang, name in [('en','01-review-2-standard-en.md'),('vi','02-review-2-standard-vi.md')]:
        p = ROOT/name
        text = p.read_text(encoding='utf8')
        section = report_section(lang)
        if '<!-- role-usecases:start -->' in text:
            text = re.sub(r'<!-- role-usecases:start -->[\s\S]*?<!-- role-usecases:end -->\n?',section,text)
        else:
            marker = '### 1.3. Sequences Diagrams'
            assert marker in text
            text = text.replace(marker, section+'\n'+marker, 1)
        p.write_text(text,encoding='utf8')
    # Machine-readable evidence supports checking bilingual parity and role coverage.
    (OUT/'role-usecases.json').write_text(json.dumps(DATA,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print('Authored 8 role diagrams × 2 languages × 2 orientations and report sections')


if __name__ == '__main__':
    main()
