"""OPTIONAL, local only: make sprites for the app. Output goes to app/src/main/assets/sprites/,
which is git-ignored so no Nintendo art is ever committed (the project promises to bundle no art).
Sprites are cosmetic; the app works without them.
  py -3 -m pip install Pillow
  py -3 tools/build_sprites.py C:\\path\\to\\pokegold      (the same pret/pokegold clone used by build_catalog.py)
"""
import json,re,sys,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'app/src/main/assets/sprites'
def pokemon(pret):
    from PIL import Image
    names=[n.lower() for n in re.findall(r'^\s*const\s+(\w+)',(pret/'constants/pokemon_constants.asm').read_text(encoding='utf8',errors='replace'),re.M)[:251]]
    (OUT/'pokemon').mkdir(parents=True,exist_ok=True);n=0
    for dex,slug in enumerate(names,1):
        src=pret/'gfx/pokemon'/slug/'front_silver.png'
        if not src.is_file():print('missing',slug);continue
        im=Image.open(src).convert('RGBA');px=im.load()
        for y in range(im.height):
            for x in range(im.width):
                r,g,b,a=px[x,y]
                if r>250 and g>250 and b>250:px[x,y]=(255,255,255,0)
        im.resize((im.width*3,im.height*3),Image.NEAREST).save(OUT/'pokemon'/f'{dex:03d}.png');n+=1
    print(n,'pokemon sprites')
def items():
    (OUT/'items').mkdir(parents=True,exist_ok=True)
    stems=sorted({s['sprite'] for s in json.loads((ROOT/'tools/spots.json').read_text(encoding='utf8')) if s['cat']=='item' and s['sprite']});ok=0
    for s in stems:
        t=OUT/'items'/f'{s}.png'
        if t.exists():ok+=1;continue
        try:
            t.write_bytes(urllib.request.urlopen(f'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/{s}.png',timeout=20).read());ok+=1
        except Exception as e:print('skip',s,e)
    print(ok,'/',len(stems),'item icons')
if __name__=='__main__':
    if len(sys.argv)>1:pokemon(Path(sys.argv[1]))
    items()
