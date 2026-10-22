"""Browser test for the generated userscript, on a local copy of RankedBoost's button markup (no network, no real site).
   python tools/rankedboost/test_userscript_browser.py      (needs: pip install playwright + a Chromium)"""
import json,sys
from pathlib import Path
from playwright.sync_api import sync_playwright
HERE=Path(__file__).resolve().parent;SCRIPT=(HERE/'savequest-rankedboost.user.js').read_text(encoding='utf8')
def sram(caught=(),flags=()):
    b=bytearray(32768)
    for i in range(7):b[0x200b+i]=0x80+i
    for n in caught:b[0x2a4c+((n-1)>>3)]|=1<<((n-1)&7)
    for f in flags:b[0x261f+(f>>3)]|=1<<(f&7)
    s=sum(b[0x2009:0x2d69])&0xffff;b[0x2d69]=s&255;b[0x2d6a]=s>>8;return bytes(b)
def item(slug,label,kind,pressed='false'):
    return f'<div data-v3d-detail="{label}|{kind}|x"><button class="v3d-it-detail__got-btn" data-v3d-toggle="{slug}" aria-pressed="{pressed}" onclick="this.setAttribute(\'aria-pressed\',this.getAttribute(\'aria-pressed\')===\'true\'?\'false\':\'true\');this.textContent=this.getAttribute(\'aria-pressed\')===\'true\'?\'Collected\':\'Mark as collected\'">Mark as collected</button></div>'
PAGE='<!doctype html><title>t</title>'+item('full-heal','Full Heal','hidden')+item('ether','Ether','field')+item('nugget','Nugget','field','true')
def run():
    fails=[]
    with sync_playwright() as p:
        br=p.chromium.launch(args=['--no-sandbox']);ctx=br.new_context();page=ctx.new_page()
        page.on('dialog',lambda d:d.accept())
        page.route('https://rankedboost.com/**',lambda r:r.fulfill(status=200,content_type='text/html',body=PAGE))
        page.goto('https://rankedboost.com/pokemon/location/gold-silver/azalea-town/')
        page.evaluate(SCRIPT)
        def check(ok,msg):
            print(('PASS ' if ok else 'FAIL ')+msg)
            if not ok:fails.append(msg)
        check(page.locator('#savequest-rankedboost-import').count()==1,'panel appears on a RankedBoost location page')
        page.set_input_files('#savequest-rankedboost-import input[type=file]',files=[{'name':'s.srm','mimeType':'application/octet-stream','buffer':sram(caught=[152],flags=[177])}])
        page.wait_for_timeout(300)
        txt=page.inner_text('#savequest-rankedboost-import [role=status]')
        check('1 caught Pokémon' in txt and '1 eligible unchecked' in txt,'preview counts caught + eligible items: '+txt.splitlines()[0]+' / '+txt.splitlines()[2])
        check(page.get_attribute('[data-v3d-toggle=full-heal]','aria-pressed')=='false','preview does not change any checkbox')
        page.click('text=Apply to RankedBoost');page.wait_for_timeout(600)
        check(page.get_attribute('[data-v3d-toggle=full-heal]','aria-pressed')=='true','finished hidden item is ticked')
        check(page.get_attribute('[data-v3d-toggle=ether]','aria-pressed')=='false','unfinished item is left alone')
        check(page.get_attribute('[data-v3d-toggle=nugget]','aria-pressed')=='true','existing tick is never removed')
        caught=json.loads(page.evaluate("localStorage.getItem('rb_pokemon_caught_gold-silver')"))
        check(caught=={'chikorita':True},'caught Pokémon merged into the native key')
        stored=page.evaluate("localStorage.getItem('savequest_rb_progress_v1')")
        check('Trainer' not in stored and len(stored)<5000,'only derived flags are stored (no name / bytes)')
        page.evaluate("localStorage.setItem('rb_pokemon_caught_gold-silver','[1,2]')")
        page.evaluate("document.querySelector('#savequest-rankedboost-import').remove()");page.evaluate(SCRIPT)
        check('unexpected format' in page.inner_text('#savequest-rankedboost-import [role=status]'),'refuses to touch unexpected RankedBoost data')
        # embedded mode (app WebView): save handed over as base64, no file picker, variable removed at once
        import base64
        ctx2=br.new_context();page2=ctx2.new_page();page2.on('dialog',lambda d:d.accept())
        page2.route('https://rankedboost.com/**',lambda r:r.fulfill(status=200,content_type='text/html',body=PAGE))
        page2.goto('https://rankedboost.com/pokemon/location/gold-silver/azalea-town/')
        page2.evaluate("window.__SQ_SAVE_B64='"+base64.b64encode(sram(caught=[152],flags=[177])).decode()+"';\n"+SCRIPT)
        check(page2.evaluate("typeof window.__SQ_SAVE_B64")=='undefined','embedded: save variable is removed right after injection')
        check(not page2.is_visible('#savequest-rankedboost-import input[type=file]'),'embedded: no file picker shown')
        check('1 eligible unchecked' in page2.inner_text('#savequest-rankedboost-import [role=status]'),'embedded: preview works straight away')
        page2.click('text=Apply to RankedBoost');page2.wait_for_timeout(600)
        check(page2.get_attribute('[data-v3d-toggle=full-heal]','aria-pressed')=='true','embedded: Apply ticks the item')
        page3=br.new_context().new_page();page3.route('https://rankedboost.com/**',lambda r:r.fulfill(status=200,content_type='text/html',body=PAGE))
        page3.goto('https://rankedboost.com/pokemon/location/gold-silver/azalea-town/')
        page3.evaluate("window.__SQ_SAVE_B64='AAAA';\n"+SCRIPT)
        check('could not be used' in page3.inner_text('#savequest-rankedboost-import [role=status]'),'embedded: a bad save is reported, nothing applied')
        br.close()
    return fails
if __name__=='__main__':sys.exit(1 if run() else 0)
