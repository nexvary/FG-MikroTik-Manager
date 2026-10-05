"""Browser tests against the real HTTP service + PostgreSQL; fixtures are never shipped as data."""
import os
import threading
import unittest
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from wsgiref.simple_server import make_server
from fg_server.app import Application
from fg_server.http import BoundedWSGIServer, Handler
from fg_server.service import Service


@unittest.skipUnless(os.getenv('FG_PANEL_UI')=='1' and os.getenv('FG_DATABASE_URL'),'Opt-in PostgreSQL/Chromium acceptance')
class PanelBrowserTest(unittest.TestCase):
    def test_real_login_rtl_filters_sync_xss_logout_and_expired_session(self):
        from playwright.sync_api import sync_playwright, expect
        from test_business_sync import records
        s=Service(os.environ['FG_DATABASE_URL']);s.migrate();tenant='web-'+str(uuid.uuid4())
        password='Isolated panel password!';s.create_account(tenant,'main',tenant,password,'owner')
        token=s.login(tenant,password)
        payload=records(tenant);payload[2]['body']['name']='<img src=x onerror=alert(1)>'
        s.business_sync_write(token,dict(revision=0,device='browser-test',records=payload))
        s.provision_radius_user(tenant,'main','current-user',password,datetime.now(timezone.utc)+timedelta(days=1))
        s.provision_radius_user(tenant,'main','old-user',password,datetime.now(timezone.utc)-timedelta(days=1))
        other=tenant+'-other';s.create_account(tenant,'other',other,password,'owner')
        s.provision_radius_user(tenant,'other','foreign-user',password,datetime.now(timezone.utc)+timedelta(days=1))
        event=dict(version=1,id='start',device='browser-test',kind='radius.accounting',body=dict(nas='nas-web',session='session-web',user='current-user',status='Start',seconds=0,input_octets=0,output_octets=0))
        s.ingest(token,[event]);s.work()
        server=make_server('127.0.0.1',0,Application(s),handler_class=Handler,server_class=BoundedWSGIServer)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        proof=Path('panel-proof');proof.mkdir(exist_ok=True)
        try:
            with sync_playwright() as p:
                browser=p.chromium.launch();context=browser.new_context(viewport={'width':390,'height':844})
                page=context.new_page();errors=[];dialogs=[]
                page.on('pageerror',lambda error:errors.append(str(error)))
                page.on('dialog',lambda dialog:(dialogs.append(dialog.message),dialog.dismiss()))
                page.goto(f'http://127.0.0.1:{server.server_port}/panel/')
                expect(page.locator('html')).to_have_attribute('dir','rtl')
                page.locator('#username').fill(tenant);page.locator('#password').fill(password);page.get_by_role('button',name='تسجيل الدخول',exact=True).click()
                expect(page.locator('#workspace')).to_be_visible();expect(page.locator('#updated')).to_contain_text('آخر قراءة')
                self.assertEqual('',page.locator('#password').input_value())
                page.screenshot(path=str(proof/'dashboard-ar-mobile.png'),full_page=True)
                page.get_by_role('button',name='مستخدمو RADIUS',exact=True).click()
                expect(page.locator('table')).to_contain_text('current-user');expect(page.locator('table')).not_to_contain_text('foreign-user')
                page.locator('select').select_option('expired');expect(page.locator('table')).to_contain_text('old-user');expect(page.locator('table')).not_to_contain_text('current-user')
                page.get_by_role('button',name='جلسات RADIUS',exact=True).click();expect(page.locator('table')).to_contain_text('session-web')
                page.get_by_role('checkbox').check();expect(page.locator('#updated')).to_contain_text('آخر قراءة');expect(page.locator('table')).to_contain_text('current-user')
                page.get_by_role('button',name='بيانات الأعمال',exact=True).click();expect(page.locator('table')).to_contain_text('<img src=x onerror=alert(1)>')
                self.assertEqual([],dialogs);self.assertEqual(0,page.locator('#view img').count());self.assertTrue(page.evaluate('document.documentElement.scrollWidth <= innerWidth'))
                page.locator('select').select_option('ledger');expect(page.locator('table')).to_contain_text('1.00')
                page.screenshot(path=str(proof/'business-ar-mobile.png'),full_page=True)
                page.get_by_role('button',name='English',exact=True).click();expect(page.locator('html')).to_have_attribute('dir','ltr')
                page.set_viewport_size({'width':1440,'height':900});expect(page.locator('table')).to_contain_text('1.00')
                page.get_by_role('button',name='RADIUS users',exact=True).click();expect(page.locator('#updated')).to_contain_text('Last read')
                page.screenshot(path=str(proof/'radius-en-desktop.png'),full_page=True)
                page.get_by_role('button',name='Synchronization events',exact=True).click();expect(page.locator('table')).to_contain_text('APPLIED')
                page.get_by_role('button',name='Back',exact=True).click();expect(page.locator('#page-title')).to_have_text('RADIUS users');expect(page.locator('table')).to_contain_text('old-user')
                self.assertEqual(0,page.evaluate('localStorage.length'));self.assertEqual(0,page.evaluate('sessionStorage.length'))
                self.assertEqual([],errors)
                page.get_by_role('button',name='Sign out',exact=True).click();expect(page.locator('#login-card')).to_be_visible()
                # Expiry/revocation must clear displayed branch data, not leave a stale dashboard.
                page.locator('#username').fill(tenant);page.locator('#password').fill(password);page.get_by_role('button',name='Sign in',exact=True).click();expect(page.locator('#updated')).to_contain_text('Last read')
                with s.connect() as db:db.execute('DELETE FROM sessions WHERE account IN (SELECT id FROM accounts WHERE username=%s)',(tenant,))
                page.get_by_role('button',name='Refresh',exact=True).click();expect(page.locator('#login-card')).to_be_visible();expect(page.locator('#view')).to_be_empty();expect(page.locator('#message')).to_contain_text('expired')
                browser.close()
        finally:
            server.shutdown();server.server_close();thread.join(timeout=2)
