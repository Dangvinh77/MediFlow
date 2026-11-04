"""Assemble only recorded UI frames. Missing chapters never become synthetic screens."""
import datetime as dt
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent
manifest_path = root / 'recording.json'
if not manifest_path.is_file():
    raise SystemExit('Chưa có bản ghi. Khởi tạo bản làm việc và quay UI trước khi ghép video; xem README.md.')
manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
clips = []
for i, segment in enumerate(manifest['segments']):
    if segment.get('error'):
        continue
    start = dt.datetime.fromisoformat(segment['startedAt'].replace('Z', '+00:00')).timestamp()
    end = dt.datetime.fromisoformat(segment['endedAt'].replace('Z', '+00:00')).timestamp()
    frames = [f for f in segment['frames'] if f['time'] >= start - .1]
    if not frames:
        continue
    lines = []
    duration = 0
    for n, frame in enumerate(frames):
        next_time = frames[n + 1]['time'] if n + 1 < len(frames) else max(end, frame['time'] + 1.5)
        hold = max(1 / 30, min(2, next_time - frame['time']))
        lines += ["file 'assets/frames/" + frame['file'] + "'", f'duration {hold:.6f}']
        duration += hold
    lines.append("file 'assets/frames/" + frames[-1]['file'] + "'")
    concat = root / f'frames-{i:03}.txt'
    concat.write_text('\n'.join(lines), encoding='utf-8')
    media = f'assets/segment-{i:03}.mp4'
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', str(concat),
                    '-vf', 'fps=30,scale=1920:1080:flags=lanczos', '-c:v', 'libx264', '-preset', 'fast',
                    '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-an', '-y', str(root / media)], check=True)
    actual = float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of',
                                           'default=noprint_wrappers=1:nokey=1', str(root / media)], text=True).strip())
    clips.append(dict(label=segment['label'], media=media, duration=actual))

total = 0
hosts = []
for i, clip in enumerate(clips):
    sid = f'chapter-{i:03}'
    host = f'<div id="{sid}" class="clip" data-composition-id="{sid}" data-composition-src="compositions/{sid}.html" data-start="{total:.6f}" data-duration="{clip["duration"]:.6f}" data-track-index="1" data-track-kind="graphics" data-width="1920" data-height="1080"></div>'
    hosts.append(host)
    (root / 'compositions').mkdir(exist_ok=True)
    (root / 'compositions' / (sid + '.html')).write_text(f'''<template><style>#root{{position:absolute;inset:0}}video{{position:absolute;inset:0;width:1920px;height:1080px;object-fit:contain}}</style>
<div id="root" data-composition-id="{sid}" data-width="1920" data-height="1080"><video id="{sid}-ui" class="clip" src="{clip['media']}" data-start="0" data-duration="{clip['duration']:.6f}" data-track-index="1" muted playsinline></video></div>
<script>window.__timelines['{sid}']=gsap.timeline({{paused:true}});</script></template>''', encoding='utf-8')
    clip['start'] = total
    total += clip['duration']
(root / 'index.html').write_text('''<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>MediFlow — bản ghi UI</title><script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script><style>*{box-sizing:border-box}html,body{margin:0;width:1920px;height:1080px;overflow:hidden;background:#fff}#root{position:relative;width:1920px;height:1080px}</style></head><body>'''+f'<div id="root" data-composition-id="main" data-start="0" data-duration="{total:.6f}" data-width="1920" data-height="1080" data-fps="30">'+''.join(hosts)+'''</div><script>window.__timelines['main']=gsap.timeline({paused:true});</script></body></html>''', encoding='utf-8')
(root / 'chapters.json').write_text(json.dumps({'status':'PARTIAL until coverage is verified', 'duration': total, 'clips': clips}, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'{len(clips)} real recorded segments, {total:.3f}s. Coverage must be verified separately.')
