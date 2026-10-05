import json
import unittest
from unittest.mock import Mock, MagicMock, patch
from fg_server.app import Application
from fg_server.diagnostics import Diagnostics
from test_panel import request

class DiagnosticsTest(unittest.TestCase):
    def test_auth_role_and_exact_body_before_probes(self):
        s=Mock();app=Application(s);app.diagnostics=Mock();s.identity.side_effect=PermissionError()
        self.assertEqual('403 Forbidden',request(app,'/v1/diagnostics','POST',{},'invalid')[0]);app.diagnostics.run.assert_not_called()
        s.identity.side_effect=None;s.identity.return_value={'role':'cashier'}
        self.assertEqual('403 Forbidden',request(app,'/v1/diagnostics','POST',{},'cashier')[0])
        self.assertEqual('400 Bad Request',request(app,'/v1/diagnostics','POST',{'host':'127.0.0.1'},'owner')[0]);app.diagnostics.run.assert_not_called()
        for role in ['reader','owner']:
            s.identity.return_value={'role':role};app.diagnostics.run.return_value={'source':'server','checks':[]}
            status,_,body=request(app,'/v1/diagnostics','POST',{},role)
            self.assertEqual('200 OK',status);self.assertEqual('server',json.loads(body)['source'])
        self.assertEqual('404 Not Found',request(app,'/v1/diagnostics')[0])

    def test_independent_failures_cache_busy_no_exception_leak(self):
        runner=Diagnostics()
        with patch.object(runner,'database'),patch.object(runner,'dns',side_effect=OSError('private error')),patch.object(runner,'tcp'),patch.object(runner,'tls'):
            first=runner.run(Mock());again=runner.run(Mock())
            self.assertEqual(['passed','failed','passed','passed'],[c['state'] for c in first['checks']])
            self.assertFalse(first['cached']);self.assertTrue(again['cached']);self.assertEqual(first['checked_at'],again['checked_at'])
            self.assertNotIn('private error',json.dumps(first));self.assertTrue(all(c['elapsed_ms']>=0 for c in first['checks']))
        runner.lock.acquire()
        try:
            with self.assertRaises(RuntimeError):runner.run(Mock())
        finally:runner.lock.release()

    def test_fixed_targets_timeouts_verified_tls_and_db_read(self):
        with patch('fg_server.diagnostics.subprocess.run') as child:
            Diagnostics.dns();args,kwargs=child.call_args
            self.assertIn('example.com',args[0][-1]);self.assertEqual(2,kwargs['timeout']);self.assertTrue(kwargs['check']);self.assertNotIn('shell',kwargs)
        with patch('fg_server.diagnostics.socket.create_connection') as connect,patch('fg_server.diagnostics.ssl.create_default_context') as context:
            Diagnostics.tcp();connect.assert_called_with(('1.1.1.1',443),timeout=2)
            Diagnostics.tls();context.return_value.wrap_socket.assert_called_once();self.assertEqual('cloudflare-dns.com',context.return_value.wrap_socket.call_args.kwargs['server_hostname'])
        s=MagicMock();Diagnostics.database(s);calls=s.connect.return_value.__enter__.return_value.execute.call_args_list
        self.assertEqual("SET LOCAL statement_timeout = '2000ms'",calls[0].args[0]);self.assertEqual('SELECT 1',calls[1].args[0])

    def test_revocation_while_running_discards_results(self):
        s=Mock();s.identity.side_effect=[{'role':'owner'},PermissionError()];app=Application(s);app.diagnostics=Mock();app.diagnostics.run.return_value={'source':'server'}
        status,_,body=request(app,'/v1/diagnostics','POST',{},'token')
        self.assertEqual('403 Forbidden',status);self.assertNotIn(b'source',body)
