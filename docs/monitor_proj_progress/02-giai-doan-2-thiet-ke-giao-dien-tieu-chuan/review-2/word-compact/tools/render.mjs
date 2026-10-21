import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {createRequire} from 'node:module';
import crypto from 'node:crypto';
// Use local npm dependencies, or an explicitly supplied dependency directory.
const toolDir=path.dirname(fileURLToPath(import.meta.url));
const require=createRequire(path.join(process.env.MERMAID_RUNTIME || toolDir,'package.json'));
const puppeteer=(await import(pathToFileURL(require.resolve('puppeteer')).href)).default;
const {renderMermaid}=await import(pathToFileURL(path.join(process.env.MERMAID_RUNTIME || toolDir,'node_modules/@mermaid-js/mermaid-cli/src/index.js')).href);
const out=path.resolve(toolDir,'..');const root=path.resolve(out,'..');
const original=process.argv.includes('--original');
const selected=process.argv.slice(2).filter(x=>!['--original','--resume'].includes(x));const jobs=[];
const baseFor=orientation=>orientation==='original'?path.join(root,'assets'):path.join(out,orientation);
for(const orientation of (original?['original']:['portrait','landscape','panels']))for(const file of (await fs.readdir(path.join(baseFor(orientation),'mermaid'))).filter(x=>x.endsWith('.mmd')))if(!selected.length||selected.includes(file))jobs.push({orientation,file});
const browser=await puppeteer.launch({executablePath:process.env.CHROME_PATH || undefined,headless:true,args:['--allow-file-access-from-files','--disable-background-timer-throttling','--disable-renderer-backgrounding','--disable-backgrounding-occluded-windows']});
const config=JSON.parse(await fs.readFile(path.join(root,'assets/mermaid-config.json'),'utf8'));
const results=[];let next=0;
let writeId=0;
async function writeArtifact(file,data){
 const temp=file+'.render-'+process.pid+'-'+(++writeId)+'.tmp';
 try{for(let attempt=0;;attempt++)try{await fs.writeFile(temp,data);await fs.rename(temp,file);return;}catch(e){if(attempt>=4||!['UNKNOWN','EPERM','EACCES','EBUSY'].includes(e.code))throw e;await new Promise(r=>setTimeout(r,200*(attempt+1)));}}
 finally{await fs.rm(temp,{force:true}).catch(()=>{});}
}
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
// Reuse only successful, unchanged source/SVG/PNG triples after an interrupted
// batch or transient Windows file lock. Final verification still checks all files.
if(process.argv.includes('--resume')){
 const priorPath=path.join(out,original?'render-original-validation.json':selected.length?'render-selected-validation.json':'render-validation.json');
 let prior=[];try{prior=JSON.parse(await fs.readFile(priorPath,'utf8')).results;}catch{}
 for(let i=jobs.length-1;i>=0;i--){const job=jobs[i],old=prior.find(r=>r.orientation===job.orientation&&r.file===job.file&&r.status==='pass');if(!old)continue;
  try{const base=baseFor(job.orientation),stem=job.file.slice(0,-4);
   const source=hash((await fs.readFile(path.join(base,'mermaid',job.file),'utf8')).replace(/\r\n/g,'\n'));
   if(source!==old.sourceSha256||hash(await fs.readFile(path.join(base,'svg',stem+'.svg')))!==old.svgSha256||hash(await fs.readFile(path.join(base,'png',stem+'.png')))!==old.pngSha256)continue;
   results.push(old);jobs.splice(i,1);
  }catch{}
 }
 console.log('Reused',results.length,'verified unchanged triples; rendering',jobs.length,'remaining jobs');
}
async function ready(page){await page.evaluate(async()=>{await document.fonts.ready;await Promise.all([...document.querySelectorAll('img')].map(async i=>{await i.decode();if(!i.complete||!i.naturalWidth)throw Error('Missing embedded image')}));await new Promise(r=>setTimeout(r,80));});}
async function normalize(page){return page.$eval('svg',svg=>{
 const inv=svg.getScreenCTM().inverse();let left=Infinity,top=Infinity,right=-Infinity,bottom=-Infinity;
 const include=r=>{const a=new DOMPoint(r.left,r.top).matrixTransform(inv),b=new DOMPoint(r.right,r.bottom).matrixTransform(inv);if(!Number.isFinite(a.x+b.y)||r.width+r.height===0)return;left=Math.min(left,a.x);top=Math.min(top,a.y);right=Math.max(right,b.x);bottom=Math.max(bottom,b.y);};
 const leaves=[...svg.querySelectorAll('text,foreignObject,path,rect,polygon,polyline,circle,line,ellipse,image')].filter(e=>!e.closest('defs,marker,clipPath,mask')&&!e.classList.contains('actor-line')&&getComputedStyle(e).display!=='none');
 for(const e of leaves)include(e.getBoundingClientRect());
 // foreignObject child text can extend beyond its nominal box: include its real line boxes.
 for(const fo of svg.querySelectorAll('foreignObject')){for(const e of fo.querySelectorAll('img,span,p,div'))include(e.getBoundingClientRect());}
 if(!Number.isFinite(left+top+right+bottom)||right<=left||bottom<=top)throw Error('Invalid painted bounds');
 for(const line of svg.querySelectorAll('.actor-line')){const current=Number(line.getAttribute('y2'));if(current>bottom)line.setAttribute('y2',String(bottom));}
 const pad=24,x=Math.floor(left-pad),y=Math.floor(top-pad),w=Math.ceil(right+pad-x),h=Math.ceil(bottom+pad-y);
 const originalViewBox=svg.getAttribute('viewBox');svg.setAttribute('viewBox',`${x} ${y} ${w} ${h}`);svg.setAttribute('width',String(w));svg.setAttribute('height',String(h));svg.style.maxWidth='none';svg.style.width=w+'px';svg.style.height=h+'px';svg.style.backgroundColor='white';svg.setAttribute('preserveAspectRatio','xMidYMid meet');
 return {width:w,height:h,originalViewBox,viewBox:[x,y,w,h],paintedBounds:[left,top,right,bottom],padding:pad,leafCount:leaves.length,images:svg.querySelectorAll('img').length};
 });}
async function screenshot(svgPath,pngPath,width){const p=await browser.newPage();try{await p.setViewport({width:2000,height:1200,deviceScaleFactor:1});await p.goto(pathToFileURL(svgPath).href,{waitUntil:'load'});await ready(p);const dims=await p.$eval('svg',e=>({w:e.viewBox.baseVal.width,h:e.viewBox.baseVal.height}));const factor=Math.min(width/dims.w,14000/Math.max(dims.w,dims.h));const w=Math.ceil(dims.w*factor),h=Math.ceil(dims.h*factor);await p.setViewport({width:w,height:h,deviceScaleFactor:1});await p.$eval('svg',(e,{w,h})=>{e.setAttribute('width',w);e.setAttribute('height',h);e.style.width=w+'px';e.style.height=h+'px';e.style.maxWidth='none'},{w,h});await p.bringToFront();await ready(p);const el=await p.$('svg');const data=await el.screenshot({omitBackground:false});if(pngPath)await writeArtifact(pngPath,data);return {data,w,h};}finally{await p.close();}}
const diagnostic=original?'render-original-validation.json':selected.length?'render-selected-validation.json':'render-validation.json';
let checkpoint=Promise.resolve();
function saveProgress(){const payload=JSON.stringify({renderer:'Mermaid CLI 11.12.0 + isolated Chromium SVG replay',results},null,2);checkpoint=checkpoint.then(()=>writeArtifact(path.join(out,diagnostic),payload));return checkpoint;}
async function worker(){while(next<jobs.length){const j=jobs[next++];let page;try{
 const base=baseFor(j.orientation);await fs.mkdir(path.join(base,'png'),{recursive:true});
 const srcPath=path.join(base,'mermaid',j.file);let def=await fs.readFile(srcPath,'utf8');
 // Rank extensions after their base during layout, then restore the true
 // extension-to-base arrowhead in the exported SVG. Source remains valid Mermaid.
 const extensionEdges=[];
 if(def.includes('%% Role use case:'))def=def.replace(/^\s+(\w+) -\.->\|"([^"]*«extend»[^"]*)"\| (\w+)$/gm,(_,src,label,dst)=>{
  extensionEdges.push({src,dst});return ` ${dst} -.->|"${label}"| ${src}`;
 });
 for(const url of [...new Set(def.match(/https:\/\/cdn\.jsdelivr\.net\/gh\/devicons\/devicon@v2\.17\.0\/icons\/[^'"\s]+/g)||[])]){const key=url.split('/icons/')[1].split('/')[0];def=def.replaceAll(url,'data:image/svg+xml;base64,'+(await fs.readFile(path.join(root,'assets/logos',key+'.svg'))).toString('base64'));}
 const rendered=await renderMermaid(browser,def,'svg',{viewport:{width:2000,height:1400,deviceScaleFactor:1},mermaidConfig:config});const svg=new TextDecoder().decode(rendered.data);if(/Maximum text size|NaN|Syntax error in text/.test(svg))throw Error('Invalid Mermaid render');
 page=await browser.newPage();await page.setViewport({width:2000,height:1400});await page.setContent('<!doctype html><meta charset="utf-8"><style>html,body{margin:0;padding:0;background:white}</style>'+svg);await ready(page);const grid=def.match(/%% Word entity-grid columns: (\d+)/);
 if(extensionEdges.length)await page.evaluate(edges=>{
  const marker=document.querySelector('marker[id$="pointStart"]');if(!marker)throw Error('Missing start arrow marker');
  for(const {src,dst} of edges){const edge=[...document.querySelectorAll('path.flowchart-link')].find(e=>e.getAttribute('data-id')?.startsWith(`L_${dst}_${src}_`));
   if(!edge)throw Error('Missing extension edge');edge.removeAttribute('marker-end');edge.setAttribute('marker-start',`url(#${marker.id})`);
  }
 },extensionEdges);
 if(grid)await page.$eval('svg',(svg,columns)=>{
  if(svg.querySelector('.edgePaths path'))throw Error('Entity grid requires disconnected tables');
  const nodes=[...svg.querySelectorAll('g.nodes > g.node')];const boxes=nodes.map(e=>e.getBBox());const gap=40;const widths=Array.from({length:columns},(_,c)=>Math.max(0,...boxes.filter((_,i)=>i%columns===c).map(b=>b.width)));
  let y=0;for(let row=0;row<Math.ceil(nodes.length/columns);row++){let x=0;const height=Math.max(...boxes.slice(row*columns,(row+1)*columns).map(b=>b.height));for(let c=0;c<columns;c++){const i=row*columns+c;if(i>=nodes.length)break;const b=boxes[i];nodes[i].setAttribute('transform',`translate(${x-b.x} ${y-b.y})`);x+=widths[c]+gap;}y+=height+gap;}
 },Number(grid[1]));
 // Mermaid's portable stadium syntax remains editable in Markdown. For the
 // role-specific use cases, export true UML-style ellipses at the same bounds.
 // Actor geometry and edge attachment points stay unchanged.
 if(def.includes('%% Role use case:'))await page.$$eval('g.node',nodes=>{
  for(const node of nodes){if(node.classList.contains('actor'))continue;
   const boundary=node.querySelector('.label-container');if(!boundary)continue;
   const b=boundary.getBBox(),ellipse=document.createElementNS('http://www.w3.org/2000/svg','ellipse');
   ellipse.setAttribute('style',node.classList.contains('main')?'fill:#e0f2fe;stroke:#0284c7;stroke-width:1.3px':'fill:#f0fdfa;stroke:#0f766e;stroke-width:1.3px');
   ellipse.setAttribute('cx',b.x+b.width/2);ellipse.setAttribute('cy',b.y+b.height/2);ellipse.setAttribute('rx',b.width/2);ellipse.setAttribute('ry',b.height/2);boundary.replaceChildren(ellipse);
  }
 });
 // Architecture group headings sit in their reserved header band. Centered
 // headings can collide with request/response labels crossing a group boundary.
 if(/^(01|03)-/.test(j.file))await page.$$eval('g.cluster',groups=>{
  const obstacles=[...document.querySelectorAll('g.edgeLabel,g.node')].map(e=>e.getBoundingClientRect());
  const overlap=(a,b)=>Math.max(0,Math.min(a.right+4,b.right)-Math.max(a.left-4,b.left))*Math.max(0,Math.min(a.bottom+4,b.bottom)-Math.max(a.top-4,b.top));
  for(const group of groups){const rect=group.querySelector(':scope > rect'),label=group.querySelector(':scope > g.cluster-label');
   if(!rect||!label)continue;
   const x=Number(rect.getAttribute('x')),y=Number(rect.getAttribute('y')),w=Number(rect.getAttribute('width')),lw=label.getBBox().width;
   let best=null;
   for(const dy of [12,42,72,102])for(const dx of [16,Math.max(16,w-lw-16),Math.max(16,(w-lw)/2)]){
    label.setAttribute('transform',`translate(${x+dx},${y+dy})`);
    const box=label.getBoundingClientRect(),score=obstacles.reduce((s,b)=>s+overlap(box,b),0);
    if(!best||score<best.score)best={dx,dy,score};
   }
   label.setAttribute('transform',`translate(${x+best.dx},${y+best.dy})`);
   if(best.score>0)throw Error('Architecture heading overlaps content: '+group.id);
   obstacles.push(label.getBoundingClientRect());
  }
  // Nested cluster rectangles are painted after their parent's label. Keep all
  // headings above those rectangles while preserving their SVG coordinates.
  const svg=document.querySelector('svg');
  const headings=groups.map(group=>group.querySelector(':scope > g.cluster-label')).filter(Boolean);
  for(const label of headings){
   const matrix=svg.getScreenCTM().inverse().multiply(label.getScreenCTM());
   label.setAttribute('transform',`matrix(${matrix.a} ${matrix.b} ${matrix.c} ${matrix.d} ${matrix.e} ${matrix.f})`);
   svg.appendChild(label);
  }
 });
 const geometry=await normalize(page);await ready(page);const finalSvg=await page.$eval('svg',e=>new XMLSerializer().serializeToString(e));const stem=j.file.slice(0,-4);const svgPath=path.join(base,'svg',stem+'.svg');await writeArtifact(svgPath,finalSvg);await page.close();page=null;
 const fit=o=>16*Math.min((o==='portrait'?160:247)/geometry.width,(o==='portrait'?230:150)/geometry.height)*72/25.4;
 const preferred=['panels','original'].includes(j.orientation)?(fit('portrait')>=fit('landscape')?'portrait':'landscape'):j.orientation;
 const raster=await screenshot(svgPath,path.join(base,'png',stem+'.png'),preferred==='portrait'?2400:3600);
 // Reopen the saved SVG independently and compare exact pixel output, not merely PNG existence.
 const replay=await screenshot(svgPath,undefined,preferred==='portrait'?2400:3600);
 let pixelComparison={identical:true,maxChannelDelta:0,changedFraction:0};
 if(hash(raster.data)!==hash(replay.data)){
  const check=await browser.newPage();try{pixelComparison=await check.evaluate(async(a,b)=>{
   async function pixels(s){const i=new Image();i.src='data:image/png;base64,'+s;await i.decode();const c=document.createElement('canvas');c.width=i.width;c.height=i.height;const ctx=c.getContext('2d');ctx.drawImage(i,0,0);return {w:i.width,h:i.height,data:ctx.getImageData(0,0,i.width,i.height).data};}
   const x=await pixels(a),y=await pixels(b);if(x.w!==y.w||x.h!==y.h)throw Error('Replay dimensions differ');let max=0,changed=0;for(let i=0;i<x.data.length;i+=4){let d=0;for(let c=0;c<4;c++)d=Math.max(d,Math.abs(x.data[i+c]-y.data[i+c]));if(d)changed++;max=Math.max(max,d);}return {identical:changed===0,maxChannelDelta:max,changedFraction:changed/(x.w*x.h)};
  },Buffer.from(raster.data).toString('base64'),Buffer.from(replay.data).toString('base64'));}finally{await check.close();}
  // Allow only sparse, sub-10-level antialiasing differences from Chromium.
  if(pixelComparison.maxChannelDelta>10||pixelComparison.changedFraction>0.001)throw Error('Saved-SVG replay pixel mismatch: '+JSON.stringify(pixelComparison));
 }
 results.push({...j,status:'pass',...geometry,pngWidth:raster.w,pngHeight:raster.h,estimatedTextPt:+fit(preferred).toFixed(1),preferred,sourceSha256:hash((await fs.readFile(srcPath,'utf8')).replace(/\r\n/g,'\n')),svgSha256:hash(finalSvg),pngSha256:hash(raster.data),pngMatchesSavedSvg:true,pixelComparison});console.log('PASS',j.orientation,j.file,geometry.width,geometry.height);
 }catch(e){results.push({...j,status:'fail',error:String(e)});console.log('FAIL',j.orientation,j.file,String(e));if(page)await page.close();}await saveProgress();}}
await Promise.all(Array.from({length:Number(process.env.RENDER_WORKERS||3)},()=>worker()));await browser.close();
const target=original?'render-original-validation.json':selected.length?'render-selected-validation.json':'render-validation.json';await writeArtifact(path.join(out,target),JSON.stringify({renderer:'Mermaid CLI 11.12.0 + isolated Chromium SVG replay',results},null,2));
if(!original&&selected.length&&results.every(r=>r.status==='pass')){
 const ledger=JSON.parse(await fs.readFile(path.join(out,'render-validation.json'),'utf8'));const keys=new Set(results.map(r=>r.orientation+'/'+r.file));
 const live=new Set();for(const orientation of ['portrait','landscape','panels'])for(const file of await fs.readdir(path.join(out,orientation,'mermaid')))if(file.endsWith('.mmd'))live.add(orientation+'/'+file);
 ledger.results=[...ledger.results.filter(r=>live.has(r.orientation+'/'+r.file)&&!keys.has(r.orientation+'/'+r.file)),...results];await writeArtifact(path.join(out,'render-validation.json'),JSON.stringify(ledger,null,2));
}
if(results.some(r=>r.status!=='pass'))process.exitCode=1;
