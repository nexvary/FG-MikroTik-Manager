import io
import json
import os
import unittest
from fg_server.app import Application


def request(app, path, method='GET', body=None, token=''):
    data=json.dumps(body).encode() if body is not None else b''
    result=[]
    env=dict(REQUEST_METHOD=method,PATH_INFO=path,HTTP_AUTHORIZATION='Bearer '+token,
             CONTENT_TYPE='application/json',CONTENT_LENGTH=str(len(data)))
    env['wsgi.input']=io.BytesIO(data)
    response=b''.join(app(env,lambda status,headers:result.append((status,dict(headers)))))
    return *result[0],response


class PanelAssetsTest(unittest.TestCase):
    def test_public_assets_have_security_headers_and_no_directory_traversal(self):
        app=Application(None)
        for path,mime in [('/panel/','text/html'),('/panel/panel.js','text/javascript'),('/panel/panel.css','text/css'),('/panel/fg-machines.svg','image/svg+xml')]:
            status,headers,body=request(app,path)
            self.assertEqual('200 OK',status);self.assertTrue(headers['Content-Type'].startswith(mime));self.assertTrue(body)
            self.assertIn("frame-ancestors 'none'",headers['Content-Security-Policy'])
            self.assertIn("script-src 'self'",headers['Content-Security-Policy'])
            self.assertNotIn('unsafe-inline',headers['Content-Security-Policy'])
            self.assertEqual('no-store',headers['Cache-Control'])
            self.assertEqual(b'',request(app,path,'HEAD')[2])
        for path in ['/panel/../schema.sql','/panel/%2e%2e/schema.sql','/panel/unknown','/panel/index.html']:
            self.assertEqual('404 Not Found',request(app,path)[0])

    def test_logout_contract_never_passes_extra_parameters(self):
        class Fixture:
            seen=[]
            def logout(self,token):self.seen.append(token)
        fixture=Fixture();app=Application(fixture)
        self.assertEqual('400 Bad Request',request(app,'/v1/logout','POST',{'tenant':'other'},'token')[0])
        self.assertEqual([],fixture.seen)
        self.assertEqual('204 No Content',request(app,'/v1/logout','POST',{},'token')[0])
        self.assertEqual(['token'],fixture.seen)


@unittest.skipUnless(os.getenv('FG_DATABASE_URL'),'PostgreSQL required')
class PanelSessionTest(unittest.TestCase):
    def test_logout_revokes_only_its_token_and_is_idempotent(self):
        import uuid
        from fg_server.service import Service
        s=Service(os.environ['FG_DATABASE_URL']);s.migrate();name='panel-'+str(uuid.uuid4())
        s.create_account(name,'main',name,'Panel test password!','owner')
        token=s.login(name,'Panel test password!');self.assertEqual(name,s.identity(token)['tenant'])
        s.logout(token)
        with self.assertRaises(PermissionError):s.identity(token)
        replacement=s.login(name,'Panel test password!');s.logout(token)
        self.assertEqual(name,s.identity(replacement)['tenant'])
        s.logout(replacement)
