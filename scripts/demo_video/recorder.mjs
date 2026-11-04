import fs from 'node:fs/promises';
const overlay = `(()=>{if(document.getElementById('mf-demo-pointer'))return;const s=document.createElement('style');s.id='mf-demo-pointer-style';s.textContent='#mf-demo-pointer{position:fixed;left:0;top:0;z-index:2147483647;pointer-events:none;width:24px;height:32px}.mf-demo-ring{position:fixed;z-index:2147483646;pointer-events:none;border:3px solid #ef4444;border-radius:50%;width:46px;height:46px;margin:-23px;animation:mf-demo-click .7s ease-out forwards}@keyframes mf-demo-click{from{opacity:1;scale:.65}to{opacity:0;scale:1.5}}';document.head.appendChild(s);const c=document.createElement('div');c.id='mf-demo-pointer';c.innerHTML='<svg viewBox="0 0 24 32" width="24" height="32"><path d="M2 2 L2 25 L8 19 L13 30 L17 28 L12 17 L22 17 Z" fill="white" stroke="#111827" stroke-width="2"/></svg>';document.body.appendChild(c);document.addEventListener('pointermove',e=>{c.style.left=e.clientX+'px';c.style.top=e.clientY+'px';});document.addEventListener('pointerdown',e=>{const r=document.createElement('div');r.className='mf-demo-ring';r.style.left=e.clientX+'px';r.style.top=e.clientY+'px';document.body.appendChild(r);r.addEventListener('animationend',()=>r.remove());});})();`;
export async function createRecorder(tab, cdp, dir) {
  await fs.mkdir(dir+'/assets/frames',{recursive:true});
  let cursor=(await cdp.readEvents({methods:['Page.screencastFrame']})).cursor;
  await cdp.send('Page.startScreencast',{format:'jpeg',quality:90,maxWidth:1920,maxHeight:1080,everyNthFrame:1});
  let manifest;
  try { manifest=JSON.parse(await fs.readFile(dir+'/recording.json','utf8')); }
  catch { manifest={source:'CDP Page.screencastFrame from real MediFlow UI',segments:[]}; }
  const oldFiles=await fs.readdir(dir+'/assets/frames');
  let frame=oldFiles.filter(x=>/^\d+\.jpg$/.test(x)).reduce((n,x)=>Math.max(n,Number(x.split('.')[0])+1),0);
  async function segment(label,fn){
    const item={label,startedAt:new Date().toISOString(),frames:[]};
    let active=true;
    const pump=(async()=>{while(active){
      const ev=await cdp.readEvents({afterSequence:cursor,methods:['Page.screencastFrame'],limit:100});cursor=ev.cursor;
      if(ev.truncated)item.truncated=true;
      for(const e of ev.events){const p=e.params;const file=String(frame++).padStart(7,'0')+'.jpg';await fs.writeFile(dir+'/assets/frames/'+file,Buffer.from(p.data,'base64'));item.frames.push({file,time:p.metadata.timestamp});await cdp.send('Page.screencastFrameAck',{sessionId:p.sessionId});}
      await new Promise(r=>setTimeout(r,250));
    }})();
    try{await cdp.send('Runtime.evaluate',{expression:overlay});await fn();await tab.getAXState();await new Promise(r=>setTimeout(r,900));}
    catch(e){item.error=String(e);throw e;}
    finally{active=false;await pump;item.endedAt=new Date().toISOString();await fs.writeFile(dir+'/assets/frames/end-'+manifest.segments.length+'.png',await tab.screenshot({}));manifest.segments.push(item);await fs.writeFile(dir+'/recording.json',JSON.stringify(manifest,null,2));}
    return {label,frames:item.frames.length};
  }
  async function close(){await cdp.send('Page.stopScreencast');await cdp.send('Runtime.evaluate',{expression:"document.getElementById('mf-demo-pointer')?.remove();document.getElementById('mf-demo-pointer-style')?.remove();document.querySelectorAll('.mf-demo-ring').forEach(e=>e.remove());"});}
  return {segment,close};
}
