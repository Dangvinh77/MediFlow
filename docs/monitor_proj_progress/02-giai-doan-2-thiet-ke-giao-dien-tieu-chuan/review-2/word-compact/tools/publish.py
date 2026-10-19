from pathlib import Path
import json,re,shutil,hashlib,html
root=Path(__file__).resolve().parents[2];out=root/'word-compact'
ledger=json.loads((out/'render-validation.json').read_text(encoding='utf8'));ledger_name=ledger['renderer'];all_results=ledger['results']
raw=[r for r in all_results if r['orientation']!='panels'];assert raw and all(r['status']=='pass' for r in raw)
panels=[r for r in all_results if r['orientation']=='panels'];assert all(r['status']=='pass' for r in panels)
def fit(r,o):return round(16*min((160 if o=='portrait' else 247)/r['width'],(230 if o=='portrait' else 150)/r['height'])*72/25.4,1)
titles={}
for lang,name in [('en','01-review-2-standard-en.md'),('vi','02-review-2-standard-vi.md')]:
 text=(root/name).read_text(encoding='utf8')
 for title,stem in re.findall(r'!\[([^\]]+)\]\(assets/svg/([^\)]+)\.svg\)',text):titles[stem]=title
assert len(raw)==2*len(titles), 'Every diagram needs both paper orientations'
figure_numbers=sorted({int(stem.split('-')[0]) for stem in titles})
manifest=[]
for stem in sorted(titles):
 cand=[r for r in raw if r['file']==stem+'.mmd'];data={}
 buffers={(r['orientation'],ext):(out/r['orientation']/ext/f'{stem}.{ext if ext!="mermaid" else "mmd"}').read_bytes() for r in cand for ext in ['mermaid','svg','png']}
 for orient in ['portrait','landscape']:
  best=next(r for r in cand if r['orientation']==orient)
  data[orient]={'candidate':best['orientation'],'width':best['width'],'height':best['height'],'estimatedTextPt':fit(best,orient)}
 recommended=max(data,key=lambda o:data[o]['estimatedTextPt']);detail=[]
 for p in sorted([r for r in panels if r['file'].startswith(stem+'-part-')],key=lambda r:r['file']):
  o=max(['portrait','landscape'],key=lambda o:fit(p,o));detail.append({'id':p['file'][:-4],'orientation':o,'estimatedTextPt':fit(p,o)})
 use_parts=bool(detail) and data[recommended]['estimatedTextPt']<7.5
 manifest.append({'id':stem,'title':titles[stem],'recommended':recommended,'usePanels':use_parts,'variants':data,'panels':detail,'singleEntity':stem.startswith('16-')})
(out/'layout-manifest.json').write_text(json.dumps({'paper':'A4','marginsMm':25,'usableDiagramMm':{'portrait':[160,230],'landscape':[247,150]},'textEstimate':'Conservative 16px base text scaled to page bounds; actual font sizes vary','layoutRule':'Portrait and landscape are authored independently; Patient ERD is a single-table exception','rasterization':'PNG captured from the saved standalone SVG after font/image decode; no global CSS overrides','diagrams':manifest},ensure_ascii=False,indent=2),encoding='utf8')
# Full bilingual report options reuse the existing prose, tables, and contract links.
for lang,old,new in [('en','01-review-2-standard-en.md','03-review-2-word-en.md'),('vi','02-review-2-standard-vi.md','04-review-2-word-vi.md')]:
 text=(root/old).read_text(encoding='utf8')
 intro=('\n> **Word layout option.** A4 with 25 mm margins. Use the PNG images below. Large diagrams use consecutive panels; repeated nodes connect the panels. The [selection guide](word-compact/README.md) provides both paper orientations and the complete overview. Architecture 01 groups deployments and stores; use the original for individual connectors.\n' if lang=='en' else '\n> **Option bố cục cho Word.** A4, lề 25 mm. Chèn ảnh PNG bên dưới. Sơ đồ lớn chia thành các hình liên tiếp; nút lặp lại dùng để nối nội dung giữa các phần. [Bảng chọn sơ đồ](word-compact/README.md) có cả hai khổ giấy và bản tổng quan. Kiến trúc 01 gom nhóm deployment và store; xem bản gốc để đối chiếu từng đường nối.\n')
 text=text.replace('\n',intro+'\n',1)
 text=text.replace('(01-review-2-standard-en.md)','(03-review-2-word-en.md)').replace('(02-review-2-standard-vi.md)','(04-review-2-word-vi.md)')
 pattern=r'!\[([^\]]+)\]\(assets/svg/([^\)]+)\.svg\)\s*<details>[\s\S]*?</details>'
 def repl(m):
  title,stem=m.groups();d=next(d for d in manifest if d['id']==stem);parts=d['panels'] if d['usePanels'] else []
  result=[]
  if parts:
   for i,p in enumerate(parts,1):
    o=p['orientation'];label=('Part' if lang=='en' else 'Phần')+f' {i}/{len(parts)}';paper=('A4 portrait' if o=='portrait' else 'A4 landscape')
    result+=['**'+label+' · '+paper+'**',f'![{title} — {label}](word-compact/panels/png/{p["id"]}.png)',f'[Mermaid](word-compact/panels/mermaid/{p["id"]}.mmd) · [SVG](word-compact/panels/svg/{p["id"]}.svg)']
  else:
   o=d['recommended'];result+=['**A4 '+o+'**',f'![{title}](word-compact/{o}/png/{stem}.png)',f'[Mermaid](word-compact/{o}/mermaid/{stem}.mmd) · [SVG](word-compact/{o}/svg/{stem}.svg)']
  result+=[f'[Original SVG](assets/svg/{stem}.svg) · [Portrait PNG](word-compact/portrait/png/{stem}.png) · [Landscape PNG](word-compact/landscape/png/{stem}.png)']
  return '\n\n'.join(result)
 text,count=re.subn(pattern,repl,text);assert count==len(figure_numbers),count
 # The option uses PNGs and linked sources rather than the original inline SVG/Mermaid presentation.
 a=text.index('## Diagram standard' if '## Diagram standard' in text else '## Quy chuẩn sơ đồ') if ('## Diagram standard' in text or '## Quy chuẩn sơ đồ' in text) else -1
 if lang=='en':
  start=text.index('SVG previews are provided');end=text.index('\n\n',start)
  text=text[:start]+'This Word option uses PNG images to preserve text, technology logos, and actors. Mermaid sources and SVGs are linked beside each figure. The Sky/Teal theme and original logos are retained. SVG labels use HTML, so check rendering before inserting SVG directly into Word.'+text[end:]
 if lang=='vi':
  start=text.index('Ảnh SVG là bản dựng');end=text.index('\n\n',start)
  text=text[:start]+'Bản Word dùng PNG để giữ chữ, logo và actor ổn định khi chèn. Nguồn Mermaid và SVG của từng hình nằm ở liên kết đi kèm. Theme Sky/Teal và toàn bộ logo gốc được giữ. SVG có nhãn HTML nên cần kiểm tra khả năng hiển thị nếu chèn trực tiếp vào Word.'+text[end:]
 (root/new).write_text(text,encoding='utf8')
# Human selection guide.
lines=['# Chọn sơ đồ Review 2 cho Word','','Bộ bổ sung có phiên bản gốc và phiên bản dàn trang Word. Mỗi sơ đồ có bản dàn cho A4 dọc và A4 ngang, cùng nguồn Mermaid, SVG và PNG. PNG dùng nền trắng, rộng 2400 hoặc 3600 px (cạnh dài tối đa 14000 px); nên chèn PNG vào Word để giữ nguyên nhãn HTML, logo và actor.','','- [Báo cáo option Word tiếng Việt](../04-review-2-word-vi.md) — giữ phần nội dung và bảng, thay bằng hình phù hợp trang.','- [English Word layout option](../03-review-2-word-en.md).','- [Trang chọn và so sánh hình](choose-diagrams.html) — mở bằng trình duyệt, chọn ngôn ngữ, hình, khổ giấy và bản gốc/bản gọn/các phần.','','Hình 01 và 03 có Client side, Server side, icon và luồng request/response. Hình 01 bỏ số port triển khai; hình 03 bổ sung chi tiết xử lý và phụ thuộc. Các bản trước được lưu tại `../assets/history/architecture-before-side-icons/` và `../assets/history/architecture-before-request-response/`.', '', '## Thiết lập Word','','1. Chọn A4, lề 2,5 cm. Trang dọc: ảnh tối đa rộng 16 cm, cao 23 cm. Trang ngang: ảnh tối đa rộng 24,7 cm, cao 15 cm để còn chỗ cho caption.','2. Chọn Insert → Pictures → This Device và lấy PNG ở liên kết bên dưới; đặt Wrap Text → In Line with Text, bật Lock aspect ratio. Giới hạn đồng thời chiều rộng và chiều cao, không kéo giãn ảnh.','3. Khi đổi riêng một trang sang ngang, thêm Section Break → Next Page trước và sau trang đó, rồi chọn Orientation → Landscape cho section.','4. Với hình lớn, dùng từng phần liên tiếp theo thứ tự. Các nút lặp là điểm nối giữa các phần, không phải bảng hoặc tiến trình mới. Nhãn cạnh, cardinality và thông điệp vẫn giữ trong tập các phần. DFD và graph chia theo nhóm quan hệ, không hàm ý thứ tự chạy.','5. Trong Word bật chất lượng ảnh High fidelity và tránh nén ảnh. Kiểm tra bản in ở 100%; cỡ chữ ước tính trong manifest chỉ hỗ trợ lựa chọn bố cục.','','## Chọn phiên bản','','Hình 01 là tổng quan gom nhóm sáu deployment và tám store, có liệt kê ánh xạ store. Hình 02 giữ đủ 20 logo công nghệ. Hình 03 chia thành Web, Flutter, backend đồng bộ, sự kiện bất đồng bộ và phụ thuộc tĩnh. Các sơ đồ còn lại dàn lại hướng, khoảng cách và xuống dòng; các bản chia phần giữ toàn bộ quan hệ của sơ đồ tương ứng. Bản đầy đủ vẫn có thể nhỏ chữ trên một trang; cột đề xuất ưu tiên các phần khi cần.','','| ID | Nội dung | Đề xuất | Tiếng Việt | English | Các phần chi tiết |','|---|---|---|---|---|---|']
for num in figure_numbers:
 d=next(d for d in manifest if d['id']==f'{num:02}-vi');e=next(e for e in manifest if e['id']==f'{num:02}-en');suggest='Dùng các phần' if d['usePanels'] else ('A4 dọc' if d['recommended']=='portrait' else 'A4 ngang')
 links=lambda lang:f'[Dọc](portrait/png/{num:02}-{lang}.png) · [Ngang](landscape/png/{num:02}-{lang}.png)'
 details=' · '.join(f'[{i+1}](panels/png/{p["id"]}.png)' for i,p in enumerate(d['panels'])) or '—'
 lines.append(f'| {num:02} | {d["title"]} | {suggest} | {links("vi")} | {links("en")} | {details} |')
lines+=['','## Nguồn và tính tương thích','','Các thư mục `portrait/`, `landscape/`, `panels/` đều có `mermaid/`, `svg/`, `png/`. Không xoay ảnh để đổi khổ giấy. Hai khổ được dàn riêng: dọc ưu tiên các cột/luồng cao, ngang ưu tiên các hàng/luồng rộng. Use case và sitemap dùng hướng bố trí riêng để cân bằng trang. Sequence giữ đúng thứ tự thông điệp, thay đổi độ rộng lifeline và cách xuống dòng. Không sao chép bố cục tốt nhất vào cả hai thư mục. Riêng ERD Patient (16) chỉ có một bảng nên cấu trúc trong hai khổ vẫn giống nhau; trang chọn ghi rõ trường hợp này. Các ERD không có quan hệ (21, 22) được xếp lại thành lưới sau khi dựng Mermaid, chỉ di chuyển bảng, không tạo thêm quan hệ. SVG có nhãn HTML nên PNG là định dạng ưu tiên trong Word. Logo SVG được nhúng offline vào SVG/PNG; nguồn Mermaid dùng cùng CDN Devicon có phiên bản cố định như bộ gốc.','','Bộ này chỉ bổ sung lựa chọn sơ đồ, chưa thay hoặc xuất lại các tệp DOCX/PDF lịch sử. Muốn dùng một hình cụ thể, mở PNG tương ứng hoặc dùng trang chọn bên trên.']
(out/'README.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
# Offline HTML chooser. It uses existing local assets and needs no server.
data=json.dumps(manifest,ensure_ascii=False).replace('</','<\\/')
page='''<!doctype html><html lang="vi"><meta charset="utf-8"><title>Chọn sơ đồ Review 2 cho Word</title><style>
*{box-sizing:border-box}body{margin:0;background:#f1f5f9;color:#0f172a;font:16px Arial,sans-serif}header{padding:24px 32px;background:#fff;border-bottom:1px solid #cbd5e1}h1{font-size:26px;margin:0 0 12px}p{max-width:1000px;line-height:1.5}.controls{display:flex;flex-wrap:wrap;gap:16px;align-items:end}label{display:grid;gap:6px;font-size:14px}select,a.button{padding:10px;border:1px solid #94a3b8;border-radius:6px;background:white;color:#0f172a}select#figure{max-width:560px}main{padding:24px 32px}.notice{padding:12px;background:#ccfbf1;border-radius:6px;max-width:1080px}.sheet{background:white;box-shadow:0 4px 24px #94a3b855;margin:24px auto;padding:28px;display:grid;grid-template-rows:minmax(0,1fr) auto;align-items:center;gap:14px;max-width:100%}.sheet.portrait{width:640px;aspect-ratio:210/297}.sheet.landscape{width:950px;aspect-ratio:297/210}.sheet img{display:block;width:100%;height:100%;min-height:0;min-width:0;object-fit:contain}.caption{font-size:14px;text-align:center;margin:0}nav{display:flex;gap:20px;justify-content:center;flex-wrap:wrap}a{color:#0369a1}.links{display:flex;gap:20px;flex-wrap:wrap}.muted{color:#475569;font-size:14px}@media print{header,.notice,nav{display:none}.sheet{box-shadow:none;margin:0;break-after:page}}
</style><header><h1>Chọn sơ đồ Review 2 cho Word</h1><p>Chọn hình và khổ giấy để xem trước. Tải PNG để chèn vào Word; bản gốc, SVG và nguồn Mermaid vẫn được giữ riêng.</p><div class="controls"><label>Ngôn ngữ<select id="lang"><option value="vi">Tiếng Việt</option><option value="en">English</option></select></label><label>Sơ đồ<select id="figure"></select></label><label>Phiên bản<select id="mode"><option value="recommended">Đề xuất</option><option value="compare">So sánh dọc / ngang</option><option value="portrait">A4 dọc</option><option value="landscape">A4 ngang</option><option value="panels">Các phần chi tiết</option><option value="original">Bản gốc SVG</option></select></label></div><p id="info" class="muted"></p><div class="links" id="links"></div></header><main><div class="notice">Word: A4, lề 2,5 cm, ảnh In Line with Text, khóa tỷ lệ. Dọc tối đa 16 × 23 cm; ngang 24,7 × 15 cm. Với sơ đồ lớn, dùng các phần liên tiếp để đọc rõ.</div><div id="preview"></div></main><script>
const data=DATA;const $=id=>document.getElementById(id);for(const n of [...new Set(data.map(d=>Number(d.id.split('-')[0])))].sort((a,b)=>a-b)){const o=document.createElement('option');o.value=String(n).padStart(2,'0');$('figure').append(o)}
function update(){const lang=$('lang').value;for(const o of $('figure').options)o.textContent=o.value+' · '+data.find(d=>d.id===o.value+'-'+lang).title;const d=data.find(d=>d.id===$('figure').value+'-'+lang);let mode=$('mode').value;if(mode==='recommended')mode=d.usePanels?'panels':d.recommended;let items=[];if(mode==='compare')items=['portrait','landscape'].map(o=>({base:o,id:d.id,o,title:d.title}));else if(mode==='panels'&&d.panels.length)items=d.panels.map((p,i)=>({base:'panels',id:p.id,o:p.orientation,title:d.title+' · '+(i+1)+'/'+d.panels.length}));else if(mode==='original')items=[{base:'../assets',id:d.id,o:d.recommended,title:d.title,original:true}];else{if(mode==='panels')mode=d.recommended;items=[{base:mode,id:d.id,o:mode,title:d.title}]}$('preview').replaceChildren();$('links').replaceChildren();for(const item of items){const frame=document.createElement('section');frame.className='sheet '+item.o;const img=document.createElement('img');img.alt=item.title;img.src=item.base+'/'+(item.original?'svg':'png')+'/'+item.id+(item.original?'.svg':'.png');const cap=document.createElement('p');cap.className='caption';cap.textContent=item.title+' · A4 '+item.o;frame.append(img,cap);$('preview').append(frame);const setHeight=()=>frame.style.height=(frame.clientWidth*(item.o==='portrait'?297/210:210/297))+'px';setHeight();new ResizeObserver(setHeight).observe(frame);const nav=document.createElement('nav');for(const [title,folder,ext] of item.original?[['Mở SVG','svg','svg'],['Nguồn Mermaid','mermaid','mmd']]:[['Mở PNG','png','png'],['Mở SVG','svg','svg'],['Nguồn Mermaid','mermaid','mmd']]){const a=document.createElement('a');a.textContent=title;a.href=item.base+'/'+folder+'/'+item.id+'.'+ext;a.target='_blank';nav.append(a)}$('preview').append(nav)}$('info').textContent=d.singleEntity?'ERD này chỉ có một bảng: đổi khổ không thay cấu trúc bên trong.':d.usePanels?'Đề xuất: dùng các phần. Các nút lặp là điểm nối nội dung, không phải thực thể mới.':'Đề xuất: A4 '+d.recommended+'. Có thể đổi khổ để so sánh.'}
for(const id of ['lang','figure','mode'])$(id).onchange=update;update();</script></html>'''.replace('DATA',data)
(out/'choose-diagrams.html').write_text(page,encoding='utf8')
# Register the additional option in the existing parent index only.
p=root.parent/'README.md';text=p.read_text(encoding='utf8');marker='## Tùy chọn sơ đồ cho Word\n'
if marker not in text:text+='\n'+marker+'\n- [Bảng chọn A4 dọc và ngang](review-2/word-compact/README.md)\n- [Review 2 option Word tiếng Việt](review-2/04-review-2-word-vi.md)\n- [Review 2 Word layout option English](review-2/03-review-2-word-en.md)\n\nBộ assets và báo cáo gốc giữ nguyên. Bản bổ sung có PNG, SVG, Mermaid và các phần chi tiết cho sơ đồ lớn.\n'
p.write_text(text,encoding='utf8')

(out/'panels/render-validation.json').write_text(json.dumps({'renderer':ledger_name,'results':panels},indent=2),encoding='utf8')
print('Published distinct Word layouts and updated chooser')
