from pathlib import Path
import hashlib, json, re, xml.etree.ElementTree as ET
from PIL import Image, ImageChops, ImageDraw
out=Path(__file__).resolve().parents[1]
ledger=json.loads((out/'render-validation.json').read_text(encoding='utf8'))
results=ledger['results']
expected={(o,p.name) for o in ['portrait','landscape','panels'] for p in (out/o/'mermaid').glob('*.mmd')}
assert len(results)==len(expected) and {(r['orientation'],r['file']) for r in results}==expected
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
issues=[];verified=[]
for r in results:
    stem=r['file'][:-4];base=out/r['orientation']
    try:
        assert r['status']=='pass' and r['pngMatchesSavedSvg']
        for folder,ext,key in [('mermaid','mmd','sourceSha256'),('svg','svg','svgSha256'),('png','png','pngSha256')]:
            p=base/folder/(stem+'.'+ext);actual=hashlib.sha256(p.read_bytes().replace(b'\r\n',b'\n')).hexdigest() if ext=='mmd' else sha(p);assert actual==r[key], str(p)
        svg=ET.parse(base/'svg'/(stem+'.svg')).getroot()
        assert list(map(float,svg.attrib['viewBox'].split()))==r['viewBox']
        assert not re.search(r'(?:src|href)=["\']https?://',(base/'svg'/(stem+'.svg')).read_text(encoding='utf8'))
        im=Image.open(base/'png'/(stem+'.png')).convert('RGB')
        assert im.size==(r['pngWidth'],r['pngHeight'])
        # Empty white borders on all four sides detect accidental screenshot clipping.
        mask=ImageChops.difference(im,Image.new('RGB',im.size,'white')).convert('L').point(lambda x:255 if x>8 else 0)
        box=mask.getbbox();assert box
        margins=[box[0],box[1],im.width-box[2],im.height-box[3]]
        assert min(margins)>=3, (stem,'clipped border',margins)
        verified.append({'id':stem,'orientation':r['orientation'],'whiteBorderPx':margins,'svgPngReplay':r['pixelComparison']})
    except Exception as e:issues.append({'id':stem,'orientation':r['orientation'],'error':str(e)})
# Invisible layout constraints may connect existing nodes only; they must not add invented use cases.
for f in (out/'landscape/mermaid').glob('*.mmd'):
    portrait=(out/'portrait/mermaid'/f.name).read_text(encoding='utf8').split('\n',1)[1]
    landscape=f.read_text(encoding='utf8').split('\n',1)[1]
    if not portrait.startswith('flowchart'):continue
    declared=set(re.findall(r'\b([A-Za-z_][A-Za-z_0-9]*)\s*[\[({]',re.sub(r'<img[^>]+>','',portrait)))
    for line in landscape.splitlines():
        if '~~~' not in line:continue
        for name in re.split(r'\s*~~~\s*',line.strip()):
            assert name in declared,(f.name,'invented layout node',name)
manifest=json.loads((out/'layout-manifest.json').read_text(encoding='utf8'))
for d in manifest['diagrams']:
    stem=d['id'];a=d['variants']['portrait'];b=d['variants']['landscape']
    assert a['candidate']=='portrait' and b['candidate']=='landscape'
    if not d.get('singleEntity'):
        assert (a['width'],a['height'])!=(b['width'],b['height']),stem
    assert (out/'portrait/mermaid'/(stem+'.mmd')).read_bytes()!=(out/'landscape/mermaid'/(stem+'.mmd')).read_bytes()
# Review sheets include every PNG in a stable order. They are an index for visual QA, not Word insert assets.
qa=out/'qa';qa.mkdir(exist_ok=True)
for group in ['portrait','landscape','panels']:
    files=sorted((out/group/'png').glob('*.png'))
    for start in range(0,len(files),12):
        sheet=Image.new('RGB',(1800,1800),'#e2e8f0');draw=ImageDraw.Draw(sheet)
        for i,p in enumerate(files[start:start+12]):
            im=Image.open(p).convert('RGB');im.thumbnail((570,405))
            x=(i%3)*600+(600-im.width)//2;y=(i//3)*450+30
            sheet.paste(im,(x,y));draw.text(((i%3)*600+12,(i//3)*450+8),p.stem,fill='#0f172a')
        sheet.save(qa/(group+'-'+str(start//12+1).zfill(2)+'.jpg'),quality=90)
summary={'checked':len(verified),'issues':issues,'orientationsIndependentlyLaidOut':sum(not d.get('singleEntity') for d in manifest['diagrams']),'singleEntityException':['16-en','16-vi'],'checks':['source/SVG/PNG SHA-256','SVG XML and offline assets','PNG dimensions','white borders on four sides','saved SVG pixel replay','distinct orientation geometry','all PNG contact sheets'],'results':verified}
(out/'validation.json').write_text(json.dumps(summary,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in summary.items() if k!='results'},indent=2))
assert not issues
