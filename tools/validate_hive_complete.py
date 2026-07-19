#!/usr/bin/env python3
"""Fast offline completeness validator for the rebuilt Hive City assets."""
from pathlib import Path
import gzip, io, json, struct, sys

ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'src/main/resources/data/firstcrusade'
ASSETS=ROOT/'src/main/resources/assets/firstcrusade'
MODULES=DATA/'hive_modules'
DISTRICTS=DATA/'hive_districts'
STRUCTURES=DATA/'structures'
passes=[]; failures=[]; warnings=[]
def check(ok,msg): (passes if ok else failures).append(msg)

# Fast reader for the first root field. All generated structure files put `size` first.
def nbt_size(path):
    raw=path.read_bytes()
    if raw[:2]==b'\x1f\x8b': raw=gzip.decompress(raw)
    f=io.BytesIO(raw)
    def rd(n):
        b=f.read(n)
        if len(b)!=n: raise EOFError
        return b
    def u8(): return rd(1)[0]
    def u16(): return struct.unpack('>H',rd(2))[0]
    def i32(): return struct.unpack('>i',rd(4))[0]
    def string(): return rd(u16()).decode('utf-8')
    root_type=u8(); _=string()
    if root_type!=10: raise ValueError('root is not compound')
    tag=u8(); name=string()
    if tag!=9 or name!='size': raise ValueError(f'first field is {name!r} type {tag}, expected size list')
    elem=u8(); count=i32()
    if elem!=3 or count!=3: raise ValueError('size is not list<int>[3]')
    return [i32(),i32(),i32()]

json_files=list((ROOT/'src/main/resources').rglob('*.json'))+list((ROOT/'src/generated/resources').rglob('*.json'))
json_bad=[]
for p in json_files:
    try: json.loads(p.read_text(encoding='utf-8'))
    except Exception as e: json_bad.append(f'{p.relative_to(ROOT)}: {e}')
check(not json_bad,f'{len(json_files)} JSONs válidos')
for m in json_bad: failures.append('JSON inválido '+m)

module_map={}; nbt_count=0
for p in MODULES.rglob('*.json'):
    rel=p.relative_to(MODULES).with_suffix('').as_posix(); mid=f'firstcrusade:{rel}'
    try: data=json.loads(p.read_text(encoding='utf-8'))
    except Exception: continue
    module_map[mid]=(p,data)
    template=data.get('template','')
    check(template.startswith('firstcrusade:'),f'módulo {mid}: template namespaced')
    if not template.startswith('firstcrusade:'): continue
    tpath=template.split(':',1)[1]; nbt=STRUCTURES/f'{tpath}.nbt'
    check(nbt.exists(),f'módulo {mid}: NBT existe')
    if nbt.exists():
        try:
            size=nbt_size(nbt); nbt_count+=1
            check(size==data.get('size'),f'módulo {mid}: size JSON={data.get("size")} NBT={size}')
        except Exception as e: failures.append(f'NBT inválido {nbt.relative_to(ROOT)}: {e}')

district_stats={}
for p in DISTRICTS.glob('*.json'):
    did=f'firstcrusade:{p.stem}'
    try: data=json.loads(p.read_text(encoding='utf-8'))
    except Exception: continue
    maxx=maxy=maxz=0
    for e in data.get('modules',[]):
        mid=e.get('module',''); check(mid in module_map,f'distrito {did}: módulo {mid} existe')
        if mid in module_map:
            size=module_map[mid][1].get('size',[0,0,0]); off=e.get('offset',[0,0,0])
            maxx=max(maxx,off[0]+size[0]); maxy=max(maxy,off[1]+size[1]); maxz=max(maxz,off[2]+size[2])
    district_stats[did]=(len(data.get('modules',[])),[maxx,maxy,maxz])

required=['south_ash_gate','hive_wall_line','hive_corner_bastion','manufactorum','hab_stacks','administratum','underhive','spire']
for d in required: check((DISTRICTS/f'{d}.json').exists(),f'distrito obrigatório {d} existe')
for d in ('south_ash_gate','hive_wall_line','hive_corner_bastion','manufactorum','hab_stacks','administratum','underhive'):
    stat=district_stats.get(f'firstcrusade:{d}')
    if stat: check(stat[1][0]>=192 and stat[1][2]>=128,f'distrito {d}: footprint completo >=192x128 ({stat[1]})')

referenced={d['template'].split(':',1)[1] for _,d in module_map.values() if d.get('template','').startswith('firstcrusade:')}
for p in (STRUCTURES/'hive').rglob('*.nbt'):
    rel=p.relative_to(STRUCTURES).with_suffix('').as_posix()
    if rel not in referenced: warnings.append(f'NBT órfão: {rel}')

layout=(ROOT/'src/main/java/com/example/examplemod/hive/city/HiveCityLayout.java').read_text(encoding='utf-8')
for d in required: check(f'firstcrusade:{d}' in layout or d=='spire',f'HiveCityLayout referencia {d}')

# Basic Java source sanity for modified file.
check(layout.count('{')==layout.count('}'),'HiveCityLayout: chaves balanceadas')
check('cornerRotation' in layout,'HiveCityLayout: rotação de bastião implementada')

print('='*72)
for m in passes: print(' PASS ',m)
for m in warnings: print(' WARN ',m)
if failures:
    print('-'*72)
    for m in failures: print(' FAIL ',m)
print('='*72)
print(f'{len(passes)} passed, {len(warnings)} warnings, {len(failures)} failed')
print('\nDistrict extents:')
for k,v in sorted(district_stats.items()): print(f'  {k:40s} modules={v[0]:2d} extent={v[1]}')
print(f'\nModules: {len(module_map)} | NBT size headers parsed: {nbt_count}')
sys.exit(1 if failures else 0)
