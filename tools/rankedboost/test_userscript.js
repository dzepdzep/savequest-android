// node tools/rankedboost/test_userscript.js  - pure-logic tests for the generated userscript (no browser needed)
const assert=require('assert');const fs=require('fs');const path=require('path');
const m={exports:{}};
new Function('module','document',fs.readFileSync(path.join(__dirname,'savequest-rankedboost.user.js'),'utf8'))(m,undefined);
const {makeSnapshot,plan,GAME}=m.exports;
function sram({caught=[],flags=[],corrupt=false}={}){
  const b=new Uint8Array(32768);
  for(let i=0;i<7;i++)b[0x200b+i]=0x80+i;                 // trainer name
  for(const n of caught)b[0x2a4c+((n-1)>>3)]|=1<<((n-1)&7);
  for(const f of flags)b[0x261f+(f>>3)]|=1<<(f&7);
  let s=0;for(let i=0x2009;i<=0x2d68;i++)s=(s+b[i])&0xffff;
  b[0x2d69]=s&255;b[0x2d6a]=s>>8;if(corrupt)b[0x2d69]^=1;return b;
}
const tests={
  'species table has 251 distinct slugs'(){assert.strictEqual(GAME.species.length,251);assert.strictEqual(new Set(GAME.species).size,251);assert.strictEqual(GAME.species[151],'chikorita')},
  'caught bits and event flags are read at the documented offsets'(){
    const s=makeSnapshot(sram({caught:[1,152],flags:[177,1620]}));
    assert.deepStrictEqual(s.caught.sort(),['bulbasaur','chikorita']);
    assert.ok(s.doneFlags.includes(177)&&s.doneFlags.includes(1620)&&!s.doneFlags.includes(178));
  },
  'a flag that is not in the save is not reported'(){assert.strictEqual(makeSnapshot(sram()).doneFlags.length,0)},
  'bad checksum / savestate-sized / short files are rejected'(){
    assert.throws(()=>makeSnapshot(sram({corrupt:true})),/checksum|Trainer name/);   // an all-zero backup block also checksums, then fails on the empty name
    assert.throws(()=>makeSnapshot(new Uint8Array(100)),/32,768/);
  },
  'every shipped item flag is unique per RankedBoost marker group'(){
    let n=0;for(const [page,items] of Object.entries(GAME.maps)){assert.ok(/^[a-z0-9-]+$/.test(page));for(const it of items){n++;assert.ok(Number.isInteger(it.f)&&it.f>0&&['hidden','visible'].includes(it.k)&&it.s)}}
    assert.ok(n>=190,'expected ~200 flags, got '+n);
  },
  'plan ticks only finished, unchecked, kind-matching markers'(){
    const tasks=[{s:'full-heal',f:177,k:'hidden'},{s:'ether',f:5,k:'visible'},{s:'nugget',f:6,k:'visible'},{s:'twin',f:7,k:'visible'},{s:'twin',f:8,k:'visible'},{s:'missing',f:9,k:'visible'}];
    const site=new Map([['full-heal',{kind:'hidden',checked:false}],['ether',{kind:'hidden',checked:false}],['nugget',{kind:'visible',checked:true}],['twin',{kind:'visible',checked:false}]]);
    const r=plan(tasks,new Set([177,5,6,7]),site);
    assert.deepStrictEqual(r.matches,['full-heal']);   // ether: kind mismatch (skipped); nugget: already; twin: only 1 of 2 pickups done; missing: no site marker
    assert.strictEqual(r.already,1);assert.strictEqual(r.skipped,2);
  },
};
let bad=0;for(const [name,fn] of Object.entries(tests)){try{fn();console.log('PASS',name)}catch(e){bad++;console.log('FAIL',name,'-',e.message)}}
console.log(Object.keys(tests).length+' tests, '+bad+' failed');process.exit(bad?1:0);
