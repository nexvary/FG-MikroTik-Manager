import os
import unittest
import uuid
from fg_server.service import Service

@unittest.skipUnless(os.getenv('FG_DATABASE_URL'),'Postgres integration requires FG_DATABASE_URL')
class IntegrationTest(unittest.TestCase):
    def setUp(self):
        self.s=Service(os.environ['FG_DATABASE_URL']);self.s.migrate()
        self.tenant=str(uuid.uuid4());self.name='u-'+self.tenant
        self.s.create_account(self.tenant,'main',self.name,'Long test password!','owner')
        self.token=self.s.login(self.name,'Long test password!')
    def event(self,id='e1',amount=100,reversal=None):
        return dict(version=1,id=id,device='test-device',kind='ledger.append',body=dict(subscriber='s1',currency='EGP',amount_minor=amount,reversal_of=reversal,note='test'))
    def drain(self):
        while self.s.work(): pass
    def test_replay_conflict_reversal_and_tenant_isolation(self):
        e=self.event();self.s.ingest(self.token,[e,e]);self.drain()
        self.assertEqual('APPLIED',self.s.page(self.token)[0]['state'])
        self.assertEqual('CONFLICT',self.s.ingest(self.token,[self.event(amount=200)])[0]['state'])
        self.s.ingest(self.token,[self.event('r1',-100,'e1'),self.event('r2',-100,'e1')]);self.drain()
        states=[e['state'] for e in self.s.page(self.token)]
        self.assertEqual(['APPLIED','APPLIED','QUARANTINED'],states)
        with self.s.connect() as db:
            with self.assertRaises(Exception): db.execute('UPDATE ledger SET amount=1 WHERE tenant=%s',(self.tenant,))
        other='other-'+self.tenant;self.s.create_account(other,'main',other,'Long test password!','owner')
        self.assertEqual([],self.s.page(self.s.login(other,'Long test password!')))
    def test_reader_revocation_and_throttle(self):
        name='reader-'+self.tenant;self.s.create_account(self.tenant,'main',name,'Long test password!','reader')
        token=self.s.login(name,'Long test password!')
        with self.assertRaises(PermissionError): self.s.ingest(token,[self.event()])
        with self.s.connect() as db:db.execute('UPDATE accounts SET enabled=false WHERE username=%s',(name,))
        with self.assertRaises(PermissionError):self.s.page(token)
        for _ in range(5):
            with self.assertRaises(PermissionError):self.s.login(self.name,'wrong')
        with self.assertRaises(PermissionError):self.s.login(self.name,'Long test password!')
    def test_accounting_monotonic_and_stop_cannot_reopen(self):
        def event(id,status,seconds): return dict(version=1,id=id,device='radius',kind='radius.accounting',body=dict(nas='n1',session='s1',user='u1',status=status,seconds=seconds,input_octets=seconds,output_octets=seconds))
        self.s.ingest(self.token,[event('a','Start',0),event('b','Stop',60),event('c','Interim-Update',61)]);self.drain()
        self.assertEqual(['APPLIED','APPLIED','QUARANTINED'],[x['state'] for x in self.s.page(self.token)])
    def test_bounded_batch_and_cursor(self):
        import time
        events=[self.event('load-'+str(i),i+1) for i in range(100)]
        started=time.monotonic();self.s.ingest(self.token,events);self.drain()
        rows=self.s.page(self.token)
        self.assertEqual(100,len(rows));self.assertTrue(all(x['state']=='APPLIED' for x in rows))
        self.assertEqual([],self.s.page(self.token,rows[-1]['seq']))
        print('Isolated CI: 100 financial events ingest+apply seconds:',round(time.monotonic()-started,3))
        with self.assertRaises(ValueError):self.s.ingest(self.token,events+[self.event('too-many')])
    def test_radius_pap_credentials_expiry_nas_and_scope(self):
        from datetime import datetime,timedelta,timezone
        key=self.s.provision_nas(self.tenant,'main','nas-1')
        self.s.provision_radius_user(self.tenant,'main','subscriber','Subscriber password!',datetime.now(timezone.utc)+timedelta(hours=1))
        self.assertTrue(self.s.radius_authenticate(key,'subscriber','Subscriber password!'))
        with self.assertRaises(PermissionError):self.s.radius_authenticate(key,'subscriber','wrong')
        other=self.s.provision_nas(self.tenant,'other','nas-2')
        with self.assertRaises(PermissionError):self.s.radius_authenticate(other,'subscriber','Subscriber password!')
        self.s.provision_radius_user(self.tenant,'main','subscriber','Subscriber password!',datetime.now(timezone.utc)-timedelta(seconds=1))
        with self.assertRaises(PermissionError):self.s.radius_authenticate(key,'subscriber','Subscriber password!')
