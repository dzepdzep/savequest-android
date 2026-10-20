"""Headbutt tree map for Routes 29-33 and 42-46 from the pinned pret/pokegold sources.
Usage: python tools/build_trees.py /path/to/pokegold  -> app/src/main/assets/trees.json
Rules (all from the game's own code, see engine/events/treemons.asm and home/map.asm):
  * a map block (.blk byte) has 4 collision squares in order top-left, top-right, bottom-left, bottom-right;
  * a square is a Headbutt tree when its collision is HEADBUTT_TREE (data/tilesets/johto_collision.asm);
  * tree score = ((X*Y + X + Y) / 5) % 10 with X = x+4, Y = y+4 (square coordinates);
  * the tree is RARE for a player when score == (Trainer ID % 10).
Only trees with at least one walkable neighbouring square are kept.
"""
import json,re,sys
from pathlib import Path
src=Path(sys.argv[1]);ROOT=Path(__file__).resolve().parents[1]
coll=[m.groups() for l in (src/'data/tilesets/johto_collision.asm').read_text(encoding='utf8').splitlines() for m in [re.match(r'\s*tilecoll\s+(\w+),\s*(\w+),\s*(\w+),\s*(\w+)',l)] if m]
def cls(n):
    if n=='HEADBUTT_TREE':return 'T'
    if n=='CUT_TREE':return 'c'
    if n in('WALL','01'):return '#'
    if n=='TALL_GRASS':return '"'
    if 'WATER' in n or n in('WHIRLPOOL','BUOY'):return '~'
    if 'LEDGE' in n or n.startswith('HOP'):return 'l'
    return '.'
consts=(src/'constants/map_constants.asm').read_text(encoding='utf8')
out={}
for n in (29,30,31,32,33,42,43,44,45,46):
    k=f'ROUTE_{n}';m=re.search(rf'map_const\s+{k},\s*(\d+),\s*(\d+)',consts);w,h=int(m[1]),int(m[2])
    blk=(src/f'maps/Route{n}.blk').read_bytes();assert len(blk)==w*h
    grid=[''.join(cls(coll[blk[(y//2)*w+x//2]][(x&1)+2*(y&1)]) for x in range(w*2)) for y in range(h*2)]
    trees=[]
    for y,row in enumerate(grid):
        for x,c in enumerate(row):
            if c!='T':continue
            if any(0<=x+dx<w*2 and 0<=y+dy<h*2 and grid[y+dy][x+dx] in '."l' for dx,dy in((0,1),(0,-1),(1,0),(-1,0))):
                X,Y=x+4,y+4;trees.append({'x':x,'y':y,'score':((X*Y+X+Y)//5)%10})
    out[k]={'w':w*2,'h':h*2,'grid':grid,'trees':trees}
(ROOT/'app/src/main/assets/trees.json').write_text(json.dumps(out,separators=(',',':')))
print({k:len(v['trees']) for k,v in out.items()})
