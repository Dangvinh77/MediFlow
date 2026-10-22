"""Refresh only current Review3 screenshot content, preserving other sections."""
import json
from datetime import datetime
from pathlib import Path
from shutil import copy2
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor
from docx.text.paragraph import Paragraph
from lxml import etree
from PIL import Image

FOLDER = Path(__file__).resolve().parent
TARGET = FOLDER / 'Review3.docx'
ORDER = ['Shared web screens', 'Administrator — web', 'Manager — web', 'Doctor — web', 'Nurse — web', 'Laboratory technician — web', 'Pharmacist — web', 'Cashier — web', 'Patient — web', 'Mobile GUI — shared', 'Mobile GUI — administrator', 'Mobile GUI — manager', 'Mobile GUI — doctor', 'Mobile GUI — nurse', 'Mobile GUI — laboratory technician', 'Mobile GUI — pharmacist', 'Mobile GUI — cashier', 'Mobile GUI — patient']

def xml(e):
    return etree.tostring(e)

def main():
    captures = json.loads((FOLDER/'screenshots/captures-new.json').read_text(encoding='utf-8'))
    assert {c['group'] for c in captures} == set(ORDER)
    assert len({c['file'] for c in captures}) == len(captures)
    for c in captures:
        path = (FOLDER/c['file']).resolve()
        assert path.is_relative_to(FOLDER/'screenshots')
        with Image.open(path) as im:
            im.verify()
    captures.sort(key=lambda c: (ORDER.index(c['group']), Path(c['file']).name))
    doc = Document(TARGET)
    starts = [p for p in doc.paragraphs if p.text.strip() == 'Screen shots']
    ends = [p for p in doc.paragraphs if p.text.strip() == 'Testing document']
    assert len(starts) == len(ends) == 1
    start, end = starts[0]._p, ends[0]._p
    body = doc._element.body
    children = list(body)
    first, last = children.index(start), children.index(end)
    assert first < last
    region = children[first+1:last]
    boundaries = [e for e in region if e.find('.//'+qn('w:sectPr')) is not None]
    assert len(boundaries) == 1 and boundaries[0] is region[-1]
    anchor = Paragraph(boundaries[0], doc._body)
    before = [xml(e) for e in children[:first+1]]
    after = [xml(e) for e in children[last:]]
    boundary = xml(boundaries[0])
    tables = [xml(t._tbl) for t in doc.tables]
    backup = FOLDER.parents[2]/'.git/review3-refresh'/('Review3-before-insert-'+datetime.now().strftime('%Y%m%d-%H%M%S-%f')+'.docx')
    backup.parent.mkdir(parents=True, exist_ok=True)
    copy2(TARGET, backup)
    for e in region[:-1]:
        body.remove(e)
    def add(text='', style=None, page_break=False):
        p = anchor.insert_paragraph_before(text, style)
        p.paragraph_format.space_after = Pt(6)
        p.paragraph_format.page_break_before = page_break
        for r in p.runs:
            r.font.color.rgb = RGBColor(0,0,0)
        return p
    add('Refreshed on 22 October 2026 from the source candidate based on commit 4eac7ba2. Screenshots use the running web and Flutter clients through the real Gateway, PostgreSQL, RabbitMQ and six packaged business applications. Patient and staff records are synthetic demonstration data.')
    add('Mobile evidence uses Flutter web at a 430 by 932 phone viewport, captured at 1290 by 2796 pixels. Web captures are 1904 or 1920 by 1080 pixels. This does not establish native Android or iOS execution. Lab clearance rejections, restricted name lookups and unavailable outpatient report coverage are described as observed. Inpatient care and surgery remain deferred.')
    previous = None
    for gi, group in enumerate(ORDER,1):
        items = [c for c in captures if c['group'] == group]
        platform = items[0]['platform']
        if platform != previous:
            add('Web GUI' if platform == 'web' else 'Mobile GUI','Heading 2',previous is not None)
            previous = platform
        title = group.replace(' — ', ' ') if platform == 'web' else group.replace('Mobile GUI — ', '').title()
        p = add(f'{gi}. {title}','Heading 3',gi not in (1,10))
        p.paragraph_format.keep_with_next = True
        for ii,c in enumerate(items,1):
            p = add(f'{gi}.{ii} {c["caption"]}',page_break=ii>1)
            p.paragraph_format.keep_with_next = True
            for r in p.runs:
                r.bold = True
                r.font.size = Pt(11)
            p = add()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            with Image.open(FOLDER/c['file']) as im:
                w,h = im.size
            width = min(2.8 if platform == 'mobile' else 6.5,7.8*w/h)
            pic = p.add_run().add_picture(str(FOLDER/c['file']),width=Inches(width))
            pic._inline.docPr.set('descr',c['caption'])
            p.paragraph_format.keep_with_next = bool(c.get('note'))
            if c.get('note'):
                p = add(c['note'])
                for r in p.runs:
                    r.font.size = Pt(10)
    current = list(body)
    assert [xml(e) for e in current[:first+1]] == before
    assert [xml(e) for e in current[current.index(end):]] == after
    assert xml(boundaries[0]) == boundary
    assert [xml(t._tbl) for t in doc.tables] == tables
    temp = TARGET.with_name('Review3.refreshing.docx')
    doc.save(temp)
    saved = Document(temp)
    current = list(saved._element.body)
    saved_end = next(p._p for p in saved.paragraphs if p.text.strip() == 'Testing document')
    assert [xml(e) for e in current[:first+1]] == before
    assert [xml(e) for e in current[current.index(saved_end):]] == after
    assert [xml(t._tbl) for t in saved.tables] == tables
    temp.replace(TARGET)
    print(f'Replaced screenshots: {len(captures)} images in 18 groups. Preserved all {len(tables)} tables and outside-section XML.')

if __name__ == '__main__':
    main()
