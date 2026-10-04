import copy
import io
import json
import os
import unittest
import uuid
from fg_server.business_sync import validate,preserve,SyncConflict
from fg_server.service import Service
from fg_server.app import Application

def records(tenant='company',branch='main'):
    def row(t,key,**b):return dict(table=t,id=key,body=b)
    scope=dict(organization_id=tenant,branch_id=branch)
    return [row('organizations',tenant,id=tenant,name='Business'),row('branches',branch,id=branch,organization_id=tenant,name='Branch'),
        row('subscribers','s',id='s',**scope,name='Subscriber',phone='',service='HOTSPOT',account='user',currency='EGP',created_at=1),
        row('ledger','c',id='c',**scope,subscriber_id='s',kind='CHARGE',amount_minor=100,currency='EGP',note='charge',created_at=2,reversal_of=None)]

class ContractTest(unittest.TestCase):
    def test_valid_money_scope_and_immutable_history(self):
        a=records();old=validate(a,'company','main');preserve(old,old)
        for field,value in [('amount_minor',True),('currency','USD'),('branch_id','other'),('subscriber_id','missing')]:
            bad=copy.deepcopy(a);bad[-1]['body'][field]=value
            with self.subTest(field=field),self.assertRaises((ValueError,PermissionError)):validate(bad,'company','main')
        for table,field,value in [('ledger','note',''),('subscribers','name',''),('subscribers','service','INVALID')]:
            bad=copy.deepcopy(a);next(r for r in bad if r['table']==table)['body'][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):validate(bad,'company','main')
        changed=copy.deepcopy(a);changed[-1]['body']['note']='changed'
        with self.assertRaises(ValueError):preserve(old,validate(changed,'company','main'))
        with self.assertRaises(ValueError):preserve(old,validate(a[:-1],'company','main'))
    def test_private_tables_and_duplicate_ids_are_rejected(self):
        a=records();a.append(copy.deepcopy(a[0]))
        with self.assertRaises(ValueError):validate(a,'company','main')
        a=records();a[-1]['table']='local_accounts'
        with self.assertRaises(ValueError):validate(a,'company','main')
    def test_http_sync_conflict_and_scope_denial(self):
        class Fixture:
            def business_sync_write(self,token,body):raise SyncConflict()
            def business_sync_read(self,token):raise PermissionError()
        app=Application(Fixture());states=[]
        data=b'{}'
        app(dict(REQUEST_METHOD='POST',PATH_INFO='/v1/business/sync',CONTENT_TYPE='application/json',CONTENT_LENGTH='2',**{'wsgi.input':io.BytesIO(data)}),lambda s,h:states.append(s))
        app(dict(REQUEST_METHOD='GET',PATH_INFO='/v1/business/sync',**{'wsgi.input':io.BytesIO()}),lambda s,h:states.append(s))
        self.assertEqual(['409 Conflict','403 Forbidden'],states)

@unittest.skipUnless(os.getenv('FG_DATABASE_URL'),'PostgreSQL integration requires FG_DATABASE_URL')
class ReplicaIntegrationTest(unittest.TestCase):
    def setUp(self):
        self.s=Service(os.environ['FG_DATABASE_URL']);self.s.migrate();self.tenant=str(uuid.uuid4());self.name='sync-'+self.tenant
        self.s.create_account(self.tenant,'main',self.name,'Long test password!','owner');self.token=self.s.login(self.name,'Long test password!')
    def write(self,data,revision=0,token=None):return self.s.business_sync_write(token or self.token,dict(revision=revision,device='test-phone',records=data))
    def test_two_clients_replay_revision_conflict_and_financial_immutability(self):
        data=records(self.tenant);result=self.write(data);self.assertEqual(1,result['revision'])
        self.assertEqual(1,self.write(data)['revision'])
        remote=self.s.business_sync_read(self.token);self.assertEqual(4,len(remote['records']))
        extra=copy.deepcopy(data);extra.append(dict(table='ledger',id='p',body=dict(id='p',organization_id=self.tenant,branch_id='main',subscriber_id='s',kind='PAYMENT',amount_minor=-25,currency='EGP',note='receipt',created_at=3,reversal_of=None)))
        with self.assertRaises(SyncConflict):self.write(extra)
        self.assertEqual(2,self.write(extra,1)['revision'])
        with self.assertRaises(ValueError):self.write(data,2)
        with self.s.connect() as db:
            self.assertEqual(75,db.execute('SELECT SUM(amount) AS balance FROM ledger WHERE tenant=%s',(self.tenant,)).fetchone()['balance'])
        with self.assertRaises(Exception):
            with self.s.connect() as db:db.execute('UPDATE business_sync_records SET body=%s::jsonb WHERE tenant=%s',('{}',self.tenant))
        with self.assertRaises(Exception):
            with self.s.connect() as db:db.execute('DELETE FROM ledger WHERE tenant=%s',(self.tenant,))
    def test_branch_tenant_roles_and_legacy_event_replay(self):
        self.write(records(self.tenant))
        for role,branch,tenant in [('reader','main',self.tenant),('owner','other',self.tenant),('owner','main','other-'+self.tenant)]:
            name=str(uuid.uuid4());self.s.create_account(tenant,branch,name,'Long test password!',role);token=self.s.login(name,'Long test password!')
            if role=='reader':
                with self.assertRaises(PermissionError):self.s.business_sync_read(token)
            else:self.assertEqual([],self.s.business_sync_read(token)['records'])
            with self.assertRaises((PermissionError,ValueError)):self.write(records(self.tenant),token=token)
        event=dict(version=1,id='c',device='phone',kind='ledger.append',body=dict(subscriber='s',currency='EGP',amount_minor=100,reversal_of=None,note='charge'))
        self.s.ingest(self.token,[event]);self.s.work();self.assertEqual('APPLIED',self.s.page(self.token)[0]['state'])
