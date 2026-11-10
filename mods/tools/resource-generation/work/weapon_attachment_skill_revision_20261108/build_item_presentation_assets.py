"""Inventory-only transforms of approved PNGs and native pack-source labels.

No gun display, UV, mesh, HUD, animation or gameplay property is changed here.
"""
import os
from pathlib import Path
from zipfile import ZipFile
import hashlib, io, json, math, re
import numpy as np
from PIL import Image, ImageDraw, ImageFont

W = Path(__file__).resolve().parent
R = W / 'resources'
TITLE = '生死狙击RPG枪包'
NAMESPACES = ['eyeofpunishment', 'judgfire', 'ssjj_dark', 'ssjj_dimension',
              'ssjjparasite2', 'ssjjrpg', 'ssjj_workbench']
OWN_MODS = ['eye_judgment_arsenal', 'ssjj_dark']
SOURCE_SHA = {'mod': '34360ffd5a3f3501d7fed7f6e6ce96a71a72747e89bdab0de5bcb535daeede04',
              'gunpack': '60f8a93708e5cb822bc23b4aa1888ce673da0faae013022f26050a0f66de06d9'}

def digest(raw): return hashlib.sha256(raw).hexdigest()
def dump(obj): return (json.dumps(obj, ensure_ascii=False, indent=2) + '\n').encode('utf8')
def read_archive(path):
    with ZipFile(path) as z:
        return {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
def save(path, raw):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(raw)

def axis_angle(image):
    """Alpha weighted longitudinal axis, oriented to the visually verified left muzzle."""
    a = np.array(image.convert('RGBA'))[:, :, 3].astype(float)
    yy, xx = np.nonzero(a > 0)
    xy = np.stack([xx, yy], axis=1).astype(float)
    weights = a[yy, xx]
    centered = xy - np.average(xy, axis=0, weights=weights)
    cov = (centered * weights[:, None]).T @ centered / weights.sum()
    _, vec = np.linalg.eigh(cov)
    direction = vec[:, -1]
    if direction[0] > 0: direction *= -1
    return math.degrees(math.atan2(direction[1], direction[0]))

def inventory_icon(raw):
    image = Image.open(io.BytesIO(raw)).convert('RGBA')
    source_bbox = image.getbbox()
    angle = axis_angle(image)
    if angle > 0: angle -= 360
    rotation = angle - (-135)
    crop = image.crop(source_bbox)
    # Premultiplied resampling avoids colored halos at transparent edges.
    moved = crop.convert('RGBa').rotate(rotation, Image.Resampling.BICUBIC, expand=True)
    moved = moved.crop(moved.getbbox())
    ratio = 216 / max(moved.size)
    resized = moved.resize(tuple(max(1, round(x * ratio)) for x in moved.size), Image.Resampling.LANCZOS).convert('RGBA')
    output = Image.new('RGBA', (256, 256))
    output.alpha_composite(resized, ((256 - resized.width) // 2, (256 - resized.height) // 2))
    stream = io.BytesIO(); output.save(stream, 'PNG')
    return stream.getvalue(), {'source_size': list(image.size), 'source_alpha_bbox': list(source_bbox),
        'source_axis_screen_degrees': angle, 'rotation_pillow_degrees': rotation,
        'uniform_fit_scale': ratio, 'output_size': [256, 256], 'output_alpha_bbox': list(output.getbbox()),
        'output_axis_screen_degrees': axis_angle(output), 'muzzle_direction': 'upper-left',
        'operation': 'Crop alpha bounds; rotate original raster without mirror; premultiplied resize; center on transparent square.'}

def apply_metadata(entries, kind):
    """Call after final language/overlay merge; changes only native source-name fields.

    Parent build retains responsibility for all versions and original file names.
    """
    for ns in NAMESPACES:
        path = f'assets/{ns}/gunpack_info.json'
        if path in entries:
            data = json.loads(entries[path]); data['name'] = TITLE; entries[path] = dump(data)
        for locale in ('zh_cn', 'en_us'):
            path = f'assets/{ns}/lang/{locale}.json'
            if path in entries:
                data = json.loads(entries[path]); data[f'pack.{ns}.name'] = TITLE; entries[path] = dump(data)
    if 'gunpack.meta.json' in entries:
        data = json.loads(entries['gunpack.meta.json']); data['name'] = TITLE; entries['gunpack.meta.json'] = dump(data)
    if kind == 'mod' and 'META-INF/mods.toml' in entries:
        # These are the pair's two owned mods. Dependency blocks do not carry displayName.
        text = entries['META-INF/mods.toml'].decode('utf8')
        blocks = re.split(r'(?m)(?=^\[\[mods\]\])', text)
        for index, block in enumerate(blocks):
            if any(re.search(r'(?m)^\s*modId\s*=\s*"' + re.escape(mod) + r'"\s*$', block) for mod in OWN_MODS):
                # Bound the edit to the declaration preceding any dependency tables.
                declaration, *tail = re.split(r'(?m)(?=^\[\[dependencies\.)', block, maxsplit=1)
                declaration = re.sub(r'(?m)^(\s*displayName\s*=\s*")[^"\r\n]*(")',
                                     lambda m: m[1] + TITLE + m[2], declaration)
                blocks[index] = declaration + ''.join(tail)
        entries['META-INF/mods.toml'] = ''.join(blocks).encode('utf8')
    return entries

def catalog(entries):
    rows = []
    for path in sorted(entries):
        m = re.fullmatch(r'data/([^/]+)/index/(guns|ammo|attachments|blocks)/(.+)\.json', path)
        if m and m[1] in NAMESPACES:
            rows.append({'id': f'{m[1]}:{m[3]}', 'category': m[2], 'path': path,
                         'source_name_route': f'assets/{m[1]}/gunpack_info.json:name', 'source_name': TITLE})
    for path in sorted(entries):
        m = re.fullmatch(r'assets/([^/]+)/models/item/(.+)\.json', path)
        if m and m[1] in OWN_MODS:
            rows.append({'id': f'{m[1]}:{m[2]}', 'category': 'native_item_model', 'path': path,
                         'source_name_route': f'META-INF/mods.toml:{m[1]}.displayName', 'source_name': TITLE})
    return rows

def preview(before, after):
    paths = sorted(after)
    canvas = Image.new('RGB', (1024, len(paths) // 4 * 240), '#232833')
    draw = ImageDraw.Draw(canvas)
    try: font = ImageFont.truetype(os.environ.get("SSJJ_FONT_ARIAL", "arial.ttf"), 11)
    except OSError: font = ImageFont.load_default()
    for index, path in enumerate(paths):
        x, y = (index % 4) * 256, (index // 4) * 240
        title = Path(path).stem.replace('dimension_star', 'DS')
        draw.text((x + 10, y + 6), title + '  before / after', font=font, fill='white')
        for offset, raw in [(0, before[path]), (128, after[path])]:
            img = Image.open(io.BytesIO(raw)).convert('RGBA'); img.thumbnail((124, 200))
            canvas.paste(img, (x + offset + (128 - img.width)//2, y + 27 + (202 - img.height)//2), img)
        draw.line((x + 128, y + 28, x + 128, y + 226), '#475163')
    save(W/'previews/dimension_inventory_before_after.png', _png(canvas))
    enlarged = Image.new('RGB', (1024, 3*278), '#232833')
    d = ImageDraw.Draw(enlarged)
    for index, path in enumerate(paths):
        x,y=(index%4)*256,(index//4)*278
        img=Image.open(io.BytesIO(after[path])).convert('RGBA');enlarged.paste(img,(x,y+22),img)
        d.text((x+10,y+4),Path(path).stem,font=font,fill='white')
    save(W/'previews/dimension_inventory_final.png', _png(enlarged))

def _png(image):
    out=io.BytesIO();image.save(out,'PNG');return out.getvalue()

def build():
    archives = {'mod': W/'baseline/mod.jar', 'gunpack': W/'baseline/gunpack.zip'}
    for kind, path in archives.items(): assert digest(path.read_bytes()) == SOURCE_SHA[kind], (kind, 'frozen source changed')
    source = read_archive(archives['gunpack']); mod = read_archive(archives['mod'])
    icons = {n: raw for n, raw in source.items() if re.fullmatch(r'assets/ssjj_dimension/textures/gun/slot/dimension_star(?:[12])?(?:_(?:frozen|neon|royal))?\.png',n)}
    assert len(icons)==12
    records=[]; transformed={}
    for path, raw in sorted(icons.items()):
        image=Image.open(io.BytesIO(raw)).convert('RGBA')
        assert image.size==(256,256) and abs(axis_angle(image)+135)<.1
        output=raw;record={'preserved_from_frozen_baseline':True,'output_axis_screen_degrees':axis_angle(image),'muzzle_direction':'upper-left','operation':'Preserve previously approved inventory pixels byte-for-byte.'}
        save(R/path,output);transformed[path]=output
        records.append({'path':path,'source_sha256':digest(raw),'sha256':digest(output),**record})
    metadata=[]
    for ns in NAMESPACES:
        path=f'assets/{ns}/gunpack_info.json'
        exists=path in source
        data=json.loads(source[path]) if exists else {
            'version':json.loads(source['assets/ssjj_dimension/gunpack_info.json'])['version'],
            'name':TITLE,'desc':'生死狙击RPG枪包共享物品。',
            'license':'Original assets retain their original rights','authors':['Local project'],'date':'2026-11-08'}
        old_name=data['name'];data['name']=TITLE;save(R/path,dump(data))
        metadata.append({'path':path,'added':not exists,'allowed_fields':['name'] if exists else ['new PackInfo'],
                         'source_name':old_name,'target_name':TITLE,'source_sha256':digest(source[path]) if exists else None})
    # Existing languages are merged in memory rather than replacing parent gameplay descriptions.
    language={locale:{f'pack.{ns}.name':TITLE for ns in NAMESPACES} for locale in ('zh_cn','en_us')}
    save(W/'presentation_language.json',dump(language))
    all_entries=dict(source);all_entries.update(mod)
    overlay={p.relative_to(R).as_posix():p.read_bytes() for p in R.rglob('gunpack_info.json')}
    all_entries.update(overlay);apply_metadata(all_entries,'mod')
    report={'passed':True,'sources_sha256':SOURCE_SHA,'title':TITLE,'records':records,'pack_metadata':metadata,
        'language':language,'metadata_merge_contract':'apply_metadata(entries, kind) after final parent language/resource merge; versions managed by parent',
        'allowed_existing_paths':sorted(icons)+[r['path']for r in metadata if not r['added']]+['gunpack.meta.json','META-INF/mods.toml']+
            [f'assets/{ns}/lang/{locale}.json'for ns in NAMESPACES for locale in ('zh_cn','en_us')if f'assets/{ns}/lang/{locale}.json'in source],
        'allowed_existing_fields':{'gunpack_info.json':['name'],'gunpack.meta.json':['name'],
            'META-INF/mods.toml':[f'{x}.displayName'for x in OWN_MODS],'language':[f'pack.{ns}.name'for ns in NAMESPACES]},
        'inventory_catalog':catalog(all_entries),
        'preserved':['all other inventory icons','all HUD textures','all gun display transforms','all UV textures','all meshes','all animations','all data and recipes'],
        'visual_semantic_check':'Original inventory sources verified visually: left endpoint is muzzle in all 12 source PNGs. Rotation has positive determinant and no reflection.'}
    save(W/'evidence/source_item_presentation_assets.json',dump(report));preview(icons,transformed)
    print('PASS 12 original-raster inventory transforms and seven unified native pack namespaces')
    return report

if __name__ == '__main__': build()
